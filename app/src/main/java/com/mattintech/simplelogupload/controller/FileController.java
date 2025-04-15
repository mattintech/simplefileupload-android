package com.mattintech.simplelogupload.controller;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.constant.FileConstants;
import com.mattintech.simplelogupload.util.FileUtil;
import com.mattintech.simplelogupload.util.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Controller for file operations
 */
public class FileController {
    private static final String TAG = AppConstants.APP_TAG + "FileController";
    
    /**
     * Interface for file preparation callbacks
     */
    public interface FilePrepareCallback {
        void onSuccess(File preparedFile);
        void onProgress(int progress);
        void onError(Exception e);
    }
    
    /**
     * Find the most recent dumpstate file
     * 
     * @return The most recent dumpstate file or null if none found
     */
    public static File getMostRecentDumpStateFile() {
        return FileUtil.findMostRecentDumpStateFile();
    }
    
    /**
     * Find all dumpstate files
     * 
     * @return Array of dumpstate files
     */
    public static File[] getAllDumpStateFiles() {
        return FileUtil.findAllDumpStateFiles();
    }
    
    /**
     * Prepare a file for upload - ensures the file is zipped
     * 
     * @param file The file to prepare
     * @param callback Callback for progress updates and completion
     */
    public static void prepareFileForUpload(File file, FilePrepareCallback callback) {
        if (file == null || !file.exists()) {
            callback.onError(new IOException("File does not exist"));
            return;
        }
        
        // Check file size
        if (!FileUtil.isValidFileSize(file)) {
            callback.onError(new IOException("File size exceeds limit of " + 
                    FileConstants.MAX_FILE_SIZE + " bytes"));
            return;
        }
        
        // If already a zip file, just return it
        if (FileUtil.isZipFile(file)) {
            callback.onSuccess(file);
            return;
        }
        
        // Otherwise, compress it
        try {
            ZipUtil.zipFile(file, new ZipUtil.ZipProgressListener() {
                @Override
                public void onProgress(long bytesProcessed, long totalBytes) {
                    int progress = (int) ((bytesProcessed * 100) / totalBytes);
                    callback.onProgress(progress);
                }
                
                @Override
                public void onComplete(File zipFile) {
                    callback.onSuccess(zipFile);
                }
                
                @Override
                public void onError(Exception e) {
                    callback.onError(e);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error preparing file for upload", e);
            callback.onError(e);
        }
    }
    
    /**
     * Prepare multiple files for upload by zipping them into a single file
     * 
     * @param files The list of files to prepare
     * @param context The context
     * @param callback Callback for progress updates and completion
     */
    public static void prepareMultipleFilesForUpload(List<File> files, Context context, FilePrepareCallback callback) {
        if (files == null || files.isEmpty()) {
            callback.onError(new IOException("No files provided"));
            return;
        }
        
        // Create a unique filename for the zip
        String zipFileName = "multiple_files_" + System.currentTimeMillis() + FileConstants.ZIP_EXTENSION;
        
        try {
            // Use cache directory for temporary zip storage
            ZipUtil.zipFiles(files, context.getCacheDir(), zipFileName, new ZipUtil.ZipProgressListener() {
                @Override
                public void onProgress(long bytesProcessed, long totalBytes) {
                    int progress = (int) ((bytesProcessed * 100) / Math.max(totalBytes, 1)); // Avoid division by zero
                    callback.onProgress(progress);
                }
                
                @Override
                public void onComplete(File zipFile) {
                    callback.onSuccess(zipFile);
                }
                
                @Override
                public void onError(Exception e) {
                    callback.onError(e);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Error preparing multiple files for upload", e);
            callback.onError(e);
        }
    }
    
    /**
     * Prepare a file for upload asynchronously
     * 
     * @param file The file to prepare
     * @return CompletableFuture containing the prepared file
     */
    public static CompletableFuture<File> prepareFileForUploadAsync(File file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (file == null || !file.exists()) {
                    throw new IOException("File does not exist");
                }
                
                // Check file size
                if (!FileUtil.isValidFileSize(file)) {
                    throw new IOException("File size exceeds limit of " + 
                            FileConstants.MAX_FILE_SIZE + " bytes");
                }
                
                // If already a zip file, just return it
                if (FileUtil.isZipFile(file)) {
                    return file;
                }
                
                // Otherwise, compress it
                return ZipUtil.zipFile(file);
            } catch (IOException e) {
                Log.e(TAG, "Error preparing file for upload", e);
                throw new RuntimeException(e);
            }
        }, Executors.newSingleThreadExecutor());
    }
    
    /**
     * Prepare multiple files for upload asynchronously
     * 
     * @param files The list of files to prepare
     * @param context The context
     * @return CompletableFuture containing the prepared file
     */
    public static CompletableFuture<File> prepareMultipleFilesForUploadAsync(List<File> files, Context context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (files == null || files.isEmpty()) {
                    throw new IOException("No files provided");
                }
                
                // Create a unique filename for the zip
                String zipFileName = "multiple_files_" + System.currentTimeMillis() + FileConstants.ZIP_EXTENSION;
                
                // Use cache directory for temporary zip storage
                return ZipUtil.zipFiles(files, context.getCacheDir(), zipFileName, null);
            } catch (IOException e) {
                Log.e(TAG, "Error preparing multiple files for upload", e);
                throw new RuntimeException(e);
            }
        }, Executors.newSingleThreadExecutor());
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
    public static File saveContentUriToFile(Context context, Uri uri, String filename) throws IOException {
        if (context == null || uri == null) {
            throw new IOException("Invalid context or URI");
        }
        
        // Create a temporary file
        File outputFile = new File(context.getCacheDir(), filename);
        
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            
            if (inputStream == null) {
                throw new IOException("Could not open input stream for URI: " + uri);
            }
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        
        return outputFile;
    }
    
    /**
     * Validates if the file size is within the allowable limits
     * 
     * @param file The file to validate
     * @return true if the file size is valid, false otherwise
     */
    public static boolean isValidFileSize(File file) {
        return FileUtil.isValidFileSize(file);
    }
    
    /**
     * Save a content URI to a temporary file asynchronously
     * 
     * @param context The context
     * @param uri The content URI
     * @param filename The desired filename
     * @return CompletableFuture containing the saved file
     */
    public static CompletableFuture<File> saveContentUriToFileAsync(
            Context context, Uri uri, String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return saveContentUriToFile(context, uri, filename);
            } catch (IOException e) {
                Log.e(TAG, "Error saving content URI to file", e);
                throw new RuntimeException(e);
            }
        }, Executors.newSingleThreadExecutor());
    }
}
