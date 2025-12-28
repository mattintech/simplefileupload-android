package com.mattintech.simplelogupload.upload

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.mattintech.simplelogupload.constant.AppConstants
import com.mattintech.simplelogupload.constant.NetworkConstants
import com.mattintech.simplelogupload.model.UploadResult
import com.mattintech.simplelogupload.util.PreferencesUtil
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.ceil
import kotlin.math.min

/**
 * Manages chunked uploads for large files using Kotlin coroutines.
 * This class handles splitting a large file into chunks, uploading them,
 * tracking progress, and reassembling on the server.
 *
 * Key improvements over Java version:
 * - Uses Dispatchers.IO for all file operations to prevent ANR
 * - Uses coroutines instead of CompletableFuture for cleaner async code
 * - Properly handles threading to never block the main thread
 */
class ChunkedUploadManager(
    context: Context,
    private val file: File,
    private val maxDownloads: Int,
    private val expiryHours: Int
) {
    companion object {
        private const val TAG = AppConstants.APP_TAG + "ChunkedUploadManager"

        // Chunk size configuration
        private const val WIFI_CHUNK_SIZE = 10 * 1024 * 1024 // 10MB for WiFi
        private const val MOBILE_CHUNK_SIZE = 4 * 1024 * 1024 // 4MB for mobile networks

        // Max parallel uploads
        private const val WIFI_MAX_PARALLEL = 3
        private const val MOBILE_MAX_PARALLEL = 2
    }

    // Context and network
    private val context: Context = context.applicationContext
    private var isWifiConnection: Boolean = false
    private var networkCallback: NetworkCallback? = null
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // Upload parameters
    private val chunkSize: Int
    private val totalChunks: Int
    private val maxParallelUploads: Int
    private val totalBytes: Long = file.length()

    // Upload state
    private var uploadId: String = UUID.randomUUID().toString()
    private val completedChunks = mutableSetOf<Int>()
    private val inProgressChunks = mutableSetOf<Int>()
    private val isPaused = AtomicBoolean(false)
    private val isCancelled = AtomicBoolean(false)
    private val activeUploads = AtomicInteger(0)
    private val uploadedBytes = AtomicLong(0)

    // Progress tracking
    private var progressListener: UploadProgressListener? = null

    // OkHttpClient for uploads
    private lateinit var client: OkHttpClient

    // Coroutine scope for upload operations
    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Determine initial connection type
        detectConnectionType()

        // Configure chunk size based on connection type
        chunkSize = if (isWifiConnection) WIFI_CHUNK_SIZE else MOBILE_CHUNK_SIZE

        // Configure max parallel uploads based on connection type and user preference
        val parallelEnabled = PreferencesUtil.isParallelUploadsEnabled(context)
        maxParallelUploads = when {
            isWifiConnection -> WIFI_MAX_PARALLEL
            parallelEnabled -> MOBILE_MAX_PARALLEL
            else -> 1
        }

        // Calculate total chunks
        totalChunks = ceil(file.length().toDouble() / chunkSize).toInt()

        // Setup HTTP client
        setupHttpClient()

        Log.d(TAG, "Created chunked upload manager for file: ${file.name}")
        Log.d(TAG, "Total size: $totalBytes bytes, Chunks: $totalChunks")
        Log.d(TAG, "Connection type: ${if (isWifiConnection) "WiFi" else "Mobile"}, " +
                "Chunk size: $chunkSize bytes, " +
                "Parallel uploads: $maxParallelUploads ${if (parallelEnabled) "(enabled)" else "(disabled)"}")
    }

    /**
     * Interface for tracking upload progress
     */
    interface UploadProgressListener {
        fun onProgress(bytesUploaded: Long, totalBytes: Long, percentComplete: Int)
        fun onChunkComplete(chunkIndex: Int, totalChunks: Int)
        fun onUploadComplete(result: UploadResult)
        fun onError(error: String)
        fun onPaused()
        fun onResumed()
    }

    /**
     * Set a listener for upload progress events
     */
    fun setProgressListener(listener: UploadProgressListener) {
        this.progressListener = listener
    }

    /**
     * Start the upload process
     * Maintains CompletableFuture API for compatibility with existing code
     */
    fun startUpload(): CompletableFuture<UploadResult> {
        val future = CompletableFuture<UploadResult>()

        uploadScope.launch {
            try {
                // Reset state
                synchronized(completedChunks) {
                    completedChunks.clear()
                }
                synchronized(inProgressChunks) {
                    inProgressChunks.clear()
                }
                uploadedBytes.set(0)
                isPaused.set(false)
                isCancelled.set(false)

                // Start network monitoring
                withContext(Dispatchers.Main) {
                    registerNetworkCallback()
                }

                // Initialize upload session
                val sessionId = initializeUploadSession()
                if (sessionId.isNullOrEmpty()) {
                    future.complete(UploadResult("Failed to initialize upload session"))
                    return@launch
                }

                uploadId = sessionId
                Log.d(TAG, "Upload session initialized with ID: $uploadId")

                // Upload all chunks
                val isComplete = uploadAllChunks()

                if (isComplete) {
                    // Finalize the upload
                    val result = finalizeUpload()
                    future.complete(result)
                } else {
                    future.complete(UploadResult("Upload incomplete or cancelled"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during upload", e)
                future.complete(UploadResult("Upload error: ${e.message}"))
            }
        }

        return future
    }

    /**
     * Initialize an upload session with the server
     */
    private suspend fun initializeUploadSession(): String? = suspendCoroutine { continuation ->
        val serverBaseUrl = PreferencesUtil.getServerBaseUrl(context)
        val clientKey = PreferencesUtil.getClientKey(context)
        val initUrl = "$serverBaseUrl/upload/start"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("filename", file.name)
            .addFormDataPart("filesize", totalBytes.toString())
            .addFormDataPart("chunk_size", chunkSize.toString())
            .addFormDataPart("total_chunks", totalChunks.toString())
            .addFormDataPart("max_downloads", maxDownloads.toString())
            .addFormDataPart("expiry_hours", expiryHours.toString())
            .build()

        val request = Request.Builder()
            .url(initUrl)
            .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
            .post(requestBody)
            .build()

        Log.d(TAG, "Initializing upload session...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to initialize upload session", e)
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string() ?: ""
                            val jsonResponse = JSONObject(responseBody)
                            val sessionId = jsonResponse.getString("upload_id")
                            continuation.resume(sessionId)
                        } catch (e: JSONException) {
                            Log.e(TAG, "Failed to parse session initialization response", e)
                            continuation.resume(null)
                        }
                    } else {
                        Log.e(TAG, "Session initialization failed with code: ${response.code}")
                        continuation.resume(null)
                    }
                }
            }
        })
    }

    /**
     * Upload all chunks to the server
     * Uses coroutines to manage parallel uploads efficiently
     */
    private suspend fun uploadAllChunks(): Boolean = coroutineScope {
        val deferredResults = mutableListOf<Deferred<Boolean>>()

        while (true) {
            if (isCancelled.get()) {
                return@coroutineScope false
            }

            if (isPaused.get()) {
                delay(100)
                continue
            }

                // Check if all chunks are complete
                synchronized(completedChunks) {
                    if (completedChunks.size == totalChunks) {
                        val allChunksPresent = (0 until totalChunks).all { it in completedChunks }
                        if (allChunksPresent) {
                            Log.d(TAG, "All chunks uploaded successfully")
                            return@coroutineScope true
                        }
                    }
                }

            // Find next chunks to upload
            val chunksToUpload = mutableListOf<Int>()
            synchronized(completedChunks) {
                synchronized(inProgressChunks) {
                    for (i in 0 until totalChunks) {
                        if (activeUploads.get() >= maxParallelUploads) break
                        if (i !in completedChunks && i !in inProgressChunks) {
                            chunksToUpload.add(i)
                            inProgressChunks.add(i)
                            activeUploads.incrementAndGet()
                        }
                    }
                }
            }

            // Launch uploads for new chunks
            for (chunkIndex in chunksToUpload) {
                val deferred = async {
                    try {
                        val success = uploadChunk(chunkIndex)

                        synchronized(inProgressChunks) {
                            inProgressChunks.remove(chunkIndex)
                        }
                        activeUploads.decrementAndGet()

                        if (success) {
                            synchronized(completedChunks) {
                                completedChunks.add(chunkIndex)
                            }
                            progressListener?.onChunkComplete(chunkIndex, totalChunks)
                        } else if (!isCancelled.get() && !isPaused.get()) {
                            // Retry failed chunks
                            Log.d(TAG, "Chunk $chunkIndex failed, will retry")
                            synchronized(completedChunks) {
                                completedChunks.remove(chunkIndex)
                            }
                        }

                        success
                    } catch (e: Exception) {
                        Log.e(TAG, "Error uploading chunk $chunkIndex", e)
                        synchronized(inProgressChunks) {
                            inProgressChunks.remove(chunkIndex)
                        }
                        activeUploads.decrementAndGet()
                        false
                    }
                }
                deferredResults.add(deferred)
            }

            // Clean up completed deferreds
            deferredResults.removeAll { it.isCompleted }

            // Check if we're stalled
            synchronized(completedChunks) {
                synchronized(inProgressChunks) {
                    if (activeUploads.get() == 0 && inProgressChunks.isEmpty() &&
                        completedChunks.size < totalChunks && !isPaused.get()) {
                        Log.e(TAG, "Upload stalled with ${completedChunks.size}/$totalChunks chunks completed")
                        return@coroutineScope false
                    }
                }
            }

            // Small delay before checking again
            delay(100)
        }

        @Suppress("UNREACHABLE_CODE")
        false // This line is unreachable but needed for type inference
    }

    /**
     * Upload a single chunk to the server
     * CRITICAL: Uses Dispatchers.IO for file reading to prevent ANR
     */
    private suspend fun uploadChunk(chunkIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val serverBaseUrl = PreferencesUtil.getServerBaseUrl(context)
        val clientKey = PreferencesUtil.getClientKey(context)
        val chunkUrl = "$serverBaseUrl/upload/chunk"

        try {
            // Calculate chunk boundaries
            val start = chunkIndex.toLong() * chunkSize
            val end = min(start + chunkSize, file.length())
            val currentChunkSize = (end - start).toInt()

            Log.d(TAG, "Uploading chunk $chunkIndex ($currentChunkSize bytes)")

            // Read chunk data on IO dispatcher - THIS FIXES THE ANR!
            val buffer = ByteArray(currentChunkSize)
            FileInputStream(file).use { inputStream ->
                inputStream.skip(start)
                val bytesRead = inputStream.read(buffer)
                if (bytesRead != currentChunkSize) {
                    Log.e(TAG, "Failed to read chunk data: expected $currentChunkSize bytes, got $bytesRead")
                    return@withContext false
                }
            }

            // Build request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_id", uploadId)
                .addFormDataPart("chunk_index", chunkIndex.toString())
                .addFormDataPart("total_chunks", totalChunks.toString())
                .addFormDataPart(
                    "chunk",
                    "${file.name}.part$chunkIndex",
                    buffer.toRequestBody("application/octet-stream".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url(chunkUrl)
                .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                .post(requestBody)
                .build()

            // Execute request (suspend until complete)
            val response = client.newCall(request).await()

            response.use {
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        val success = jsonResponse.optBoolean("success", false)
                        val confirmedChunkIndex = jsonResponse.optInt("chunk_index", -1)

                        if (success && (confirmedChunkIndex == chunkIndex || confirmedChunkIndex == -1)) {
                            // Update progress
                            val newTotal = uploadedBytes.addAndGet(currentChunkSize.toLong())
                            val percentComplete = (newTotal * 100 / totalBytes).toInt()

                            // Notify listener on main thread
                            withContext(Dispatchers.Main) {
                                progressListener?.onProgress(newTotal, totalBytes, percentComplete)
                            }

                            Log.d(TAG, "Chunk $chunkIndex uploaded successfully. Progress: $percentComplete%")
                            return@withContext true
                        } else {
                            Log.e(TAG, "Server reported issue with chunk $chunkIndex: $responseBody")
                            return@withContext false
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing chunk upload response for chunk $chunkIndex", e)
                        return@withContext false
                    }
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(TAG, "Chunk $chunkIndex upload failed with code: ${response.code}, Body: $errorBody")
                    return@withContext false
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error preparing chunk $chunkIndex", e)
            return@withContext false
        }
    }

    /**
     * Extension function to convert OkHttp Call to suspend function
     */
    private suspend fun Call.await(): Response = suspendCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }

    /**
     * Verify which chunks the server has received
     */
    private suspend fun verifyChunksWithServer(): Set<Int> = suspendCoroutine { continuation ->
        val serverBaseUrl = PreferencesUtil.getServerBaseUrl(context)
        val clientKey = PreferencesUtil.getClientKey(context)
        val verifyUrl = "$serverBaseUrl/upload/verify"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_id", uploadId)
            .build()

        val request = Request.Builder()
            .url(verifyUrl)
            .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
            .post(requestBody)
            .build()

        Log.d(TAG, "Verifying uploaded chunks with server...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to verify chunks with server", e)
                continuation.resume(emptySet())
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string() ?: ""
                            val jsonResponse = JSONObject(responseBody)
                            val missingChunks = mutableSetOf<Int>()

                            if (jsonResponse.has("missing_chunks")) {
                                val missingArray = jsonResponse.getJSONArray("missing_chunks")
                                for (i in 0 until missingArray.length()) {
                                    missingChunks.add(missingArray.getInt(i))
                                }
                                Log.d(TAG, "Server missing chunks: $missingChunks")
                            }

                            continuation.resume(missingChunks)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing chunk verification response", e)
                            continuation.resume(emptySet())
                        }
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Chunk verification failed with code: ${response.code}, Body: $errorBody")
                        continuation.resume(emptySet())
                    }
                }
            }
        })
    }

    /**
     * Retry uploading specific chunks
     */
    private suspend fun retrySpecificChunks(chunkIndexes: Set<Int>): Boolean = coroutineScope {
        if (chunkIndexes.isEmpty()) return@coroutineScope true

        Log.d(TAG, "Retrying specific chunks: $chunkIndexes")

        val results = chunkIndexes.map { chunkIndex ->
            async {
                Log.d(TAG, "Retrying upload of chunk $chunkIndex")
                val success = uploadChunk(chunkIndex)
                if (success) {
                    Log.d(TAG, "Successfully reuploaded chunk $chunkIndex")
                } else {
                    Log.e(TAG, "Failed to reupload chunk $chunkIndex")
                }
                success
            }
        }

        val allSuccessful = results.awaitAll().all { it }
        Log.d(TAG, "All chunk retries completed. ${results.count { it.await() }}/${results.size} successful")

        return@coroutineScope allSuccessful
    }

    /**
     * Finalize the upload on the server
     */
    private suspend fun finalizeUpload(): UploadResult {
        // Verify all chunks are uploaded
        val missingChunks = synchronized(completedChunks) {
            (0 until totalChunks).filter { it !in completedChunks }.toSet()
        }

        if (missingChunks.isNotEmpty()) {
            Log.e(TAG, "Cannot finalize: missing chunks $missingChunks")
            return UploadResult("Cannot finalize: missing chunks $missingChunks")
        }

        // Verify with server
        val serverMissingChunks = verifyChunksWithServer()
        if (serverMissingChunks.isNotEmpty()) {
            Log.e(TAG, "Server missing chunks: $serverMissingChunks, reuploading")

            // Subtract bytes for missing chunks
            for (chunkIndex in serverMissingChunks) {
                synchronized(completedChunks) {
                    completedChunks.remove(chunkIndex)
                }
                val start = chunkIndex.toLong() * chunkSize
                val end = min(start + chunkSize, file.length())
                val chunkBytes = end - start
                uploadedBytes.addAndGet(-chunkBytes)
            }

            // Retry missing chunks
            val retrySuccess = retrySpecificChunks(serverMissingChunks)
            if (!retrySuccess) {
                Log.e(TAG, "Failed to reupload missing chunks")
                return UploadResult("Failed to reupload missing chunks")
            }
            Log.d(TAG, "Missing chunks reuploaded, finalizing")
        }

        // Perform finalize request
        return performFinalizeRequest()
    }

    /**
     * Perform the finalize request to the server
     */
    private suspend fun performFinalizeRequest(): UploadResult = suspendCoroutine { continuation ->
        val serverBaseUrl = PreferencesUtil.getServerBaseUrl(context)
        val clientKey = PreferencesUtil.getClientKey(context)
        val finalizeUrl = "$serverBaseUrl/upload/complete"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_id", uploadId)
            .addFormDataPart("filename", file.name)
            .addFormDataPart("total_chunks", totalChunks.toString())
            .addFormDataPart("max_downloads", maxDownloads.toString())
            .addFormDataPart("expiry_hours", expiryHours.toString())
            .build()

        val request = Request.Builder()
            .url(finalizeUrl)
            .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
            .post(requestBody)
            .build()

        Log.d(TAG, "Finalizing upload...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to finalize upload", e)
                continuation.resume(UploadResult("Failed to finalize upload: ${e.message}"))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string() ?: ""
                            Log.d(TAG, "Server response: $responseBody")

                            val jsonResponse = JSONObject(responseBody)
                            val uploadCode = jsonResponse.getString("code")

                            Log.d(TAG, "Upload finalized successfully! Code: $uploadCode")

                            val result = UploadResult(uploadCode, file.name, maxDownloads, expiryHours)
                            progressListener?.onUploadComplete(result)

                            continuation.resume(result)
                        } catch (e: JSONException) {
                            Log.e(TAG, "Failed to parse server response", e)
                            continuation.resume(UploadResult("Failed to parse server response"))
                        }
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Finalize failed with code: ${response.code}, Body: $errorBody")
                        continuation.resume(UploadResult("Failed to finalize upload: ${response.code}"))
                    }
                }
            }
        })
    }

    /**
     * Pause the upload
     */
    fun pauseUpload() {
        if (!isPaused.getAndSet(true)) {
            Log.d(TAG, "Upload paused")
            progressListener?.onPaused()
        }
    }

    /**
     * Resume the upload
     * Maintains CompletableFuture API for compatibility
     */
    fun resumeUpload() {
        if (isPaused.getAndSet(false)) {
            Log.d(TAG, "Upload resumed")
            progressListener?.onResumed()

            // Launch resume in upload scope
            uploadScope.launch {
                try {
                    val isComplete = uploadAllChunks()
                    if (isComplete) {
                        val result = finalizeUpload()
                        withContext(Dispatchers.Main) {
                            progressListener?.onUploadComplete(result)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            progressListener?.onError("Upload incomplete or cancelled")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during chunk uploading", e)
                    withContext(Dispatchers.Main) {
                        progressListener?.onError("Chunk upload error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Cancel the upload
     */
    fun cancelUpload() {
        isCancelled.set(true)
        Log.d(TAG, "Upload cancelled")

        // Cancel all coroutines
        uploadScope.cancel()

        // Unregister network callback
        unregisterNetworkCallback()
    }

    /**
     * Setup HTTP client with timeouts
     */
    private fun setupHttpClient() {
        val connectionTimeout = if (isWifiConnection)
            NetworkConstants.CONNECTION_TIMEOUT
        else
            NetworkConstants.CONNECTION_TIMEOUT / 2

        val writeTimeout = if (isWifiConnection)
            NetworkConstants.WRITE_TIMEOUT
        else
            NetworkConstants.WRITE_TIMEOUT / 2

        val readTimeout = if (isWifiConnection)
            NetworkConstants.READ_TIMEOUT
        else
            NetworkConstants.READ_TIMEOUT / 2

        client = OkHttpClient.Builder()
            .connectTimeout(connectionTimeout.toLong(), TimeUnit.SECONDS)
            .writeTimeout(writeTimeout.toLong(), TimeUnit.SECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Detect current connection type (WiFi or Mobile)
     */
    @Suppress("DEPRECATION")
    private fun detectConnectionType() {
        val activeNetwork = connectivityManager.activeNetworkInfo
        isWifiConnection = activeNetwork != null &&
                activeNetwork.type == ConnectivityManager.TYPE_WIFI

        Log.d(TAG, "Connection type detected: ${if (isWifiConnection) "WiFi" else "Mobile"}")
    }

    /**
     * Register network callback to monitor connection changes
     */
    private fun registerNetworkCallback() {
        if (networkCallback == null) {
            networkCallback = NetworkCallback()
        }

        val builder = NetworkRequest.Builder()
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback!!)

        Log.d(TAG, "Network callback registered")
    }

    /**
     * Unregister network callback
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Network callback unregistered")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Failed to unregister network callback", e)
            }
        }
    }

    /**
     * Network callback for handling connection changes
     */
    private inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            val wasWifi = isWifiConnection
            detectConnectionType()

            Log.d(TAG, "Network available: ${if (isWifiConnection) "WiFi" else "Mobile"}")

            // Resume if paused due to network loss
            if (isPaused.get()) {
                Log.d(TAG, "Network restored, resuming upload")
                resumeUpload()
            }

            // Adjust settings if connection type changed
            if (wasWifi != isWifiConnection) {
                Log.d(TAG, "Connection type changed, adjusting settings")
                setupHttpClient()
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost")
            pauseUpload()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, capabilities)

            val newIsWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

            if (newIsWifi != isWifiConnection) {
                isWifiConnection = newIsWifi
                Log.d(TAG, "Connection capabilities changed to: ${if (isWifiConnection) "WiFi" else "Mobile"}")
                setupHttpClient()
            }
        }
    }
}
