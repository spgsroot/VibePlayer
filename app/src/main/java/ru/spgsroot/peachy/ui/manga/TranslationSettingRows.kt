package ru.spgsroot.peachy.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.manga.ReaderSettings
import ru.spgsroot.peachy.translate.TargetLanguage
import ru.spgsroot.peachy.translate.TranslateEngine
import androidx.compose.ui.text.style.TextOverflow

/**
 * The four choices behind a translation: how text is found on the page, which language it is in,
 * which language it is translated into, and whether the result is drawn on the page.
 */
@Composable
fun TranslationSettingRows(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    val auto = stringResource(R.string.language_auto)
    val app = stringResource(R.string.language_app)
    fun language(code: String) = when (code) {
        "auto" -> auto
        "app" -> app
        else -> Locale(code).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    ChoiceRow(
        Icons.Default.Layers, R.string.translate_engine,
        value = stringResource(settings.engine.label()),
        options = TranslateEngine.entries.map { it to stringResource(it.label()) },
        onPick = { onChange(settings.copy(engine = it)) }
    )
    ChoiceRow(
        Icons.Default.Language, R.string.translate_source,
        value = language(settings.sourceLanguage),
        options = TargetLanguage.SOURCE_CODES.map { it to language(it) },
        onPick = { onChange(settings.copy(sourceLanguage = it)) }
    )
    ChoiceRow(
        Icons.Default.Translate, R.string.translate_target,
        value = language(settings.targetLanguage),
        options = TargetLanguage.TARGET_CODES.map { it to language(it) },
        onPick = { onChange(settings.copy(targetLanguage = it)) }
    )
    Row(
        Modifier.fillMaxWidth().clickable { onChange(settings.copy(overlay = !settings.overlay)) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Subtitles, null)
        Text(stringResource(R.string.translate_overlay), Modifier.weight(1f))
        Switch(checked = settings.overlay, onCheckedChange = null)
    }
}

private fun TranslateEngine.label(): Int = when (this) {
    TranslateEngine.Model -> R.string.translate_engine_model
    TranslateEngine.MlKit -> R.string.translate_engine_mlkit
}

@Composable
private fun <T> ChoiceRow(
    icon: ImageVector,
    label: Int,
    value: String,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null)
        Text(stringResource(label), Modifier.weight(1f))
        Box {
            Text(
                value,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp)
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (key, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(key) })
                }
            }
        }
    }
}
