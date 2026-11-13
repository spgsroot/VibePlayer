package ru.spgsroot.peachy.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.translate.ProviderSettings
import ru.spgsroot.peachy.translate.ProviderUrl
import ru.spgsroot.peachy.translate.TranslationError

/**
 * Where page images are sent for translation. The key is typed here and goes straight to the encrypted
 * store: it is held in plain `remember`, never in saved state, and the stored key is never shown again.
 */
@Composable
fun ProviderDialog(
    current: ProviderSettings,
    onSave: (baseUrl: String, model: String, apiKey: String) -> Unit,
    onTest: suspend () -> TranslationError?,
    onDismiss: () -> Unit
) {
    var baseUrl by rememberSaveable { mutableStateOf(current.baseUrl) }
    var model by rememberSaveable { mutableStateOf(current.model) }
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result?>(null) }
    val scope = rememberCoroutineScope()
    val urlAllowed = ProviderUrl.isAllowed(baseUrl)
    val hasKey = apiKey.isNotBlank() || current.apiKey.isNotBlank()
    val canSave = urlAllowed && model.isNotBlank() && hasKey

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.provider_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; result = null },
                    label = { Text(stringResource(R.string.provider_base_url)) },
                    isError = !urlAllowed,
                    supportingText = if (urlAllowed) null else {
                        { Text(stringResource(R.string.provider_url_rejected)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; result = null },
                    label = { Text(stringResource(R.string.provider_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; result = null },
                    label = { Text(stringResource(R.string.provider_key)) },
                    placeholder = {
                        if (current.apiKey.isNotBlank()) Text(stringResource(R.string.provider_key_kept))
                    },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                stringResource(R.string.provider_show_key)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = canSave && !testing,
                    onClick = {
                        onSave(baseUrl, model, apiKey)
                        apiKey = ""
                        testing = true
                        result = null
                        scope.launch {
                            result = Result(onTest())
                            testing = false
                        }
                    }
                ) { Text(stringResource(if (testing) R.string.provider_testing else R.string.provider_test)) }
                result?.let {
                    Text(
                        if (it.error == null) stringResource(R.string.provider_test_ok) else errorName(it.error),
                        color = if (it.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(baseUrl, model, apiKey)
                    onDismiss()
                }
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

/** A finished test; [error] null means the provider answered. Boxed so a repeat of the same outcome still redraws. */
private class Result(val error: TranslationError?)

@Composable
private fun errorName(error: TranslationError?): String = when (error) {
    null -> ""
    TranslationError.NotConfigured -> stringResource(R.string.translate_error_not_configured)
    TranslationError.Auth -> stringResource(R.string.translate_error_auth)
    TranslationError.RateLimit -> stringResource(R.string.translate_error_rate_limit)
    TranslationError.Network -> stringResource(R.string.translate_error_network)
    TranslationError.BadResponse -> stringResource(R.string.translate_error_bad_response)
    TranslationError.Refused -> stringResource(R.string.translate_error_refused)
    is TranslationError.Server -> stringResource(R.string.translate_error_server, error.code)
}
