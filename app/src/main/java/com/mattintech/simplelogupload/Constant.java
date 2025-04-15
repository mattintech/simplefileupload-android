package com.mattintech.simplelogupload;

public class Constant {
    public static final String APP_TAG = "SLU::";
    
    // Server constants
    public static final String SERVER_URL = "http://172.16.100.167:7777/upload"; // HTTP protocol explicitly specified
    
    // File size limits
    public static final long MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024; // 2GB
    
    // Upload parameters
    public static final String PARAM_FILE = "file";
    public static final String PARAM_MAX_DOWNLOADS = "max_downloads";
    public static final String PARAM_EXPIRY_HOURS = "expiry_hours";
    
    // Default values
    public static final int DEFAULT_MAX_DOWNLOADS = 1;
    public static final int DEFAULT_EXPIRY_HOURS = 24;
    public static final int MAX_EXPIRY_HOURS = 24;
    
    // Broadcast actions
    public static final String ACTION_UPLOAD_COMPLETE = "com.mattintech.simplelogupload.UPLOAD_COMPLETE";
    public static final String ACTION_UPLOAD_ERROR = "com.mattintech.simplelogupload.UPLOAD_ERROR";
    
    // Intent extras
    public static final String EXTRA_UPLOAD_CODE = "upload_code";
    public static final String EXTRA_UPLOAD_ERROR = "upload_error";
}
