package com.mattintech.simplelogupload;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.mattintech.simplelogupload.view.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for MainActivity
 */
@RunWith(AndroidJUnit4.class)
@Config(sdk = 30)
public class MainActivityTest {

    private ActivityScenario<MainActivity> activityScenario;

    @Before
    public void setUp() {
        activityScenario = ActivityScenario.launch(MainActivity.class);
    }

    @After
    public void tearDown() {
        if (activityScenario != null) {
            activityScenario.close();
        }
    }

    @Test
    public void testMainActivityUIComponents() {
        activityScenario.onActivity(activity -> {
            // Verify UI components exist
            assertNotNull(activity.findViewById(R.id.headerText));
            assertNotNull(activity.findViewById(R.id.uploadOptionsCard));
            assertNotNull(activity.findViewById(R.id.dumpstateCard));
            assertNotNull(activity.findViewById(R.id.selectFileButton));
            assertNotNull(activity.findViewById(R.id.uploadDumpstateButton));
            
            // Verify initial state
            TextView dumpstateStatusText = activity.findViewById(R.id.dumpstateStatusText);
            assertNotNull(dumpstateStatusText);
            
            // Selected file card should be hidden initially
            CardView selectedFileCard = activity.findViewById(R.id.selectedFileCard);
            assertEquals(View.GONE, selectedFileCard.getVisibility());
        });
    }

    @Test
    public void testFileSelectionUI() {
        activityScenario.onActivity(activity -> {
            // Simulate file selection
            TextView selectedFileText = activity.findViewById(R.id.selectedFileText);
            Button uploadSelectedFileButton = activity.findViewById(R.id.uploadSelectedFileButton);
            CardView selectedFileCard = activity.findViewById(R.id.selectedFileCard);
            
            // Initially hidden
            assertEquals(View.GONE, selectedFileCard.getVisibility());
            assertEquals(View.GONE, uploadSelectedFileButton.getVisibility());
            
            // After selection (would require more complex testing with Intents)
            // This is just to verify the UI logic
        });
    }
}
