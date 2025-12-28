package com.mattintech.simplelogupload.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mattintech.simplelogupload.util.PreferencesUtil

/**
 * Dialog for entering upload parameters
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onConfirm Callback when upload is confirmed with (maxDownloads, expiryHours)
 */
@Composable
fun UploadParamsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    var maxDownloads by remember {
        mutableStateOf(PreferencesUtil.getDefaultMaxDownloads(context).toString())
    }
    var expiryHours by remember {
        mutableStateOf(PreferencesUtil.getDefaultExpiryHours(context).toString())
    }

    var maxDownloadsError by remember { mutableStateOf<String?>(null) }
    var expiryHoursError by remember { mutableStateOf<String?>(null) }

    fun validateAndConfirm() {
        maxDownloadsError = null
        expiryHoursError = null

        var hasError = false

        val maxDl = maxDownloads.toIntOrNull()
        if (maxDl == null || maxDl < 0) {
            maxDownloadsError = "Must be 0 or greater (0 = unlimited)"
            hasError = true
        }

        val expiry = expiryHours.toIntOrNull()
        if (expiry == null || expiry <= 0 || expiry > 24) {
            expiryHoursError = "Must be between 1 and 24 hours"
            hasError = true
        }

        if (!hasError) {
            onConfirm(maxDl!!, expiry!!)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Parameters") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Max Downloads
                OutlinedTextField(
                    value = maxDownloads,
                    onValueChange = {
                        maxDownloads = it
                        maxDownloadsError = null
                    },
                    label = { Text("Maximum Downloads") },
                    placeholder = { Text("Default: 5") },
                    supportingText = {
                        Text(maxDownloadsError ?: "Set to 0 for unlimited downloads")
                    },
                    isError = maxDownloadsError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Expiry Hours
                OutlinedTextField(
                    value = expiryHours,
                    onValueChange = {
                        expiryHours = it
                        expiryHoursError = null
                    },
                    label = { Text("Expiry Time (hours)") },
                    placeholder = { Text("Default: 24") },
                    supportingText = {
                        Text(expiryHoursError ?: "Maximum 24 hours (1 day)")
                    },
                    isError = expiryHoursError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { validateAndConfirm() }) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
