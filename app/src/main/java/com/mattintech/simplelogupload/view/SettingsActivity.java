package com.mattintech.simplelogupload.view;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.model.QrServerConfig;
import com.mattintech.simplelogupload.util.PermissionUtil;
import com.mattintech.simplelogupload.util.PreferencesUtil;
import com.mattintech.simplelogupload.util.QrScannerUtil;

/**
 * Activity for application settings
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.APP_TAG + "SettingsActivity";

    private TextInputEditText serverAddressInput;
    private TextInputEditText serverPortInput;
    private TextInputEditText clientKeyInput;
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;
    private Button scanQrButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Initialize UI components
        serverAddressInput = findViewById(R.id.serverAddressInput);
        serverPortInput = findViewById(R.id.serverPortInput);
        clientKeyInput = findViewById(R.id.clientKeyInput);
        Button saveButton = findViewById(R.id.saveSettingsButton);
        Button resetButton = findViewById(R.id.resetSettingsButton);
        scanQrButton = findViewById(R.id.scanQrButton);

        // Initialize QR scanner launcher
        qrScannerLauncher = registerForActivityResult(
                new ScanContract(),
                result -> handleQrScanResult(result)
        );

        // Set up QR scan button click listener
        scanQrButton.setOnClickListener(v -> initiateQrScan());

        // Set up action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        
        // Load current settings
        loadSettings();
        
        // Set up button click listeners
        saveButton.setOnClickListener(v -> saveSettings());
        resetButton.setOnClickListener(v -> resetSettings());
    }
    
    /**
     * Load current settings from SharedPreferences
     */
    private void loadSettings() {
        String serverAddress = PreferencesUtil.getServerAddress(this);
        int serverPort = PreferencesUtil.getServerPort(this);
        String clientKey = PreferencesUtil.getClientKey(this);
        
        serverAddressInput.setText(serverAddress);
        if (serverPort > 0) {
            serverPortInput.setText(String.valueOf(serverPort));
        } else {
            serverPortInput.setText("");
        }
        clientKeyInput.setText(clientKey);
    }
    
    /**
     * Save settings to SharedPreferences
     */
    private void saveSettings() {
        try {
            // Get values from input fields
            String serverAddress = serverAddressInput.getText().toString().trim();
            String serverPortStr = serverPortInput.getText().toString().trim();
            String clientKey = clientKeyInput.getText().toString().trim();
            
            // Validate input
            if (TextUtils.isEmpty(serverAddress)) {
                serverAddressInput.setError("Server address is required");
                return;
            }
            
            if (TextUtils.isEmpty(serverPortStr)) {
                serverPortInput.setError("Server port is required");
                return;
            }
            
            int serverPort;
            try {
                serverPort = Integer.parseInt(serverPortStr);
                if (serverPort <= 0 || serverPort > 65535) {
                    serverPortInput.setError("Port must be between 1 and 65535");
                    return;
                }
            } catch (NumberFormatException e) {
                serverPortInput.setError("Invalid port number");
                return;
            }
            
            if (TextUtils.isEmpty(clientKey)) {
                clientKeyInput.setError("Client key is required");
                return;
            }
            
            // Save settings
            PreferencesUtil.setServerAddress(this, serverAddress);
            PreferencesUtil.setServerPort(this, serverPort);
            PreferencesUtil.setClientKey(this, clientKey);
            
            Log.d(TAG, "Settings saved successfully");
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show();
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving settings", e);
            Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Reset settings to default values
     */
    private void resetSettings() {
        PreferencesUtil.resetToDefaults(this);
        loadSettings();
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show();
    }

    /**
     * Initiate QR code scan
     */
    private void initiateQrScan() {
        // Check camera permission
        if (!PermissionUtil.hasCameraPermission(this)) {
            PermissionUtil.requestCameraPermission(this);
            return;
        }

        // Launch scanner
        ScanOptions options = QrScannerUtil.createScanOptions();
        qrScannerLauncher.launch(options);
    }

    /**
     * Handle QR scan result
     *
     * @param result The scan result
     */
    private void handleQrScanResult(ScanIntentResult result) {
        QrScannerUtil.processScanResult(
                result.getContents(),
                new QrScannerUtil.QrScanCallback() {
                    @Override
                    public void onQrScanned(QrServerConfig config) {
                        // Auto-fill the input fields
                        serverAddressInput.setText(config.getServer());
                        serverPortInput.setText(String.valueOf(config.getPort()));
                        clientKeyInput.setText(config.getKey());

                        Toast.makeText(SettingsActivity.this,
                                R.string.qr_scan_success,
                                Toast.LENGTH_SHORT).show();

                        Log.d(TAG, "QR code scanned successfully");
                    }

                    @Override
                    public void onQrScanCancelled() {
                        Toast.makeText(SettingsActivity.this,
                                R.string.qr_scan_cancelled,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onQrScanError(String errorMessage) {
                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.qr_scan_error, errorMessage),
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, "QR scan error: " + errorMessage);
                    }
                }
        );
    }

    /**
     * Handle permission request results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionUtil.REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initiate scan
                initiateQrScan();
            } else {
                // Permission denied
                Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
