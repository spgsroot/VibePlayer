package ru.spgsroot.peachy.ui.dialog

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R

@Composable
fun PasswordSetupDialog(
    onDismiss: () -> Unit,
    onSetPassword: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // String resources - loaded once at composition time
    val errorPasswordMinLength = stringResource(R.string.error_password_min_length)
    val errorPasswordsMismatch = stringResource(R.string.error_passwords_mismatch)
    val btnSetText = stringResource(R.string.btn_set)
    val btnCancelText = stringResource(R.string.btn_cancel)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_set_password)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.password_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.label_confirm_password)) },
                    visualTransformation = if (confirmVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { confirmVisible = !confirmVisible }) {
                            Icon(
                                if (confirmVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.isEmpty() -> error = errorPasswordMinLength
                        password != confirmPassword -> error = errorPasswordsMismatch
                        else -> {
                            onSetPassword(password)
                            onDismiss()
                        }
                    }
                }
            ) {
                Text(btnSetText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(btnCancelText)
            }
        }
    )
}

@Composable
fun PasswordChangeDialog(
    onDismiss: () -> Unit,
    currentPasswordError: Boolean,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmNewPasswordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // String resources - loaded once at composition time
    val errorPasswordMinLength = stringResource(R.string.error_password_min_length)
    val errorPasswordsMismatch = stringResource(R.string.error_passwords_mismatch)
    val errorCurrentPasswordWrong = stringResource(R.string.error_current_password_wrong)
    val btnChangeText = stringResource(R.string.btn_change)
    val btnCancelText = stringResource(R.string.btn_cancel)

    // Reset error when dialog is dismissed
    LaunchedEffect(currentPasswordError) {
        if (currentPasswordError) {
            error = errorCurrentPasswordWrong
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_change_password)) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.label_current_password)) },
                    visualTransformation = if (currentPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                            Icon(
                                if (currentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error == errorCurrentPasswordWrong,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = if (newPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = {
                        confirmNewPassword = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.label_confirm_password)) },
                    visualTransformation = if (confirmNewPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    trailingIcon = {
                        IconButton(onClick = { confirmNewPasswordVisible = !confirmNewPasswordVisible }) {
                            Icon(
                                if (confirmNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    isError = error != null && error != errorCurrentPasswordWrong,
                    supportingText = error?.let { if (it != errorCurrentPasswordWrong) { { Text(it) } } else null },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        newPassword.isEmpty() -> error = errorPasswordMinLength
                        newPassword != confirmNewPassword -> error = errorPasswordsMismatch
                        else -> {
                            onChangePassword(currentPassword, newPassword)
                        }
                    }
                }
            ) {
                Text(btnChangeText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(btnCancelText)
            }
        }
    )
}
