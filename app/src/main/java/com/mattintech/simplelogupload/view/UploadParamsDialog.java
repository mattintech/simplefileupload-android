package com.mattintech.simplelogupload.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.NetworkConstants;

/**
 * Dialog for setting upload parameters
 */
public class UploadParamsDialog extends Dialog {
    
    private EditText maxDownloadsInput;
    private EditText expiryTimeInput;
    private final UploadParamsListener listener;
    
    /**
     * Interface for upload parameter callbacks
     */
    public interface UploadParamsListener {
        void onUploadParamsSet(int maxDownloads, int expiryHours);
        void onUploadParamsCancelled();
    }
    
    /**
     * Constructor
     * 
     * @param context The context
     * @param listener Callback listener
     */
    public UploadParamsDialog(@NonNull Context context, UploadParamsListener listener) {
        super(context);
        this.listener = listener;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_upload_params);
        
        // Initialize views
        maxDownloadsInput = findViewById(R.id.maxDownloadsInput);
        expiryTimeInput = findViewById(R.id.expiryTimeInput);
        
        // Set default values as hints
        maxDownloadsInput.setHint("Default: " + NetworkConstants.DEFAULT_MAX_DOWNLOADS);
        expiryTimeInput.setHint("Default: " + NetworkConstants.DEFAULT_EXPIRY_HOURS);
        
        // Set up buttons
        Button confirmButton = findViewById(R.id.confirmButton);
        Button cancelButton = findViewById(R.id.cancelButton);
        
        confirmButton.setOnClickListener(v -> {
            int maxDownloads = getMaxDownloadsValue();
            int expiryHours = getExpiryHoursValue();
            
            // Validate expiry time
            if (expiryHours > NetworkConstants.MAX_EXPIRY_HOURS) {
                expiryTimeInput.setError("Maximum expiry time is " + 
                        NetworkConstants.MAX_EXPIRY_HOURS + " hours");
                return;
            }
            
            // Validate max downloads (must be non-negative)
            if (maxDownloads < 0) {
                maxDownloadsInput.setError("Downloads must be 0 or greater");
                return;
            }
            
            if (listener != null) {
                listener.onUploadParamsSet(maxDownloads, expiryHours);
            }
            dismiss();
        });
        
        cancelButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUploadParamsCancelled();
            }
            dismiss();
        });
    }
    
    /**
     * Get the max downloads value from the input
     * 
     * @return The max downloads value (default if not set)
     */
    private int getMaxDownloadsValue() {
        if (TextUtils.isEmpty(maxDownloadsInput.getText())) {
            return NetworkConstants.DEFAULT_MAX_DOWNLOADS;
        }
        
        try {
            return Integer.parseInt(maxDownloadsInput.getText().toString());
        } catch (NumberFormatException e) {
            return NetworkConstants.DEFAULT_MAX_DOWNLOADS;
        }
    }
    
    /**
     * Get the expiry hours value from the input
     * 
     * @return The expiry hours value (default if not set)
     */
    private int getExpiryHoursValue() {
        if (TextUtils.isEmpty(expiryTimeInput.getText())) {
            return NetworkConstants.DEFAULT_EXPIRY_HOURS;
        }
        
        try {
            return Integer.parseInt(expiryTimeInput.getText().toString());
        } catch (NumberFormatException e) {
            return NetworkConstants.DEFAULT_EXPIRY_HOURS;
        }
    }
}
