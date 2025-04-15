package com.mattintech.simplelogupload.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Entity class for upload history
 */
@Entity(tableName = "upload_history")
public class UploadHistory {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    @ColumnInfo(name = "file_name")
    private String fileName;
    
    @ColumnInfo(name = "upload_time")
    private long uploadTime;
    
    @ColumnInfo(name = "upload_code")
    private String uploadCode;
    
    @ColumnInfo(name = "max_downloads")
    private int maxDownloads;
    
    @ColumnInfo(name = "expiry_hours")
    private int expiryHours;
    
    /**
     * Constructor
     * 
     * @param fileName The name of the uploaded file
     * @param uploadTime The time of upload (in milliseconds)
     * @param uploadCode The server-provided upload code
     * @param maxDownloads The maximum number of downloads allowed
     * @param expiryHours The expiry time in hours
     */
    public UploadHistory(String fileName, long uploadTime, String uploadCode, int maxDownloads, int expiryHours) {
        this.fileName = fileName;
        this.uploadTime = uploadTime;
        this.uploadCode = uploadCode;
        this.maxDownloads = maxDownloads;
        this.expiryHours = expiryHours;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getUploadTime() {
        return uploadTime;
    }

    public void setUploadTime(long uploadTime) {
        this.uploadTime = uploadTime;
    }

    public String getUploadCode() {
        return uploadCode;
    }

    public void setUploadCode(String uploadCode) {
        this.uploadCode = uploadCode;
    }

    public int getMaxDownloads() {
        return maxDownloads;
    }

    public void setMaxDownloads(int maxDownloads) {
        this.maxDownloads = maxDownloads;
    }

    public int getExpiryHours() {
        return expiryHours;
    }

    public void setExpiryHours(int expiryHours) {
        this.expiryHours = expiryHours;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "UploadHistory{" +
                "id=" + id +
                ", fileName='" + fileName + '\'' +
                ", uploadTime=" + uploadTime +
                ", uploadCode='" + uploadCode + '\'' +
                ", maxDownloads=" + maxDownloads +
                ", expiryHours=" + expiryHours +
                '}';
    }
}
