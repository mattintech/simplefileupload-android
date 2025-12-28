package com.mattintech.simplelogupload.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class for upload history
 */
@Entity(tableName = "upload_history")
data class UploadHistory(
    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "upload_time")
    val uploadTime: Long,

    @ColumnInfo(name = "upload_code")
    val uploadCode: String,

    @ColumnInfo(name = "max_downloads")
    val maxDownloads: Int,

    @ColumnInfo(name = "expiry_hours")
    val expiryHours: Int
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0
}
