package com.mattintech.simplelogupload.db;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.mattintech.simplelogupload.model.UploadHistory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for handling upload history data operations
 */
public class UploadHistoryRepository {
    private final UploadHistoryDao mUploadHistoryDao;
    private final LiveData<List<UploadHistory>> mAllHistory;
    
    /**
     * Constructor
     * 
     * @param application The application
     */
    public UploadHistoryRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        mUploadHistoryDao = db.uploadHistoryDao();
        mAllHistory = mUploadHistoryDao.getAllHistory();
    }
    
    /**
     * Get all history entries
     * 
     * @return LiveData list of all history entries
     */
    public LiveData<List<UploadHistory>> getAllHistory() {
        return mAllHistory;
    }
    
    /**
     * Get all history entries as a one-time operation
     * 
     * @return CompletableFuture containing a list of all history entries
     */
    public CompletableFuture<List<UploadHistory>> getAllHistoryOneTime() {
        return CompletableFuture.supplyAsync(() -> 
                mUploadHistoryDao.getAllHistoryDirect(), 
                AppDatabase.databaseWriteExecutor);
    }
    
    /**
     * Insert a new history entry asynchronously
     * 
     * @param entry The entry to insert
     * @return CompletableFuture containing the inserted row ID
     */
    public CompletableFuture<Long> insert(final UploadHistory entry) {
        return CompletableFuture.supplyAsync(() -> 
                mUploadHistoryDao.insert(entry), 
                AppDatabase.databaseWriteExecutor);
    }
    
    /**
     * Delete a history entry asynchronously
     * 
     * @param entry The entry to delete
     */
    public void delete(final UploadHistory entry) {
        AppDatabase.databaseWriteExecutor.execute(() -> 
                mUploadHistoryDao.delete(entry));
    }
    
    /**
     * Delete a history entry by ID asynchronously
     * 
     * @param id The ID of the entry to delete
     */
    public void deleteById(final int id) {
        AppDatabase.databaseWriteExecutor.execute(() -> 
                mUploadHistoryDao.deleteById(id));
    }
    
    /**
     * Delete all history entries asynchronously
     */
    public void deleteAll() {
        AppDatabase.databaseWriteExecutor.execute(
                mUploadHistoryDao::deleteAll);
    }
    
    /**
     * Get a history entry by ID asynchronously
     * 
     * @param id The ID of the entry
     * @return CompletableFuture containing the history entry
     */
    public CompletableFuture<UploadHistory> getEntryById(final int id) {
        return CompletableFuture.supplyAsync(() -> 
                mUploadHistoryDao.getEntryById(id), 
                AppDatabase.databaseWriteExecutor);
    }
}
