package com.mattintech.simplelogupload.constant;

/**
 * General application constants
 */
public class AppConstants {
    public static final String APP_TAG = "SLU::";
    
    // Broadcast actions
    public static final String ACTION_UPLOAD_COMPLETE = "com.mattintech.simplelogupload.UPLOAD_COMPLETE";
    public static final String ACTION_UPLOAD_ERROR = "com.mattintech.simplelogupload.UPLOAD_ERROR";
    
    // Intent extras
    public static final String EXTRA_UPLOAD_CODE = "upload_code";
    public static final String EXTRA_UPLOAD_ERROR = "upload_error";
    
    // File patterns
    public static final String DUMPSTATE_FILE_PATTERN = "dumpState_.*\\.zip";
    
    // Notification
    public static final String NOTIFICATION_CHANNEL_ID = "FileUploadChannel";
}
