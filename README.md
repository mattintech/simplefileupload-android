# SimpleFileUpload

Android client written in Java to easily upload files, including Samsung dumpstate files.
Note - this requires that you leverage SimpleFileUpload-Server 

## Features
- General file upload capability from device storage
- Support for Samsung dumpstate files as a specialized feature
- Automatic dumpstate file detection
- Multi-select capability for uploading multiple dumpstate files in one package
- Expandable dumpstate file list with detailed information
- Automatically compress files to .zip format before upload
- Modern Material Design interface with intuitive UI
- View upload history with received codes
- Copy codes for easy sharing
- Support for Android 14+ with proper foreground service implementation
- MVC architecture for clean code separation
- Custom upload parameters (expiry time, download limit)
- Server-side file deletion from history

## Recent Updates
- Added server-side file deletion when removing items from history
- Added custom upload parameters for maximum downloads and expiry time
- Added multi-select capability for dumpstate files
- Implemented zipping multiple files into a single package for upload
- Added in-place expandable file list to avoid popup dialogs
- Redesigned UI with improved user experience
- Added general file upload capability as the main feature
- Dumpstate upload functionality moved to a sub-feature
- Updated app icons for a more modern look
- Fixed broadcast receiver registration for Android 13+
- Added proper foreground service type declaration for Android 14+
- Improved reliability of upload completion handling
- Enhanced error handling and logging

## How to Use
1. **Upload a File**: Tap "Select File from Storage" to pick any file from your device.
2. **Set Upload Parameters**: When uploading, set custom parameters such as maximum downloads allowed and expiry time.
3. **Upload a Dumpstate**: The app automatically detects Samsung dumpstate files on your device.
4. **Multi-select Mode**: When multiple dumpstate files are detected, tap "Show Files" to view and select multiple files for upload.
5. **Select Files**: Check multiple files to upload them as a single package.
6. **View History**: Access your upload history from the menu to find previously uploaded file codes.
7. **Delete From Server**: When deleting items from history, files will also be removed from the server.

## Upcoming Features
- Progress tracking for large files
- Dark mode support
- Upload history filtering and search
- QR code generation for quick sharing

## Development and Releases

### Automated Releases with GitHub Actions
This project uses GitHub Actions to automate the build and release process:

1. **Creating a new release:**
   - Make your changes and commit them to the repository
   - Create and push a new tag following the format `vYY.MM.PATCH` (e.g., `v25.04.1`)
   - GitHub Actions will automatically:
     - Build the APK
     - Sign it using the stored signing keys
     - Create a GitHub release with the APK attached

2. **Signing Configuration:**
   Before the automated releases will work, you need to add the following secrets to your GitHub repository:
   - `SIGNING_KEY`: Base64-encoded keystore file
   - `KEY_ALIAS`: The alias used in the keystore
   - `KEY_STORE_PASSWORD`: The keystore password
   - `KEY_PASSWORD`: The key password

3. **Release Versioning:**
   We follow a `vYY.MM.PATCH` versioning scheme:
   - `YY`: Year (e.g., 25 for 2025)
   - `MM`: Month (e.g., 04 for April)
   - `PATCH`: Incremental patch number for the month

## Feature Details

### Custom Upload Parameters

The app allows you to customize how your uploads are handled on the server:

#### Maximum Downloads

- **Purpose**: Limit how many times a file can be downloaded
- **Default**: 1 download (file is removed after first download)
- **Options**: 
  - Set to any positive number to limit downloads
  - Set to 0 for unlimited downloads
- **How to use**: Enter your desired number in the "Maximum Downloads" field when uploading a file

#### Expiry Time

- **Purpose**: Set how long the file remains available on the server
- **Default**: 24 hours (1 day)
- **Maximum**: 24 hours (server-enforced limit)
- **How to use**: Enter your desired number of hours in the "Expiry Time" field when uploading

#### Upload Parameter Dialog

When you initiate an upload (either a selected file or dumpstate), the app will present a dialog where you can set these parameters before the actual upload begins. If you don't modify the fields, the default values will be used.

### Server-Side File Deletion

The app integrates with the server's delete endpoint to remove files from the server when you delete them from your local history.

#### Single Item Deletion

When you delete a single item from your upload history:
1. The app attempts to delete the file from the server using its upload code
2. Regardless of server response, the item is removed from your local history
3. You will receive feedback about whether the server deletion was successful

#### Bulk Deletion (Clear History)

When you clear all history:
1. The app attempts to delete each file from the server
2. All items are removed from your local history
3. You will receive feedback about whether any of the server deletions were successful

#### Benefits

- **Privacy**: Ensures your uploaded files don't remain on the server indefinitely
- **Resource Management**: Helps keep the server storage clean
- **Control**: Gives you full control over the lifecycle of your uploaded files

#### API Integration

The app uses the server's delete endpoint:
```
DELETE /delete/<code>
```

This endpoint requires the same client authentication key used for uploads. The server returns:
- 200 OK: File successfully deleted
- 404 Not Found: File doesn't exist (already deleted or expired)
- 401 Unauthorized: Invalid client key
