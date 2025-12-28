package com.mattintech.simplelogupload.upload;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.util.Log;

import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.constant.NetworkConstants;
import com.mattintech.simplelogupload.model.UploadResult;
import com.mattintech.simplelogupload.util.PreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Manages chunked uploads for large files.
 * This class handles splitting a large file into chunks, uploading them,
 * tracking progress, and reassembling on the server.
 */
public class ChunkedUploadManager {
    private static final String TAG = AppConstants.APP_TAG + "ChunkedUploadManager";

    // Chunk size configuration
    private static final int WIFI_CHUNK_SIZE = 10 * 1024 * 1024; // 10MB for WiFi
    private static final int MOBILE_CHUNK_SIZE = 4 * 1024 * 1024; // 4MB for mobile networks (increased from 2MB)
    private static final int DEFAULT_CHUNK_SIZE = WIFI_CHUNK_SIZE;

    // Max parallel uploads
    private static final int WIFI_MAX_PARALLEL = 3;
    private static final int MOBILE_MAX_PARALLEL = 2; // 2 parallel chunks on mobile (testing with nginx config updates)

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int BASE_RETRY_DELAY_MS = 1000;

    // Context and network
    private final Context context;
    private boolean isWifiConnection;
    private NetworkCallback networkCallback;
    private final ConnectivityManager connectivityManager;

    // Upload parameters
    private final File file;
    private final int maxDownloads;
    private final int expiryHours;
    private final int chunkSize;
    private final int totalChunks;
    private final int maxParallelUploads;

    // Upload state
    private String uploadId;
    private final Set<Integer> completedChunks = new HashSet<>();
    private final Set<Integer> inProgressChunks = new HashSet<>();
    private final AtomicBoolean isPaused = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private final AtomicInteger activeUploads = new AtomicInteger(0);
    private final AtomicLong uploadedBytes = new AtomicLong(0);

    // Progress tracking
    private UploadProgressListener progressListener;
    private final long totalBytes;

    // OkHttpClient for uploads
    private OkHttpClient client;

    /**
     * Interface for tracking upload progress
     */
    public interface UploadProgressListener {
        void onProgress(long bytesUploaded, long totalBytes, int percentComplete);
        void onChunkComplete(int chunkIndex, int totalChunks);
        void onUploadComplete(UploadResult result);
        void onError(String error);
        void onPaused();
        void onResumed();
    }

    /**
     * Create a new chunked upload manager
     *
     * @param context The application context
     * @param file The file to upload
     * @param maxDownloads Maximum number of downloads
     * @param expiryHours Expiry time in hours
     */
    public ChunkedUploadManager(Context context, File file, int maxDownloads, int expiryHours) {
        this.context = context.getApplicationContext();
        this.file = file;
        this.maxDownloads = maxDownloads;
        this.expiryHours = expiryHours;
        this.totalBytes = file.length();

        // Set up connectivity manager for network monitoring
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Determine initial connection type
        detectConnectionType();

        // Configure chunk size based on connection type
        this.chunkSize = isWifiConnection ? WIFI_CHUNK_SIZE : MOBILE_CHUNK_SIZE;

        // Configure max parallel uploads based on connection type and user preference
        boolean parallelEnabled = PreferencesUtil.isParallelUploadsEnabled(context);
        if (isWifiConnection) {
            this.maxParallelUploads = WIFI_MAX_PARALLEL;
        } else {
            // On mobile, use parallel uploads only if enabled in settings
            this.maxParallelUploads = parallelEnabled ? MOBILE_MAX_PARALLEL : 1;
        }
        
        // Calculate total chunks
        this.totalChunks = (int) Math.ceil((double) file.length() / chunkSize);
        
        // Setup HTTP client with appropriate timeouts based on connection type
        setupHttpClient();
        
        // Generate upload ID
        this.uploadId = UUID.randomUUID().toString();
        
        Log.d(TAG, "Created chunked upload manager for file: " + file.getName());
        Log.d(TAG, "Total size: " + totalBytes + " bytes, Chunks: " + totalChunks);
        Log.d(TAG, "Connection type: " + (isWifiConnection ? "WiFi" : "Mobile") +
                ", Chunk size: " + chunkSize + " bytes" +
                ", Parallel uploads: " + maxParallelUploads + (parallelEnabled ? " (enabled)" : " (disabled)"));
    }

    /**
     * Set a listener for upload progress events
     *
     * @param listener The progress listener
     */
    public void setProgressListener(UploadProgressListener listener) {
        this.progressListener = listener;
    }

    /**
     * Start the upload process
     *
     * @return A CompletableFuture that completes when the upload is done
     */
    public CompletableFuture<UploadResult> startUpload() {
        CompletableFuture<UploadResult> resultFuture = new CompletableFuture<>();
        
        // Reset state
        completedChunks.clear();
        inProgressChunks.clear();
        uploadedBytes.set(0);
        isPaused.set(false);
        isCancelled.set(false);
        
        // Start network monitoring
        registerNetworkCallback();
        
        // Begin upload process by initializing a session with the server
        initializeUploadSession()
            .thenAccept(sessionId -> {
                if (sessionId != null && !sessionId.isEmpty()) {
                    this.uploadId = sessionId;
                    Log.d(TAG, "Upload session initialized with ID: " + uploadId);
                    
                    // Start uploading chunks
                    uploadNextChunks()
                        .thenAccept(isComplete -> {
                            if (isComplete) {
                                // All chunks uploaded, finalize the upload
                                finalizeUpload()
                                    .thenAccept(resultFuture::complete)
                                    .exceptionally(ex -> {
                                        Log.e(TAG, "Error finalizing upload", ex);
                                        resultFuture.complete(new UploadResult("Failed to finalize upload: " + ex.getMessage()));
                                        return null;
                                    });
                            } else {
                                resultFuture.complete(new UploadResult("Upload incomplete or cancelled"));
                            }
                        })
                        .exceptionally(ex -> {
                            Log.e(TAG, "Error during chunk uploading", ex);
                            resultFuture.complete(new UploadResult("Chunk upload error: " + ex.getMessage()));
                            return null;
                        });
                } else {
                    Log.e(TAG, "Failed to initialize upload session");
                    resultFuture.complete(new UploadResult("Failed to initialize upload session"));
                }
            })
            .exceptionally(ex -> {
                Log.e(TAG, "Error initializing upload session", ex);
                resultFuture.complete(new UploadResult("Session initialization error: " + ex.getMessage()));
                return null;
            });
        
        return resultFuture;
    }

    /**
     * Initialize an upload session with the server
     *
     * @return A future that completes with the session ID
     */
    private CompletableFuture<String> initializeUploadSession() {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // Get server URL and client key from preferences
        String serverBaseUrl = PreferencesUtil.getServerBaseUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);
        
        // Create the session initialization URL
        String initUrl = serverBaseUrl + "/upload/start";
        
        // Build the request body with file metadata
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("filename", file.getName())
                .addFormDataPart("filesize", String.valueOf(totalBytes))
                .addFormDataPart("chunk_size", String.valueOf(chunkSize))
                .addFormDataPart("total_chunks", String.valueOf(totalChunks))
                .addFormDataPart("max_downloads", String.valueOf(maxDownloads))
                .addFormDataPart("expiry_hours", String.valueOf(expiryHours))
                .build();
        
        // Build the request
        Request request = new Request.Builder()
                .url(initUrl)
                .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                .post(requestBody)
                .build();
        
        Log.d(TAG, "Initializing upload session...");
        
        // Execute the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to initialize upload session", e);
                future.complete(null); // Complete with null to indicate failure
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String sessionId = jsonResponse.getString("upload_id");
                        future.complete(sessionId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse session initialization response", e);
                        future.complete(null);
                    }
                } else {
                    Log.e(TAG, "Session initialization failed with code: " + response.code());
                    future.complete(null);
                }
            }
        });
        
        return future;
    }

    /**
     * Upload chunks to the server
     *
     * @return A future that completes when all chunks are uploaded
     */
    // This runnable is defined as a class member to avoid initialization issues
    private Runnable uploadProgressChecker;
    private CompletableFuture<Boolean> uploadProgressFuture;
    
    /**
     * Upload chunks to the server
     *
     * @return A future that completes when all chunks are uploaded
     */
    private CompletableFuture<Boolean> uploadNextChunks() {
        uploadProgressFuture = new CompletableFuture<>();
        
        // Define the progress checker runnable
        uploadProgressChecker = new Runnable() {
            @Override
            public void run() {
                if (isCancelled.get()) {
                    uploadProgressFuture.complete(false);
                    return;
                }
                
                if (isPaused.get()) {
                    // Upload is paused, do nothing
                    return;
                }
                
                // If all chunks are complete, verify each index was uploaded
                if (completedChunks.size() == totalChunks) {
                    // Additional verification: check if all required chunk indexes exist
                    boolean allChunksUploaded = true;
                    Set<Integer> missingChunks = new HashSet<>();
                    
                    for (int i = 0; i < totalChunks; i++) {
                        if (!completedChunks.contains(i)) {
                            missingChunks.add(i);
                            allChunksUploaded = false;
                        }
                    }
                    
                    if (allChunksUploaded) {
                        Log.d(TAG, "All chunks uploaded successfully");
                        uploadProgressFuture.complete(true);
                        return;
                    } else {
                        Log.e(TAG, "Missing chunks despite having " + completedChunks.size() + 
                              " chunks completed. Missing indexes: " + missingChunks);
                        // Continue with upload process to retry missing chunks
                    }
                }
                
                // Find next chunks to upload
                for (int i = 0; i < totalChunks && activeUploads.get() < maxParallelUploads; i++) {
                    final int chunkIndex = i; // Create final variable for use in lambda
                    if (!completedChunks.contains(chunkIndex) && !inProgressChunks.contains(chunkIndex)) {
                        // Start upload for this chunk
                        inProgressChunks.add(chunkIndex);
                        activeUploads.incrementAndGet();
                        
                        uploadChunk(chunkIndex)
                            .thenAccept(success -> {
                                // Remove from in-progress
                                inProgressChunks.remove(chunkIndex);
                                activeUploads.decrementAndGet();
                                
                                if (success) {
                                    // Add to completed
                                    completedChunks.add(chunkIndex);
                                    
                                    // Notify listener
                                    if (progressListener != null) {
                                        progressListener.onChunkComplete(chunkIndex, totalChunks);
                                    }
                                    
                                    // Check progress again
                                    uploadProgressChecker.run();
                                } else if (!isCancelled.get()) {
                                    // Retry failed chunks if not cancelled
                                    if (!isPaused.get()) {
                                        Log.d(TAG, "Scheduling retry for chunk " + chunkIndex);
                                        // Reschedule after a delay
                                        // Using a simple retry here, but could implement exponential backoff
                                        java.util.concurrent.ScheduledExecutorService scheduler = 
                                                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                                        scheduler.schedule(uploadProgressChecker, 1000, TimeUnit.MILLISECONDS);
                                        scheduler.shutdown();
                                    }
                                }
                            })
                            .exceptionally(ex -> {
                                Log.e(TAG, "Error uploading chunk " + chunkIndex, ex);
                                inProgressChunks.remove(chunkIndex);
                                activeUploads.decrementAndGet();
                                return null;
                            });
                    }
                }
                
                // If no active uploads and no more chunks to upload, but not all chunks are complete,
                // something went wrong
                if (activeUploads.get() == 0 && inProgressChunks.isEmpty() && 
                        completedChunks.size() < totalChunks && !isPaused.get()) {
                    Log.e(TAG, "Upload stalled with " + completedChunks.size() + "/" + totalChunks + " chunks completed");
                    uploadProgressFuture.complete(false);
                }
            }
        };
        
        // Start uploading chunks
        uploadProgressChecker.run();
        
        return uploadProgressFuture;
    }

    /**
     * Upload a single chunk to the server
     *
     * @param chunkIndex The index of the chunk to upload
     * @return A future that completes when the chunk is uploaded
     */
    private CompletableFuture<Boolean> uploadChunk(int chunkIndex) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Get server URL and client key from preferences
        String serverBaseUrl = PreferencesUtil.getServerBaseUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);
        
        // Create the chunk upload URL
        String chunkUrl = serverBaseUrl + "/upload/chunk";
        
        try {
            // Calculate chunk start and end positions
            long start = (long) chunkIndex * chunkSize;
            long end = Math.min(start + chunkSize, file.length());
            int currentChunkSize = (int) (end - start);
            
            Log.d(TAG, "Uploading chunk " + chunkIndex + " (" + currentChunkSize + " bytes)");
            
            // Read the chunk data
            byte[] buffer = new byte[currentChunkSize];
            try (FileInputStream inputStream = new FileInputStream(file)) {
                inputStream.skip(start);
                int bytesRead = inputStream.read(buffer);
                if (bytesRead != currentChunkSize) {
                    Log.e(TAG, "Failed to read chunk data: expected " + currentChunkSize + 
                            " bytes, got " + bytesRead);
                    future.complete(false);
                    return future;
                }
            }
            
            // Create the request body
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_id", uploadId)
                    .addFormDataPart("chunk_index", String.valueOf(chunkIndex))
                    .addFormDataPart("total_chunks", String.valueOf(totalChunks))
                    .addFormDataPart("chunk", file.getName() + ".part" + chunkIndex,
                            RequestBody.create(MediaType.parse("application/octet-stream"), buffer))
                    .build();
            
            // Build the request
            Request request = new Request.Builder()
                    .url(chunkUrl)
                    .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                    .post(requestBody)
                    .build();
            
            // Execute the request
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to upload chunk " + chunkIndex, e);
                    future.complete(false);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            // Parse the response to make sure the server confirmed success
                            String responseBody = response.body().string();
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            
                            boolean success = jsonResponse.optBoolean("success", false);
                            int confirmedChunkIndex = jsonResponse.optInt("chunk_index", -1);
                            
                            if (success && (confirmedChunkIndex == chunkIndex || confirmedChunkIndex == -1)) {
                                // Update progress
                                long newTotal = uploadedBytes.addAndGet(currentChunkSize);
                                int percentComplete = (int) (newTotal * 100 / totalBytes);
                                
                                // Notify listener
                                if (progressListener != null) {
                                    progressListener.onProgress(newTotal, totalBytes, percentComplete);
                                }
                                
                                Log.d(TAG, "Chunk " + chunkIndex + " uploaded successfully. Progress: " + 
                                        percentComplete + "%");
                                future.complete(true);
                            } else {
                                Log.e(TAG, "Server reported issue with chunk " + chunkIndex + ": " + responseBody);
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing chunk upload response for chunk " + chunkIndex, e);
                            future.complete(false);
                        }
                    } else {
                        try {
                            String errorBody = response.body().string();
                            Log.e(TAG, "Chunk " + chunkIndex + " upload failed with code: " + response.code() + 
                                    ", Body: " + errorBody);
                        } catch (Exception e) {
                            Log.e(TAG, "Chunk " + chunkIndex + " upload failed with code: " + response.code());
                        }
                        future.complete(false);
                    }
                }
            });
            
        } catch (IOException e) {
            Log.e(TAG, "Error preparing chunk " + chunkIndex, e);
            future.complete(false);
        }
        
        return future;
    }

    /**
     * Verify which chunks the server has actually received
     * 
     * @return A CompletableFuture with a Set of missing chunk indexes
     */
    private CompletableFuture<Set<Integer>> verifyChunksWithServer() {
        CompletableFuture<Set<Integer>> future = new CompletableFuture<>();
        Set<Integer> missingChunks = new HashSet<>();
        
        // Get server URL and client key from preferences
        String serverBaseUrl = PreferencesUtil.getServerBaseUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);
        
        // Create the verification URL
        String verifyUrl = serverBaseUrl + "/upload/verify";
        
        // Build the request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_id", uploadId)
                .build();
        
        // Build the request
        Request request = new Request.Builder()
                .url(verifyUrl)
                .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                .post(requestBody)
                .build();
        
        Log.d(TAG, "Verifying uploaded chunks with server...");
        
        // Execute the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to verify chunks with server", e);
                // If we can't verify, assume all are ok since we've tracked them locally
                future.complete(new HashSet<>());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        // Check for missing chunks
                        if (jsonResponse.has("missing_chunks")) {
                            JSONArray missingArray = jsonResponse.getJSONArray("missing_chunks");
                            for (int i = 0; i < missingArray.length(); i++) {
                                missingChunks.add(missingArray.getInt(i));
                            }
                            Log.d(TAG, "Server missing chunks: " + missingChunks);
                        }
                        
                        future.complete(missingChunks);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing chunk verification response", e);
                        future.complete(new HashSet<>());
                    }
                } else {
                    try {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Chunk verification failed with code: " + response.code() + ", Body: " + errorBody);
                    } catch (Exception e) {
                        Log.e(TAG, "Chunk verification failed with code: " + response.code());
                    }
                    
                    // If verification fails, try the finalization anyway with our client-side tracking
                    future.complete(new HashSet<>());
                }
            }
        });
        
        return future;
    }
    
    /**
     * Retry uploading specific chunks that the server is missing
     * 
     * @param chunkIndexes Set of chunk indexes to retry
     * @return CompletableFuture<Boolean> indicating if all retries were successful
     */
    private CompletableFuture<Boolean> retrySpecificChunks(Set<Integer> chunkIndexes) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        if (chunkIndexes.isEmpty()) {
            future.complete(true);
            return future;
        }
        
        Log.d(TAG, "Retrying specific chunks: " + chunkIndexes);
        
        // Create a counter for completed retries
        AtomicInteger completedRetries = new AtomicInteger(0);
        AtomicInteger successfulRetries = new AtomicInteger(0);
        int totalRetries = chunkIndexes.size();
        
        // Try each chunk
        for (Integer chunkIndex : chunkIndexes) {
            Log.d(TAG, "Retrying upload of chunk " + chunkIndex);
            
            uploadChunk(chunkIndex)
                .thenAccept(success -> {
                    int completed = completedRetries.incrementAndGet();
                    
                    if (success) {
                        successfulRetries.incrementAndGet();
                        Log.d(TAG, "Successfully reuploaded chunk " + chunkIndex);
                    } else {
                        Log.e(TAG, "Failed to reupload chunk " + chunkIndex);
                    }
                    
                    // If all retries have completed, complete the future
                    if (completed == totalRetries) {
                        boolean allSuccessful = successfulRetries.get() == totalRetries;
                        Log.d(TAG, "All chunk retries completed. " + successfulRetries.get() + "/" + 
                                totalRetries + " successful");
                        future.complete(allSuccessful);
                    }
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "Exception retrying chunk " + chunkIndex, ex);
                    int completed = completedRetries.incrementAndGet();
                    
                    // If all retries have completed, complete the future
                    if (completed == totalRetries) {
                        boolean allSuccessful = successfulRetries.get() == totalRetries;
                        Log.d(TAG, "All chunk retries completed. " + successfulRetries.get() + "/" + 
                                totalRetries + " successful");
                        future.complete(allSuccessful);
                    }
                    return null;
                });
        }
        
        return future;
    }

    /**
     * Finalize the upload on the server
     *
     * @return A future that completes with the upload result
     */
    private CompletableFuture<UploadResult> finalizeUpload() {
        CompletableFuture<UploadResult> future = new CompletableFuture<>();
        
        // Verify all chunks are uploaded before finalizing
        boolean allChunksUploaded = true;
        Set<Integer> missingChunks = new HashSet<>();

        for (int i = 0; i < totalChunks; i++) {
            if (!completedChunks.contains(i)) {
                missingChunks.add(i);
                allChunksUploaded = false;
            }
        }

        if (!allChunksUploaded) {
            Log.e(TAG, "Cannot finalize: missing chunks " + missingChunks);
            future.complete(new UploadResult("Cannot finalize: missing chunks " + missingChunks));
            return future;
        }

        // Get server URL and client key from preferences
        String serverBaseUrl = PreferencesUtil.getServerBaseUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);

        // Before finalizing, verify with the server which chunks it has received
        verifyChunksWithServer()
            .thenAccept(serverMissingChunks -> {
                if (!serverMissingChunks.isEmpty()) {
                    Log.e(TAG, "Server missing chunks: " + serverMissingChunks + ", reuploading missing chunks");

                    // Reset these chunks as not uploaded so they'll be retried
                    // Also subtract their bytes from uploadedBytes to prevent progress > 100%
                    for (Integer chunkIndex : serverMissingChunks) {
                        completedChunks.remove(chunkIndex);

                        // Calculate chunk size and subtract from uploaded bytes
                        long start = (long) chunkIndex * chunkSize;
                        long end = Math.min(start + chunkSize, file.length());
                        long chunkBytes = end - start;
                        uploadedBytes.addAndGet(-chunkBytes);
                    }
                    
                    // Retry uploading the missing chunks
                    retrySpecificChunks(serverMissingChunks)
                        .thenAccept(retrySuccess -> {
                            if (retrySuccess) {
                                // Now try finalizing again
                                Log.d(TAG, "Missing chunks reuploaded, finalizing again");
                                performFinalizeRequest(future);
                            } else {
                                Log.e(TAG, "Failed to reupload missing chunks");
                                future.complete(new UploadResult("Failed to reupload missing chunks"));
                            }
                        });
                } else {
                    // All chunks are verified by the server, proceed with finalization
                    performFinalizeRequest(future);
                }
            })
            .exceptionally(ex -> {
                Log.e(TAG, "Error verifying chunks with server", ex);
                // Try to finalize anyway
                performFinalizeRequest(future);
                return null;
            });
        
        return future;
    }
    
    /**
     * Perform the finalize request to the server
     * 
     * @param future The CompletableFuture to complete with the upload result
     */
    private void performFinalizeRequest(CompletableFuture<UploadResult> future) {
        // Get server URL and client key from preferences
        String serverBaseUrl = PreferencesUtil.getServerBaseUrl(context);
        String clientKey = PreferencesUtil.getClientKey(context);
        
        // Create the finalize URL
        String finalizeUrl = serverBaseUrl + "/upload/complete";
        
        // Build the request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_id", uploadId)
                .addFormDataPart("filename", file.getName())
                .addFormDataPart("total_chunks", String.valueOf(totalChunks))
                .addFormDataPart("max_downloads", String.valueOf(maxDownloads))
                .addFormDataPart("expiry_hours", String.valueOf(expiryHours))
                .build();
        
        // Build the request
        Request request = new Request.Builder()
                .url(finalizeUrl)
                .header(NetworkConstants.CLIENT_KEY_HEADER, clientKey)
                .post(requestBody)
                .build();
        
        Log.d(TAG, "Finalizing upload...");
        
        // Execute the request
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to finalize upload", e);
                future.complete(new UploadResult("Failed to finalize upload: " + e.getMessage()));
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
                        
                        Log.d(TAG, "Upload finalized successfully! Code: " + uploadCode);
                        
                        // Create the result
                        UploadResult result = new UploadResult(
                                uploadCode, file.getName(), maxDownloads, expiryHours);
                        
                        // Notify listener
                        if (progressListener != null) {
                            progressListener.onUploadComplete(result);
                        }
                        
                        future.complete(result);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse server response", e);
                        future.complete(new UploadResult("Failed to parse server response"));
                    }
                } else {
                    try {
                        String errorBody = response.body().string();
                        Log.e(TAG, "Finalize failed with code: " + response.code() + ", Body: " + errorBody);
                        
                        // Try to parse error body to see if there are missing chunks we can retry
                        try {
                            JSONObject errorJson = new JSONObject(errorBody);
                            if (errorJson.has("missing_chunks")) {
                                JSONArray missingArray = errorJson.getJSONArray("missing_chunks");
                                Set<Integer> missingChunks = new HashSet<>();
                                
                                for (int i = 0; i < missingArray.length(); i++) {
                                    missingChunks.add(missingArray.getInt(i));
                                }
                                
                                // Automatically retry these specific chunks
                                if (!missingChunks.isEmpty()) {
                                    Log.d(TAG, "Server reported missing chunks: " + missingChunks +
                                          ". Will retry these chunks specifically.");

                                    // Remove these chunks from completedChunks
                                    // Also subtract their bytes from uploadedBytes to prevent progress > 100%
                                    for (Integer chunkIndex : missingChunks) {
                                        completedChunks.remove(chunkIndex);

                                        // Calculate chunk size and subtract from uploaded bytes
                                        long start = (long) chunkIndex * chunkSize;
                                        long end = Math.min(start + chunkSize, file.length());
                                        long chunkBytes = end - start;
                                        uploadedBytes.addAndGet(-chunkBytes);
                                    }
                                    
                                    retrySpecificChunks(missingChunks)
                                        .thenAccept(retrySuccess -> {
                                            if (retrySuccess) {
                                                // Try finalizing again
                                                Log.d(TAG, "Successfully reuploaded missing chunks, retrying finalize");
                                                performFinalizeRequest(future);
                                            } else {
                                                Log.e(TAG, "Failed to reupload missing chunks, giving up");
                                                future.complete(new UploadResult("Failed to reupload missing chunks"));
                                            }
                                        });
                                    
                                    // Return early as we'll complete the future after retrying
                                    return;
                                }
                            }
                        } catch (JSONException e) {
                            // Not a JSON response or doesn't have missing_chunks key
                        }
                        
                        future.complete(new UploadResult("Failed to finalize upload: " + response.code()));
                    } catch (Exception e) {
                        Log.e(TAG, "Error reading finalize error response", e);
                        future.complete(new UploadResult("Failed to finalize upload: " + response.code()));
                    }
                }
            }
        });
    }

    /**
     * Pause the upload
     */
    public void pauseUpload() {
        if (!isPaused.getAndSet(true)) {
            Log.d(TAG, "Upload paused");
            
            // Notify listener
            if (progressListener != null) {
                progressListener.onPaused();
            }
        }
    }

    /**
     * Resume the upload
     */
    public void resumeUpload() {
        if (isPaused.getAndSet(false)) {
            Log.d(TAG, "Upload resumed");
            
            // Notify listener
            if (progressListener != null) {
                progressListener.onResumed();
            }
            
            // Start uploading again
            uploadNextChunks()
                .thenAccept(isComplete -> {
                    if (isComplete) {
                        // All chunks uploaded, finalize the upload
                        finalizeUpload()
                            .thenAccept(result -> {
                                // Notify listener
                                if (progressListener != null) {
                                    progressListener.onUploadComplete(result);
                                }
                            })
                            .exceptionally(ex -> {
                                Log.e(TAG, "Error finalizing upload", ex);
                                if (progressListener != null) {
                                    progressListener.onError("Failed to finalize upload: " + ex.getMessage());
                                }
                                return null;
                            });
                    } else if (progressListener != null) {
                        progressListener.onError("Upload incomplete or cancelled");
                    }
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "Error during chunk uploading", ex);
                    if (progressListener != null) {
                        progressListener.onError("Chunk upload error: " + ex.getMessage());
                    }
                    return null;
                });
        }
    }

    /**
     * Cancel the upload
     */
    public void cancelUpload() {
        isCancelled.set(true);
        Log.d(TAG, "Upload cancelled");
        
        // Unregister network callback
        unregisterNetworkCallback();
    }

    /**
     * Set up HTTP client with timeouts based on connection type
     */
    private void setupHttpClient() {
        int connectionTimeout = isWifiConnection ? 
                NetworkConstants.CONNECTION_TIMEOUT : NetworkConstants.CONNECTION_TIMEOUT / 2;
        int writeTimeout = isWifiConnection ? 
                NetworkConstants.WRITE_TIMEOUT : NetworkConstants.WRITE_TIMEOUT / 2;
        int readTimeout = isWifiConnection ? 
                NetworkConstants.READ_TIMEOUT : NetworkConstants.READ_TIMEOUT / 2;
        
        client = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                .writeTimeout(writeTimeout, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Detect current connection type (WiFi or Mobile)
     */
    private void detectConnectionType() {
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        isWifiConnection = activeNetwork != null && 
                activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        
        Log.d(TAG, "Connection type detected: " + (isWifiConnection ? "WiFi" : "Mobile"));
    }

    /**
     * Register network callback to monitor connection changes
     */
    private void registerNetworkCallback() {
        // Create network callback if not already created
        if (networkCallback == null) {
            networkCallback = new NetworkCallback();
        }
        
        // Register the callback
        NetworkRequest.Builder builder = new NetworkRequest.Builder();
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
        
        Log.d(TAG, "Network callback registered");
    }

    /**
     * Unregister network callback
     */
    private void unregisterNetworkCallback() {
        if (networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                Log.d(TAG, "Network callback unregistered");
            } catch (IllegalArgumentException e) {
                // Callback was not registered or already unregistered
                Log.w(TAG, "Failed to unregister network callback", e);
            }
        }
    }

    /**
     * Network callback for handling connection changes
     */
    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            
            // Check if connection type changed
            boolean wasWifi = isWifiConnection;
            detectConnectionType();
            
            Log.d(TAG, "Network available: " + (isWifiConnection ? "WiFi" : "Mobile"));
            
            // If we were paused due to network loss, resume
            if (isPaused.get()) {
                Log.d(TAG, "Network restored, resuming upload");
                resumeUpload();
            }
            
            // If connection type changed, adjust settings
            if (wasWifi != isWifiConnection) {
                Log.d(TAG, "Connection type changed, adjusting settings");
                setupHttpClient();
            }
        }
        
        @Override
        public void onLost(Network network) {
            super.onLost(network);
            
            Log.d(TAG, "Network lost");
            
            // Pause upload to prevent failures
            pauseUpload();
        }
        
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            super.onCapabilitiesChanged(network, capabilities);
            
            boolean newIsWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            
            // If connection type changed, adjust settings
            if (newIsWifi != isWifiConnection) {
                isWifiConnection = newIsWifi;
                Log.d(TAG, "Connection capabilities changed to: " + (isWifiConnection ? "WiFi" : "Mobile"));
                setupHttpClient();
            }
        }
    }
}
