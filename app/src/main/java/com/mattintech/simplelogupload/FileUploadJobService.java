package com.mattintech.simplelogupload;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

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

public class FileUploadJobService extends JobService {
    private static final String TAG = Constant.APP_TAG + "FileUploadJobService:";

    @Override
    public boolean onStartJob(JobParameters params) {
        File dir = new File(Environment.getExternalStorageDirectory(), "log");
        File mostRecentFile = findMostRecentFile(dir);

        if (mostRecentFile != null) {
            Toast.makeText(this, "Starting Upload", Toast.LENGTH_SHORT).show();
            uploadFile(mostRecentFile, params);
        } else {
            Toast.makeText(getApplicationContext(), "No DUMPSTATE file found.", Toast.LENGTH_SHORT).show();
        }

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    private File findMostRecentFile(File dir) {
        File[] files = dir.listFiles((d, name) -> name.matches("dumpState_.*\\.zip"));
        return (files != null && files.length > 0) ? Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null) : null;
    }
    private void uploadFile(File file, JobParameters params) {
        OkHttpClient client = new OkHttpClient();
        Log.d(TAG, "Uploading file: " + file.getName() + ", Size: " + file.length());
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/zip"));

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url("http://172.16.30.69:7777/upload") // Replace with your Flask server's URL
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Upload failed: " + e.getMessage());
                jobFinished(params, false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String tinyUrl = response.body().string(); // Assuming the server returns tinyURL in response
                    Log.d(TAG, "Upload successful! TinyURL: " + tinyUrl);

                    // Show tinyURL on the UI (using BroadcastReceiver or local storage to communicate with MainActivity)
                } else {
                    Log.e(TAG, "Upload failed, Response code: " + response.code());
                }
                jobFinished(params, false);
            }
        });
    }



}