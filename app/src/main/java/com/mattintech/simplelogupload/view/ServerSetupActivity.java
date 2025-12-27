package com.mattintech.simplelogupload.view;

import android.content.Intent;
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
 * Activity for server setup
 */
public class ServerSetupActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.APP_TAG + "ServerSetupActivity";

    private TextInputEditText serverAddressInput;
    private TextInputEditText serverPortInput;
    private TextInputEditText clientKeyInput;
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;
    private Button scanQrButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_setup);

        // Initialize UI components
        serverAddressInput = findViewById(R.id.serverAddressInput);
        serverPortInput = findViewById(R.id.serverPortInput);
        clientKeyInput = findViewById(R.id.clientKeyInput);
        Button finishSetupButton = findViewById(R.id.finishSetupButton);
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
            getSupportActionBar().setTitle("Server Setup");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Set up button click listener
        finishSetupButton.setOnClickListener(v -> saveSettings());
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
            PreferencesUtil.setSetupCompleted(this, true);
            
            Log.d(TAG, "Server setup completed successfully");
            Toast.makeText(this, "Setup completed successfully!", Toast.LENGTH_SHORT).show();
            
            // Navigate to main activity
            Intent intent = new Intent(ServerSetupActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            
        } catch (Exception e) {
            Log.e(TAG, "Error saving settings", e);
            Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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

                        Toast.makeText(ServerSetupActivity.this,
                                R.string.qr_scan_success,
                                Toast.LENGTH_SHORT).show();

                        Log.d(TAG, "QR code scanned successfully");
                    }

                    @Override
                    public void onQrScanCancelled() {
                        Toast.makeText(ServerSetupActivity.this,
                                R.string.qr_scan_cancelled,
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onQrScanError(String errorMessage) {
                        Toast.makeText(ServerSetupActivity.this,
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
