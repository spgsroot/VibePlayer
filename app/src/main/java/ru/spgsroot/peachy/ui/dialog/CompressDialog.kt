package ru.spgsroot.peachy.ui.dialog

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.compress.AudioMode
import ru.spgsroot.peachy.compress.CompressionPlanner
import ru.spgsroot.peachy.compress.CompressionPreset
import ru.spgsroot.peachy.compress.CompressionPresets
import ru.spgsroot.peachy.compress.CompressionSettings
import ru.spgsroot.peachy.compress.RateControl
import ru.spgsroot.peachy.compress.SourceInfo
import ru.spgsroot.peachy.compress.VideoCodec
import ru.spgsroot.peachy.compress.VideoProbe
import java.util.Locale
import kotlin.math.roundToInt

private val HEIGHTS = listOf<Int?>(null, 1440, 1080, 720, 540, 480)
private val FRAME_RATES = listOf<Int?>(null, 60, 30, 24)

/**
 * Picks how the selected videos are compressed: a preset, or every parameter by hand.
 * [sources] is null while the files are still being read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressDialog(
    sources: List<SourceInfo>?,
    videoCount: Int,
    onStart: (CompressionSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var presetId by remember { mutableStateOf<String?>(CompressionPresets.visuallyLossless.id) }
    var settings by remember { mutableStateOf(CompressionPresets.visuallyLossless.settings) }

    fun custom(change: CompressionSettings) {
        settings = change
        presetId = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compress_title, videoCount)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompressionPresets.all.forEach { preset ->
                    PresetRow(
                        preset = preset,
                        selected = presetId == preset.id,
                        onClick = {
                            presetId = preset.id
                            settings = preset.settings
                        }
                    )
                }
                PresetRowCustom(selected = presetId == null)

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                Label(R.string.compress_codec)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.codec == VideoCodec.HEVC,
                        onClick = { custom(settings.copy(codec = VideoCodec.HEVC)) },
                        label = { Text("HEVC") }
                    )
                    FilterChip(
                        selected = settings.codec == VideoCodec.H264,
                        onClick = { custom(settings.copy(codec = VideoCodec.H264)) },
                        label = { Text("H.264") }
                    )
                }

                Label(R.string.compress_rate)
                val rate = settings.rate
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = rate is RateControl.Quality,
                        onClick = { if (rate !is RateControl.Quality) custom(settings.copy(rate = RateControl.Quality(0.08f))) },
                        label = { Text(stringResource(R.string.compress_rate_quality)) }
                    )
                    FilterChip(
                        selected = rate is RateControl.Bitrate,
                        onClick = { if (rate !is RateControl.Bitrate) custom(settings.copy(rate = RateControl.Bitrate(4000))) },
                        label = { Text(stringResource(R.string.compress_rate_bitrate)) }
                    )
                }
                when (rate) {
                    is RateControl.Quality -> {
                        Text(
                            stringResource(R.string.compress_quality_value, String.format(Locale.US, "%.3f", rate.bitsPerPixel)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = rate.bitsPerPixel,
                            onValueChange = { custom(settings.copy(rate = RateControl.Quality((it * 200).roundToInt() / 200f))) },
                            valueRange = 0.02f..0.20f
                        )
                        Text(
                            stringResource(R.string.compress_quality_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is RateControl.Bitrate -> {
                        Text(
                            stringResource(R.string.compress_bitrate_value, String.format(Locale.US, "%.1f", rate.kbps / 1000f)),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = rate.kbps.toFloat(),
                            onValueChange = { custom(settings.copy(rate = RateControl.Bitrate((it / 100).roundToInt() * 100))) },
                            valueRange = 300f..20000f
                        )
                    }
                }

                val share = settings.maxShareOfSource
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.compress_cap),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Switch(
                        checked = share != null,
                        onCheckedChange = { on -> custom(settings.copy(maxShareOfSource = if (on) 0.6f else null)) }
                    )
                }
                if (share != null) {
                    Text(
                        stringResource(R.string.compress_cap_value, (share * 100).roundToInt()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = share,
                        onValueChange = { custom(settings.copy(maxShareOfSource = (it * 20).roundToInt() / 20f)) },
                        valueRange = 0.15f..1f
                    )
                }

                Label(R.string.compress_height)
                ChipRow(HEIGHTS, settings.maxHeight, { it?.let { h -> "${h}p" } }) { custom(settings.copy(maxHeight = it)) }

                Label(R.string.compress_fps)
                ChipRow(FRAME_RATES, settings.maxFps, { it?.toString() }) { custom(settings.copy(maxFps = it)) }

                Label(R.string.compress_audio)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.audio == AudioMode.KEEP,
                        onClick = { custom(settings.copy(audio = AudioMode.KEEP)) },
                        label = { Text(stringResource(R.string.compress_audio_keep)) }
                    )
                    FilterChip(
                        selected = settings.audio == AudioMode.REMOVE,
                        onClick = { custom(settings.copy(audio = AudioMode.REMOVE)) },
                        label = { Text(stringResource(R.string.compress_audio_remove)) }
                    )
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                if (sources == null) {
                    Text(stringResource(R.string.compress_reading), style = MaterialTheme.typography.bodyMedium)
                } else {
                    val estimate = remember(sources, settings) { estimate(sources, settings) }
                    if (estimate.before > 0) {
                        Text(
                            stringResource(
                                R.string.compress_estimate,
                                Formatter.formatFileSize(context, estimate.before),
                                Formatter.formatFileSize(context, estimate.after),
                                estimate.savedPercent
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (estimate.skipped > 0) {
                        Text(
                            stringResource(R.string.compress_skipped_note, estimate.skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (estimate.fallback > 0) {
                        Text(
                            stringResource(R.string.compress_hevc_missing, estimate.fallback),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (videoCount > sources.size) {
                        Text(
                            stringResource(R.string.compress_unreadable_note, videoCount - sources.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.compress_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = sources != null, onClick = { onStart(settings) }) {
                Text(stringResource(R.string.compress_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

private data class Estimate(val before: Long, val after: Long, val skipped: Int, val fallback: Int) {
    val savedPercent: Int get() = if (before > 0) (100 - after * 100 / before).toInt().coerceAtLeast(0) else 0
}

private fun estimate(sources: List<SourceInfo>, settings: CompressionSettings): Estimate {
    var before = 0L
    var after = 0L
    var skipped = 0
    var fallback = 0
    for (source in sources) {
        before += source.sizeBytes
        val plan = CompressionPlanner.plan(source, settings, VideoProbe.encoderSupport)
        if (plan != null && plan.codec != settings.codec) fallback++
        if (plan != null && CompressionPlanner.isWorthwhile(plan, source)) {
            after += plan.estimatedBytes
        } else {
            after += source.sizeBytes
            skipped++
        }
    }
    return Estimate(before, after, skipped, fallback)
}

@Composable
private fun Label(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, text: (T) -> String?, onPick: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onPick(option) },
                label = { Text(text(option) ?: stringResource(R.string.compress_original)) }
            )
        }
    }
}

@Composable
private fun PresetRow(preset: CompressionPreset, selected: Boolean, onClick: () -> Unit) {
    val (name, description) = when (preset.id) {
        CompressionPresets.VISUALLY_LOSSLESS -> R.string.compress_preset_lossless to R.string.compress_preset_lossless_desc
        CompressionPresets.COMPACT -> R.string.compress_preset_compact to R.string.compress_preset_compact_desc
        CompressionPresets.SMALLER -> R.string.compress_preset_smaller to R.string.compress_preset_smaller_desc
        else -> R.string.compress_preset_smallest to R.string.compress_preset_smallest_desc
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(stringResource(name), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (preset.lossy) {
                Text(
                    stringResource(R.string.compress_lossy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PresetRowCustom(selected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = null)
        Text(
            stringResource(R.string.compress_custom),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
