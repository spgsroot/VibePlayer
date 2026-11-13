package ru.spgsroot.peachy.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.browser.BrowserUrls

/** Opens the browser, optionally at an address, optionally with page audio driving the haptics. */
@Composable
fun WebViewUrlDialog(
    onDismiss: () -> Unit,
    onOpen: (url: String, capture: Boolean) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_browser_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.dialog_browser_url_hint)) },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text(stringResource(R.string.error_webview_https)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Checkbox(checked = capture, onCheckedChange = { capture = it })
                    Text(stringResource(R.string.dialog_browser_capture), style = MaterialTheme.typography.bodyLarge)
                }
                if (capture) {
                    Text(
                        text = stringResource(R.string.webview_audio_capture_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val typed = url.trim()
                    // Blank opens the start page; anything typed must be an address or a search.
                    if (typed.isNotEmpty() && BrowserUrls.resolve(typed) == null) {
                        isError = true
                        return@Button
                    }
                    onOpen(typed, capture)
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
