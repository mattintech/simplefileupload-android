package com.mattintech.simplelogupload.model;

/**
 * Model class for upload results
 */
public class UploadResult {
    private final String code;
    private final String error;
    private final boolean success;
    private final String fileName;
    private final int maxDownloads;
    private final int expiryHours;
    
    /**
     * Constructor for successful uploads
     * 
     * @param code The server-provided upload code
     * @param fileName The name of the uploaded file
     * @param maxDownloads The maximum number of downloads
     * @param expiryHours The expiry time in hours
     */
    public UploadResult(String code, String fileName, int maxDownloads, int expiryHours) {
        this.code = code;
        this.error = null;
        this.success = true;
        this.fileName = fileName;
        this.maxDownloads = maxDownloads;
        this.expiryHours = expiryHours;
    }

    /**
     * Constructor for failed uploads
     * 
     * @param error The error message
     */
    public UploadResult(String error) {
        this.code = null;
        this.error = error;
        this.success = false;
        this.fileName = null;
        this.maxDownloads = 0;
        this.expiryHours = 0;
    }

    public String getCode() {
        return code;
    }

    public String getError() {
        return error;
    }

    public boolean isSuccess() {
        return success;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public int getMaxDownloads() {
        return maxDownloads;
    }
    
    public int getExpiryHours() {
        return expiryHours;
    }
}
