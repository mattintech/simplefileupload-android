package com.mattintech.simplelogupload.constant;

/**
 * General application constants
 */
public class AppConstants {
    public static final String APP_TAG = "SLU::";
    
    // Broadcast actions
    public static final String ACTION_UPLOAD_COMPLETE = "com.mattintech.simplelogupload.UPLOAD_COMPLETE";
    public static final String ACTION_UPLOAD_ERROR = "com.mattintech.simplelogupload.UPLOAD_ERROR";
    public static final String ACTION_UPLOAD_PROGRESS = "com.mattintech.simplelogupload.UPLOAD_PROGRESS";

    // Intent extras
    public static final String EXTRA_UPLOAD_CODE = "upload_code";
    public static final String EXTRA_UPLOAD_ERROR = "upload_error";
    public static final String EXTRA_BYTES_UPLOADED = "bytes_uploaded";
    public static final String EXTRA_TOTAL_BYTES = "total_bytes";
    public static final String EXTRA_PERCENT_COMPLETE = "percent_complete";
    
    // File patterns
    public static final String DUMPSTATE_FILE_PATTERN = "dumpState_.*\\.zip";
    
    // Notification
    public static final String NOTIFICATION_CHANNEL_ID = "FileUploadChannel";
}
