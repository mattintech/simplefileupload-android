package com.mattintech.simplelogupload.upload;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.mattintech.simplelogupload.constant.AppConstants;

import java.io.File;

/**
 * Helper class to determine the best upload strategy based on file size and network conditions
 */
public class UploadStrategySelector {
    private static final String TAG = AppConstants.APP_TAG + "UploadStrategy";
    
    // Threshold for using chunked uploads - matches chunk sizes
    // Only use chunked upload if file is larger than chunk size
    private static final long CHUNKED_UPLOAD_THRESHOLD_WIFI = 10 * 1024 * 1024; // 10MB for WiFi (matches chunk size)
    private static final long CHUNKED_UPLOAD_THRESHOLD_MOBILE = 4 * 1024 * 1024; // 4MB for mobile (matches chunk size)
    
    /**
     * Check if chunked uploads should be used for the given file
     * 
     * @param context Application context
     * @param file The file to upload
     * @return True if chunked upload should be used, false otherwise
     */
    public static boolean shouldUseChunkedUpload(Context context, File file) {
        if (file == null) {
            return false;
        }
        
        long fileSize = file.length();
        boolean isWifiConnection = isWifiConnection(context);
        long threshold = isWifiConnection ? CHUNKED_UPLOAD_THRESHOLD_WIFI : CHUNKED_UPLOAD_THRESHOLD_MOBILE;
        
        boolean useChunked = fileSize > threshold;
        Log.d(TAG, "File size: " + fileSize + " bytes, Connection: " + (isWifiConnection ? "WiFi" : "Mobile") + 
                ", Using chunked upload: " + useChunked);
        
        return useChunked;
    }
    
    /**
     * Check if the device is currently connected to WiFi
     * 
     * @param context Application context
     * @return True if connected to WiFi, false otherwise
     */
    public static boolean isWifiConnection(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    }
    
    /**
     * Get the network type name for logging
     * 
     * @param context Application context
     * @return String representing the network type ("WiFi", "Mobile", or "Unknown")
     */
    public static String getNetworkTypeName(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        
        if (info == null || !info.isConnected()) {
            return "Unknown";
        }
        
        if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            return "WiFi";
        }
        
        if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
            String subType = "";
            switch (info.getSubtype()) {
                case 13: // LTE
                    subType = "LTE";
                    break;
                case 19: // 5G
                    subType = "5G";
                    break;
                case 20: // 5G+
                    subType = "5G+";
                    break;
                default:
                    subType = info.getSubtypeName();
            }
            return "Mobile (" + subType + ")";
        }
        
        return "Unknown";
    }
}
