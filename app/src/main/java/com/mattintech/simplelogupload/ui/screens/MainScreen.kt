package com.mattintech.simplelogupload.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mattintech.simplelogupload.ui.screens.tabs.DumpstateTab
import com.mattintech.simplelogupload.ui.screens.tabs.GeneralUploadTab
import com.mattintech.simplelogupload.util.DeviceUtil
import com.mattintech.simplelogupload.viewmodel.MainViewModel

/**
 * Main screen with upload functionality and bottom navigation
 * Shows different tabs based on device type (Samsung vs others)
 *
 * @param onNavigateToHistory Callback to navigate to upload history
 * @param onNavigateToSettings Callback to navigate to settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val isSamsungDevice = remember { DeviceUtil.isSamsungDevice() }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimpleLogUpload") },
                actions = {
                    // History button
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = "Upload History"
                        )
                    }

                    // Settings button
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Only show bottom navigation on Samsung devices
            if (isSamsungDevice) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        icon = {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = "General Upload"
                            )
                        },
                        label = { Text("Upload") }
                    )
                    NavigationBarItem(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        icon = {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = "Dumpstate"
                            )
                        },
                        label = { Text("Dumpstate") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Global upload progress indicator - visible across all tabs
            if (uiState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Uploading",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (uiState.totalBytes > 0) {
                                Text(
                                    text = "${uiState.uploadProgress}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress bar
                        LinearProgressIndicator(
                            progress = { uiState.uploadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                        )

                        // Progress details
                        if (uiState.totalBytes > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val uploadedMB = uiState.bytesUploaded / (1024f * 1024f)
                            val totalMB = uiState.totalBytes / (1024f * 1024f)
                            Text(
                                text = String.format("%.1f / %.1f MB", uploadedMB, totalMB),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Tab content
            when {
                // Non-Samsung device: show only general upload
                !isSamsungDevice -> {
                    GeneralUploadTab(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel
                    )
                }
                // Samsung device with tab 0 selected: general upload
                selectedTabIndex == 0 -> {
                    GeneralUploadTab(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel
                    )
                }
                // Samsung device with tab 1 selected: dumpstate
                else -> {
                    DumpstateTab(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
