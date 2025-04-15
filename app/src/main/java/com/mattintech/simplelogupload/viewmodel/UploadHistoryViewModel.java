package com.mattintech.simplelogupload.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mattintech.simplelogupload.controller.UploadController;
import com.mattintech.simplelogupload.db.UploadHistoryRepository;
import com.mattintech.simplelogupload.model.UploadHistory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ViewModel for upload history
 */
public class UploadHistoryViewModel extends AndroidViewModel {
    
    private final UploadHistoryRepository repository;
    private final UploadController uploadController;
    private final LiveData<List<UploadHistory>> allHistory;
    private final MutableLiveData<Boolean> deleteStatus = new MutableLiveData<>();
    
    /**
     * Constructor
     * 
     * @param application The application
     */
    public UploadHistoryViewModel(@NonNull Application application) {
        super(application);
        repository = new UploadHistoryRepository(application);
        uploadController = new UploadController(application);
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
     * Get the delete status LiveData
     * 
     * @return LiveData containing the delete status
     */
    public LiveData<Boolean> getDeleteStatus() {
        return deleteStatus;
    }
    
    /**
     * Delete a history entry and the associated file from the server
     * 
     * @param history The entry to delete
     * @return CompletableFuture for the operation
     */
    public CompletableFuture<Void> delete(UploadHistory history) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        // First attempt to delete from server
        uploadController.deleteFileFromServer(history.getUploadCode())
                .thenAccept(success -> {
                    // Update the status
                    deleteStatus.postValue(success);
                    
                    // Always delete from local database, even if server delete fails
                    repository.delete(history);
                    future.complete(null);
                })
                .exceptionally(ex -> {
                    // On exception, still delete from local database
                    deleteStatus.postValue(false);
                    repository.delete(history);
                    future.complete(null);
                    return null;
                });
        
        return future;
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
     * Delete all history entries and attempt to delete all files from server
     * 
     * @return CompletableFuture for the operation
     */
    public CompletableFuture<Void> deleteAll() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        // First get all history entries
        repository.getAllHistoryOneTime()
                .thenAccept(historyList -> {
                    if (historyList == null || historyList.isEmpty()) {
                        // No entries to delete
                        repository.deleteAll();
                        future.complete(null);
                        return;
                    }
                    
                    // Counter for tracking completion
                    final int[] completedCount = {0};
                    final int totalCount = historyList.size();
                    final boolean[] anyServerSuccess = {false};
                    
                    // Process each entry
                    for (UploadHistory history : historyList) {
                        uploadController.deleteFileFromServer(history.getUploadCode())
                                .thenAccept(success -> {
                                    if (success) {
                                        anyServerSuccess[0] = true;
                                    }
                                    
                                    // Increment counter
                                    completedCount[0]++;
                                    
                                    // Check if all operations are complete
                                    if (completedCount[0] >= totalCount) {
                                        // All server delete attempts completed, now delete local entries
                                        repository.deleteAll();
                                        
                                        // Update status based on overall success
                                        deleteStatus.postValue(anyServerSuccess[0]);
                                        
                                        future.complete(null);
                                    }
                                })
                                .exceptionally(ex -> {
                                    // Count failed operations too
                                    completedCount[0]++;
                                    
                                    // Check if all operations are complete
                                    if (completedCount[0] >= totalCount) {
                                        // All server delete attempts completed, now delete local entries
                                        repository.deleteAll();
                                        
                                        // Update status based on overall success
                                        deleteStatus.postValue(anyServerSuccess[0]);
                                        
                                        future.complete(null);
                                    }
                                    return null;
                                });
                    }
                })
                .exceptionally(ex -> {
                    // If we can't get the history, just delete local entries
                    repository.deleteAll();
                    deleteStatus.postValue(false);
                    future.complete(null);
                    return null;
                });
        
        return future;
    }
}
