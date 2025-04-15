package com.mattintech.simplelogupload.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.mattintech.simplelogupload.model.UploadHistory;

import java.util.List;

/**
 * Data Access Object for upload history
 */
@Dao
public interface UploadHistoryDao {
    /**
     * Insert a new history entry
     * 
     * @param entry The entry to insert
     * @return The row ID of the inserted entry
     */
    @Insert
    long insert(UploadHistory entry);
    
    /**
     * Get all history entries ordered by upload time (most recent first)
     * 
     * @return LiveData list of history entries
     */
    @Query("SELECT * FROM upload_history ORDER BY upload_time DESC")
    LiveData<List<UploadHistory>> getAllHistory();
    
    /**
     * Get a history entry by ID
     * 
     * @param id The ID of the entry
     * @return The history entry
     */
    @Query("SELECT * FROM upload_history WHERE id = :id")
    UploadHistory getEntryById(int id);
    
    /**
     * Delete a history entry
     * 
     * @param entry The entry to delete
     */
    @Delete
    void delete(UploadHistory entry);
    
    /**
     * Delete a history entry by ID
     * 
     * @param id The ID of the entry to delete
     */
    @Query("DELETE FROM upload_history WHERE id = :id")
    void deleteById(int id);
    
    /**
     * Delete all history entries
     */
    @Query("DELETE FROM upload_history")
    void deleteAll();
}
