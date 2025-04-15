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
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_clear_history) {
            viewModel.deleteAll();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
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
        viewModel.delete(history);
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
