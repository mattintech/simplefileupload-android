package com.mattintech.simplelogupload.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.mattintech.simplelogupload.R
import com.mattintech.simplelogupload.model.QrServerConfig
import com.mattintech.simplelogupload.util.PreferencesUtil
import com.mattintech.simplelogupload.util.QrScannerUtil

/**
 * Server setup screen for configuring server connection
 *
 * @param onSetupComplete Callback when setup is successfully completed
 * @param onNavigateBack Callback when back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    onSetupComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var serverAddress by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("") }
    var clientKey by remember { mutableStateOf("") }

    var serverAddressError by remember { mutableStateOf<String?>(null) }
    var serverPortError by remember { mutableStateOf<String?>(null) }
    var clientKeyError by remember { mutableStateOf<String?>(null) }

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

    // Camera permission launcher
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

    fun validateAndSave() {
        // Clear previous errors
        serverAddressError = null
        serverPortError = null
        clientKeyError = null

        var hasError = false

        // Validate server address
        if (serverAddress.isBlank()) {
            serverAddressError = "Server address is required"
            hasError = true
        }

        // Validate server port
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

        // Validate client key
        if (clientKey.isBlank()) {
            clientKeyError = "Client key is required"
            hasError = true
        }

        if (hasError) return

        // Save settings
        PreferencesUtil.setServerAddress(context, serverAddress)
        PreferencesUtil.setServerPort(context, serverPort.toInt())
        PreferencesUtil.setClientKey(context, clientKey)
        PreferencesUtil.setSetupCompleted(context, true)

        Toast.makeText(context, "Setup completed successfully!", Toast.LENGTH_SHORT).show()
        onSetupComplete()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Setup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Navigate back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Instructions card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "Enter your server connection details below, or scan a QR code from your server admin panel to auto-fill.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server Address
            OutlinedTextField(
                value = serverAddress,
                onValueChange = {
                    serverAddress = it
                    serverAddressError = null
                },
                label = { Text("Server Address") },
                placeholder = { Text("e.g., 192.168.1.100 or example.com") },
                modifier = Modifier.fillMaxWidth(),
                isError = serverAddressError != null,
                supportingText = serverAddressError?.let { { Text(it) } },
                singleLine = true
            )

            // Server Port
            OutlinedTextField(
                value = serverPort,
                onValueChange = {
                    serverPort = it
                    serverPortError = null
                },
                label = { Text("Server Port") },
                placeholder = { Text("e.g., 8080") },
                modifier = Modifier.fillMaxWidth(),
                isError = serverPortError != null,
                supportingText = serverPortError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Client Key
            OutlinedTextField(
                value = clientKey,
                onValueChange = {
                    clientKey = it
                    clientKeyError = null
                },
                label = { Text("Client Key") },
                placeholder = { Text("Authentication key") },
                modifier = Modifier.fillMaxWidth(),
                isError = clientKeyError != null,
                supportingText = clientKeyError?.let { { Text(it) } },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // QR Scan Button
            OutlinedButton(
                onClick = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Finish Setup Button
            Button(
                onClick = { validateAndSave() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Finish Setup",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
