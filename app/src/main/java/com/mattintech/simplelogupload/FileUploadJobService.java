package com.mattintech.simplelogupload;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

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
            sendErrorBroadcast("No DUMPSTATE file found");
            jobFinished(params, false);
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
        // Validate file size before upload
        if (file.length() > Constant.MAX_FILE_SIZE) {
            Log.e(TAG, "File too large: " + file.length() + " bytes (max: " + Constant.MAX_FILE_SIZE + " bytes)");
            sendErrorBroadcast("File size exceeds the maximum allowed size of 2GB");
            jobFinished(params, false);
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
                jobFinished(params, false);
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
                jobFinished(params, false);
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