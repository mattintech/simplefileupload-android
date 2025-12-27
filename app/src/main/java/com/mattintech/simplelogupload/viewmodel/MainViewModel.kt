package com.mattintech.simplelogupload.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mattintech.simplelogupload.constant.AppConstants
import com.mattintech.simplelogupload.controller.FileController
import com.mattintech.simplelogupload.controller.UploadController
import com.mattintech.simplelogupload.model.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI state for the main screen
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val selectedFile: File? = null,
    val selectedFileName: String? = null,
    val dumpstateFiles: List<File> = emptyList(),
    val selectedDumpstateIndices: Set<Int> = emptySet(),
    val uploadResult: UploadResult? = null,
    val errorMessage: String? = null,
    val showUploadParamsDialog: Boolean = false,
    val showUploadResultDialog: Boolean = false
)

/**
 * ViewModel for the main screen with upload functionality
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = AppConstants.APP_TAG + "MainViewModel"

    private val uploadController = UploadController(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var uploadReceiver: BroadcastReceiver? = null

    init {
        loadDumpstateFiles()
        registerUploadReceiver()
    }

    /**
     * Register broadcast receiver for upload completion
     */
    private fun registerUploadReceiver() {
        uploadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    AppConstants.ACTION_UPLOAD_COMPLETE -> {
                        val code = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_CODE)
                        Log.d(TAG, "Upload completed: $code")

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            uploadResult = UploadResult(code ?: "UNKNOWN"),
                            showUploadResultDialog = true
                        )
                    }
                    AppConstants.ACTION_UPLOAD_ERROR -> {
                        val error = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_ERROR)
                        Log.e(TAG, "Upload error: $error")

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error ?: "Unknown error"
                        )
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AppConstants.ACTION_UPLOAD_COMPLETE)
            addAction(AppConstants.ACTION_UPLOAD_ERROR)
        }

        LocalBroadcastManager.getInstance(getApplication())
            .registerReceiver(uploadReceiver!!, filter)
    }

    /**
     * Load all dumpstate files from device
     */
    fun loadDumpstateFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = FileController.getAllDumpStateFiles()
                _uiState.value = _uiState.value.copy(
                    dumpstateFiles = files.toList()
                )
                Log.d(TAG, "Loaded ${files.size} dumpstate files")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading dumpstate files", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error loading dumpstate files: ${e.message}"
                )
            }
        }
    }

    /**
     * Handle file selection from file picker
     */
    fun handleFileSelection(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // TODO: Convert URI to File using FileController
                // For now, store URI info
                val fileName = uri.lastPathSegment ?: "Unknown file"
                _uiState.value = _uiState.value.copy(
                    selectedFileName = fileName,
                    selectedFile = null // Will need to implement URI to File conversion
                )
                Log.d(TAG, "File selected: $fileName")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling file selection", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error selecting file: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle dumpstate file selection
     */
    fun toggleDumpstateSelection(index: Int) {
        val currentSelections = _uiState.value.selectedDumpstateIndices.toMutableSet()
        if (currentSelections.contains(index)) {
            currentSelections.remove(index)
        } else {
            currentSelections.add(index)
        }
        _uiState.value = _uiState.value.copy(
            selectedDumpstateIndices = currentSelections
        )
    }

    /**
     * Select all dumpstate files
     */
    fun selectAllDumpstates() {
        val allIndices = _uiState.value.dumpstateFiles.indices.toSet()
        _uiState.value = _uiState.value.copy(
            selectedDumpstateIndices = allIndices
        )
    }

    /**
     * Clear all dumpstate selections
     */
    fun clearDumpstateSelections() {
        _uiState.value = _uiState.value.copy(
            selectedDumpstateIndices = emptySet()
        )
    }

    /**
     * Show upload parameters dialog
     */
    fun showUploadParamsDialog() {
        _uiState.value = _uiState.value.copy(
            showUploadParamsDialog = true
        )
    }

    /**
     * Hide upload parameters dialog
     */
    fun hideUploadParamsDialog() {
        _uiState.value = _uiState.value.copy(
            showUploadParamsDialog = false
        )
    }

    /**
     * Hide upload result dialog
     */
    fun hideUploadResultDialog() {
        _uiState.value = _uiState.value.copy(
            showUploadResultDialog = false,
            uploadResult = null
        )
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    /**
     * Upload a general file
     */
    fun uploadFile(file: File, maxDownloads: Int, expiryHours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    showUploadParamsDialog = false
                )

                Log.d(TAG, "Starting upload for file: ${file.name}")

                // Upload file and handle result
                uploadController.uploadFile(file, maxDownloads, expiryHours)
                    .thenAccept { result ->
                        if (result.isSuccess) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                uploadResult = result,
                                showUploadResultDialog = true
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = result.error ?: "Upload failed"
                            )
                        }
                    }
                    .exceptionally { ex ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Error uploading file: ${ex.message}"
                        )
                        null
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error uploading file: ${e.message}"
                )
            }
        }
    }

    /**
     * Upload selected dumpstate files
     * Uploads multiple files by combining them into a ZIP first
     */
    fun uploadSelectedDumpstates(maxDownloads: Int, expiryHours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val files = _uiState.value.selectedDumpstateIndices
                    .map { _uiState.value.dumpstateFiles[it] }

                if (files.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "No dumpstate files selected"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    showUploadParamsDialog = false
                )

                Log.d(TAG, "Starting upload for ${files.size} dumpstate files")

                // For now, upload the first file
                // TODO: Implement proper multi-file ZIP and upload
                val firstFile = files.first()
                uploadController.uploadFile(firstFile, maxDownloads, expiryHours)
                    .thenAccept { result ->
                        if (result.isSuccess) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                uploadResult = result,
                                showUploadResultDialog = true,
                                selectedDumpstateIndices = emptySet()
                            )
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                errorMessage = result.error ?: "Upload failed"
                            )
                        }
                    }
                    .exceptionally { ex ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Error uploading files: ${ex.message}"
                        )
                        null
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading dumpstate files", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error uploading files: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister broadcast receiver
        uploadReceiver?.let {
            LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(it)
        }
    }
}
