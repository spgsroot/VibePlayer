package ru.spgsroot.vibeplayer.ui.dialog

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ru.spgsroot.vibeplayer.ui.player.BatchImportProgress
import ru.spgsroot.vibeplayer.ui.player.BatchImportSummary

@Composable
fun BatchImportDialog(
    onDismiss: () -> Unit,
    onImport: suspend (List<String>, (BatchImportProgress) -> Unit) -> BatchImportSummary
) {
    var urlText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<BatchImportProgress?>(null) }
    var summary by remember { mutableStateOf<BatchImportSummary?>(null) }

    val scope = rememberCoroutineScope()
    
    // String resources - loaded once at composition time
    val errorNoUrls = stringResource(R.string.error_no_urls)
    val errorImportFailed = stringResource(R.string.error_import_failed)
    val importingText = stringResource(R.string.importing)
    val btnReadyText = stringResource(R.string.btn_ready)
    val btnImportText = stringResource(R.string.btn_import)
    val btnCloseText = stringResource(R.string.btn_close)
    val btnCancelText = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = {
            if (!isLoading) onDismiss()
        },
        title = {
            Text(stringResource(R.string.dialog_import_list))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.import_hint),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        inputError = null
                        summary = null
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 260.dp),
                    placeholder = {
                        Text(
                            stringResource(R.string.import_url_placeholder)
                        )
                    }
                )

                inputError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                progress?.let { state ->
                    if (isLoading || summary != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.import_processed, state.completed, state.total),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (state.currentUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.import_current, state.currentUrl),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.import_success_failed, state.succeeded, state.failed),
                            style = MaterialTheme.typography.bodySmall
                        )

                        state.lastError?.takeIf { it.isNotBlank() }?.let { lastError ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.import_last_error, lastError),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                summary?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.import_complete),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.import_total, result.total),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.import_succeeded, result.succeeded),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.import_failed, result.failed),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (result.failedUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_failed_list),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.failedUrls.joinToString(separator = "\n"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (summary != null) {
                        onDismiss()
                        return@Button
                    }

                    val urls = parseUrls(urlText)
                    if (urls.isEmpty()) {
                        inputError = errorNoUrls
                        return@Button
                    }

                    isLoading = true
                    inputError = null
                    summary = null
                    progress = BatchImportProgress(
                        total = urls.size,
                        completed = 0,
                        currentUrl = "",
                        succeeded = 0,
                        failed = 0
                    )

                    scope.launch {
                        runCatching {
                            onImport(urls) { update ->
                                scope.launch {
                                    progress = update
                                }
                            }
                        }.onSuccess { result ->
                            summary = result
                            isLoading = false
                        }.onFailure { error ->
                            inputError = error.message ?: errorImportFailed
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(
                    when {
                        isLoading -> importingText
                        summary != null -> btnReadyText
                        else -> btnImportText
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
                Text(if (summary != null) btnCloseText else btnCancelText)
            }
        }
    )
}

private fun parseUrls(text: String): List<String> {
    return text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filter { isValidUrl(it) }
        .distinct()
}

private fun isValidUrl(url: String): Boolean {
    return (url.startsWith("http://") || url.startsWith("https://")) &&
            Patterns.WEB_URL.matcher(url).matches()
}
