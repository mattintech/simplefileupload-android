
package com.mattintech.simplelogupload;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 101;
    private TextView resultView;
    private Button uploadButton;
    private File mostRecentDumpStateFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultView = findViewById(R.id.resultView);
        uploadButton = findViewById(R.id.uploadButton);


        // Check for All Files Access Permission
        requestNotificationPermission();
        if (!hasAllFilesAccessPermission()) {
            requestAllFilesAccessPermission();
        } else {
            checkForDumpStateAndUpdateUI();
        }

        uploadButton.setOnClickListener(v -> {
            if (mostRecentDumpStateFile != null) {
                //scheduleJob();
                startFileUpload();
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    private void checkForDumpStateAndUpdateUI() {
        // Find the most recent *DUMPSTATE*.zip file
        File dir = new File(Environment.getExternalStorageDirectory(), "log");
        mostRecentDumpStateFile = findMostRecentFile(dir);

        if (mostRecentDumpStateFile != null) {
            resultView.setText("Dumpstate found: " + mostRecentDumpStateFile.getName());
            uploadButton.setVisibility(View.VISIBLE); // Show the button
        } else {
            resultView.setText("No dumpstate file found.");
            uploadButton.setVisibility(View.GONE);  // Hide the button if no file found
        }
    }

    private File findMostRecentFile(File dir) {
        File[] files = dir.listFiles((d, name) -> name.matches("dumpState_.*\\.zip")); // Regex to match dumpstate
        return (files != null && files.length > 0) ? Arrays.stream(files)
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null) : null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (hasAllFilesAccessPermission()) {
                scheduleJob(); // Now you can proceed if permission is granted
            } else {
                Toast.makeText(this, "Permission not granted!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean hasAllFilesAccessPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void requestAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
            } catch (Exception e) {
                // Fallback in case the direct intent fails (should not normally happen)
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void scheduleJob() {
        ComponentName componentName = new ComponentName(this, FileUploadJobService.class);
        JobInfo info = new JobInfo.Builder(123, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();

        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        scheduler.schedule(info);
    }

    private void startFileUpload() {
        Intent serviceIntent = new Intent(this, FileUploadForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

    }

}
