package com.mattintech.simplelogupload.view;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.mattintech.simplelogupload.R;

/**
 * Welcome activity for first-time setup
 */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Hide action bar for cleaner look
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Set up next button
        Button nextButton = findViewById(R.id.nextButton);
        nextButton.setOnClickListener(v -> {
            // Proceed to server setup activity
            Intent intent = new Intent(WelcomeActivity.this, ServerSetupActivity.class);
            startActivity(intent);
            // No finish() here to allow back navigation
        });
    }
}
