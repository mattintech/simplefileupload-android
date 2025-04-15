package com.mattintech.simplelogupload.constant;

/**
 * Network-related constants
 */
public class NetworkConstants {
    // These URL constants are now dynamically set in getServerUrl()
    // via PreferencesUtil to allow for customization
    public static final String SERVER_URL_BASE = "/upload";
    
    // Upload parameters
    public static final String PARAM_FILE = "file";
    public static final String PARAM_MAX_DOWNLOADS = "max_downloads";
    public static final String PARAM_EXPIRY_HOURS = "expiry_hours";
    
    // Authentication
    public static final String CLIENT_KEY_HEADER = "client-key";
    
    // Default values
    public static final int DEFAULT_MAX_DOWNLOADS = 1;
    public static final int DEFAULT_EXPIRY_HOURS = 24;
    public static final int MAX_EXPIRY_HOURS = 24;
    
    // Timeouts (in seconds)
    public static final int CONNECTION_TIMEOUT = 60;
    public static final int WRITE_TIMEOUT = 180;
    public static final int READ_TIMEOUT = 60;
}
