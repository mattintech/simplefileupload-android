package com.mattintech.simplelogupload.view;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.mattintech.simplelogupload.R;
import com.mattintech.simplelogupload.constant.AppConstants;

/**
 * Dialog to display upload result
 */
public class UploadResultDialog extends Dialog {
    private static final String TAG = AppConstants.APP_TAG + "ResultDialog";
    
    private final String uploadCode;

    /**
     * Constructor
     * 
     * @param context The context
     * @param uploadCode The upload code
     */
    public UploadResultDialog(Context context, String uploadCode) {
        super(context);
        this.uploadCode = uploadCode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.upload_result_dialog);

        TextView codeTextView = findViewById(R.id.uploadCode);
        codeTextView.setText(uploadCode);

        Button copyButton = findViewById(R.id.copyButton);
        copyButton.setOnClickListener(v -> copyCodeToClipboard());
        
        Button closeButton = findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss());
    }
    
    /**
     * Copy the upload code to the clipboard
     */
    private void copyCodeToClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) 
                    getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            
            ClipData clip = ClipData.newPlainText("Upload Code", uploadCode);
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(getContext(), "Code copied to clipboard", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error copying to clipboard", e);
            Toast.makeText(getContext(), "Failed to copy code", Toast.LENGTH_SHORT).show();
        }
    }
}
