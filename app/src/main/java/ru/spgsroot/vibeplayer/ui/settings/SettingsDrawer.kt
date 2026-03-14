package ru.spgsroot.vibeplayer.ui.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.locale.LocaleManager
import ru.spgsroot.vibeplayer.ui.dialog.DeviceScanDialog
import ru.spgsroot.vibeplayer.ui.dialog.PasswordChangeDialog
import ru.spgsroot.vibeplayer.ui.dialog.PasswordSetupDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawer(
    viewModel: SettingsViewModel = hiltViewModel(),
    onClose: () -> Unit = {}
) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value
    val isPasswordSet by viewModel.isPasswordSet.collectAsStateWithLifecycle()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val tonWallet = "UQCGFymEHFNq1IcIhXBWJJe7Ha7Cx7RU6apvotRs5DcEEAaG"

    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(24.dp))

            settings?.let { s ->
                TimerSlider(
                    timerMs = s.timerMs,
                    onTimerChange = viewModel::updateTimer
                )

                Spacer(modifier = Modifier.height(16.dp))

                PlaybackSpeedSlider(
                    speed = s.playbackSpeed,
                    onSpeedChange = viewModel::updatePlaybackSpeed
                )

                Spacer(modifier = Modifier.height(16.dp))

                DspSettings(
                    lowFreq = s.dspConfig.lowFreqHz,
                    highFreq = s.dspConfig.highFreqHz,
                    smoothing = s.dspConfig.smoothingAlpha,
                    onLowFreqChange = viewModel::updateDspLowFreq,
                    onHighFreqChange = viewModel::updateDspHighFreq,
                    onSmoothingChange = viewModel::updateDspSmoothing
                )

                Spacer(modifier = Modifier.height(16.dp))

                LanguageSelector(
                    currentLanguage = s.languageCode,
                    onLanguageChange = { language ->
                        viewModel.updateLanguage(language)
                        applyLocale(context, language)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (isPasswordSet) {
                        showChangePasswordDialog = true
                    } else {
                        showPasswordDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (isPasswordSet) {
                            R.string.btn_app_password_change
                        } else {
                            R.string.btn_app_password_set
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showDeviceDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_device_connect))
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(stringResource(R.string.support_title), style = MaterialTheme.typography.titleMedium)

            Text(
                text = "${stringResource(R.string.support_ton)} $tonWallet",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable {
                    clipboardManager.setText(AnnotatedString(tonWallet))
                }
            )

        }
    }

    if (showPasswordDialog) {
        PasswordSetupDialog(
            onDismiss = { showPasswordDialog = false },
            onSetPassword = { password ->
                viewModel.setPassword(password)
            }
        )
    }

    if (showChangePasswordDialog) {
        var passwordError by remember { mutableStateOf(false) }
        PasswordChangeDialog(
            onDismiss = {
                showChangePasswordDialog = false
                passwordError = false
            },
            currentPasswordError = passwordError,
            onChangePassword = { currentPassword, newPassword ->
                viewModel.changePassword(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    onSuccess = {
                        showChangePasswordDialog = false
                    },
                    onError = {
                        passwordError = true
                    }
                )
            }
        )
    }

    if (showDeviceDialog) {
        DeviceScanDialog(
            connectionManager = viewModel.connectionManager,
            onDismiss = { showDeviceDialog = false },
            onDeviceSelected = { deviceIndex ->
                viewModel.connectDevice(deviceIndex.toString())
                showDeviceDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(stringResource(R.string.language_label), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = when (currentLanguage) {
                    LocaleManager.LANGUAGE_RUSSIAN -> stringResource(R.string.language_russian)
                    LocaleManager.LANGUAGE_ENGLISH -> stringResource(R.string.language_english)
                    else -> stringResource(R.string.language_system)
                },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.language_system)) },
                    onClick = {
                        onLanguageChange(LocaleManager.LANGUAGE_SYSTEM)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.language_russian)) },
                    onClick = {
                        onLanguageChange(LocaleManager.LANGUAGE_RUSSIAN)
                        expanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.language_english)) },
                    onClick = {
                        onLanguageChange(LocaleManager.LANGUAGE_ENGLISH)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun applyLocale(context: Context, languageCode: String) {
    val locale = LocaleManager.getLocale(languageCode)
    LocaleManager.setLocale(context, languageCode)

    // For Android 13+, use the new LocaleManager API
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as android.app.LocaleManager
        localeManager.applicationLocales = android.os.LocaleList(locale)
    }
}
