# Chunked Upload Implementation

This document describes the chunked upload implementation added to the SimpleLogUpload Android application to solve issues with large file uploads on mobile networks.

## Overview

The chunked upload system splits large files into smaller pieces (chunks), uploads them individually, and then reassembles them on the server. This approach provides several benefits:

1. Better reliability on unstable networks (especially 5G/mobile)
2. Ability to resume interrupted uploads
3. More accurate progress reporting
4. Adaptability to different network conditions

## Components

### 1. ChunkedUploadManager

The core component that handles:
- Splitting files into chunks
- Managing parallel uploads
- Tracking upload progress
- Handling network changes
- Implementing retry logic
- Verifying chunk status with server
- Automatically retrying missing chunks

```java
ChunkedUploadManager uploadManager = new ChunkedUploadManager(
        context, file, maxDownloads, expiryHours);
```

### 2. UploadStrategySelector

Determines whether to use chunked uploads based on:
- File size
- Network type (WiFi vs. Mobile)
- Configurable thresholds

```java
boolean useChunked = UploadStrategySelector.shouldUseChunkedUpload(context, file);
```

### 3. Modified UploadController

The existing UploadController now:
- Selects between standard and chunked uploads
- Handles both upload methods with a unified API
- Provides progress updates

## API Requirements

The server implements four endpoints:

1. `/upload/start` - Initializes a chunked upload session
   - Accepts: filename, filesize, chunk_size, total_chunks, max_downloads, expiry_hours
   - Returns: upload_id

2. `/upload/chunk` - Handles individual chunk uploads
   - Accepts: upload_id, chunk_index, total_chunks, chunk (binary)
   - Returns: success/failure

3. `/upload/verify` - Verifies which chunks have been received
   - Accepts: upload_id
   - Returns: received_chunks, missing_chunks, total_chunks

4. `/upload/complete` - Finalizes an upload by reassembling chunks
   - Accepts: upload_id, filename, total_chunks
   - Returns: upload code (same as standard upload)

## Configuration

The chunked upload system is configured with the following parameters:

- WiFi chunk size: 10MB
- Mobile chunk size: 2MB
- WiFi threshold: 50MB (files larger than this use chunked upload)
- Mobile threshold: 10MB
- WiFi parallel uploads: 3
- Mobile parallel uploads: 1

These values can be adjusted in the `ChunkedUploadManager` and `UploadStrategySelector` classes.

## Enhanced Reliability Features

### Chunk Verification

The system implements robust verification to ensure all chunks are properly uploaded:

1. Client-side tracking of all uploaded chunks by index
2. Server endpoint to verify which chunks have been received
3. Automatic reupload of any missing chunks
4. Verification that files actually exist on disk before finalizing

### Network Handling

The implementation includes robust network handling:

- Detects network type (WiFi/Mobile/5G/LTE)
- Adjusts chunk size and parallel uploads accordingly
- Monitors network state changes
- Pauses uploads on network loss
- Resumes uploads when network is restored
- Adapts to network type changes during upload

### Progressive Retry

Implements intelligent retry logic:

- Specific retry of only failed chunks
- Server-client synchronization of chunk status
- Automatic recovery from partial upload failures
- Verification before finalization to catch missing chunks

## Testing

To test this implementation:

1. Ensure the server has the required endpoints implemented
2. Test with large files (100MB+) on both WiFi and mobile networks
3. Test with network transitions (WiFi -> Mobile)
4. Test interrupting uploads and resuming them
5. Compare performance with the previous implementation

## Troubleshooting

- Check logs for entries from `ChunkedUploadManager` and `UploadController`
- Look for network-related logs to identify connection issues
- Check server logs for chunk verification and missing chunk information
- Verify server-side chunk handling is working correctly

## Implementation Status

- ✅ Basic chunked upload system
- ✅ Network type detection and adaptivity
- ✅ Chunk verification and automatic retry
- ✅ Resume capability for interrupted uploads
- ✅ Parallel chunk uploading
- ✅ Network state monitoring
- ✅ Progressive backoff and retry strategy
- ✅ Enhanced progress tracking

