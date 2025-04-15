package com.mattintech.simplelogupload.controller;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.constant.NetworkConstants;
import com.mattintech.simplelogupload.db.UploadHistoryRepository;
import com.mattintech.simplelogupload.model.UploadHistory;
import com.mattintech.simplelogupload.model.UploadResult;
import com.mattintech.simplelogupload.service.FileUploadForegroundService;
import com.mattintech.simplelogupload.util.PreferencesUtil;

import java.io.File;
import java.io.IOException;

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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controller for handling file uploads
 */
public class UploadController {
    private static final String TAG = AppConstants.APP_TAG + "UploadController";
    
    private final Context context;
    private final UploadHistoryRepository repository;
    
    /**
     * Constructor
     * 
     * @param context The application context
     */
    public UploadController(Context context) {
        this.context = context.getApplicationContext();
        this.repository = new UploadHistoryRepository((Application) this.context);
    }
    
    /**
     * Start an upload using the foreground service
     * 
     * @param file The file to upload
     */
    public void startUploadService(File file) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "Cannot start upload service with null or non-existent file");
            return;
        }
        
        Intent serviceIntent = new Intent(context, FileUploadForegroundService.class);
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_FILE_PATH, file.getAbsolutePath());
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_MAX_DOWNLOADS, NetworkConstants.DEFAULT_MAX_DOWNLOADS);
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_EXPIRY_HOURS, NetworkConstants.DEFAULT_EXPIRY_HOURS);
        
        context.startService(serviceIntent);
    }
    
    /**
     * Upload a file directly and return a future with the result
     * 
     * @param file The file to upload
     * @param maxDownloads Maximum number of downloads
     * @param expiryHours Expiry time in hours
     * @return CompletableFuture containing the upload result
     */
    public CompletableFuture<UploadResult> uploadFile(
            File file, int maxDownloads, int expiryHours) {
        CompletableFuture<UploadResult> future = new CompletableFuture<>();
        
        if (file == null || !file.exists()) {
            future.complete(new UploadResult("File does not exist"));
            return future;
        }
        
        // Create OkHttpClient with timeout configuration
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(NetworkConstants.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(NetworkConstants.READ_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
                
        Log.d(TAG, "Uploading file: " + file.getName() + ", Size: " + file.length());

        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(NetworkConstants.PARAM_FILE, file.getName(), fileBody)
                .addFormDataPart(NetworkConstants.PARAM_MAX_DOWNLOADS, String.valueOf(maxDownloads))
                .addFormDataPart(NetworkConstants.PARAM_EXPIRY_HOURS, String.valueOf(expiryHours))
                .build();

        // Get server URL and client key from preferences
        String serverUrl = PreferencesUtil.getServerUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);
        
        Log.d(TAG, "Using server URL: " + serverUrl);
        Log.d(TAG, "Adding header '" + NetworkConstants.CLIENT_KEY_HEADER + "' with value: " + clientKey);
        
        // Build request with client-key header
        Request request = new Request.Builder()
                .url(serverUrl)
                .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                .post(requestBody)
                .build();
        
        Log.d(TAG, "Sending upload request with " + NetworkConstants.CLIENT_KEY_HEADER + " header");

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Upload failed", e);
                future.complete(new UploadResult("Upload failed: " + e.getMessage()));
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
                        
                        // Create the result
                        UploadResult result = new UploadResult(
                                uploadCode, file.getName(), maxDownloads, expiryHours);
                        
                        // Save to history
                        saveToHistory(result);
                        
                        future.complete(result);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse server response", e);
                        future.complete(new UploadResult("Failed to parse server response"));
                    }
                } else {
                    String errorBody = response.body().string();
                    String errorMsg;
                    
                    // Check for authentication errors
                    if (response.code() == 401 || response.code() == 403) {
                        errorMsg = "Authentication failed. Please check client key.";
                        Log.e(TAG, "Authentication error: " + response.code() + ", Body: " + errorBody);
                        Log.e(TAG, "Header sent: " + NetworkConstants.CLIENT_KEY_HEADER + ": " + clientKey);
                    } else {
                        errorMsg = "Upload failed, Response code: " + response.code();
                        Log.e(TAG, errorMsg + ", Body: " + errorBody);
                    }
                    
                    try {
                        JSONObject errorJson = new JSONObject(errorBody);
                        if (errorJson.has("error")) {
                            errorMsg = errorJson.getString("error");
                        }
                    } catch (JSONException e) {
                        // If parsing fails, use the default error message
                    }
                    
                    future.complete(new UploadResult(errorMsg));
                }
            }
        });
        
        return future;
    }
    
    /**
     * Save an upload result to the history database
     * 
     * @param result The upload result
     */
    private void saveToHistory(UploadResult result) {
        if (result == null || !result.isSuccess()) {
            return;
        }
        
        UploadHistory historyEntry = new UploadHistory(
                result.getFileName(),
                System.currentTimeMillis(),
                result.getCode(),
                result.getMaxDownloads(),
                result.getExpiryHours()
        );
        
        repository.insert(historyEntry)
                .thenAccept(id -> Log.d(TAG, "Saved to history with ID: " + id))
                .exceptionally(ex -> {
                    Log.e(TAG, "Error saving to history", ex);
                    return null;
                });
    }
    
    /**
     * Delete a file from the server using its upload code
     * 
     * @param uploadCode The upload code of the file to delete
     * @return CompletableFuture containing a boolean indicating success or failure
     */
    public CompletableFuture<Boolean> deleteFileFromServer(String uploadCode) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (uploadCode == null || uploadCode.isEmpty()) {
            Log.e(TAG, "Cannot delete file with empty upload code");
            future.complete(false);
            return future;
        }
        
        // Create OkHttpClient with timeout configuration
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(NetworkConstants.CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(NetworkConstants.READ_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        
        // Get server base URL and client key from preferences
        String serverBaseUrl = PreferencesUtil.getServerBaseUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);
        
        // Create the delete URL
        String deleteUrl = serverBaseUrl + "/delete/" + uploadCode;
        Log.d(TAG, "Deleting file from server: " + deleteUrl);
        
        // Build request with client-key header
        Request request = new Request.Builder()
                .url(deleteUrl)
                .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                .delete()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Server delete failed", e);
                future.complete(false);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Server delete successful for code: " + uploadCode);
                    future.complete(true);
                } else {
                    String errorBody = response.body().string();
                    Log.e(TAG, "Server delete failed with code: " + response.code() + ", Body: " + errorBody);
                    future.complete(false);
                }
            }
        });
        
        return future;
    }
}
