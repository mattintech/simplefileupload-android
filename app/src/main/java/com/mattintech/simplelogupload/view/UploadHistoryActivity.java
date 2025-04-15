package com.mattintech.simplelogupload.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.model.UploadHistory;
import com.mattintech.simplelogupload.view.adapter.UploadHistoryAdapter;
import com.mattintech.simplelogupload.viewmodel.UploadHistoryViewModel;

import java.util.List;

/**
 * Activity for displaying upload history
 */
public class UploadHistoryActivity extends AppCompatActivity implements UploadHistoryAdapter.OnHistoryItemClickListener {
    private static final String TAG = AppConstants.APP_TAG + "HistoryActivity";
    
    private UploadHistoryViewModel viewModel;
    private UploadHistoryAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_history);
        
        // Enable "up" navigation
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        // Initialize views
        recyclerView = findViewById(R.id.historyRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        
        // Set up the RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UploadHistoryAdapter(this);
        recyclerView.setAdapter(adapter);
        
        // Initialize the ViewModel
        viewModel = new ViewModelProvider.AndroidViewModelFactory(getApplication())
                .create(UploadHistoryViewModel.class);
        
        // Observe the history data
        viewModel.getAllHistory().observe(this, new Observer<List<UploadHistory>>() {
            @Override
            public void onChanged(List<UploadHistory> history) {
                // Update the cached copy of the history in the adapter
                adapter.setHistory(history);
                
                // Show/hide empty view
                if (history == null || history.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                } else {
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyView.setVisibility(View.GONE);
                }
            }
        });
        
        // Observe the delete status to provide feedback
        viewModel.getDeleteStatus().observe(this, deleteSuccess -> {
            if (deleteSuccess != null) {
                if (deleteSuccess) {
                    Toast.makeText(this, "File deleted from server successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to delete file from server, but removed from local history", 
                           Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_clear_history) {
            // Show confirmation dialog
            new AlertDialog.Builder(this)
                    .setTitle("Clear All History")
                    .setMessage("Do you want to delete all uploads from both local history and the server?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        // Show progress dialog
                        AlertDialog progressDialog = new AlertDialog.Builder(this)
                                .setTitle("Deleting...")
                                .setMessage("Attempting to delete all files from server and clear history.")
                                .setCancelable(false)
                                .create();
                        progressDialog.show();
                        
                        // Attempt to delete all
                        viewModel.deleteAll()
                                .thenAccept(unused -> {
                                    runOnUiThread(() -> {
                                        progressDialog.dismiss();
                                        // Toast message will be shown through the observer
                                    });
                                });
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onCopyCodeClick(UploadHistory history) {
        copyToClipboard(history.getUploadCode());
    }
    
    @Override
    public void onDeleteClick(UploadHistory history) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Delete Upload")
                .setMessage("Do you want to delete this upload from both local history and the server?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Show progress dialog
                    AlertDialog progressDialog = new AlertDialog.Builder(this)
                            .setTitle("Deleting...")
                            .setMessage("Attempting to delete from server and local history.")
                            .setCancelable(false)
                            .create();
                    progressDialog.show();
                    
                    // Attempt to delete
                    viewModel.delete(history)
                            .thenAccept(unused -> {
                                runOnUiThread(() -> {
                                    progressDialog.dismiss();
                                });
                            });
                })
                .setNegativeButton("No", null)
                .show();
    }
    
    /**
     * Copy text to clipboard
     * 
     * @param text The text to copy
     */
    private void copyToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Upload Code", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error copying to clipboard", e);
            Toast.makeText(this, "Failed to copy code", Toast.LENGTH_SHORT).show();
        }
    }
}
