package ru.spgsroot.peachy.ui.dialog

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.downloader.DownloadMethod
import ru.spgsroot.peachy.data.downloader.DownloadOptionsStore
import ru.spgsroot.peachy.data.downloader.externalDownloadChooser
import ru.spgsroot.peachy.data.downloader.isDownloadUrl

@Composable
fun UrlInputDialog(
    onDismiss: () -> Unit,
    onDownload: suspend (String, String?) -> Result<Int>
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("download_options", Context.MODE_PRIVATE) }
    var options by remember(prefs) { mutableStateOf(DownloadOptionsStore.load(prefs)) }
    var url by remember { mutableStateOf("") }
    var isUrlError by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun resetStatus() {
        isSuccess = false
        isError = false
        statusMessage = null
    }
    fun saveOptions(value: ru.spgsroot.peachy.data.downloader.DownloadOptions) {
        val previousFolder = options.folderUri
        if (previousFolder != null && previousFolder != value.folderUri) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(Uri.parse(previousFolder),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
        }
        options = value
        DownloadOptionsStore.save(prefs, value)
        resetStatus()
    }
    val folderError = stringResource(R.string.download_folder_permission_error)
    val unnamedFolder = stringResource(R.string.download_folder_selected)
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                val selected = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
                        val name = runCatching {
                            context.contentResolver.query(document,
                                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                                if (it.moveToFirst()) it.getString(0) else null
                            }
                        }.getOrNull() ?: uri.lastPathSegment ?: unnamedFolder
                        name
                    }
                }
                isLoading = false
                selected.onSuccess { name -> saveOptions(options.copy(folderUri = uri.toString(), folderName = name)) }
                    .onFailure { isError = true; statusMessage = folderError }
            }
        }
    }
    val invalidUrl = stringResource(R.string.error_invalid_url)
    val downloadSuccess = stringResource(R.string.download_success)
    val downloadFailed = stringResource(R.string.error_download_failed)
    val copyFailed = stringResource(R.string.download_copy_failed)
    val externalUnavailable = stringResource(R.string.download_external_unavailable)
    val externalHint = stringResource(R.string.download_external_hint)
    val chooserTitle = stringResource(R.string.download_another_app)
    val shareLabel = stringResource(R.string.download_send_link)
    fun launchExternal(sendLink: Boolean) {
        val link = url.trim()
        resetStatus()
        if (!isValidUrl(link)) {
            isUrlError = true
            return
        }
        try {
            context.startActivity(externalDownloadChooser(context, link,
                if (sendLink) shareLabel else chooserTitle, sendLink))
            // Opening a chooser is not a completed download or a library import.
        } catch (_: ActivityNotFoundException) {
            isError = true
            statusMessage = externalUnavailable
        } catch (_: SecurityException) {
            isError = true
            statusMessage = externalUnavailable
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.dialog_download)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; isUrlError = false; resetStatus() },
                    label = { Text(stringResource(R.string.label_url)) },
                    isError = isUrlError,
                    enabled = !isLoading,
                    supportingText = if (isUrlError) { { Text(invalidUrl) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                DownloadMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = options.method == method, enabled = !isLoading, role = Role.RadioButton,
                            onClick = { saveOptions(options.copy(method = method)) }
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = options.method == method, onClick = null, enabled = !isLoading)
                        Text(stringResource(if (method == DownloadMethod.InApp) R.string.download_in_app else R.string.download_another_app))
                    }
                }
                if (options.method == DownloadMethod.InApp) {
                    Text(stringResource(R.string.download_folder_hint), style = MaterialTheme.typography.bodySmall)
                    options.folderUri?.let {
                        Text(options.folderName ?: unnamedFolder, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row {
                        TextButton(enabled = !isLoading, onClick = {
                            try { folderLauncher.launch(options.folderUri?.let(Uri::parse)) }
                            catch (_: ActivityNotFoundException) { isError = true; statusMessage = folderError }
                        }) {
                            Text(stringResource(if (options.folderUri == null) R.string.download_pick_folder else R.string.download_change_folder))
                        }
                        if (options.folderUri != null) {
                            TextButton(enabled = !isLoading, onClick = {
                                saveOptions(options.copy(folderUri = null, folderName = null))
                            }) { Text(stringResource(R.string.download_clear_folder)) }
                        }
                    }
                } else {
                    Text(externalHint, style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !isLoading, onClick = { launchExternal(sendLink = true) }) {
                        Text(shareLabel)
                    }
                }
                if (isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.download_in_progress), style = MaterialTheme.typography.bodyMedium)
                }
                statusMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(enabled = !isLoading, onClick = {
                if (isSuccess) { onDismiss(); return@Button }
                val trimmedUrl = url.trim()
                if (!isValidUrl(trimmedUrl)) {
                    isUrlError = true
                    resetStatus()
                    return@Button
                }
                resetStatus()
                if (options.method == DownloadMethod.AnotherApp) {
                    launchExternal(sendLink = false)
                } else {
                    isLoading = true
                    val folderUri = options.folderUri
                    scope.launch {
                        val result = onDownload(trimmedUrl, folderUri)
                        isLoading = false
                        result.onSuccess { copyFailures ->
                            isSuccess = true
                            isError = copyFailures > 0
                            statusMessage = if (copyFailures > 0) copyFailed else downloadSuccess
                        }.onFailure {
                            isError = true
                            statusMessage = it.message ?: downloadFailed
                        }
                    }
                }
            }) {
                Text(stringResource(when {
                    isLoading -> R.string.downloading
                    isSuccess -> R.string.btn_ready
                    else -> R.string.btn_download
                }))
            }
        },
        dismissButton = {
            TextButton(enabled = !isLoading, onClick = onDismiss) {
                Text(stringResource(if (isSuccess) R.string.btn_close else R.string.btn_cancel))
            }
        }
    )
}

private fun isValidUrl(url: String): Boolean =
    isDownloadUrl(url) && Patterns.WEB_URL.matcher(url).matches()
