package com.mattintech.simplelogupload.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.constant.FileConstants;
import com.mattintech.simplelogupload.constant.NetworkConstants;
import com.mattintech.simplelogupload.controller.FileController;
import com.mattintech.simplelogupload.controller.UploadController;
import com.mattintech.simplelogupload.model.UploadResult;
import com.mattintech.simplelogupload.view.MainActivity;

import java.io.File;

/**
 * Foreground service for file uploads
 */
public class FileUploadForegroundService extends Service {
    private static final String TAG = AppConstants.APP_TAG + "UploadService";
    private static final int NOTIFICATION_ID = 1;
    
    // Intent extras
    public static final String EXTRA_FILE_PATH = "extra_file_path";
    public static final String EXTRA_MAX_DOWNLOADS = "extra_max_downloads";
    public static final String EXTRA_EXPIRY_HOURS = "extra_expiry_hours";
    
    private UploadController uploadController;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    @Override
    public void onCreate() {
        super.onCreate();
        uploadController = new UploadController(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Foreground Service started.");

        // Get file path from intent
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        int maxDownloads = intent.getIntExtra(EXTRA_MAX_DOWNLOADS, NetworkConstants.DEFAULT_MAX_DOWNLOADS);
        int expiryHours = intent.getIntExtra(EXTRA_EXPIRY_HOURS, NetworkConstants.DEFAULT_EXPIRY_HOURS);
        
        if (filePath == null) {
            Log.e(TAG, "No file path provided");
            // Try to find the most recent dump state file
            File mostRecentFile = FileController.getMostRecentDumpStateFile();
            if (mostRecentFile != null) {
                filePath = mostRecentFile.getAbsolutePath();
            } else {
                Log.e(TAG, "No file to upload");
                sendErrorBroadcast("No file to upload");
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            Log.e(TAG, "File not found: " + filePath);
            sendErrorBroadcast("File not found");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Create a notification and run the service in the foreground
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // For Android 14+ (API 34+)
            startForeground(NOTIFICATION_ID, createInitialNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            // For Android 13 and below
            startForeground(NOTIFICATION_ID, createInitialNotification());
        }
        
        // Prepare file for upload
        FileController.prepareFileForUpload(file, new FileController.FilePrepareCallback() {
            @Override
            public void onSuccess(File preparedFile) {
                Log.d(TAG, "File prepared for upload: " + preparedFile.getName());
                uploadFile(preparedFile, maxDownloads, expiryHours);
            }
            
            @Override
            public void onProgress(int progress) {
                updateNotification("Preparing file: " + progress + "%");
            }
            
            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error preparing file", e);
                sendErrorBroadcast("Error preparing file: " + e.getMessage());
                stopSelf();
            }
        });

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Upload a file using the UploadController
     * 
     * @param file The file to upload
     * @param maxDownloads Maximum number of downloads
     * @param expiryHours Expiry time in hours
     */
    private void uploadFile(File file, int maxDownloads, int expiryHours) {
        // Validate file size before upload
        if (!FileController.isValidFileSize(file)) {
            Log.e(TAG, "File too large: " + file.length() + " bytes (max: " + FileConstants.MAX_FILE_SIZE + " bytes)");
            sendErrorBroadcast("File size exceeds the maximum allowed size");
            stopSelf();
            return;
        }
        
        // Update notification to show uploading
        updateNotification("Uploading " + file.getName() + "...");
        
        // Upload the file
        uploadController.uploadFile(file, maxDownloads, expiryHours)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        Log.d(TAG, "Upload successful! Code: " + result.getCode());
                        sendSuccessBroadcast(result.getCode());
                    } else {
                        Log.e(TAG, "Upload failed: " + result.getError());
                        sendErrorBroadcast(result.getError());
                    }
                    stopSelf();
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "Exception during upload", ex);
                    sendErrorBroadcast("Error during upload: " + ex.getMessage());
                    stopSelf();
                    return null;
                });
    }
    
    /**
     * Create the initial notification
     * 
     * @return The notification
     */
    private Notification createInitialNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, 
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        notificationBuilder = new NotificationCompat.Builder(this, AppConstants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle("File Upload")
                .setContentText("Preparing file...")
                .setSmallIcon(R.drawable.logupload)
                .setContentIntent(pendingIntent)
                .setProgress(100, 0, true)
                .setOngoing(true);

        return notificationBuilder.build();
    }
    
    /**
     * Update the notification with a new status message
     * 
     * @param status The status message
     */
    private void updateNotification(String status) {
        if (notificationBuilder != null) {
            notificationBuilder.setContentText(status);
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    /**
     * Create the notification channel (required for Android 8.0+)
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "File Upload Channel";
            String description = "Notification channel for file upload";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(
                    AppConstants.NOTIFICATION_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Send a broadcast with the upload success result
     * 
     * @param uploadCode The upload code from the server
     */
    private void sendSuccessBroadcast(String uploadCode) {
        Log.d(TAG, "Sending success broadcast with code: " + uploadCode);
        Intent intent = new Intent(AppConstants.ACTION_UPLOAD_COMPLETE);
        intent.putExtra(AppConstants.EXTRA_UPLOAD_CODE, uploadCode);
        
        // Send both a regular broadcast and a local broadcast to ensure delivery
        sendBroadcast(intent);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    /**
     * Send a broadcast with the upload error result
     * 
     * @param errorMessage The error message
     */
    private void sendErrorBroadcast(String errorMessage) {
        Log.e(TAG, "Sending error broadcast: " + errorMessage);
        Intent intent = new Intent(AppConstants.ACTION_UPLOAD_ERROR);
        intent.putExtra(AppConstants.EXTRA_UPLOAD_ERROR, errorMessage);
        
        // Send both a regular broadcast and a local broadcast to ensure delivery
        sendBroadcast(intent);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
