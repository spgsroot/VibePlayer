package ru.spgsroot.peachy.ui.dialog

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.compress.CompressionState

/** Shown while a compression runs. It cannot be dismissed; only Cancel stops the work. */
@Composable
fun CompressProgressDialog(state: CompressionState.Running, onCancel: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.compress_progress_title, state.index, state.total)) },
        text = {
            Column {
                Text(state.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                Text(
                    stringResource(
                        R.string.compress_progress_detail,
                        (state.progress * 100).toInt().coerceIn(0, 100),
                        Formatter.formatFileSize(context, state.savedBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.compress_progress_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
