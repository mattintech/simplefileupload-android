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
        OkHttpClient client = new OkHttpClient();
        Log.d(TAG, "Uploading file: " + file.getName() + ", Size: " + file.length());

        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/zip"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url("http://172.16.30.69:7777/upload") // Replace with your server's URL
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Upload failed: " + e.getMessage());
                stopSelf();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String tinyUrl = response.body().string(); // Assuming the server returns tinyURL in response
                    Log.d(TAG, "Upload successful! TinyURL: " + tinyUrl);
                } else {
                    Log.e(TAG, "Upload failed, Response code: " + response.code());
                }
                stopSelf();
            }
        });
    }
}
