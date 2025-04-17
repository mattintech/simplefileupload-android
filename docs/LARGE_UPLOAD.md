# Large File Upload Analysis and Improvements

## Current Implementation Analysis

### Client Side (Android)

The SimpleLogUpload Android application is designed to upload large log files (up to 220MB) to a simple file upload server. The current implementation uses:

- OkHttp3 for HTTP requests
- A single monolithic file upload in one HTTP request
- Fixed network timeouts:
  - Connection timeout: 60 seconds
  - Write timeout: 180 seconds (3 minutes)
  - Read timeout: 60 seconds
- Basic retry on connection failure

Behavior by network type:
- **WiFi**: Uploads work successfully
- **5G/Mobile**: Socket timeouts occur during upload (`java.net.SocketException: Socket closed`)

### Server Side (Python/Flask)

The server is a simple Flask application with:
- Standard file upload endpoint (`/upload`)
- No explicit chunked upload support
- Uses Gunicorn as WSGI server without specific timeout configurations
- No specific optimizations for large files

## Problem Root Causes

1. **Mobile Network Limitations**:
   - Mobile carriers may have connection restrictions for large uploads
   - 5G networks may have more aggressive connection management
   - Network fluctuations are more common in mobile networks

2. **Timeout Configuration**:
   - Current timeout settings may be insufficient for large files over mobile networks
   - Server and client timeouts aren't aligned

3. **Single Upload Approach**:
   - The entire 220MB file is uploaded in a single HTTP request
   - No resume capability if connection drops
   - No progress tracking or partial file recovery

4. **Missing Network Adaptability**:
   - No differentiation between WiFi and mobile network settings
   - No adaptive timeout or retry mechanisms
   - No connection monitoring during upload

## Recommended Solutions

### 1. Implement Chunked Uploading

Develop a proper chunked upload system with these features:

```java
// Example chunked upload strategy
public class ChunkedUploadManager {
    // Adjust these based on testing
    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 1000;

    // File metadata
    private File file;
    private int chunkSize;
    private int totalChunks;
    private String uploadId;

    // Network state
    private boolean isWifiConnection;
    private boolean isPaused;

    // Tracking
    private int currentChunkIndex;
    private Set<Integer> uploadedChunks = new HashSet<>();

    public ChunkedUploadManager(File file, boolean isWifiConnection) {
        this.file = file;
        this.isWifiConnection = isWifiConnection;
        
        // Adjust chunk size based on network type
        this.chunkSize = isWifiConnection ? DEFAULT_CHUNK_SIZE : (DEFAULT_CHUNK_SIZE / 2);
        
        this.totalChunks = (int) Math.ceil((double) file.length() / chunkSize);
    }

    // Methods for initialization, chunk upload, tracking, etc.
}
```

**Server Changes Implemented**:
```python
# New endpoint for chunk verification
@app.route('/upload/verify', methods=['POST'])
@require_client_key
def verify_chunks():
    # Get upload ID from request
    upload_id = request.form.get('upload_id')
    
    # Get session metadata
    session = metadata[upload_id]
    
    # Calculate missing chunks
    received_chunks = set(session['received_chunks'])
    all_chunks = set(range(session['total_chunks']))
    missing_chunks = list(all_chunks - received_chunks)
    
    # Verify files exist on disk
    for chunk_index in range(session['total_chunks']):
        chunk_path = os.path.join(session['chunk_dir'], f'chunk_{chunk_index}')
        if not os.path.exists(chunk_path) and chunk_index in received_chunks:
            # Remove from received_chunks if file is missing
            session['received_chunks'].remove(chunk_index)
    
    # Return missing chunks information
    return jsonify({
        'total_chunks': session['total_chunks'],
        'received_chunks': len(session['received_chunks']),
        'missing_chunks': missing_chunks
    })
```

### 2. Adaptive Network Settings

Implement network-aware settings that adjust based on connection type:

```java
private void configureNetworkSettings() {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    boolean isWifi = activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
    
    // Adjust settings based on network type
    if (isWifi) {
        // WiFi settings - can be more aggressive
        CONNECTION_TIMEOUT = 60;  // seconds
        WRITE_TIMEOUT = 180;      // seconds
        READ_TIMEOUT = 60;        // seconds
        CHUNK_SIZE = 10_485_760;  // 10MB
    } else {
        // Mobile network settings - more conservative
        CONNECTION_TIMEOUT = 30;  // seconds
        WRITE_TIMEOUT = 90;       // seconds
        READ_TIMEOUT = 30;        // seconds
        CHUNK_SIZE = 2_097_152;   // 2MB
    }
}
```

### 3. Network Monitoring and Handling

Implement network state monitoring to handle:
- Network type changes during upload
- Connection losses
- Bandwidth fluctuations

```java
private void registerNetworkCallback() {
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkRequest.Builder builder = new NetworkRequest.Builder();
    
    cm.registerNetworkCallback(builder.build(), new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            // Resume upload if paused
            resumeUploadIfPaused();
        }
        
        @Override
        public void onLost(Network network) {
            // Pause upload
            pauseUpload();
        }
        
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
            // Adjust strategies based on new network capabilities
            boolean isUnmetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            adjustUploadParameters(isUnmetered);
        }
    });
}
```

### 4. Progressive Backoff and Retry Strategy

Implement a more sophisticated retry mechanism:

```java
private void uploadChunkWithRetry(int chunkIndex) {
    int retryCount = 0;
    int maxRetries = 5;
    long backoffMs = 1000; // Start with 1 second
    
    while (retryCount < maxRetries) {
        try {
            // Attempt to upload chunk
            boolean success = uploadChunk(chunkIndex);
            if (success) {
                return; // Success, exit retry loop
            }
        } catch (IOException e) {
            Log.e(TAG, "Chunk upload failed: " + e.getMessage());
        }
        
        retryCount++;
        if (retryCount < maxRetries) {
            // Calculate backoff with exponential increase and jitter
            long jitter = (long) (Math.random() * 0.3 * backoffMs);
            long sleepTime = backoffMs + jitter;
            
            Log.d(TAG, "Retrying chunk " + chunkIndex + " after " + sleepTime + "ms (attempt " + retryCount + ")");
            
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            
            // Increase backoff for next iteration
            backoffMs = Math.min(backoffMs * 2, 30000); // Cap at 30 seconds
        }
    }
    
    // If we get here, all retries failed
    Log.e(TAG, "All retry attempts failed for chunk " + chunkIndex);
    reportChunkFailed(chunkIndex);
}
```

### 5. Enhanced Progress Tracking and Resumability

Implement more granular progress tracking for better user experience and resumability:

```java
public class UploadProgress {
    private long totalBytes;
    private long uploadedBytes;
    private int totalChunks;
    private Set<Integer> completedChunks;
    private boolean isComplete;
    private String sessionId;
    
    // Persist progress to disk for resumability
    public void saveProgress() {
        SharedPreferences prefs = context.getSharedPreferences("upload_progress", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Store progress data
        editor.putString("session_" + sessionId + "_chunks", TextUtils.join(",", completedChunks));
        editor.putLong("session_" + sessionId + "_bytes", uploadedBytes);
        editor.putBoolean("session_" + sessionId + "_complete", isComplete);
        
        editor.apply();
    }
    
    // Load progress for resuming uploads
    public static UploadProgress loadProgress(Context context, String sessionId) {
        // Implementation details
    }
}
```

### 6. Server-Side Optimizations

Modify the server to better handle large file uploads:

1. Implement explicit chunked upload support
2. Configure Gunicorn timeouts appropriately:
   ```
   gunicorn wsgi:app --bind 0.0.0.0:7777 --timeout 300 --workers 4
   ```
3. Add support for resumable uploads with progress tracking
4. Implement temporary file cleanup for abandoned uploads

## Implementation Priority

1. **First Phase**:
   - Implement basic chunked uploading
   - Add network type detection and adaptive settings
   - Configure server for higher timeouts

2. **Second Phase**:
   - Add upload resumability
   - Implement network state monitoring
   - Enhanced progress tracking
   - Progressive retry mechanism

3. **Final Phase**:
   - Server-side optimizations for chunked uploads
   - Background upload functionality
   - Bandwidth control options

## Testing Strategy

1. Test uploading the same 220MB file under various network conditions:
   - Stable WiFi
   - Congested WiFi
   - 5G with strong signal
   - 5G with weak signal
   - 4G fallback
   - Network transitions (WiFi → Mobile)

2. Measure and compare:
   - Success rate of uploads
   - Total upload time
   - Battery consumption
   - Data usage efficiency

## Conclusion

The socket timeout issues on 5G networks stem from trying to upload large files in a single HTTP request without adapting to the characteristics of mobile networks. By implementing chunked uploads with resume capability, network-aware configurations, and robust error handling, we can create a more reliable upload solution for all network types.

The most critical issue to address is the lack of chunking, which leaves the application vulnerable to connection interruptions, particularly on mobile networks where connections may be less stable than WiFi.
