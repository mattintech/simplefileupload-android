# Mobile Integration Guide

This document outlines the requirements for mobile applications to integrate with the Simple File Upload Server.

## API Endpoints

### Upload File
**Endpoint:** `POST /upload`  
**Content-Type:** `multipart/form-data`

**Request Parameters:**
```
file: File (required) - The file to upload
max_downloads: Integer (optional) - Maximum number of downloads allowed (default: 1)
expiry_hours: Integer (optional) - Hours until file expires (default: 24, max: 24)
```

**Example Request (Swift):**
```swift
let url = URL(string: "http://your-server:7777/upload")!
var request = URLRequest(url: url)
request.httpMethod = "POST"

let boundary = "Boundary-\(UUID().uuidString)"
request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")

var body = Data()
// Add file data
body.append("--\(boundary)\r\n")
body.append("Content-Disposition: form-data; name=\"file\"; filename=\"log.txt\"\r\n")
body.append("Content-Type: text/plain\r\n\r\n")
body.append(fileData)
body.append("\r\n")
// Add parameters
body.append("--\(boundary)\r\n")
body.append("Content-Disposition: form-data; name=\"max_downloads\"\r\n\r\n")
body.append("3\r\n")
body.append("--\(boundary)\r\n")
body.append("Content-Disposition: form-data; name=\"expiry_hours\"\r\n\r\n")
body.append("24\r\n")
body.append("--\(boundary)--\r\n")

request.httpBody = body
```

**Example Request (Kotlin):**
```kotlin
val file = File("log.txt")
val requestBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart(
        "file",
        file.name,
        file.asRequestBody("application/octet-stream".toMediaType())
    )
    .addFormDataPart("max_downloads", "3")
    .addFormDataPart("expiry_hours", "24")
    .build()

val request = Request.Builder()
    .url("http://your-server:7777/upload")
    .post(requestBody)
    .build()
```

**Success Response:**
```json
{
    "code": "ABC123"
}
```

**Error Responses:**
```json
{
    "error": "No file part"
}
```
Status: 400 Bad Request

```json
{
    "error": "No selected file"
}
```
Status: 400 Bad Request

### Download File
**Endpoint:** `GET /download/{code}`

**Parameters:**
- `code`: 6-character alphanumeric code received from upload

**Example Request (Swift):**
```swift
let code = "ABC123"
let url = URL(string: "http://your-server:7777/download/\(code)")!
let task = URLSession.shared.downloadTask(with: url) { localURL, response, error in
    guard let localURL = localURL else { return }
    // Handle downloaded file
}
task.resume()
```

**Example Request (Kotlin):**
```kotlin
val code = "ABC123"
val request = Request.Builder()
    .url("http://your-server:7777/download/$code")
    .get()
    .build()

client.newCall(request).enqueue(object : Callback {
    override fun onResponse(call: Call, response: Response) {
        // Handle downloaded file
    }
    override fun onFailure(call: Call, e: IOException) {
        // Handle error
    }
})
```

**Success Response:**
- Status: 200 OK
- Content-Type: application/octet-stream
- Content-Disposition: attachment; filename="original_filename.ext"

**Error Responses:**
- Status: 404 Not Found
- Content-Type: application/json

Various error messages:
```json
{
    "error": "Invalid or expired code"
}
```
```json
{
    "error": "File expired"
}
```
```json
{
    "error": "Download limit reached"
}
```
```json
{
    "error": "File not found"
}
```

## Implementation Requirements

### File Upload
1. Implement proper error handling for network issues
2. Show upload progress to user
3. Handle timeout scenarios (recommended timeout: 60 seconds)
4. Store received code securely if needed
5. Validate file size before upload (max size: 16MB)
6. Implement retry logic for failed uploads

### File Download
1. Handle all error scenarios gracefully
2. Show download progress to user
3. Validate downloaded file integrity
4. Implement proper file saving logic
5. Handle interrupted downloads with resume capability if possible

### Security Considerations
1. Use HTTPS for all API calls in production
2. Don't store sensitive files for extended periods
3. Implement proper error messages without exposing server details
4. Sanitize all user inputs
5. Handle session timeouts appropriately

## Testing Requirements
1. Test with various file types and sizes
2. Verify all error scenarios
3. Test network condition variations:
    - Slow network
    - Intermittent connectivity
    - Network switches (WiFi to Cellular)
4. Test concurrent uploads/downloads
5. Verify proper cleanup of temporary files

## Sample Status Codes
- 200: Success
- 400: Bad Request (invalid parameters)
- 404: Not Found (invalid code or expired file)
- 413: Payload Too Large (file size exceeded)
- 500: Server Error

## Rate Limiting
- Maximum 10 requests per minute per IP
- Implement exponential backoff for retries

## Migration Notes
If upgrading from previous versions:
1. Update API endpoint URLs
2. Implement new error handling
3. Update file size limits
4. Add support for download codes
5. Remove old URL-based download logic