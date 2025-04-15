package com.mattintech.simplelogupload.util;

import android.util.Log;

import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.constant.FileConstants;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for zipping files
 */
public class ZipUtil {
    private static final String TAG = AppConstants.APP_TAG + "ZipUtil";
    private static final int BUFFER_SIZE = 8192; // 8KB buffer
    
    /**
     * Interface for tracking zip progress
     */
    public interface ZipProgressListener {
        void onProgress(long bytesProcessed, long totalBytes);
        void onComplete(File zipFile);
        void onError(Exception e);
    }
    
    /**
     * Compresses a file into a zip archive
     * 
     * @param sourceFile The file to compress
     * @return The compressed zip file
     * @throws IOException If compression fails
     */
    public static File zipFile(File sourceFile) throws IOException {
        return zipFile(sourceFile, null);
    }
    
    /**
     * Compresses a file into a zip archive with progress tracking
     * 
     * @param sourceFile The file to compress
     * @param listener A listener for tracking progress (can be null)
     * @return The compressed zip file
     * @throws IOException If compression fails
     */
    public static File zipFile(File sourceFile, ZipProgressListener listener) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            String msg = "Source file does not exist: " + (sourceFile != null ? sourceFile.getAbsolutePath() : "null");
            Log.e(TAG, msg);
            throw new IOException(msg);
        }
        
        // If already a zip file, just return it
        if (FileUtil.isZipFile(sourceFile)) {
            Log.d(TAG, "File is already a zip file: " + sourceFile.getName());
            if (listener != null) {
                listener.onComplete(sourceFile);
            }
            return sourceFile;
        }
        
        // Create a zip file in the same directory
        String zipFileName = sourceFile.getName() + FileConstants.ZIP_EXTENSION;
        File zipFile = new File(sourceFile.getParentFile(), zipFileName);
        
        Log.d(TAG, "Creating zip file: " + zipFile.getAbsolutePath());
        
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos);
             FileInputStream fis = new FileInputStream(sourceFile);
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            
            ZipEntry zipEntry = new ZipEntry(sourceFile.getName());
            zipEntry.setSize(sourceFile.length());
            zos.putNextEntry(zipEntry);
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            long bytesProcessed = 0;
            long totalBytes = sourceFile.length();
            
            while ((len = bis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
                bytesProcessed += len;
                
                if (listener != null) {
                    listener.onProgress(bytesProcessed, totalBytes);
                }
            }
            
            zos.closeEntry();
            
            if (listener != null) {
                listener.onComplete(zipFile);
            }
            
            Log.d(TAG, "Successfully created zip file: " + zipFile.getName());
            return zipFile;
        } catch (IOException e) {
            Log.e(TAG, "Error creating zip file", e);
            
            // Clean up the partial zip file if it exists
            if (zipFile.exists()) {
                boolean deleted = zipFile.delete();
                if (!deleted) {
                    Log.w(TAG, "Failed to delete partial zip file: " + zipFile.getAbsolutePath());
                }
            }
            
            if (listener != null) {
                listener.onError(e);
            }
            
            throw e;
        }
    }
}
