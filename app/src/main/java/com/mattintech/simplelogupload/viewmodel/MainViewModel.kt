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
import com.mattintech.simplelogupload.service.FileUploadForegroundService
import com.mattintech.simplelogupload.upload.UploadStrategySelector
import com.mattintech.simplelogupload.util.PreferencesUtil
import com.mattintech.simplelogupload.util.ZipUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * UI state for the main screen
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val isPaused: Boolean = false,
    val isChunkedUpload: Boolean = false,
    val uploadStatusMessage: String? = null,
    val selectedFile: File? = null,
    val selectedFileName: String? = null,
    val dumpstateFiles: List<File> = emptyList(),
    val selectedDumpstateIndices: Set<Int> = emptySet(),
    val uploadResult: UploadResult? = null,
    val errorMessage: String? = null,
    val showUploadParamsDialog: Boolean = false,
    val showUploadResultDialog: Boolean = false,
    val uploadProgress: Int = 0,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0
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

    // Track current upload parameters for result construction
    private var currentUploadFileName: String? = null
    private var currentUploadMaxDownloads: Int = 0
    private var currentUploadExpiryHours: Int = 0
    private var currentUploadTempZipFile: File? = null

    init {
        loadDumpstateFiles()
        registerUploadReceiver()

        // Check if there's an ongoing upload from a previous session
        if (PreferencesUtil.isUploadInProgress(application)) {
            val fileName = PreferencesUtil.getUploadFileName(application)
            Log.d(TAG, "Detected ongoing upload: $fileName")
            _uiState.value = _uiState.value.copy(
                isLoading = true
            )
        }
    }

    /**
     * Register broadcast receiver for upload completion
     */
    private fun registerUploadReceiver() {
        uploadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    AppConstants.ACTION_UPLOAD_PROGRESS -> {
                        val bytesUploaded = intent.getLongExtra(AppConstants.EXTRA_BYTES_UPLOADED, 0)
                        val totalBytes = intent.getLongExtra(AppConstants.EXTRA_TOTAL_BYTES, 0)
                        val percentComplete = intent.getIntExtra(AppConstants.EXTRA_PERCENT_COMPLETE, 0)

                        _uiState.value = _uiState.value.copy(
                            uploadProgress = percentComplete,
                            bytesUploaded = bytesUploaded,
                            totalBytes = totalBytes
                        )
                    }
                    AppConstants.ACTION_UPLOAD_COMPLETE -> {
                        val code = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_CODE)
                        Log.d(TAG, "Upload completed: $code")

                        // Create proper success UploadResult with tracked parameters
                        val result = if (code != null && currentUploadFileName != null) {
                            UploadResult(
                                code,
                                currentUploadFileName!!,
                                currentUploadMaxDownloads,
                                currentUploadExpiryHours
                            )
                        } else {
                            // Fallback for error case
                            UploadResult("Upload completed but code is missing")
                        }

                        // Clean up temporary zip file if it exists
                        currentUploadTempZipFile?.let { tempFile ->
                            if (tempFile.exists()) {
                                val deleted = tempFile.delete()
                                Log.d(TAG, "Cleaned up temp zip file after upload: ${tempFile.name}, deleted: $deleted")
                            }
                            currentUploadTempZipFile = null
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            uploadResult = result,
                            showUploadResultDialog = true,
                            uploadProgress = 0,
                            bytesUploaded = 0,
                            totalBytes = 0
                        )
                    }
                    AppConstants.ACTION_UPLOAD_ERROR -> {
                        val error = intent.getStringExtra(AppConstants.EXTRA_UPLOAD_ERROR)
                        Log.e(TAG, "Upload error: $error")

                        // Clean up temporary zip file if it exists
                        currentUploadTempZipFile?.let { tempFile ->
                            if (tempFile.exists()) {
                                val deleted = tempFile.delete()
                                Log.d(TAG, "Cleaned up temp zip file after error: ${tempFile.name}, deleted: $deleted")
                            }
                            currentUploadTempZipFile = null
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error ?: "Unknown error",
                            uploadProgress = 0,
                            bytesUploaded = 0,
                            totalBytes = 0
                        )
                    }
                    AppConstants.ACTION_UPLOAD_PAUSED -> {
                        Log.d(TAG, "Upload paused")
                        _uiState.value = _uiState.value.copy(
                            isPaused = true,
                            uploadStatusMessage = "Upload paused"
                        )
                    }
                    AppConstants.ACTION_UPLOAD_RESUMED -> {
                        Log.d(TAG, "Upload resumed")
                        _uiState.value = _uiState.value.copy(
                            isPaused = false,
                            uploadStatusMessage = "Resuming upload..."
                        )
                    }
                    AppConstants.ACTION_UPLOAD_CANCELLED -> {
                        Log.d(TAG, "Upload cancelled")

                        // Clean up temporary zip file if it exists
                        currentUploadTempZipFile?.let { tempFile ->
                            if (tempFile.exists()) {
                                val deleted = tempFile.delete()
                                Log.d(TAG, "Cleaned up temp zip file after cancellation: ${tempFile.name}, deleted: $deleted")
                            }
                            currentUploadTempZipFile = null
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isPaused = false,
                            uploadProgress = 0,
                            bytesUploaded = 0,
                            totalBytes = 0,
                            uploadStatusMessage = "Upload cancelled"
                        )
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AppConstants.ACTION_UPLOAD_PROGRESS)
            addAction(AppConstants.ACTION_UPLOAD_COMPLETE)
            addAction(AppConstants.ACTION_UPLOAD_ERROR)
            addAction(AppConstants.ACTION_UPLOAD_PAUSED)
            addAction(AppConstants.ACTION_UPLOAD_RESUMED)
            addAction(AppConstants.ACTION_UPLOAD_CANCELLED)
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
                // Get filename from URI
                val fileName = getFileNameFromUri(uri)
                Log.d(TAG, "File selected from URI: $fileName")

                // Convert URI to File using FileController
                val file = FileController.saveContentUriToFile(
                    getApplication(),
                    uri,
                    fileName
                )

                _uiState.value = _uiState.value.copy(
                    selectedFileName = fileName,
                    selectedFile = file
                )
                Log.d(TAG, "File saved to cache: ${file.absolutePath}, size: ${file.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling file selection", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error selecting file: ${e.message}"
                )
            }
        }
    }

    /**
     * Get filename from URI using ContentResolver
     */
    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "selected_file_${System.currentTimeMillis()}"

        // Try to get the display name from the content resolver
        try {
            getApplication<Application>().contentResolver.query(
                uri, null, null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get filename from URI, using default", e)
            // Fall back to using last path segment if available
            fileName = uri.lastPathSegment ?: fileName
        }

        return fileName
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
     * Upload a general file using foreground service
     */
    fun uploadFile(file: File, maxDownloads: Int, expiryHours: Int) {
        try {
            // Track upload parameters for result construction
            currentUploadFileName = file.name
            currentUploadMaxDownloads = maxDownloads
            currentUploadExpiryHours = expiryHours

            // Determine if this will be a chunked upload
            val isChunked = UploadStrategySelector.shouldUseChunkedUpload(getApplication(), file)
            Log.d(TAG, "Upload will use ${if (isChunked) "chunked" else "standard"} strategy")

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                isChunkedUpload = isChunked,
                isPaused = false,
                uploadStatusMessage = null,
                showUploadParamsDialog = false
            )

            Log.d(TAG, "Starting upload for file: ${file.name}")

            // Start foreground service for upload with notification
            uploadController.startUploadService(file, maxDownloads, expiryHours)

        } catch (e: Exception) {
            Log.e(TAG, "Error starting upload service", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Error starting upload: ${e.message}"
            )
        }
    }

    /**
     * Upload selected dumpstate files
     * If multiple files are selected, combines them into a ZIP first
     */
    fun uploadSelectedDumpstates(maxDownloads: Int, expiryHours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            var zipFile: File? = null
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

                Log.d(TAG, "Starting upload for ${files.size} dumpstate file(s)")

                val fileToUpload: File

                if (files.size == 1) {
                    // Single file: upload directly
                    fileToUpload = files.first()
                    currentUploadTempZipFile = null
                    Log.d(TAG, "Uploading single dumpstate file: ${fileToUpload.name}")
                } else {
                    // Multiple files: zip them first
                    Log.d(TAG, "Zipping ${files.size} dumpstate files")

                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val zipFileName = "dumpstates_$timestamp"
                    val cacheDir = getApplication<Application>().cacheDir

                    zipFile = ZipUtil.zipFiles(files, cacheDir, zipFileName, null)
                    fileToUpload = zipFile
                    currentUploadTempZipFile = zipFile
                    Log.d(TAG, "Created zip file: ${fileToUpload.name} (${fileToUpload.length()} bytes)")
                }

                // Track upload parameters for result construction
                currentUploadFileName = fileToUpload.name
                currentUploadMaxDownloads = maxDownloads
                currentUploadExpiryHours = expiryHours

                // Determine if this will be a chunked upload
                val isChunked = UploadStrategySelector.shouldUseChunkedUpload(getApplication(), fileToUpload)
                Log.d(TAG, "Upload will use ${if (isChunked) "chunked" else "standard"} strategy")

                // Update state with upload type
                _uiState.value = _uiState.value.copy(
                    isChunkedUpload = isChunked,
                    isPaused = false,
                    uploadStatusMessage = null
                )

                uploadController.startUploadService(fileToUpload, maxDownloads, expiryHours)
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading dumpstate files", e)

                // Clean up zip file if it was created
                zipFile?.let {
                    if (it.exists()) {
                        val deleted = it.delete()
                        Log.d(TAG, "Cleaned up zip file: ${it.name}, deleted: $deleted")
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error uploading files: ${e.message}"
                )
            }
        }
    }

    /**
     * Pause the current upload
     */
    fun pauseUpload() {
        Log.d(TAG, "Pause upload requested")
        val intent = Intent(getApplication(), FileUploadForegroundService::class.java).apply {
            action = AppConstants.ACTION_PAUSE_UPLOAD
        }
        getApplication<Application>().startService(intent)
    }

    /**
     * Resume the paused upload
     */
    fun resumeUpload() {
        Log.d(TAG, "Resume upload requested")
        val intent = Intent(getApplication(), FileUploadForegroundService::class.java).apply {
            action = AppConstants.ACTION_RESUME_UPLOAD
        }
        getApplication<Application>().startService(intent)
    }

    /**
     * Cancel the current upload
     */
    fun cancelUpload() {
        Log.d(TAG, "Cancel upload requested")
        val intent = Intent(getApplication(), FileUploadForegroundService::class.java).apply {
            action = AppConstants.ACTION_CANCEL_UPLOAD
        }
        getApplication<Application>().startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister broadcast receiver
        uploadReceiver?.let {
            LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(it)
        }
    }
}
