package com.mattintech.simplelogupload.controller

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mattintech.simplelogupload.constant.AppConstants
import com.mattintech.simplelogupload.constant.NetworkConstants
import com.mattintech.simplelogupload.db.UploadHistoryRepository
import com.mattintech.simplelogupload.util.PreferencesUtil
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Controller for handling file uploads with progress tracking
 */
class UploadController(context: Context) {

    companion object {
        private const val TAG = AppConstants.APP_TAG + "UploadController"
    }

    private val context: Context = context.applicationContext
    private val repository: UploadHistoryRepository = UploadHistoryRepository(this.context as Application)

    /**
     * Start an upload using the foreground service
     *
     * @param file The file to upload
     * @param maxDownloads Maximum number of downloads
     * @param expiryHours Expiry time in hours
     */
    fun startUploadService(file: File, maxDownloads: Int, expiryHours: Int) {
        if (!file.exists()) {
            Log.e(TAG, "Cannot start upload service with non-existent file")
            return
        }

        val serviceIntent = Intent(context, com.mattintech.simplelogupload.service.FileUploadForegroundService::class.java).apply {
            putExtra(com.mattintech.simplelogupload.service.FileUploadForegroundService.EXTRA_FILE_PATH, file.absolutePath)
            putExtra(com.mattintech.simplelogupload.service.FileUploadForegroundService.EXTRA_MAX_DOWNLOADS, maxDownloads)
            putExtra(com.mattintech.simplelogupload.service.FileUploadForegroundService.EXTRA_EXPIRY_HOURS, expiryHours)
        }

        // On Android 12+ (API 31+), we need to use startForegroundService
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            Log.d(TAG, "Starting foreground service (Android 12+)")
            context.startForegroundService(serviceIntent)
        } else {
            Log.d(TAG, "Starting service (pre-Android 12)")
            context.startService(serviceIntent)
        }
    }

    /**
     * Delete a file from the server using its upload code
     */
    fun deleteFileFromServer(uploadCode: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()

        if (uploadCode.isEmpty()) {
            Log.e(TAG, "Cannot delete file with empty upload code")
            future.complete(false)
            return future
        }

        // Create OkHttpClient with timeout configuration
        val client = OkHttpClient.Builder()
            .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(NetworkConstants.READ_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // Get server base URL and client key from preferences
        val serverBaseUrl = PreferencesUtil.getServerBaseUrl(context)
        val clientKey = PreferencesUtil.getClientKey(context)

        // Create the delete URL
        val deleteUrl = "$serverBaseUrl/delete/$uploadCode"
        Log.d(TAG, "Deleting file from server: $deleteUrl")

        // Build request with client-key header
        val request = Request.Builder()
            .url(deleteUrl)
            .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Server delete failed", e)
                future.complete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Server delete successful for code: $uploadCode")
                        future.complete(true)
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "Server delete failed with code: ${response.code}, Body: $errorBody")
                        future.complete(false)
                    }
                }
            }
        })

        return future
    }
}
