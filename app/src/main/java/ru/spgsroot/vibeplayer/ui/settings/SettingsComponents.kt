package ru.spgsroot.vibeplayer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.spgsroot.vibeplayer.R

@Composable
fun TimerSlider(
    timerMs: Long,
    onTimerChange: (Long) -> Unit
) {
    Column {
        Text(stringResource(R.string.timer_label), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = timerMs.toFloat(),
            onValueChange = { onTimerChange(it.toLong()) },
            valueRange = 5000f..300000f,
            steps = 58
        )
        val text = if (timerMs >= 300000L) stringResource(R.string.timer_full_video) else stringResource(R.string.timer_seconds, timerMs / 1000)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PlaybackSpeedSlider(
    speed: Float,
    onSpeedChange: (Float) -> Unit
) {
    Column {
        Text(stringResource(R.string.playback_speed_label), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = speed,
            onValueChange = onSpeedChange,
            valueRange = 0.5f..2.0f,
            steps = 14
        )
        Text(stringResource(R.string.playback_speed_x, speed), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DspSettings(
    lowFreq: Int,
    highFreq: Int,
    smoothing: Float,
    onLowFreqChange: (Int) -> Unit,
    onHighFreqChange: (Int) -> Unit,
    onSmoothingChange: (Float) -> Unit
) {
    Column {
        Text(stringResource(R.string.dsp_settings_title), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        Text(stringResource(R.string.bass_range, lowFreq, highFreq), style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.smoothing, smoothing), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = smoothing,
            onValueChange = onSmoothingChange,
            valueRange = 0.1f..0.5f
        )
    }
}
