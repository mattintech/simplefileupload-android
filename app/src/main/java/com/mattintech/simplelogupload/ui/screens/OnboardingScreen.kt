package com.mattintech.simplelogupload.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.mattintech.simplelogupload.R
import com.mattintech.simplelogupload.model.QrServerConfig
import com.mattintech.simplelogupload.util.PermissionUtil
import com.mattintech.simplelogupload.util.PreferencesUtil
import com.mattintech.simplelogupload.util.QrScannerUtil
import kotlinx.coroutines.launch

/**
 * Onboarding screen with carousel explaining app and permissions
 *
 * @param onOnboardingComplete Callback when onboarding is complete
 */
@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    // Permission states
    var notificationPermissionGranted by remember {
        mutableStateOf(PermissionUtil.hasNotificationPermission(context))
    }
    var filesPermissionGranted by remember {
        mutableStateOf(PermissionUtil.hasAllFilesAccessPermission())
    }

    // Server configuration states
    var serverAddress by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var clientKey by remember { mutableStateOf("") }
    var serverAddressError by remember { mutableStateOf<String?>(null) }
    var serverPortError by remember { mutableStateOf<String?>(null) }
    var clientKeyError by remember { mutableStateOf<String?>(null) }

    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionGranted = isGranted
        if (isGranted) {
            // Auto-advance to next page after permission granted
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        } else {
            Toast.makeText(
                context,
                "Notification permission helps you track upload progress",
                Toast.LENGTH_SHORT
            ).show()
            // Still allow advancing even if denied
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    // Files access launcher
    val filesPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        filesPermissionGranted = PermissionUtil.hasAllFilesAccessPermission()
        if (filesPermissionGranted) {
            // Auto-advance to server setup page
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        } else {
            Toast.makeText(
                context,
                "File access is needed to upload files from your device",
                Toast.LENGTH_SHORT
            ).show()
            // Still allow advancing even if denied
            scope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage + 1)
            }
        }
    }

    // QR scanner launcher
    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ScanContract()
    ) { result ->
        QrScannerUtil.processScanResult(
            result.contents,
            object : QrScannerUtil.QrScanCallback {
                override fun onQrScanned(config: QrServerConfig) {
                    serverAddress = config.server
                    serverPort = config.port.toString()
                    clientKey = config.key
                    Toast.makeText(
                        context,
                        R.string.qr_scan_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onQrScanCancelled() {
                    Toast.makeText(
                        context,
                        R.string.qr_scan_cancelled,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onQrScanError(errorMessage: String) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.qr_scan_error, errorMessage),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    // Camera permission launcher (for QR scanning)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val options = QrScannerUtil.createScanOptions()
            qrScannerLauncher.launch(options)
        } else {
            Toast.makeText(
                context,
                R.string.camera_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false // Disable manual scrolling
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    onContinue = {
                        scope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    }
                )
                1 -> NotificationPermissionPage(
                    isGranted = notificationPermissionGranted,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // On older versions, permission is granted by default
                            notificationPermissionGranted = true
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
                2 -> FilesPermissionPage(
                    isGranted = filesPermissionGranted,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.data = Uri.parse("package:" + context.packageName)
                                filesPermissionLauncher.launch(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                filesPermissionLauncher.launch(intent)
                            }
                        } else {
                            // On older versions, permission is granted by default
                            filesPermissionGranted = true
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
                3 -> ServerSetupPage(
                    serverAddress = serverAddress,
                    serverPort = serverPort,
                    clientKey = clientKey,
                    serverAddressError = serverAddressError,
                    serverPortError = serverPortError,
                    clientKeyError = clientKeyError,
                    onServerAddressChange = {
                        serverAddress = it
                        serverAddressError = null
                    },
                    onServerPortChange = {
                        serverPort = it
                        serverPortError = null
                    },
                    onClientKeyChange = {
                        clientKey = it
                        clientKeyError = null
                    },
                    onScanQr = {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onContinue = {
                        // Validate server settings
                        var hasError = false

                        if (serverAddress.isBlank()) {
                            serverAddressError = "Server address is required"
                            hasError = true
                        }

                        if (serverPort.isBlank()) {
                            serverPortError = "Server port is required"
                            hasError = true
                        } else {
                            val port = serverPort.toIntOrNull()
                            if (port == null || port <= 0 || port > 65535) {
                                serverPortError = "Port must be between 1 and 65535"
                                hasError = true
                            }
                        }

                        if (clientKey.isBlank()) {
                            clientKeyError = "Client key is required"
                            hasError = true
                        }

                        if (!hasError) {
                            // Save settings
                            PreferencesUtil.setServerAddress(context, serverAddress)
                            PreferencesUtil.setServerPort(context, serverPort.toInt())
                            PreferencesUtil.setClientKey(context, clientKey)
                            PreferencesUtil.setSetupCompleted(context, true)

                            // Advance to final page
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
                4 -> ReadyPage(
                    onGetStarted = {
                        // Mark onboarding as complete
                        PreferencesUtil.setOnboardingCompleted(context, true)
                        onOnboardingComplete()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Page indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(5) { iteration ->
                val color = if (pagerState.currentPage == iteration) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(8.dp)
                        .background(color, shape = MaterialTheme.shapes.small)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Welcome page explaining the app
 */
@Composable
private fun WelcomePage(
    onContinue: () -> Unit
) {
    OnboardingPageTemplate(
        icon = Icons.Default.CloudUpload,
        title = "Welcome to SimpleLogUpload",
        description = "A simple and secure way to upload files to your server.\n\n" +
                "This app requires a backend server to function. Your administrator should have " +
                "provided you with server details.\n\n" +
                "Let's get your app set up!",
        buttonText = "Continue",
        onButtonClick = onContinue
    )
}

/**
 * Notification permission page
 */
@Composable
private fun NotificationPermissionPage(
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    OnboardingPageTemplate(
        icon = Icons.Default.Notifications,
        title = "Upload Progress Notifications",
        description = "SimpleLogUpload needs notification permission to:\n\n" +
                "• Show upload progress in real-time\n" +
                "• Display upload completion status\n" +
                "• Keep you informed about file transfers\n\n" +
                (if (isGranted) "✓ Permission granted!" else "This helps you track uploads even when the app is in the background."),
        buttonText = if (isGranted) "Continue" else "Grant Permission",
        onButtonClick = onRequestPermission,
        isPermissionGranted = isGranted
    )
}

/**
 * Files permission page
 */
@Composable
private fun FilesPermissionPage(
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    OnboardingPageTemplate(
        icon = Icons.Default.Folder,
        title = "File Access Permission",
        description = "SimpleLogUpload needs access to your files to:\n\n" +
                "• Browse and select files to upload\n" +
                "• Access dumpstate files (on Samsung devices)\n" +
                "• Read files from any location on your device\n\n" +
                (if (isGranted) "✓ Permission granted!" else "You'll be taken to Settings to grant this permission."),
        buttonText = if (isGranted) "Continue" else "Grant Permission",
        onButtonClick = onRequestPermission,
        isPermissionGranted = isGranted
    )
}

/**
 * Server setup page
 */
@Composable
private fun ServerSetupPage(
    serverAddress: String,
    serverPort: String,
    clientKey: String,
    serverAddressError: String?,
    serverPortError: String?,
    clientKeyError: String?,
    onServerAddressChange: (String) -> Unit,
    onServerPortChange: (String) -> Unit,
    onClientKeyChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Server Configuration",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Enter your server details or scan a QR code from your admin panel.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Server Address
            OutlinedTextField(
                value = serverAddress,
                onValueChange = onServerAddressChange,
                label = { Text("Server Address") },
                placeholder = { Text("e.g., 192.168.1.100") },
                modifier = Modifier.fillMaxWidth(),
                isError = serverAddressError != null,
                supportingText = serverAddressError?.let { { Text(it) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Server Port
            OutlinedTextField(
                value = serverPort,
                onValueChange = onServerPortChange,
                label = { Text("Server Port") },
                placeholder = { Text("e.g., 8080") },
                modifier = Modifier.fillMaxWidth(),
                isError = serverPortError != null,
                supportingText = serverPortError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Client Key
            OutlinedTextField(
                value = clientKey,
                onValueChange = onClientKeyChange,
                label = { Text("Client Key") },
                placeholder = { Text("Authentication key") },
                modifier = Modifier.fillMaxWidth(),
                isError = clientKeyError != null,
                supportingText = clientKeyError?.let { { Text(it) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // QR Scan Button
            OutlinedButton(
                onClick = onScanQr,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code")
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "Continue",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

/**
 * Ready page - final page
 */
@Composable
private fun ReadyPage(
    onGetStarted: () -> Unit
) {
    OnboardingPageTemplate(
        icon = Icons.Default.Check,
        title = "You're All Set!",
        description = "Your app is now configured and ready to use!\n\n" +
                "✓ Permissions granted\n" +
                "✓ Server configured\n\n" +
                "You can now upload files to your server. " +
                "Tap the button below to start using SimpleLogUpload!",
        buttonText = "Start Using App",
        onButtonClick = onGetStarted
    )
}

/**
 * Reusable template for onboarding pages
 */
@Composable
private fun OnboardingPageTemplate(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    isPermissionGranted: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onButtonClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = buttonText,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
