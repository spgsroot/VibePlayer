package ru.spgsroot.peachy.ui.dialog

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.translate.ProviderUrl

/** Asked once per provider host, before the first page image leaves the phone. */
@Composable
fun TranslateConsentDialog(baseUrl: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.consent_title)) },
        text = { Text(stringResource(R.string.consent_text, ProviderUrl.host(baseUrl) ?: baseUrl)) },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.consent_accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.consent_decline)) } }
    )
}
