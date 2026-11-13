package ru.spgsroot.peachy.ui.manga

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.translate.BatchState
import ru.spgsroot.peachy.translate.LineKind
import ru.spgsroot.peachy.translate.PageState
import ru.spgsroot.peachy.translate.TranslationError

/**
 * The translation of the page on screen, over its bottom edge. Collapsed it is one line; expanded it
 * takes up to half the screen and scrolls. Taps above it still reach the page.
 */
@Composable
fun TranslationPanel(
    state: PageState,
    batch: BatchState?,
    onAgain: () -> Unit,
    onProvider: () -> Unit,
    onCancelBatch: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val maxExpanded = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Surface(
        modifier,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 3.dp
    ) {
        Column(Modifier.animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state == PageState.Loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Translate, null, Modifier.size(18.dp))
                }
                Text(
                    summary(state, expanded),
                    Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (state is PageState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (state != PageState.Loading) {
                    IconButton(onClick = onAgain) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.translate_again))
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    stringResource(if (expanded) R.string.translate_collapse else R.string.translate_expand),
                    Modifier.padding(horizontal = 4.dp)
                )
                IconButton(onClick = onHide) {
                    Icon(Icons.Default.Close, stringResource(R.string.translate_close))
                }
            }
            if (batch != null) BatchRow(batch, onCancelBatch, onProvider)
            val failure = (state as? PageState.Failed)?.error
            if (failure.needsProvider()) {
                TextButton(onClick = onProvider, Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.translate_provider_link))
                }
            }
            if (expanded && state is PageState.Done) {
                Column(
                    Modifier.heightIn(max = maxExpanded).verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.translation.lines.forEach { line ->
                        val sfx = line.kind == LineKind.Sfx
                        Column {
                            Text(
                                line.translation,
                                style = MaterialTheme.typography.titleMedium,
                                fontStyle = if (sfx) FontStyle.Italic else FontStyle.Normal,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (sfx) 0.6f else 1f)
                            )
                            if (line.text.isNotBlank()) {
                                Text(
                                    line.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchRow(batch: BatchState, onCancel: () -> Unit, onProvider: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val stopped = batch.stopped
        Text(
            if (stopped != null) errorText(stopped) else stringResource(R.string.translate_batch_progress, batch.done, batch.total),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = if (stopped != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        if (stopped.needsProvider()) {
            TextButton(onClick = onProvider) { Text(stringResource(R.string.translate_provider_link)) }
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.translate_cancel)) }
    }
}

private fun TranslationError?.needsProvider() =
    this == TranslationError.NotConfigured || this == TranslationError.Auth

@Composable
private fun summary(state: PageState, expanded: Boolean): String = when (state) {
    PageState.Idle -> stringResource(R.string.translate_idle)
    PageState.Loading -> stringResource(R.string.translate_loading)
    is PageState.Done -> when {
        state.translation.lines.isEmpty() -> stringResource(R.string.translate_empty)
        expanded -> stringResource(R.string.translate_button)
        else -> state.translation.lines.first().translation
    }
    is PageState.Failed -> errorText(state.error)
}

@Composable
private fun errorText(error: TranslationError): String = when (error) {
    TranslationError.NotConfigured -> stringResource(R.string.translate_error_not_configured)
    TranslationError.Auth -> stringResource(R.string.translate_error_auth)
    is TranslationError.RateLimit ->
        if (error.retryAfterSeconds != null) stringResource(R.string.translate_error_rate_limit_after, error.retryAfterSeconds)
        else stringResource(R.string.translate_error_rate_limit)
    TranslationError.Network -> stringResource(R.string.translate_error_network)
    TranslationError.BadResponse -> stringResource(R.string.translate_error_bad_response)
    TranslationError.Refused -> stringResource(R.string.translate_error_refused)
    is TranslationError.Server -> stringResource(R.string.translate_error_server, error.code)
}
