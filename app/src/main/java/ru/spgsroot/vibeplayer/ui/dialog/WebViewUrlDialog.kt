package ru.spgsroot.vibeplayer.ui.dialog

import android.util.Patterns
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.spgsroot.vibeplayer.R

@Composable
fun WebViewUrlDialog(
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_webview_url)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.webview_audio_capture_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.label_url)) },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.error_invalid_url)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedUrl = url.trim()
                    if (!isValidUrl(trimmedUrl)) {
                        isError = true
                        return@Button
                    }
                    onOpen(trimmedUrl)
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.btn_open_webview))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

private fun isValidUrl(url: String): Boolean {
    return (url.startsWith("http://") || url.startsWith("https://")) &&
        Patterns.WEB_URL.matcher(url).matches()
}
