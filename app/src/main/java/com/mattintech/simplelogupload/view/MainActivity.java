package com.mattintech.simplelogupload.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.controller.FileController;
import com.mattintech.simplelogupload.controller.ShareIntentHandler;
import com.mattintech.simplelogupload.controller.UploadController;
import com.mattintech.simplelogupload.service.FileUploadForegroundService;
import com.mattintech.simplelogupload.util.PermissionUtil;

import java.io.File;
import java.util.List;

/**
 * Main activity for the application
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.APP_TAG + "MainActivity";
    
    private TextView resultView;
    private Button uploadButton;
    private ProgressBar progressBar;
    private File selectedFile;
    
    private UploadController uploadController;
    
    // BroadcastReceiver to listen for upload results
    private BroadcastReceiver uploadReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        uploadController = new UploadController(this);
        
        // Initialize UI elements
        resultView = findViewById(R.id.resultView);
        uploadButton = findViewById(R.id.uploadButton);
        progressBar = findViewById(R.id.progressBar);
        
        // Set up button click listeners
        uploadButton.setOnClickListener(v -> startUpload());
        
        // Check for necessary permissions
        checkAndRequestPermissions();
        
        // Initialize broadcast receiver
        setupBroadcastReceiver();
        
        // Handle intent (e.g., from share)
        handleIntent(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }
    
    /**
     * Handle the intent, particularly for share intents
     * 
     * @param intent The intent to handle
     */
    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        
        // Check if this is a share intent
        if (ShareIntentHandler.isShareIntent(intent)) {
            handleShareIntent(intent);
        }
    }
    
    /**
     * Handle a share intent
     * 
     * @param intent The share intent
     */
    private void handleShareIntent(Intent intent) {
        List<Uri> sharedUris = ShareIntentHandler.extractSharedUris(intent);
        
        if (sharedUris.isEmpty()) {
            Toast.makeText(this, "No files shared", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // For now, just handle the first URI
        Uri sharedUri = sharedUris.get(0);
        
        resultView.setText("Processing shared file...");
        progressBar.setVisibility(View.VISIBLE);
        uploadButton.setEnabled(false);
        
        // Process the shared URI
        ShareIntentHandler.processSharedUri(this, sharedUri)
                .thenAccept(file -> {
                    // Save the file for upload
                    selectedFile = file;
                    runOnUiThread(() -> {
                        resultView.setText("File ready: " + file.getName());
                        uploadButton.setEnabled(true);
                        uploadButton.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    });
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "Error processing shared file", ex);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                "Error processing file: " + ex.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        resultView.setText("Error processing file");
                        progressBar.setVisibility(View.GONE);
                    });
                    return null;
                });
    }
    
    /**
     * Set up the broadcast receiver for upload results
     */
    private void setupBroadcastReceiver() {
        uploadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received broadcast: " + intent.getAction());
                progressBar.setVisibility(View.GONE);
                uploadButton.setEnabled(true);
                
                if (intent.getAction().equals(AppConstants.ACTION_UPLOAD_COMPLETE)) {
                    String uploadCode = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_CODE);
                    Log.d(TAG, "Upload complete with code: " + uploadCode);
                    showUploadSuccessDialog(uploadCode);
                } else if (intent.getAction().equals(AppConstants.ACTION_UPLOAD_ERROR)) {
                    String errorMessage = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_ERROR);
                    Log.e(TAG, "Upload error: " + errorMessage);
                    Toast.makeText(MainActivity.this, "Upload Error: " + errorMessage, Toast.LENGTH_LONG).show();
                    resultView.setText("Upload failed: " + errorMessage);
                }
            }
        };
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Check for file if we have permissions
        if (PermissionUtil.hasAllFilesAccessPermission()) {
            checkForDumpStateAndUpdateUI();
        }
        
        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConstants.ACTION_UPLOAD_COMPLETE);
        filter.addAction(AppConstants.ACTION_UPLOAD_ERROR);
        
        // Register with both regular and local broadcast managers to ensure delivery
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uploadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(uploadReceiver, filter);
        }
        
        // Also register with LocalBroadcastManager for reliability
        LocalBroadcastManager.getInstance(this).registerReceiver(uploadReceiver, filter);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Unregister the broadcast receiver
        try {
            unregisterReceiver(uploadReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered, ignore
        }
        
        // Also unregister from LocalBroadcastManager
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uploadReceiver);
    }
    
    /**
     * Check for and request necessary permissions
     */
    private void checkAndRequestPermissions() {
        // Request notification permission
        PermissionUtil.requestNotificationPermission(this);
        
        // Check and request all files access permission
        if (!PermissionUtil.hasAllFilesAccessPermission()) {
            PermissionUtil.requestAllFilesAccessPermission(this);
        }
    }
    
    /**
     * Check for dumpstate files and update the UI
     */
    private void checkForDumpStateAndUpdateUI() {
        // If we already have a selected file (e.g., from share), use that
        if (selectedFile != null && selectedFile.exists()) {
            resultView.setText("File selected: " + selectedFile.getName());
            uploadButton.setVisibility(View.VISIBLE);
            return;
        }
        
        // Otherwise, look for dumpstate files
        File mostRecentDumpStateFile = FileController.getMostRecentDumpStateFile();
        
        if (mostRecentDumpStateFile != null) {
            selectedFile = mostRecentDumpStateFile;
            resultView.setText("Dumpstate found: " + mostRecentDumpStateFile.getName());
            uploadButton.setVisibility(View.VISIBLE);
        } else {
            resultView.setText("No dumpstate file found.");
            uploadButton.setVisibility(View.GONE);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PermissionUtil.REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (PermissionUtil.hasAllFilesAccessPermission()) {
                checkForDumpStateAndUpdateUI();
            } else {
                Toast.makeText(this, "Storage permission not granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    /**
     * Start the upload process
     */
    private void startUpload() {
        if (selectedFile == null || !selectedFile.exists()) {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update UI
        progressBar.setVisibility(View.VISIBLE);
        uploadButton.setEnabled(false);
        resultView.setText("Uploading file...");
        
        // Start the upload service
        Intent serviceIntent = new Intent(this, FileUploadForegroundService.class);
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_FILE_PATH, selectedFile.getAbsolutePath());
        startService(serviceIntent);
    }
    
    /**
     * Show a dialog with the upload success result
     * 
     * @param uploadCode The upload code
     */
    private void showUploadSuccessDialog(String uploadCode) {
        Log.d(TAG, "Opening upload success dialog with code: " + uploadCode);
        runOnUiThread(() -> {
            try {
                UploadResultDialog dialog = new UploadResultDialog(this, uploadCode);
                dialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing upload result dialog", e);
                Toast.makeText(this, "Upload successful! Code: " + uploadCode, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_history) {
            // Open upload history activity
            startActivity(new Intent(this, UploadHistoryActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            // Open settings activity
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}
