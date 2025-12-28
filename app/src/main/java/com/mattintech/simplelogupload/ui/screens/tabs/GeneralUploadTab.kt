package com.mattintech.simplelogupload.ui.screens.tabs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mattintech.simplelogupload.ui.components.UploadParamsDialog
import com.mattintech.simplelogupload.ui.components.UploadResultDialog
import com.mattintech.simplelogupload.util.PermissionUtil
import com.mattintech.simplelogupload.util.PreferencesUtil
import com.mattintech.simplelogupload.viewmodel.MainViewModel

/**
 * General file upload tab
 * Allows uploading any file from device storage
 *
 * @param modifier Modifier for the composable
 * @param viewModel MainViewModel instance
 */
@Composable
fun GeneralUploadTab(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity
    val uiState by viewModel.uiState.collectAsState()

    // State to track pending upload parameters
    var pendingUpload by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with pending upload
            pendingUpload?.let { (maxDownloads, expiryHours) ->
                uiState.selectedFile?.let { file ->
                    viewModel.uploadFile(file, maxDownloads, expiryHours)
                }
            }
            pendingUpload = null
        } else {
            Toast.makeText(
                context,
                "Notification permission is required to show upload progress",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.handleFileSelection(it)
        }
    }

    // Settings launcher for when user returns from granting all files access
    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Check if permission was granted after returning from settings
        if (PermissionUtil.hasAllFilesAccessPermission()) {
            filePickerLauncher.launch("*/*")
        } else {
            Toast.makeText(
                context,
                "All files access permission is required to select files",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Helper function to check permission and upload
    val uploadWithPermissionCheck: (Int, Int) -> Unit = { maxDownloads, expiryHours ->
        if (PermissionUtil.hasNotificationPermission(context)) {
            // Permission already granted, proceed with upload
            uiState.selectedFile?.let { file ->
                viewModel.uploadFile(file, maxDownloads, expiryHours)
            }
        } else {
            // Need to request permission first
            pendingUpload = Pair(maxDownloads, expiryHours)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Show error toast
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Instructions card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Upload Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a file from your device to upload to your server. You'll receive a code to share for downloading the file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Select file button
        Button(
            onClick = {
                if (PermissionUtil.hasAllFilesAccessPermission()) {
                    filePickerLauncher.launch("*/*")
                } else {
                    // Request all files access permission by opening settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:" + context.packageName)
                            settingsLauncher.launch(intent)
                        } catch (e: Exception) {
                            // Fallback to general all files access settings
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            settingsLauncher.launch(intent)
                        }
                    } else {
                        // On older Android versions, just launch file picker
                        filePickerLauncher.launch("*/*")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Icon(
                Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select File from Storage",
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // Selected file info
        uiState.selectedFileName?.let { fileName ->
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Selected File",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (PreferencesUtil.getAskBeforeUpload(context)) {
                                viewModel.showUploadParamsDialog()
                            } else {
                                val maxDownloads = PreferencesUtil.getDefaultMaxDownloads(context)
                                val expiryHours = PreferencesUtil.getDefaultExpiryHours(context)
                                uploadWithPermissionCheck(maxDownloads, expiryHours)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Upload File")
                    }
                }
            }
        }

    }

    // Upload params dialog
    if (uiState.showUploadParamsDialog) {
        UploadParamsDialog(
            onDismiss = { viewModel.hideUploadParamsDialog() },
            onConfirm = { maxDownloads, expiryHours ->
                uploadWithPermissionCheck(maxDownloads, expiryHours)
            }
        )
    }

    // Upload result dialog
    if (uiState.showUploadResultDialog) {
        uiState.uploadResult?.let { result ->
            UploadResultDialog(
                uploadCode = result.code,
                onDismiss = { viewModel.hideUploadResultDialog() }
            )
        }
    }
}
