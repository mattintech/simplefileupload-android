package com.mattintech.simplelogupload;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import com.mattintech.simplelogupload.model.UploadResult;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileUploadForegroundService extends Service {

    private static final String TAG = Constant.APP_TAG + "FileUploadService";
    private static final String CHANNEL_ID = "FileUploadChannel";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Foreground Service started.");

        // Create a notification and run the service in the foreground
        createNotificationChannel();
        Notification notification = createNotification();
        startForeground(1, notification);

        Log.d(TAG, "Notification created.");

        // Start file upload process
        File dir = new File(Environment.getExternalStorageDirectory(), "log");
        File mostRecentFile = findMostRecentFile(dir);
        if (mostRecentFile != null) {
            uploadFile(mostRecentFile);
        } else {
            Log.e(TAG, "No DUMPSTATE file found.");
            sendErrorBroadcast("No DUMPSTATE file found");
            stopSelf();
        }

        return START_NOT_STICKY;
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("File Upload")
                .setContentText("Uploading file in progress...")
                .setSmallIcon(R.drawable.logupload)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "File Upload Channel";
            String description = "Notification channel for file upload";
            int importance = NotificationManager.IMPORTANCE_HIGH;  // Change to HIGH to ensure visibility
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private File findMostRecentFile(File dir) {
        File[] files = dir.listFiles((d, name) -> name.matches("dumpState_.*\\.zip"));
        return (files != null && files.length > 0) ? Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null) : null;
    }

    private void uploadFile(File file) {
        // Validate file size before upload
        if (file.length() > Constant.MAX_FILE_SIZE) {
            Log.e(TAG, "File too large: " + file.length() + " bytes (max: " + Constant.MAX_FILE_SIZE + " bytes)");
            sendErrorBroadcast("File size exceeds the maximum allowed size of 2GB");
            stopSelf();
            return;
        }
        
        // Create OkHttpClient with timeout configuration for large files
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(180, TimeUnit.SECONDS) // 3 minutes timeout for large uploads
                .readTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
                
        Log.d(TAG, "Uploading file: " + file.getName() + ", Size: " + file.length());

        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/zip"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(Constant.PARAM_FILE, file.getName(), fileBody)
                .addFormDataPart(Constant.PARAM_MAX_DOWNLOADS, String.valueOf(Constant.DEFAULT_MAX_DOWNLOADS))
                .addFormDataPart(Constant.PARAM_EXPIRY_HOURS, String.valueOf(Constant.DEFAULT_EXPIRY_HOURS))
                .build();

        Request request = new Request.Builder()
                .url(Constant.SERVER_URL)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                String errorMsg = "Upload failed: " + e.getMessage();
                Log.e(TAG, errorMsg);
                sendErrorBroadcast(errorMsg);
                stopSelf();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Server response: " + responseBody);
                        
                        // Parse JSON response to get the upload code
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String uploadCode = jsonResponse.getString("code");
                        
                        Log.d(TAG, "Upload successful! Code: " + uploadCode);
                        sendSuccessBroadcast(uploadCode);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse server response", e);
                        sendErrorBroadcast("Failed to parse server response");
                    }
                } else {
                    String errorBody = response.body().string();
                    String errorMsg = "Upload failed, Response code: " + response.code();
                    Log.e(TAG, errorMsg + ", Body: " + errorBody);
                    
                    try {
                        JSONObject errorJson = new JSONObject(errorBody);
                        if (errorJson.has("error")) {
                            errorMsg = errorJson.getString("error");
                        }
                    } catch (JSONException e) {
                        // If parsing fails, use the default error message
                    }
                    
                    sendErrorBroadcast(errorMsg);
                }
                stopSelf();
            }
        });
    }
    
    private void sendSuccessBroadcast(String uploadCode) {
        Intent intent = new Intent(Constant.ACTION_UPLOAD_COMPLETE);
        intent.putExtra(Constant.EXTRA_UPLOAD_CODE, uploadCode);
        sendBroadcast(intent);
    }
    
    private void sendErrorBroadcast(String errorMessage) {
        Intent intent = new Intent(Constant.ACTION_UPLOAD_ERROR);
        intent.putExtra(Constant.EXTRA_UPLOAD_ERROR, errorMessage);
        sendBroadcast(intent);
    }
}
