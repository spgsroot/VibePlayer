package ru.spgsroot.peachy.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.ui.settings.VaultFailure
import ru.spgsroot.peachy.ui.settings.VaultUiState

/** Smallest password accepted for a new vault: the file may sit in a cloud drive. */
const val MIN_VAULT_PASSWORD = 8

/**
 * Asks for the vault password and shows what the export or import is doing. [export] asks for the
 * password twice; the caller starts the work in [onStart].
 */
@Composable
fun VaultDialog(
    export: Boolean,
    state: VaultUiState,
    onStart: (CharArray) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val working = state is VaultUiState.Working
    val finished = state is VaultUiState.Exported || state is VaultUiState.Imported
    val tooShort = export && password.isNotEmpty() && password.length < MIN_VAULT_PASSWORD
    val mismatch = export && repeat.isNotEmpty() && repeat != password
    val ready = password.isNotEmpty() && (!export || (password.length >= MIN_VAULT_PASSWORD && repeat == password))

    AlertDialog(
        onDismissRequest = { if (!working) onClose() },
        title = { Text(stringResource(if (export) R.string.vault_export_title else R.string.vault_import_title)) },
        text = {
            Column {
                if (finished) {
                    Text(finishedText(state))
                } else if (working) {
                    val work = state as VaultUiState.Working
                    Text(stringResource(if (export) R.string.vault_exporting else R.string.vault_importing))
                    Spacer(Modifier.height(12.dp))
                    if (work.total > 0) {
                        LinearProgressIndicator(
                            progress = { work.done.toFloat() / work.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.vault_progress, work.done, work.total),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Text(
                        stringResource(if (export) R.string.vault_export_hint else R.string.vault_import_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.vault_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = tooShort,
                        supportingText = if (tooShort) {
                            { Text(stringResource(R.string.vault_password_short, MIN_VAULT_PASSWORD)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (export) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = repeat,
                            onValueChange = { repeat = it },
                            label = { Text(stringResource(R.string.vault_password_repeat)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = mismatch,
                            supportingText = if (mismatch) {
                                { Text(stringResource(R.string.vault_password_mismatch)) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.vault_password_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state is VaultUiState.Failed) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(failureText(state.reason)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                finished -> TextButton(onClick = onClose) { Text(stringResource(R.string.btn_close)) }
                working -> Unit
                else -> TextButton(
                    enabled = ready,
                    onClick = { onStart(password.toCharArray()) }
                ) { Text(stringResource(if (export) R.string.vault_export_start else R.string.vault_import_start)) }
            }
        },
        dismissButton = {
            if (!finished) {
                TextButton(onClick = if (working) onCancel else onClose) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        }
    )
}

@Composable
private fun finishedText(state: VaultUiState): String = when (state) {
    is VaultUiState.Exported -> stringResource(
        R.string.vault_export_done, state.result.videos, state.result.galleries, state.result.notes, state.result.photos
    )
    is VaultUiState.Imported -> stringResource(
        R.string.vault_import_done,
        state.result.videosAdded, state.result.videosSkipped, state.result.galleries, state.result.notes,
        state.result.photosAdded, state.result.photosSkipped
    )
    else -> ""
}

private fun failureText(reason: VaultFailure): Int = when (reason) {
    VaultFailure.WRONG_PASSWORD -> R.string.vault_err_password
    VaultFailure.NOT_A_VAULT -> R.string.vault_err_not_vault
    VaultFailure.UNSUPPORTED -> R.string.vault_err_unsupported
    VaultFailure.DAMAGED -> R.string.vault_err_damaged
    VaultFailure.NO_SPACE -> R.string.vault_err_space
    VaultFailure.IO -> R.string.vault_err_io
    VaultFailure.UNKNOWN -> R.string.vault_err_unknown
}
