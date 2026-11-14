package ru.spgsroot.peachy.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.R

@Composable
fun AuthScreen(
    onVerifyPassword: suspend (String) -> Boolean,
    onAuthenticated: () -> Unit,
    statusMessage: String? = null,
    inputEnabled: Boolean = true
) {
    // Credentials deliberately do not enter saved instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val enabled = inputEnabled && !verifying
    fun submit() {
        if (!enabled || password.isEmpty()) return
        verifying = true
        scope.launch {
            try {
                if (onVerifyPassword(password)) {
                    password = ""
                    onAuthenticated()
                } else {
                    failed = true
                    password = ""
                }
            } finally { verifying = false }
        }
    }
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(statusMessage ?: stringResource(R.string.auth_enter_password))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; failed = false },
                    enabled = enabled,
                    label = { Text(stringResource(R.string.label_password)) },
                    singleLine = true,
                    isError = failed,
                    supportingText = if (failed) {{ Text(stringResource(R.string.auth_wrong_password)) }} else null,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                stringResource(R.string.label_password))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { submit() }, enabled = enabled && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (verifying) R.string.verifying else R.string.btn_login))
                }
            }
        }
    }
}
