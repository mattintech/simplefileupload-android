package com.mattintech.simplelogupload

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.mattintech.simplelogupload.navigation.AppNavigation
import com.mattintech.simplelogupload.navigation.Screen
import com.mattintech.simplelogupload.ui.theme.SimpleLogUploadTheme
import com.mattintech.simplelogupload.util.PermissionUtil
import com.mattintech.simplelogupload.util.PreferencesUtil

/**
 * Main Activity for the Compose-based UI
 * Replaces all previous XML-based Activities with a single Compose Activity
 */
class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for modern UI
        enableEdgeToEdge()

        // Determine start destination based on setup status
        val startDestination = getStartDestination()

        // Handle share intents if this was launched from external app
        val sharedFileUri = handleShareIntent(intent)

        setContent {
            SimpleLogUploadTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    AppNavigation(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle new share intents when activity is already running
        handleShareIntent(intent)
    }

    /**
     * Determine which screen to show first based on app configuration
     */
    private fun getStartDestination(): String {
        // Check if onboarding has been completed first
        if (!PreferencesUtil.isOnboardingCompleted(this)) {
            return Screen.Onboarding.route
        }

        // Then check if setup is complete
        return if (PreferencesUtil.isSetupCompleted(this) &&
            PreferencesUtil.areServerSettingsConfigured(this)) {
            Screen.Main.route
        } else {
            Screen.Welcome.route
        }
    }

    /**
     * Handle share intents from external apps
     *
     * @param intent The intent that launched this activity
     * @return Uri of shared file, or null if not a share intent
     */
    private fun handleShareIntent(intent: Intent?): Uri? {
        if (intent?.action == Intent.ACTION_SEND) {
            if (intent.type?.startsWith("text/") == true) {
                // Handle shared text
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                // TODO: Handle shared text if needed
                return null
            } else {
                // Handle shared file
                val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                // TODO: Pass this URI to MainScreen/MainViewModel for upload
                return sharedUri
            }
        } else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            // Handle multiple shared files
            val sharedUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            // TODO: Handle multiple files
            return sharedUris?.firstOrNull()
        }
        return null
    }
}
