package com.mattintech.simplelogupload.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
        when {
            // Non-Samsung device: show only general upload
            !isSamsungDevice -> {
                GeneralUploadTab(
                    modifier = Modifier.padding(paddingValues),
                    viewModel = viewModel
                )
            }
            // Samsung device with tab 0 selected: general upload
            selectedTabIndex == 0 -> {
                GeneralUploadTab(
                    modifier = Modifier.padding(paddingValues),
                    viewModel = viewModel
                )
            }
            // Samsung device with tab 1 selected: dumpstate
            else -> {
                DumpstateTab(
                    modifier = Modifier.padding(paddingValues),
                    viewModel = viewModel
                )
            }
        }
    }
}
