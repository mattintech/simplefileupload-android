package com.mattintech.simplelogupload.util;

import android.os.Environment;
import android.util.Log;

import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.constant.FileConstants;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Utility class for file operations
 */
public class FileUtil {
    private static final String TAG = AppConstants.APP_TAG + "FileUtil";
    
    /**
     * Finds the most recent dumpstate file in the logs directory
     * @return The most recent dumpstate file or null if none found
     */
    public static File findMostRecentDumpStateFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), FileConstants.LOG_DIRECTORY);
        return findMostRecentFileByPattern(dir, AppConstants.DUMPSTATE_FILE_PATTERN);
    }
    
    /**
     * Finds the most recent file in a directory matching the given pattern
     * 
     * @param dir The directory to search
     * @param pattern The regex pattern to match filenames
     * @return The most recent matching file or null if none found
     */
    public static File findMostRecentFileByPattern(File dir, String pattern) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Log.e(TAG, "Invalid directory: " + (dir != null ? dir.getAbsolutePath() : "null"));
            return null;
        }
        
        File[] files = dir.listFiles((d, name) -> name.matches(pattern));
        
        if (files == null || files.length == 0) {
            Log.d(TAG, "No files matching pattern '" + pattern + "' found in " + dir.getAbsolutePath());
            return null;
        }
        
        return Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
    }
    
    /**
     * Checks if a file needs to be zipped
     * @param file The file to check
     * @return true if the file is already a zip, false otherwise
     */
    public static boolean isZipFile(File file) {
        if (file == null) {
            return false;
        }
        return file.getName().toLowerCase().endsWith(FileConstants.ZIP_EXTENSION);
    }
    
    /**
     * Validates if the file size is within the allowable limits
     * @param file The file to validate
     * @return true if the file size is valid, false otherwise
     */
    public static boolean isValidFileSize(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        
        boolean isValid = file.length() <= FileConstants.MAX_FILE_SIZE;
        
        if (!isValid) {
            Log.w(TAG, "File size check failed: " + file.length() + " bytes exceeds limit of " 
                + FileConstants.MAX_FILE_SIZE + " bytes");
        }
        
        return isValid;
    }
    
    /**
     * Gets the file extension from a filename
     * @param filename The filename
     * @return The file extension or an empty string if none
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDotIndex);
    }
}
