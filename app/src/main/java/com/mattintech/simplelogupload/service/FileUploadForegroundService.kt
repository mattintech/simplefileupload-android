package com.mattintech.simplelogupload.service

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mattintech.simplelogupload.ComposeMainActivity
import com.mattintech.simplelogupload.R
import com.mattintech.simplelogupload.constant.AppConstants
import com.mattintech.simplelogupload.constant.FileConstants
import com.mattintech.simplelogupload.constant.NetworkConstants
import com.mattintech.simplelogupload.controller.FileController
import com.mattintech.simplelogupload.controller.UploadController
import com.mattintech.simplelogupload.db.UploadHistoryRepository
import com.mattintech.simplelogupload.model.UploadHistory
import com.mattintech.simplelogupload.model.UploadResult
import com.mattintech.simplelogupload.upload.ChunkedUploadManager
import com.mattintech.simplelogupload.upload.UploadProgressCallback
import com.mattintech.simplelogupload.upload.UploadStrategySelector
import com.mattintech.simplelogupload.util.PreferencesUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Foreground service for file uploads with progress tracking
 */
class FileUploadForegroundService : Service() {

    companion object {
        private const val TAG = AppConstants.APP_TAG + "UploadService"
        private const val NOTIFICATION_ID = 1

        // Intent extras
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_MAX_DOWNLOADS = "extra_max_downloads"
        const val EXTRA_EXPIRY_HOURS = "extra_expiry_hours"
    }

    private lateinit var uploadController: UploadController
    private lateinit var notificationManager: NotificationManager
    private lateinit var repository: UploadHistoryRepository
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var lastProgressUpdate: Long = 0

    override fun onCreate() {
        super.onCreate()
        uploadController = UploadController(this)
        repository = UploadHistoryRepository(application as Application)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "========== Foreground Service started ==========")

        if (intent == null) {
            Log.e(TAG, "Intent is null")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Intent received, extracting parameters...")

        // Get file path from intent
        var filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val maxDownloads = intent.getIntExtra(EXTRA_MAX_DOWNLOADS, NetworkConstants.DEFAULT_MAX_DOWNLOADS)
        var expiryHours = intent.getIntExtra(EXTRA_EXPIRY_HOURS, NetworkConstants.DEFAULT_EXPIRY_HOURS)

        // Log parameters for debugging
        Log.d(TAG, "Upload parameters - Max Downloads: $maxDownloads, Expiry Hours: $expiryHours")

        // Validate expiry hours
        if (expiryHours > NetworkConstants.MAX_EXPIRY_HOURS) {
            Log.w(TAG, "Expiry hours exceeded maximum allowed value, capping at ${NetworkConstants.MAX_EXPIRY_HOURS}")
            expiryHours = NetworkConstants.MAX_EXPIRY_HOURS
        }

        if (filePath == null) {
            Log.e(TAG, "No file path provided")
            // Try to find the most recent dump state file
            val mostRecentFile = FileController.getMostRecentDumpStateFile()
            if (mostRecentFile != null) {
                filePath = mostRecentFile.absolutePath
            } else {
                Log.e(TAG, "No file to upload")
                sendErrorBroadcast("No file to upload")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: $filePath")
            sendErrorBroadcast("File not found")
            stopSelf()
            return START_NOT_STICKY
        }

        // Create a notification and run the service in the foreground
        Log.d(TAG, "Creating initial notification and starting foreground...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // For Android 14+ (API 34+)
            try {
                Log.d(TAG, "Starting foreground service with DATA_SYNC type (Android 14+)")
                val dataSync = 1 // ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC value is 1
                startForeground(NOTIFICATION_ID, createInitialNotification(), dataSync)
                Log.d(TAG, "Foreground service started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service with type", e)
                stopSelf()
                return START_NOT_STICKY
            }
        } else {
            // For Android 13 and below
            Log.d(TAG, "Starting foreground service (Android <14)")
            startForeground(NOTIFICATION_ID, createInitialNotification())
            Log.d(TAG, "Foreground service started successfully")
        }

        // Prepare file for upload
        Log.d(TAG, "Starting file preparation...")
        FileController.prepareFileForUpload(file, object : FileController.FilePrepareCallback {
            override fun onSuccess(preparedFile: File) {
                Log.d(TAG, "File prepared for upload: ${preparedFile.name}")
                Log.d(TAG, "Starting upload process...")
                uploadFile(preparedFile, maxDownloads, expiryHours)
            }

            override fun onProgress(progress: Int) {
                Log.d(TAG, "File preparation progress: $progress%")
                updateNotification("Preparing file: $progress%", progress, 100)
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "Error preparing file", e)
                sendErrorBroadcast("Error preparing file: ${e.message}")
                stopSelf()
            }
        })

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Upload a file directly with progress tracking
     */
    private fun uploadFile(file: File, maxDownloads: Int, expiryHours: Int) {
        // Validate file size before upload
        if (!FileController.isValidFileSize(file)) {
            Log.e(TAG, "File too large: ${file.length()} bytes (max: ${FileConstants.MAX_FILE_SIZE} bytes)")
            sendErrorBroadcast("File size exceeds the maximum allowed size")
            stopSelf()
            return
        }

        // Determine upload strategy
        val useChunkedUpload = UploadStrategySelector.shouldUseChunkedUpload(this, file)
        val networkType = UploadStrategySelector.getNetworkTypeName(this)

        Log.d(TAG, "Starting upload - Network: $networkType, Chunked: $useChunkedUpload, File: ${file.name}")

        // Execute chosen strategy
        val uploadFuture = if (useChunkedUpload) {
            performChunkedUpload(file, maxDownloads, expiryHours)
        } else {
            performStandardUpload(file, maxDownloads, expiryHours)
        }

        // Handle completion
        uploadFuture.thenAccept { result ->
            if (result.isSuccess) {
                Log.d(TAG, "Upload successful! Code: ${result.code}")
                updateNotification("Upload complete!", 100, 100, isComplete = true)
                sendSuccessBroadcast(result.code)
            } else {
                Log.e(TAG, "Upload failed: ${result.error}")
                updateNotification("Upload failed: ${result.error}", -1, 100, isComplete = true)
                sendErrorBroadcast(result.error)
            }

            // Give user a moment to see the final status, then stop service
            Handler(Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 3000)
        }.exceptionally { ex ->
            Log.e(TAG, "Exception during upload", ex)
            updateNotification("Upload error: ${ex.message}", -1, 100, isComplete = true)
            sendErrorBroadcast("Error during upload: ${ex.message}")

            // Give user a moment to see the error, then stop service
            Handler(Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 3000)
            null
        }
    }

    /**
     * Perform a chunked upload for large files
     */
    private fun performChunkedUpload(
        file: File,
        maxDownloads: Int,
        expiryHours: Int
    ): CompletableFuture<UploadResult> {
        val future = CompletableFuture<UploadResult>()

        // CRITICAL: Show session initialization progress IMMEDIATELY
        updateNotification("Initializing upload session...", -1, 100)

        // Create chunked upload manager directly
        val uploadManager = ChunkedUploadManager(this, file, maxDownloads, expiryHours)

        // Set progress listener with direct notification updates
        uploadManager.setProgressListener(object : ChunkedUploadManager.UploadProgressListener {
            override fun onProgress(bytesUploaded: Long, totalBytes: Long, percentComplete: Int) {
                // Direct notification update - no intermediate callbacks
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProgressUpdate >= 500 || percentComplete == 100) {
                    lastProgressUpdate = currentTime

                    val uploadedMB = bytesUploaded / (1024f * 1024f)
                    val totalMB = totalBytes / (1024f * 1024f)
                    val statusText = String.format(
                        "Uploading: %.1f / %.1f MB (%d%%)",
                        uploadedMB, totalMB, percentComplete
                    )

                    updateNotification(statusText, percentComplete, 100)
                    Log.d(TAG, "Upload progress: $percentComplete% ($uploadedMB MB / $totalMB MB)")
                }
            }

            override fun onChunkComplete(chunkIndex: Int, totalChunks: Int) {
                Log.d(TAG, "Chunk $chunkIndex/$totalChunks complete")
            }

            override fun onUploadComplete(result: UploadResult) {
                Log.d(TAG, "Chunked upload complete: $result")

                // Save to history directly if successful
                if (result.isSuccess) {
                    saveToHistory(result)
                }

                future.complete(result)
            }

            override fun onError(error: String) {
                Log.e(TAG, "Chunked upload error: $error")
                future.complete(UploadResult(error))
            }

            override fun onPaused() {
                Log.d(TAG, "Chunked upload paused")
                updateNotification("Upload paused", -1, 100)
            }

            override fun onResumed() {
                Log.d(TAG, "Chunked upload resumed")
                updateNotification("Resuming upload...", -1, 100)
            }
        })

        // Start the upload - this triggers session initialization
        uploadManager.startUpload()
            .exceptionally { ex ->
                Log.e(TAG, "Exception during chunked upload", ex)
                future.complete(UploadResult("Exception during upload: ${ex.message}"))
                null
            }

        return future
    }

    /**
     * Perform a standard (non-chunked) upload for smaller files
     */
    private fun performStandardUpload(
        file: File,
        maxDownloads: Int,
        expiryHours: Int
    ): CompletableFuture<UploadResult> {
        val future = CompletableFuture<UploadResult>()

        // Update notification immediately
        updateNotification("Uploading ${file.name}...", 0, 100)

        // Create OkHttpClient with timeout configuration
        val client = OkHttpClient.Builder()
            .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(NetworkConstants.WRITE_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.READ_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        Log.d(TAG, "Using standard upload for file: ${file.name}")

        val fileBody = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(NetworkConstants.PARAM_FILE, file.name, fileBody)
            .addFormDataPart(NetworkConstants.PARAM_MAX_DOWNLOADS, maxDownloads.toString())
            .addFormDataPart(NetworkConstants.PARAM_EXPIRY_HOURS, expiryHours.toString())
            .build()

        // Get server URL and client key from preferences
        val serverUrl = PreferencesUtil.getServerUrl(this)
        val clientKey = PreferencesUtil.getClientKey(this)

        Log.d(TAG, "Using server URL: $serverUrl")

        // Build request with client-key header
        val request = Request.Builder()
            .url(serverUrl)
            .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
            .post(requestBody)
            .build()

        Log.d(TAG, "Sending upload request with ${NetworkConstants.CLIENT_KEY_HEADER} header")

        // Show initial progress
        updateNotification("Uploading ${file.name}...", 0, 100)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Upload failed", e)
                future.complete(UploadResult("Upload failed: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string() ?: ""
                            Log.d(TAG, "Server response: $responseBody")

                            // Update progress to 100% on success
                            updateNotification("Upload complete!", 100, 100)

                            // Parse JSON response to get the upload code
                            val jsonResponse = JSONObject(responseBody)
                            val uploadCode = jsonResponse.getString("code")

                            Log.d(TAG, "Upload successful! Code: $uploadCode")

                            // Create the result
                            val result = UploadResult(
                                uploadCode,
                                file.name,
                                maxDownloads,
                                expiryHours
                            )

                            // Save to history
                            saveToHistory(result)

                            future.complete(result)
                        } catch (e: JSONException) {
                            Log.e(TAG, "Failed to parse server response", e)
                            future.complete(UploadResult("Failed to parse server response"))
                        }
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        val errorMsg = when (response.code) {
                            401, 403 -> {
                                Log.e(TAG, "Authentication error: ${response.code}, Body: $errorBody")
                                "Authentication failed. Please check client key."
                            }
                            else -> {
                                Log.e(TAG, "Upload failed: ${response.code}, Body: $errorBody")
                                "Upload failed, Response code: ${response.code}"
                            }
                        }

                        // Try to extract error message from JSON
                        val finalErrorMsg = try {
                            val errorJson = JSONObject(errorBody)
                            errorJson.optString("error", errorMsg)
                        } catch (e: JSONException) {
                            errorMsg
                        }

                        future.complete(UploadResult(finalErrorMsg))
                    }
                }
            }
        })

        return future
    }

    /**
     * Save an upload result to the history database
     */
    private fun saveToHistory(result: UploadResult) {
        if (!result.isSuccess) {
            return
        }

        val historyEntry = UploadHistory(
            fileName = result.fileName,
            uploadTime = System.currentTimeMillis(),
            uploadCode = result.code,
            maxDownloads = result.maxDownloads,
            expiryHours = result.expiryHours
        )

        repository.insert(historyEntry)
            .thenAccept { id -> Log.d(TAG, "Saved to history with ID: $id") }
            .exceptionally { ex ->
                Log.e(TAG, "Error saving to history", ex)
                null
            }
    }

    /**
     * Create the initial notification
     */
    private fun createInitialNotification(): Notification {
        val notificationIntent = Intent(this, ComposeMainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("File Upload")
            .setContentText("Preparing file...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setProgress(100, 0, true) // Start with indeterminate progress
            .setOngoing(true)
            .setOnlyAlertOnce(true) // Don't alert on every progress update
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for better visibility
            .apply {
                // Force immediate notification display on Android 14+ (prevents system delay)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }

        notificationBuilder = builder
        return builder.build()
    }

    /**
     * Update the notification with progress
     *
     * @param status The status message
     * @param progress Current progress value (-1 for indeterminate)
     * @param max Maximum progress value
     * @param isComplete Whether the upload is complete (makes notification dismissible)
     */
    private fun updateNotification(status: String, progress: Int = -1, max: Int = 100, isComplete: Boolean = false) {
        val builder = notificationBuilder ?: return

        Log.d(TAG, "Updating notification: $status (progress: $progress/$max)")

        builder.setContentText(status)

        if (progress >= 0) {
            // Determinate progress
            builder.setProgress(max, progress, false)
        } else {
            // Indeterminate progress
            builder.setProgress(max, 0, true)
        }

        // When complete, make notification dismissible and remove progress bar
        if (isComplete) {
            builder.setOngoing(false)
                .setProgress(0, 0, false) // Remove progress bar
                .setAutoCancel(true) // Allow swipe to dismiss
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Create the notification channel (required for Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "File Upload Channel"
            val description = "Notification channel for file upload progress"
            val importance = NotificationManager.IMPORTANCE_HIGH // Changed to HIGH for better visibility
            val channel = NotificationChannel(
                AppConstants.NOTIFICATION_CHANNEL_ID,
                name,
                importance
            ).apply {
                this.description = description
                setShowBadge(true)
                enableVibration(false) // Don't vibrate on progress updates
                enableLights(false) // Don't flash lights on progress updates
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Send a broadcast with the upload success result
     */
    private fun sendSuccessBroadcast(uploadCode: String) {
        Log.d(TAG, "Sending success broadcast with code: $uploadCode")
        val intent = Intent(AppConstants.ACTION_UPLOAD_COMPLETE).apply {
            putExtra(AppConstants.EXTRA_UPLOAD_CODE, uploadCode)
        }

        // Send both a regular broadcast and a local broadcast to ensure delivery
        sendBroadcast(intent)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /**
     * Send a broadcast with the upload error result
     */
    private fun sendErrorBroadcast(errorMessage: String) {
        Log.e(TAG, "Sending error broadcast: $errorMessage")
        val intent = Intent(AppConstants.ACTION_UPLOAD_ERROR).apply {
            putExtra(AppConstants.EXTRA_UPLOAD_ERROR, errorMessage)
        }

        // Send both a regular broadcast and a local broadcast to ensure delivery
        sendBroadcast(intent)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}
