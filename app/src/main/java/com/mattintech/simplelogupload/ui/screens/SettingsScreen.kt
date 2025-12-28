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
 * Settings screen for modifying server configuration
 *
 * @param onNavigateBack Callback when back button is pressed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    // Load existing settings
    var serverAddress by remember {
        mutableStateOf(PreferencesUtil.getServerAddress(context))
    }
    var serverPort by remember {
        mutableStateOf(PreferencesUtil.getServerPort(context).toString())
    }
    var clientKey by remember {
        mutableStateOf(PreferencesUtil.getClientKey(context))
    }
    var defaultMaxDownloads by remember {
        mutableStateOf(PreferencesUtil.getDefaultMaxDownloads(context).toString())
    }
    var defaultExpiryHours by remember {
        mutableStateOf(PreferencesUtil.getDefaultExpiryHours(context).toString())
    }
    var askBeforeUpload by remember {
        mutableStateOf(PreferencesUtil.getAskBeforeUpload(context))
    }

    var serverAddressError by remember { mutableStateOf<String?>(null) }
    var serverPortError by remember { mutableStateOf<String?>(null) }
    var clientKeyError by remember { mutableStateOf<String?>(null) }
    var maxDownloadsError by remember { mutableStateOf<String?>(null) }
    var expiryHoursError by remember { mutableStateOf<String?>(null) }

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
        maxDownloadsError = null
        expiryHoursError = null

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

        // Validate max downloads
        val maxDl = defaultMaxDownloads.toIntOrNull()
        if (maxDl == null || maxDl < 0) {
            maxDownloadsError = "Must be 0 or greater (0 = unlimited)"
            hasError = true
        }

        // Validate expiry hours
        val expiry = defaultExpiryHours.toIntOrNull()
        if (expiry == null || expiry <= 0 || expiry > 24) {
            expiryHoursError = "Must be between 1 and 24 hours"
            hasError = true
        }

        if (hasError) return

        // Save settings
        PreferencesUtil.setServerAddress(context, serverAddress)
        PreferencesUtil.setServerPort(context, serverPort.toInt())
        PreferencesUtil.setClientKey(context, clientKey)
        PreferencesUtil.setDefaultMaxDownloads(context, maxDl!!)
        PreferencesUtil.setDefaultExpiryHours(context, expiry!!)
        PreferencesUtil.setAskBeforeUpload(context, askBeforeUpload)

        Toast.makeText(context, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        onNavigateBack()
    }

    fun resetSettings() {
        // Reload original values
        serverAddress = PreferencesUtil.getServerAddress(context)
        serverPort = PreferencesUtil.getServerPort(context).toString()
        clientKey = PreferencesUtil.getClientKey(context)
        defaultMaxDownloads = PreferencesUtil.getDefaultMaxDownloads(context).toString()
        defaultExpiryHours = PreferencesUtil.getDefaultExpiryHours(context).toString()
        askBeforeUpload = PreferencesUtil.getAskBeforeUpload(context)

        // Clear errors
        serverAddressError = null
        serverPortError = null
        clientKeyError = null
        maxDownloadsError = null
        expiryHoursError = null

        Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            Text(
                text = "Server Configuration",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

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

            Spacer(modifier = Modifier.height(16.dp))

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
                Text("Scan QR Code to Auto-Fill")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Upload Defaults Section
            Text(
                text = "Upload Defaults",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Default Max Downloads
            OutlinedTextField(
                value = defaultMaxDownloads,
                onValueChange = {
                    defaultMaxDownloads = it
                    maxDownloadsError = null
                },
                label = { Text("Default Maximum Downloads") },
                placeholder = { Text("5") },
                modifier = Modifier.fillMaxWidth(),
                isError = maxDownloadsError != null,
                supportingText = {
                    Text(maxDownloadsError ?: "Set to 0 for unlimited downloads")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Default Expiry Hours
            OutlinedTextField(
                value = defaultExpiryHours,
                onValueChange = {
                    defaultExpiryHours = it
                    expiryHoursError = null
                },
                label = { Text("Default Expiry Time (hours)") },
                placeholder = { Text("24") },
                modifier = Modifier.fillMaxWidth(),
                isError = expiryHoursError != null,
                supportingText = {
                    Text(expiryHoursError ?: "Maximum 24 hours (1 day)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Ask Before Upload Switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ask Before Upload",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (askBeforeUpload) "Show upload parameters dialog" else "Use default settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = askBeforeUpload,
                        onCheckedChange = { askBeforeUpload = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reset Button
                OutlinedButton(
                    onClick = { resetSettings() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }

                // Save Button
                Button(
                    onClick = { validateAndSave() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
