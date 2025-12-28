package com.mattintech.simplelogupload.upload

/**
 * Callback interface for tracking upload progress
 */
interface UploadProgressCallback {
    /**
     * Called when upload progress changes
     *
     * @param bytesUploaded Number of bytes uploaded so far
     * @param totalBytes Total number of bytes to upload
     * @param percentComplete Percentage complete (0-100)
     */
    fun onProgress(bytesUploaded: Long, totalBytes: Long, percentComplete: Int)

    /**
     * Called when a chunk upload completes (for chunked uploads)
     *
     * @param chunkIndex Index of the completed chunk
     * @param totalChunks Total number of chunks
     */
    fun onChunkComplete(chunkIndex: Int, totalChunks: Int)

    /**
     * Called for general status updates
     *
     * @param status Status message
     */
    fun onStatusUpdate(status: String)
}
