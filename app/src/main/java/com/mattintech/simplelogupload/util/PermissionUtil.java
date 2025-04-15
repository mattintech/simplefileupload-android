package com.mattintech.simplelogupload.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mattintech.simplelogupload.constant.AppConstants;

/**
 * Utility class for handling permissions
 */
public class PermissionUtil {
    private static final String TAG = AppConstants.APP_TAG + "PermissionUtil";
    
    public static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 101;
    public static final int REQUEST_NOTIFICATION_PERMISSION = 102;
    
    /**
     * Checks if the app has notification permission
     * 
     * @param context The context
     * @return true if the app has notification permission, false otherwise
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
        }
        // Permission is implicitly granted on older Android versions
        return true;
    }
    
    /**
     * Requests notification permission
     * 
     * @param activity The activity
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                Log.d(TAG, "Requesting notification permission");
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION
                );
            }
        }
    }
    
    /**
     * Checks if the app has all files access permission
     * 
     * @return true if the app has all files access permission, false otherwise
     */
    public static boolean hasAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        // Permission is handled differently on older Android versions
        return true;
    }
    
    /**
     * Requests all files access permission
     * 
     * @param activity The activity
     */
    public static void requestAllFilesAccessPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!hasAllFilesAccessPermission()) {
                Log.d(TAG, "Requesting all files access permission");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                } catch (Exception e) {
                    Log.e(TAG, "Error requesting all files access permission", e);
                    // Fallback in case the direct intent fails
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activity.startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
                }
            }
        }
    }
}
