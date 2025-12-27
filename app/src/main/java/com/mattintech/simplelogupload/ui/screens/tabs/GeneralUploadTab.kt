package com.mattintech.simplelogupload.ui.screens.tabs

import android.Manifest
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
    val uiState by viewModel.uiState.collectAsState()

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.handleFileSelection(it)
        }
    }

    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            filePickerLauncher.launch("*/*")
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to select files",
                Toast.LENGTH_LONG
            ).show()
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
                storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                        onClick = { viewModel.showUploadParamsDialog() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Upload File")
                    }
                }
            }
        }

        // Loading indicator
        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Uploading...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Upload params dialog
    if (uiState.showUploadParamsDialog) {
        UploadParamsDialog(
            onDismiss = { viewModel.hideUploadParamsDialog() },
            onConfirm = { maxDownloads, expiryHours ->
                uiState.selectedFile?.let { file ->
                    viewModel.uploadFile(file, maxDownloads, expiryHours)
                }
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
