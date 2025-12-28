package com.mattintech.simplelogupload.util

import android.util.Log
import com.mattintech.simplelogupload.constant.AppConstants
import com.mattintech.simplelogupload.constant.FileConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility class for zipping files using Kotlin coroutines
 * CRITICAL: All file I/O operations run on Dispatchers.IO to prevent ANR
 */
object ZipUtil {
    private const val TAG = AppConstants.APP_TAG + "ZipUtil"
    private const val BUFFER_SIZE = 8192 // 8KB buffer

    /**
     * Interface for tracking zip progress
     */
    interface ZipProgressListener {
        fun onProgress(bytesProcessed: Long, totalBytes: Long)
        fun onComplete(zipFile: File)
        fun onError(e: Exception)
    }

    /**
     * Compresses a file into a zip archive
     * Runs on Dispatchers.IO to prevent ANR
     * Checks for cancellation during compression
     *
     * @param sourceFile The file to compress
     * @param listener A listener for tracking progress (can be null)
     * @throws IOException If compression fails
     */
    @JvmStatic
    suspend fun zipFile(sourceFile: File, listener: ZipProgressListener? = null) {
        withContext(Dispatchers.IO) {
            try {
                if (!sourceFile.exists()) {
                    val msg = "Source file does not exist: ${sourceFile.absolutePath}"
                    Log.e(TAG, msg)
                    throw IOException(msg)
                }

                // If already a zip file, just return it
                if (FileUtil.isZipFile(sourceFile)) {
                    Log.d(TAG, "File is already a zip file: ${sourceFile.name}")
                    listener?.onComplete(sourceFile)
                    return@withContext
                }

                // Create a zip file in the same directory
                val zipFileName = sourceFile.name + FileConstants.ZIP_EXTENSION
                val zipFile = File(sourceFile.parentFile, zipFileName)

                Log.d(TAG, "Creating zip file: ${zipFile.absolutePath}")

                try {
                    FileOutputStream(zipFile).use { fos ->
                        BufferedOutputStream(fos).use { bos ->
                            ZipOutputStream(bos).use { zos ->
                                FileInputStream(sourceFile).use { fis ->
                                    BufferedInputStream(fis).use { bis ->
                                        val zipEntry = ZipEntry(sourceFile.name)
                                        zipEntry.size = sourceFile.length()
                                        zos.putNextEntry(zipEntry)

                                        val buffer = ByteArray(BUFFER_SIZE)
                                        var bytesProcessed = 0L
                                        val totalBytes = sourceFile.length()
                                        var len: Int
                                        var lastProgressUpdate = 0L

                                        while (bis.read(buffer).also { len = it } > 0) {
                                            // Check for cancellation
                                            if (!isActive) {
                                                Log.d(TAG, "Zip operation cancelled by user")
                                                throw CancellationException("Zip operation cancelled")
                                            }

                                            zos.write(buffer, 0, len)
                                            bytesProcessed += len

                                            // Update progress every 100KB to avoid overwhelming UI
                                            if (bytesProcessed - lastProgressUpdate >= 100_000 || bytesProcessed == totalBytes) {
                                                lastProgressUpdate = bytesProcessed
                                                Log.d(TAG, "Zip progress: $bytesProcessed / $totalBytes bytes (${(bytesProcessed * 100 / totalBytes)}%)")
                                                listener?.onProgress(bytesProcessed, totalBytes)
                                            }
                                        }

                                        zos.closeEntry()
                                    }
                                }
                            }
                        }
                    }

                    listener?.onComplete(zipFile)
                    Log.d(TAG, "Successfully created zip file: ${zipFile.name}")
                } catch (e: CancellationException) {
                    // Clean up the partial zip file
                    if (zipFile.exists()) {
                        zipFile.delete()
                        Log.d(TAG, "Deleted partial zip file after cancellation")
                    }
                    // Re-throw to cancel the coroutine properly
                    throw e
                } catch (e: IOException) {
                    Log.e(TAG, "Error creating zip file", e)

                    // Clean up the partial zip file if it exists
                    if (zipFile.exists()) {
                        val deleted = zipFile.delete()
                        if (!deleted) {
                            Log.w(TAG, "Failed to delete partial zip file: ${zipFile.absolutePath}")
                        }
                    }

                    listener?.onError(e)
                    throw e
                }
            } catch (e: CancellationException) {
                // Don't treat cancellation as an error - just re-throw it
                Log.d(TAG, "Zip cancelled - cleaning up")
                throw e
            } catch (e: Exception) {
                listener?.onError(e)
                throw e
            }
        }
    }

    /**
     * Compresses multiple files into a single zip archive
     * Runs on Dispatchers.IO to prevent ANR
     *
     * @param sourceFiles List of files to compress
     * @param outputDirectory Directory to store the zip file
     * @param zipFileName Name of the zip file to create
     * @param listener A listener for tracking progress (can be null)
     * @return The compressed zip file
     * @throws IOException If compression fails
     */
    @JvmStatic
    suspend fun zipFiles(
        sourceFiles: List<File>,
        outputDirectory: File,
        zipFileName: String,
        listener: ZipProgressListener? = null
    ): File = withContext(Dispatchers.IO) {
        if (sourceFiles.isEmpty()) {
            val msg = "No source files provided for zipping"
            Log.e(TAG, msg)
            throw IOException(msg)
        }

        // Create output directory if it doesn't exist
        if (!outputDirectory.exists()) {
            if (!outputDirectory.mkdirs()) {
                throw IOException("Could not create output directory: ${outputDirectory.absolutePath}")
            }
        }

        // Ensure the zip extension
        val finalZipFileName = if (zipFileName.lowercase().endsWith(FileConstants.ZIP_EXTENSION)) {
            zipFileName
        } else {
            zipFileName + FileConstants.ZIP_EXTENSION
        }

        // Create a zip file in the output directory
        val zipFile = File(outputDirectory, finalZipFileName)

        Log.d(TAG, "Creating multi-file zip: ${zipFile.absolutePath} with ${sourceFiles.size} files")

        // Calculate total size for progress reporting
        val totalBytes = sourceFiles.filter { it.exists() }.sumOf { it.length() }

        try {
            FileOutputStream(zipFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    ZipOutputStream(bos).use { zos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesProcessed = 0L

                        for (file in sourceFiles) {
                            if (!file.exists() || !file.isFile) {
                                Log.w(TAG, "Skipping non-existent or non-file: ${file.absolutePath}")
                                continue
                            }

                            FileInputStream(file).use { fis ->
                                BufferedInputStream(fis).use { bis ->
                                    val zipEntry = ZipEntry(file.name)
                                    zipEntry.size = file.length()
                                    zos.putNextEntry(zipEntry)

                                    var len: Int
                                    while (bis.read(buffer).also { len = it } > 0) {
                                        zos.write(buffer, 0, len)
                                        bytesProcessed += len

                                        listener?.onProgress(bytesProcessed, totalBytes)
                                    }

                                    zos.closeEntry()
                                }
                            }
                        }
                    }
                }
            }

            listener?.onComplete(zipFile)
            Log.d(TAG, "Successfully created multi-file zip: ${zipFile.name}")

            return@withContext zipFile
        } catch (e: IOException) {
            Log.e(TAG, "Error creating multi-file zip", e)

            // Clean up the partial zip file if it exists
            if (zipFile.exists()) {
                val deleted = zipFile.delete()
                if (!deleted) {
                    Log.w(TAG, "Failed to delete partial zip file: ${zipFile.absolutePath}")
                }
            }

            listener?.onError(e)
            throw e
        }
    }
}
