package com.mattintech.simplelogupload;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class UploadResultDialog extends Dialog {
    private String uploadCode;

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
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Upload Code", uploadCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Code copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }
}
