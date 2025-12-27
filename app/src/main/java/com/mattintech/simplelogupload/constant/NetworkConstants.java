package com.mattintech.simplelogupload.constant;

/**
 * Network-related constants
 */
public class NetworkConstants {
    // These URL constants are now dynamically set in getServerUrl()
    // via PreferencesUtil to allow for customization
    public static final String SERVER_URL_BASE = "/upload";
    
    // Chunked upload endpoints (relative to server base URL)
    public static final String CHUNKED_UPLOAD_START = "/upload/start";
    public static final String CHUNKED_UPLOAD_CHUNK = "/upload/chunk";
    public static final String CHUNKED_UPLOAD_COMPLETE = "/upload/complete";
    
    // Upload parameters
    public static final String PARAM_FILE = "file";
    public static final String PARAM_MAX_DOWNLOADS = "max_downloads";
    public static final String PARAM_EXPIRY_HOURS = "expiry_hours";
    
    // Chunked upload parameters
    public static final String PARAM_UPLOAD_ID = "upload_id";
    public static final String PARAM_CHUNK_INDEX = "chunk_index";
    public static final String PARAM_TOTAL_CHUNKS = "total_chunks";
    public static final String PARAM_CHUNK = "chunk";
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_FILESIZE = "filesize";
    public static final String PARAM_CHUNK_SIZE = "chunk_size";
    
    // Authentication
    public static final String CLIENT_KEY_HEADER = "X-Client-Key";
    
    // Default values
    public static final int DEFAULT_MAX_DOWNLOADS = 1;
    public static final int DEFAULT_EXPIRY_HOURS = 24;
    public static final int MAX_EXPIRY_HOURS = 24;
    
    // Timeouts (in seconds)
    public static final int CONNECTION_TIMEOUT = 60;
    public static final int WRITE_TIMEOUT = 180;
    public static final int READ_TIMEOUT = 60;
}
