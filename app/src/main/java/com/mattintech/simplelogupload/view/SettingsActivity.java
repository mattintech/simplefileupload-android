package com.mattintech.simplelogupload.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.AppConstants;
import com.mattintech.simplelogupload.util.PreferencesUtil;

/**
 * Activity for application settings
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.APP_TAG + "SettingsActivity";
    
    private TextInputEditText serverAddressInput;
    private TextInputEditText serverPortInput;
    private TextInputEditText clientKeyInput;
    
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
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
