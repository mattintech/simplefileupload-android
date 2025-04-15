package com.mattintech.simplelogupload.view;

import android.content.Intent;
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
 * Activity for server setup
 */
public class ServerSetupActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.APP_TAG + "ServerSetupActivity";
    
    private TextInputEditText serverAddressInput;
    private TextInputEditText serverPortInput;
    private TextInputEditText clientKeyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_setup);

        // Initialize UI components
        serverAddressInput = findViewById(R.id.serverAddressInput);
        serverPortInput = findViewById(R.id.serverPortInput);
        clientKeyInput = findViewById(R.id.clientKeyInput);
        Button finishSetupButton = findViewById(R.id.finishSetupButton);
        
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
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
