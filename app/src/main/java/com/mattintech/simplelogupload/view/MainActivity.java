package com.mattintech.simplelogupload.view;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.adapter.DumpstateAdapter;
import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.controller.FileController;
import com.mattintech.simplelogupload.controller.ShareIntentHandler;
import com.mattintech.simplelogupload.controller.UploadController;
import com.mattintech.simplelogupload.service.FileUploadForegroundService;
import com.mattintech.simplelogupload.util.PermissionUtil;
import com.mattintech.simplelogupload.util.PreferencesUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
    
    // Dumpstate multi-select components
    private RecyclerView dumpstateRecyclerView;
    private Button toggleDumpstateListButton;
    private Button selectAllButton;
    private Button clearSelectionsButton;
    private LinearLayout multiSelectButtonsLayout;
    private DumpstateAdapter dumpstateAdapter;
    
    private File selectedFile;
    private File dumpstateFile;
    
    private UploadController uploadController;
    
    // BroadcastReceiver to listen for upload results
    private BroadcastReceiver uploadReceiver;
    
    // Activity result launcher for file picking
    private ActivityResultLauncher<String> filePickerLauncher;
    
    // Flag to track if dumpstate list is expanded
    private boolean isDumpstateListExpanded = false;

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
        
        // Initialize multi-select UI elements
        dumpstateRecyclerView = findViewById(R.id.dumpstateRecyclerView);
        toggleDumpstateListButton = findViewById(R.id.toggleDumpstateListButton);
        selectAllButton = findViewById(R.id.selectAllButton);
        clearSelectionsButton = findViewById(R.id.clearSelectionsButton);
        multiSelectButtonsLayout = findViewById(R.id.multiSelectButtonsLayout);
        
        // Set up RecyclerView
        dumpstateRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Set up button click listeners
        selectFileButton.setOnClickListener(v -> openFilePicker());
        uploadDumpstateButton.setOnClickListener(v -> uploadDumpstate());
        uploadSelectedFileButton.setOnClickListener(v -> uploadSelectedFile());
        toggleDumpstateListButton.setOnClickListener(v -> toggleDumpstateList());
        selectAllButton.setOnClickListener(v -> selectAllDumpstates());
        clearSelectionsButton.setOnClickListener(v -> clearDumpstateSelections());
        
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
    
    /**
     * Toggle the visibility of the dumpstate list
     */
    private void toggleDumpstateList() {
        isDumpstateListExpanded = !isDumpstateListExpanded;
        
        if (isDumpstateListExpanded) {
            // Show the list
            loadDumpstateFiles();
            dumpstateRecyclerView.setVisibility(View.VISIBLE);
            multiSelectButtonsLayout.setVisibility(View.VISIBLE);
            toggleDumpstateListButton.setText(R.string.hide_dumpstates);
            
            // Change the upload button to handle multiple files
            updateUploadButtonForMultiSelect();
        } else {
            // Hide the list
            dumpstateRecyclerView.setVisibility(View.GONE);
            multiSelectButtonsLayout.setVisibility(View.GONE);
            toggleDumpstateListButton.setText(R.string.show_dumpstates);
            
            // Restore normal upload button
            uploadDumpstateButton.setText(R.string.upload_dumpstate_button);
            uploadDumpstateButton.setOnClickListener(v -> uploadDumpstate());
        }
    }
    
    /**
     * Load dumpstate files into the RecyclerView
     */
    private void loadDumpstateFiles() {
        File[] dumpstateFiles = FileController.getAllDumpStateFiles();
        
        if (dumpstateFiles.length > 0) {
            List<File> fileList = new ArrayList<>(Arrays.asList(dumpstateFiles));
            
            if (dumpstateAdapter == null) {
                dumpstateAdapter = new DumpstateAdapter(this, fileList);
                dumpstateAdapter.setOnSelectionChangedListener(this::onDumpstateSelectionChanged);
                dumpstateRecyclerView.setAdapter(dumpstateAdapter);
            } else {
                // Just update the adapter with new files
                dumpstateAdapter = new DumpstateAdapter(this, fileList);
                dumpstateAdapter.setOnSelectionChangedListener(this::onDumpstateSelectionChanged);
                dumpstateRecyclerView.setAdapter(dumpstateAdapter);
            }
        }
    }
    
    /**
     * Handle selection changes in the dumpstate list
     * 
     * @param selectedCount The number of selected items
     */
    private void onDumpstateSelectionChanged(int selectedCount) {
        // Update the upload button text
        if (selectedCount > 0) {
            uploadDumpstateButton.setText(getString(R.string.upload_multiple_dumpstates, selectedCount));
            uploadDumpstateButton.setEnabled(true);
        } else {
            uploadDumpstateButton.setText(R.string.upload_dumpstate_button);
            uploadDumpstateButton.setEnabled(false);
        }
    }
    
    /**
     * Update the upload button to handle multiple files
     */
    private void updateUploadButtonForMultiSelect() {
        uploadDumpstateButton.setOnClickListener(v -> uploadSelectedDumpstates());
        
        // Initially disable until files are selected
        uploadDumpstateButton.setEnabled(dumpstateAdapter != null && dumpstateAdapter.getSelectedCount() > 0);
        
        // Update button text if there are already selections
        if (dumpstateAdapter != null && dumpstateAdapter.getSelectedCount() > 0) {
            uploadDumpstateButton.setText(getString(R.string.upload_multiple_dumpstates, 
                    dumpstateAdapter.getSelectedCount()));
        } else {
            uploadDumpstateButton.setText(R.string.upload_dumpstate_button);
        }
    }
    
    /**
     * Select all dumpstate files
     */
    private void selectAllDumpstates() {
        if (dumpstateAdapter != null) {
            dumpstateAdapter.selectAll();
        }
    }
    
    /**
     * Clear all dumpstate selections
     */
    private void clearDumpstateSelections() {
        if (dumpstateAdapter != null) {
            dumpstateAdapter.clearSelections();
        }
    }
    
    /**
     * Upload selected dumpstate files
     */
    private void uploadSelectedDumpstates() {
        if (dumpstateAdapter == null || dumpstateAdapter.getSelectedCount() == 0) {
            Toast.makeText(this, R.string.no_dumpstates_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        
        List<File> selectedFiles = dumpstateAdapter.getSelectedFiles();
        int fileCount = selectedFiles.size();
        
        // Show upload parameters dialog first
        new UploadParamsDialog(this, new UploadParamsDialog.UploadParamsListener() {
            @Override
            public void onUploadParamsSet(int maxDownloads, int expiryHours) {
                // Update UI
                progressBar.setVisibility(View.VISIBLE);
                uploadDumpstateButton.setEnabled(false);
                dumpstateStatusText.setText(getString(R.string.preparing_multiple_files, fileCount));
                
                // Prepare and upload multiple files
                FileController.prepareMultipleFilesForUploadAsync(selectedFiles, MainActivity.this)
                        .thenAccept(zipFile -> {
                            Log.d(TAG, "Multiple files zipped successfully: " + zipFile.getAbsolutePath());
                            
                            runOnUiThread(() -> {
                                dumpstateStatusText.setText(getString(R.string.uploading_multiple_files, fileCount));
                            });
                            
                            // Upload the zip file with parameters
                            startUploadService(zipFile, maxDownloads, expiryHours);
                        })
                        .exceptionally(ex -> {
                            Log.e(TAG, "Error preparing multiple files", ex);
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                uploadDumpstateButton.setEnabled(true);
                                Toast.makeText(MainActivity.this, 
                                        getString(R.string.file_error, ex.getMessage()), 
                                        Toast.LENGTH_LONG).show();
                            });
                            return null;
                        });
            }
            
            @Override
            public void onUploadParamsCancelled() {
                // User cancelled, do nothing
            }
        }).show();
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
        
        // Show upload parameters dialog
        showUploadParamsDialog(selectedFile);
    }
    
    /**
     * Upload the dumpstate file
     */
    private void uploadDumpstate() {
        // Get all dumpstate files
        File[] dumpstateFiles = FileController.getAllDumpStateFiles();
        
        if (dumpstateFiles.length == 0) {
            Toast.makeText(this, R.string.no_dumpstate_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (dumpstateFiles.length == 1) {
            // Only one dumpstate file, use it directly
            dumpstateFile = dumpstateFiles[0];
            showUploadParamsDialog(dumpstateFile);
        } else {
            // Multiple dumpstate files are available, suggest expanding the list
            if (!isDumpstateListExpanded) {
                toggleDumpstateList();
            } else {
                // If list is already expanded, use the first file
                dumpstateFile = dumpstateFiles[0];
                showUploadParamsDialog(dumpstateFile);
            }
        }
    }
    
    /**
     * Start uploading the selected dumpstate file
     * 
     * @param file The dumpstate file to upload
     * @param maxDownloads The maximum number of downloads
     * @param expiryHours The expiry time in hours
     */
    private void startDumpstateUpload(File file, int maxDownloads, int expiryHours) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.no_dumpstate_found, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update UI
        progressBar.setVisibility(View.VISIBLE);
        uploadDumpstateButton.setEnabled(false);
        dumpstateStatusText.setText(getString(R.string.uploading_file, file.getName()));
        
        // Start the upload service with parameters
        startUploadService(file, maxDownloads, expiryHours);
    }
    
    /**
     * Start the upload service with the given file and parameters
     * 
     * @param file The file to upload
     * @param maxDownloads The maximum number of downloads
     * @param expiryHours The expiry time in hours
     */
    private void startUploadService(File file, int maxDownloads, int expiryHours) {
        // Start the upload service
        Intent serviceIntent = new Intent(this, FileUploadForegroundService.class);
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_FILE_PATH, file.getAbsolutePath());
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_MAX_DOWNLOADS, maxDownloads);
        serviceIntent.putExtra(FileUploadForegroundService.EXTRA_EXPIRY_HOURS, expiryHours);
        startService(serviceIntent);
    }
    
    /**
     * Show the upload parameters dialog
     * 
     * @param file The file to upload
     */
    private void showUploadParamsDialog(File file) {
        new UploadParamsDialog(this, new UploadParamsDialog.UploadParamsListener() {
            @Override
            public void onUploadParamsSet(int maxDownloads, int expiryHours) {
                // Update UI
                progressBar.setVisibility(View.VISIBLE);
                uploadSelectedFileButton.setEnabled(false);
                
                if (file == selectedFile) {
                    selectedFileText.setText(getString(R.string.uploading_file, file.getName()));
                } else if (file == dumpstateFile) {
                    dumpstateStatusText.setText(getString(R.string.uploading_file, file.getName()));
                    uploadDumpstateButton.setEnabled(false);
                }
                
                // Start the upload service with parameters
                startUploadService(file, maxDownloads, expiryHours);
            }
            
            @Override
            public void onUploadParamsCancelled() {
                // User cancelled, do nothing
            }
        }).show();
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
        // Get all dumpstate files
        File[] dumpstateFiles = FileController.getAllDumpStateFiles();
        
        if (dumpstateFiles.length > 0) {
            if (dumpstateFiles.length == 1) {
                // Only one dumpstate file
                dumpstateFile = dumpstateFiles[0];
                dumpstateStatusText.setText(getString(R.string.dumpstate_found, dumpstateFile.getName()));
            } else {
                // Multiple dumpstate files
                dumpstateStatusText.setText(getString(R.string.multiple_dumpstates_found) + 
                                           " (" + dumpstateFiles.length + ")");
                                           
                // If we have a specific file selected, show it
                if (dumpstateFile != null && dumpstateFile.exists()) {
                    dumpstateStatusText.append("\n" + dumpstateFile.getName());
                } else {
                    // Otherwise show the most recent one
                    dumpstateFile = dumpstateFiles[0];
                    dumpstateStatusText.append("\n" + dumpstateFile.getName());
                }
            }
            uploadDumpstateButton.setEnabled(true);
            
            // Update multiselect if active
            if (isDumpstateListExpanded && dumpstateAdapter != null) {
                loadDumpstateFiles();
                updateUploadButtonForMultiSelect();
            }
        } else {
            dumpstateFile = null;
            dumpstateStatusText.setText(R.string.no_dumpstate_found);
            uploadDumpstateButton.setEnabled(false);
        }
        
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
        File[] dumpstateFiles = FileController.getAllDumpStateFiles();
        
        if (dumpstateFiles.length > 0) {
            if (dumpstateFiles.length == 1) {
                // Only one dumpstate file found, use it directly
                dumpstateFile = dumpstateFiles[0];
                dumpstateStatusText.setText(getString(R.string.dumpstate_found, dumpstateFile.getName()));
                uploadDumpstateButton.setEnabled(true);
            } else {
                // Multiple dumpstate files found
                dumpstateStatusText.setText(getString(R.string.multiple_dumpstates_found) + 
                                           " (" + dumpstateFiles.length + ")");
                
                // Set the most recent one as default
                dumpstateFile = dumpstateFiles[0];
                // Show the dumpstate name in a new line
                dumpstateStatusText.append("\n" + dumpstateFile.getName());
                uploadDumpstateButton.setEnabled(true);
                
                // Update toggle button visibility
                toggleDumpstateListButton.setVisibility(View.VISIBLE);
            }
        } else {
            dumpstateFile = null;
            dumpstateStatusText.setText(R.string.no_dumpstate_found);
            uploadDumpstateButton.setEnabled(false);
            toggleDumpstateListButton.setVisibility(View.GONE);
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
