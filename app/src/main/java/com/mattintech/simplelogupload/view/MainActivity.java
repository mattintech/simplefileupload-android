package com.mattintech.simplelogupload.view;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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
import com.mattintech.simplelogupload.util.PreferencesUtil;

import java.io.File;
import java.util.List;

/**
 * Main activity for the application
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.APP_TAG + "MainActivity";
    
    private TextView dumpstateStatusText;
    private TextView selectedFileText;
    private Button selectFileButton;
    private Button uploadDumpstateButton;
    private Button uploadSelectedFileButton;
    private ProgressBar progressBar;
    private CardView selectedFileCard;
    
    private File selectedFile;
    private File dumpstateFile;
    
    private UploadController uploadController;
    
    // BroadcastReceiver to listen for upload results
    private BroadcastReceiver uploadReceiver;
    
    // Activity result launcher for file picking
    private ActivityResultLauncher<String> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if setup is required
        if (!PreferencesUtil.isSetupCompleted(this) || !PreferencesUtil.areServerSettingsConfigured(this)) {
            // Navigate to welcome activity
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        // Set content view
        setContentView(R.layout.activity_main);
        
        uploadController = new UploadController(this);
        
        // Initialize UI elements
        dumpstateStatusText = findViewById(R.id.dumpstateStatusText);
        selectedFileText = findViewById(R.id.selectedFileText);
        selectFileButton = findViewById(R.id.selectFileButton);
        uploadDumpstateButton = findViewById(R.id.uploadDumpstateButton);
        uploadSelectedFileButton = findViewById(R.id.uploadSelectedFileButton);
        progressBar = findViewById(R.id.progressBar);
        selectedFileCard = findViewById(R.id.selectedFileCard);
        
        // Set up button click listeners
        selectFileButton.setOnClickListener(v -> openFilePicker());
        uploadDumpstateButton.setOnClickListener(v -> uploadDumpstate());
        uploadSelectedFileButton.setOnClickListener(v -> uploadSelectedFile());
        
        // Initialize file picker launcher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        handleFileSelection(uri);
                    }
                });
        
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
        
        progressBar.setVisibility(View.VISIBLE);
        
        // Process the shared URI
        ShareIntentHandler.processSharedUri(this, sharedUri)
                .thenAccept(file -> {
                    // Save the file for upload
                    updateSelectedFile(file);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                    });
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "Error processing shared file", ex);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                getString(R.string.file_error, ex.getMessage()), 
                                Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                    });
                    return null;
                });
    }
    
    /**
     * Opens the file picker
     */
    private void openFilePicker() {
        filePickerLauncher.launch("*/*");
    }
    
    /**
     * Handle file selection from file picker
     * 
     * @param uri The URI of the selected file
     */
    private void handleFileSelection(Uri uri) {
        progressBar.setVisibility(View.VISIBLE);
        
        // Process the URI
        ShareIntentHandler.processSharedUri(this, uri)
                .thenAccept(file -> {
                    // Save the file for upload
                    updateSelectedFile(file);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                    });
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "Error processing selected file", ex);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, 
                                getString(R.string.file_error, ex.getMessage()), 
                                Toast.LENGTH_LONG).show();
                        progressBar.setVisibility(View.GONE);
                    });
                    return null;
                });
    }
    
    /**
     * Upload the selected file
     */
    private void uploadSelectedFile() {
        if (selectedFile == null || !selectedFile.exists()) {
            Toast.makeText(this, R.string.no_file_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update UI
        progressBar.setVisibility(View.VISIBLE);
        uploadSelectedFileButton.setEnabled(false);
        selectedFileText.setText(getString(R.string.uploading_file, selectedFile.getName()));
        
        // Start the upload service
        startUploadService(selectedFile);
    }
    
    /**
     * Upload the dumpstate file
     */
    private void uploadDumpstate() {
        if (dumpstateFile == null || !dumpstateFile.exists()) {
            Toast.makeText(this, R.string.no_dumpstate_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update UI
        progressBar.setVisibility(View.VISIBLE);
        uploadDumpstateButton.setEnabled(false);
        dumpstateStatusText.setText(getString(R.string.uploading_file, dumpstateFile.getName()));
        
        // Start the upload service
        startUploadService(dumpstateFile);
    }
    
    /**
     * Start the upload service with the given file
     * 
     * @param file The file to upload
     */
    private void startUploadService(File file) {
        // Start the upload service
        Intent serviceIntent = new Intent(this, FileUploadForegroundService.class);
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_FILE_PATH, file.getAbsolutePath());
        startService(serviceIntent);
    }
    
    /**
     * Update the selected file UI
     * 
     * @param file The selected file
     */
    private void updateSelectedFile(File file) {
        selectedFile = file;
        runOnUiThread(() -> {
            selectedFileCard.setVisibility(View.VISIBLE);
            selectedFileText.setText(getString(R.string.selected_file, file.getName()));
            uploadSelectedFileButton.setVisibility(View.VISIBLE);
            uploadSelectedFileButton.setEnabled(true);
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
                uploadSelectedFileButton.setEnabled(true);
                uploadDumpstateButton.setEnabled(true);
                
                if (intent.getAction().equals(AppConstants.ACTION_UPLOAD_COMPLETE)) {
                    String uploadCode = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_CODE);
                    Log.d(TAG, "Upload complete with code: " + uploadCode);
                    showUploadSuccessDialog(uploadCode);
                } else if (intent.getAction().equals(AppConstants.ACTION_UPLOAD_ERROR)) {
                    String errorMessage = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_ERROR);
                    Log.e(TAG, "Upload error: " + errorMessage);
                    Toast.makeText(MainActivity.this, 
                            getString(R.string.upload_error, errorMessage), 
                            Toast.LENGTH_LONG).show();
                    resetUI();
                }
            }
        };
    }
    
    /**
     * Reset the UI after upload completion or error
     */
    private void resetUI() {
        dumpstateStatusText.setText(dumpstateFile != null ? 
                getString(R.string.dumpstate_found, dumpstateFile.getName()) : 
                getString(R.string.no_dumpstate_found));
        if (selectedFile != null) {
            selectedFileText.setText(getString(R.string.selected_file, selectedFile.getName()));
        }
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
        // Look for dumpstate files
        File mostRecentDumpStateFile = FileController.getMostRecentDumpStateFile();
        
        if (mostRecentDumpStateFile != null) {
            dumpstateFile = mostRecentDumpStateFile;
            dumpstateStatusText.setText(getString(R.string.dumpstate_found, mostRecentDumpStateFile.getName()));
            uploadDumpstateButton.setEnabled(true);
        } else {
            dumpstateFile = null;
            dumpstateStatusText.setText(R.string.no_dumpstate_found);
            uploadDumpstateButton.setEnabled(false);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == PermissionUtil.REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (PermissionUtil.hasAllFilesAccessPermission()) {
                checkForDumpStateAndUpdateUI();
            } else {
                Toast.makeText(this, R.string.permission_not_granted, Toast.LENGTH_SHORT).show();
            }
        }
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
                Toast.makeText(this, getString(R.string.upload_success, uploadCode), Toast.LENGTH_LONG).show();
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
