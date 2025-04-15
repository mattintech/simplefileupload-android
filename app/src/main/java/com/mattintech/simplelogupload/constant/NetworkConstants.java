package com.mattintech.simplelogupload.constant;

/**
 * Network-related constants
 */
public class NetworkConstants {
    // Server constants
    public static final String SERVER_URL = "http://172.16.100.167:7777/upload";
    
    // Upload parameters
    public static final String PARAM_FILE = "file";
    public static final String PARAM_MAX_DOWNLOADS = "max_downloads";
    public static final String PARAM_EXPIRY_HOURS = "expiry_hours";
    
    // Default values
    public static final int DEFAULT_MAX_DOWNLOADS = 1;
    public static final int DEFAULT_EXPIRY_HOURS = 24;
    public static final int MAX_EXPIRY_HOURS = 24;
    
    // Timeouts (in seconds)
    public static final int CONNECTION_TIMEOUT = 60;
    public static final int WRITE_TIMEOUT = 180;
    public static final int READ_TIMEOUT = 60;
}
