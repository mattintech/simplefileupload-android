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

## Recent Updates
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
2. **Upload a Dumpstate**: The app automatically detects Samsung dumpstate files on your device.
3. **Multi-select Mode**: When multiple dumpstate files are detected, tap "Show Files" to view and select multiple files for upload.
4. **Select Files**: Check multiple files to upload them as a single package.
5. **View History**: Access your upload history from the menu to find previously uploaded file codes.

## Upcoming Features
- Progress tracking for large files
- Dark mode support
- Upload history filtering and search
- QR code generation for quick sharing
- Custom upload parameters (expiry time, download limit)
