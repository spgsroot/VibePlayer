package ru.spgsroot.peachy.ui.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
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
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.notes.NoteRepository

/** Edits one private note. Saving blank text removes the note. */
@Composable
fun NoteDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(NoteRepository.MAX_LENGTH) },
                placeholder = { Text(stringResource(R.string.note_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                minLines = 5,
                maxLines = 12
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
