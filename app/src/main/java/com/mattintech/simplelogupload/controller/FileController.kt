package com.mattintech.simplelogupload.controller

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mattintech.simplelogupload.constant.AppConstants
import com.mattintech.simplelogupload.constant.FileConstants
import com.mattintech.simplelogupload.util.FileUtil
import com.mattintech.simplelogupload.util.ZipUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Controller for file operations using Kotlin coroutines
 */
object FileController {
    private const val TAG = AppConstants.APP_TAG + "FileController"

    // Track current zip job for cancellation
    private var currentZipJob: kotlinx.coroutines.Job? = null

    /**
     * Interface for file preparation callbacks
     */
    interface FilePrepareCallback {
        fun onSuccess(preparedFile: File)
        fun onProgress(progress: Int)
        fun onError(e: Exception)
    }

    /**
     * Find the most recent dumpstate file
     */
    @JvmStatic
    fun getMostRecentDumpStateFile(): File? {
        return FileUtil.findMostRecentDumpStateFile()
    }

    /**
     * Find all dumpstate files
     */
    @JvmStatic
    fun getAllDumpStateFiles(): Array<File> {
        return FileUtil.findAllDumpStateFiles()
    }

    /**
     * Prepare a file for upload - ensures the file is zipped if needed
     * Runs zipping on Dispatchers.IO to prevent ANR
     *
     * @param file The file to prepare
     * @param callback Callback for progress updates and completion
     */
    @JvmStatic
    fun prepareFileForUpload(file: File, callback: FilePrepareCallback) {
        if (!file.exists()) {
            callback.onError(IOException("File does not exist"))
            return
        }

        // Check file size
        if (!FileUtil.isValidFileSize(file)) {
            callback.onError(
                IOException("File size exceeds limit of ${FileConstants.MAX_FILE_SIZE} bytes")
            )
            return
        }

        // If already a zip file, just return it
        if (FileUtil.isZipFile(file)) {
            callback.onSuccess(file)
            return
        }

        // Otherwise, compress it on a background thread
        currentZipJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                ZipUtil.zipFile(file, object : ZipUtil.ZipProgressListener {
                    override fun onProgress(bytesProcessed: Long, totalBytes: Long) {
                        val progress = ((bytesProcessed * 100) / totalBytes).toInt()
                        Log.d(TAG, "Zip progress callback: $progress%")
                        // Post callback to main thread for UI updates
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onProgress(progress)
                        }
                    }

                    override fun onComplete(zipFile: File) {
                        Log.d(TAG, "Zip complete callback")
                        // Post callback to main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onSuccess(zipFile)
                            currentZipJob = null
                        }
                    }

                    override fun onError(e: Exception) {
                        Log.e(TAG, "Zip error callback", e)
                        // Post callback to main thread
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onError(e)
                            currentZipJob = null
                        }
                    }
                })
            } catch (e: CancellationException) {
                // Cancellation is expected, just clean up
                Log.d(TAG, "File preparation cancelled")
                currentZipJob = null
                // Don't call error callback for cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing file for upload", e)
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(e)
                    currentZipJob = null
                }
            }
        }
    }

    /**
     * Prepare multiple files for upload by zipping them into a single file
     * Runs zipping on Dispatchers.IO to prevent ANR
     *
     * @param files The list of files to prepare
     * @param context The context
     * @param callback Callback for progress updates and completion
     */
    @JvmStatic
    fun prepareMultipleFilesForUpload(
        files: List<File>,
        context: Context,
        callback: FilePrepareCallback
    ) {
        if (files.isEmpty()) {
            callback.onError(IOException("No files provided"))
            return
        }

        // Create a unique filename for the zip
        val zipFileName = "multiple_files_${System.currentTimeMillis()}${FileConstants.ZIP_EXTENSION}"

        // Zip files on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ZipUtil.zipFiles(
                    files,
                    context.cacheDir,
                    zipFileName,
                    object : ZipUtil.ZipProgressListener {
                        override fun onProgress(bytesProcessed: Long, totalBytes: Long) {
                            val progress = if (totalBytes > 0) {
                                ((bytesProcessed * 100) / totalBytes).toInt()
                            } else {
                                0
                            }
                            // Post callback to main thread for UI updates
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onProgress(progress)
                            }
                        }

                        override fun onComplete(zipFile: File) {
                            // Post callback to main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onSuccess(zipFile)
                            }
                        }

                        override fun onError(e: Exception) {
                            // Post callback to main thread
                            CoroutineScope(Dispatchers.Main).launch {
                                callback.onError(e)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing multiple files for upload", e)
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(e)
                }
            }
        }
    }

    /**
     * Save a content URI to a temporary file
     *
     * @param context The context
     * @param uri The content URI
     * @param filename The desired filename
     * @return The saved file
     * @throws IOException If saving fails
     */
    @JvmStatic
    @Throws(IOException::class)
    fun saveContentUriToFile(context: Context, uri: Uri, filename: String): File {
        // Create a temporary file
        val outputFile = File(context.cacheDir, filename)

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        } ?: throw IOException("Could not open input stream for URI: $uri")

        return outputFile
    }

    /**
     * Validates if the file size is within the allowable limits
     *
     * @param file The file to validate
     * @return true if the file size is valid, false otherwise
     */
    @JvmStatic
    fun isValidFileSize(file: File): Boolean {
        return FileUtil.isValidFileSize(file)
    }

    /**
     * Cancel any ongoing file preparation (zipping) operation
     */
    @JvmStatic
    fun cancelPreparation() {
        currentZipJob?.cancel()
        currentZipJob = null
        Log.d(TAG, "Cancelled file preparation")
    }

    /**
     * Prepare a file for upload asynchronously
     * Returns a CompletableFuture for Java compatibility
     *
     * @param file The file to prepare
     * @return CompletableFuture containing the prepared file
     */
    @JvmStatic
    fun prepareFileForUploadAsync(file: File): java.util.concurrent.CompletableFuture<File> {
        val future = java.util.concurrent.CompletableFuture<File>()

        if (!file.exists()) {
            future.completeExceptionally(IOException("File does not exist"))
            return future
        }

        // Check file size
        if (!FileUtil.isValidFileSize(file)) {
            future.completeExceptionally(
                IOException("File size exceeds limit of ${FileConstants.MAX_FILE_SIZE} bytes")
            )
            return future
        }

        // If already a zip file, just return it
        if (FileUtil.isZipFile(file)) {
            future.complete(file)
            return future
        }

        // Otherwise, compress it on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resultFile: File? = null
                ZipUtil.zipFile(file, object : ZipUtil.ZipProgressListener {
                    override fun onProgress(bytesProcessed: Long, totalBytes: Long) {
                        // Progress not needed for async API
                    }

                    override fun onComplete(zipFile: File) {
                        resultFile = zipFile
                        future.complete(zipFile)
                    }

                    override fun onError(e: Exception) {
                        future.completeExceptionally(e)
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing file for upload", e)
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Prepare multiple files for upload asynchronously
     * Returns a CompletableFuture for Java compatibility
     *
     * @param files The list of files to prepare
     * @param context The context
     * @return CompletableFuture containing the prepared file
     */
    @JvmStatic
    fun prepareMultipleFilesForUploadAsync(
        files: List<File>,
        context: Context
    ): java.util.concurrent.CompletableFuture<File> {
        val future = java.util.concurrent.CompletableFuture<File>()

        if (files.isEmpty()) {
            future.completeExceptionally(IOException("No files provided"))
            return future
        }

        // Create a unique filename for the zip
        val zipFileName = "multiple_files_${System.currentTimeMillis()}${FileConstants.ZIP_EXTENSION}"

        // Zip files on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ZipUtil.zipFiles(
                    files,
                    context.cacheDir,
                    zipFileName,
                    object : ZipUtil.ZipProgressListener {
                        override fun onProgress(bytesProcessed: Long, totalBytes: Long) {
                            // Progress not needed for async API
                        }

                        override fun onComplete(zipFile: File) {
                            future.complete(zipFile)
                        }

                        override fun onError(e: Exception) {
                            future.completeExceptionally(e)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing multiple files for upload", e)
                future.completeExceptionally(e)
            }
        }

        return future
    }

    /**
     * Save a content URI to a temporary file asynchronously
     * Returns a CompletableFuture for Java compatibility
     *
     * @param context The context
     * @param uri The content URI
     * @param filename The desired filename
     * @return CompletableFuture containing the saved file
     */
    @JvmStatic
    fun saveContentUriToFileAsync(
        context: Context,
        uri: Uri,
        filename: String
    ): java.util.concurrent.CompletableFuture<File> {
        val future = java.util.concurrent.CompletableFuture<File>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = saveContentUriToFile(context, uri, filename)
                future.complete(file)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving content URI to file", e)
                future.completeExceptionally(e)
            }
        }

        return future
    }
}
