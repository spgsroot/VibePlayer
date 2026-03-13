package ru.spgsroot.vibeplayer.ui.dialog

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.R

@Composable
fun UrlInputDialog(
    onDismiss: () -> Unit,
    onDownload: suspend (String) -> Result<Unit>
) {
    var url by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // String resources - loaded once at composition time
    val errorInvalidUrl = stringResource(R.string.error_invalid_url)
    val downloadInProgress = stringResource(R.string.download_in_progress)
    val downloadSuccessText = stringResource(R.string.download_success)
    val errorDownloadFailed = stringResource(R.string.error_download_failed)
    val downloadingText = stringResource(R.string.downloading)
    val btnReadyText = stringResource(R.string.btn_ready)
    val btnDownloadText = stringResource(R.string.btn_download)
    val btnCloseText = stringResource(R.string.btn_close)
    val btnCancelText = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        title = {
            Text(stringResource(R.string.dialog_download))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isError = false
                        isSuccess = false
                        statusMessage = null
                    },
                    label = { Text(stringResource(R.string.label_url)) },
                    isError = isError,
                    enabled = !isLoading,
                    supportingText = if (isError) {
                        { Text(errorInvalidUrl) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadInProgress,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                statusMessage?.let { message ->
                    if (isLoading) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = message,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isSuccess) {
                        onDismiss()
                        return@Button
                    }

                    val trimmedUrl = url.trim()
                    if (!isValidUrl(trimmedUrl)) {
                        isError = true
                        isSuccess = false
                        statusMessage = null
                        return@Button
                    }

                    isLoading = true
                    isError = false
                    isSuccess = false
                    statusMessage = null

                    scope.launch {
                        val result = onDownload(trimmedUrl)
                        isLoading = false

                        result
                            .onSuccess {
                                isSuccess = true
                                isError = false
                                statusMessage = downloadSuccessText
                            }
                            .onFailure { error ->
                                isSuccess = false
                                isError = true
                                statusMessage = error.message ?: errorDownloadFailed
                            }
                    }
                },
                enabled = !isLoading
            ) {
                Text(
                    when {
                        isLoading -> downloadingText
                        isSuccess -> btnReadyText
                        else -> btnDownloadText
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (!isLoading) onDismiss()
                },
                enabled = !isLoading
            ) {
                Text(if (isSuccess) btnCloseText else btnCancelText)
            }
        }
    )
}

private fun isValidUrl(url: String): Boolean {
    return url.startsWith("http://") ||
            url.startsWith("https://") &&
            Patterns.WEB_URL.matcher(url).matches()
}
