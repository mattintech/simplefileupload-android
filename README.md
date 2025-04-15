# SimpleFileUpload
Android client written in java to easily upload Samsung dumpstate files.
Note - this requires that you leverage SimpleFileUpload-Server 

## Features
- Upload Samsung dumpstate files or any file via UI or share intent
- Automatically compress files to .zip format before upload
- View upload history with received codes
- Copy codes for easy sharing
- Support for Android 14+ with proper foreground service implementation
- MVC architecture for clean code separation

## Recent Updates
- Fixed broadcast receiver registration for Android 13+
- Added proper foreground service type declaration for Android 14+
- Improved reliability of upload completion handling
- Enhanced error handling and logging

