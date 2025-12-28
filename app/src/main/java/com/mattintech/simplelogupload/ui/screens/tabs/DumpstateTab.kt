package com.mattintech.simplelogupload.ui.screens.tabs

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mattintech.simplelogupload.ui.components.UploadParamsDialog
import com.mattintech.simplelogupload.ui.components.UploadResultDialog
import com.mattintech.simplelogupload.util.PreferencesUtil
import com.mattintech.simplelogupload.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dumpstate upload tab for Samsung devices
 * Shows detected dumpstate files with multi-select
 *
 * @param modifier Modifier for the composable
 * @param viewModel MainViewModel instance
 */
@Composable
fun DumpstateTab(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

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
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Samsung Dumpstate Files",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        uiState.dumpstateFiles.isEmpty() -> "No dumpstate files found on this device"
                        uiState.dumpstateFiles.size == 1 -> "1 dumpstate file found"
                        else -> "${uiState.dumpstateFiles.size} dumpstate files found"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.dumpstateFiles.isEmpty() -> {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No dumpstate files detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Generate a dumpstate file from your device settings",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadDumpstateFiles() }) {
                            Text("Refresh")
                        }
                    }
                }
            }
            else -> {
                // Selection controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { viewModel.selectAllDumpstates() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { viewModel.clearDumpstateSelections() }) {
                        Text("Clear All")
                    }
                }

                // File list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.dumpstateFiles) { index, file ->
                        DumpstateFileItem(
                            file = file,
                            isSelected = uiState.selectedDumpstateIndices.contains(index),
                            onToggle = { viewModel.toggleDumpstateSelection(index) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Upload button
                Button(
                    onClick = {
                        if (PreferencesUtil.getAskBeforeUpload(context)) {
                            viewModel.showUploadParamsDialog()
                        } else {
                            val maxDownloads = PreferencesUtil.getDefaultMaxDownloads(context)
                            val expiryHours = PreferencesUtil.getDefaultExpiryHours(context)
                            viewModel.uploadSelectedDumpstates(maxDownloads, expiryHours)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && uiState.selectedDumpstateIndices.isNotEmpty()
                ) {
                    val count = uiState.selectedDumpstateIndices.size
                    Text(
                        text = if (count > 0) "Upload Selected Files ($count)" else "Select Files to Upload",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                // Loading indicator
                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
                viewModel.uploadSelectedDumpstates(maxDownloads, expiryHours)
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

/**
 * Individual dumpstate file item with checkbox
 */
@Composable
private fun DumpstateFileItem(
    file: java.io.File,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Size: ${formatFileSize(file.length())}  •  ${formatDate(file.lastModified())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Format file size to human-readable string
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

/**
 * Format timestamp to readable date
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
