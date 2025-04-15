package com.mattintech.simplelogupload.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.mattintech.simplelogupload.db.UploadHistoryRepository;
import com.mattintech.simplelogupload.model.UploadHistory;

import java.util.List;

/**
 * ViewModel for upload history
 */
public class UploadHistoryViewModel extends AndroidViewModel {
    
    private final UploadHistoryRepository repository;
    private final LiveData<List<UploadHistory>> allHistory;
    
    /**
     * Constructor
     * 
     * @param application The application
     */
    public UploadHistoryViewModel(@NonNull Application application) {
        super(application);
        repository = new UploadHistoryRepository(application);
        allHistory = repository.getAllHistory();
    }
    
    /**
     * Get all history entries
     * 
     * @return LiveData containing all history entries
     */
    public LiveData<List<UploadHistory>> getAllHistory() {
        return allHistory;
    }
    
    /**
     * Insert a new history entry
     * 
     * @param history The entry to insert
     */
    public void insert(UploadHistory history) {
        repository.insert(history);
    }
    
    /**
     * Delete a history entry
     * 
     * @param history The entry to delete
     */
    public void delete(UploadHistory history) {
        repository.delete(history);
    }
    
    /**
     * Delete a history entry by ID
     * 
     * @param id The ID of the entry to delete
     */
    public void deleteById(int id) {
        repository.deleteById(id);
    }
    
    /**
     * Delete all history entries
     */
    public void deleteAll() {
        repository.deleteAll();
    }
}
