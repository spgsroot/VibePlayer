package ru.spgsroot.vibeplayer.device.buttplug

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.domain.dsp.HapticMapper
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CommandSender @Inject constructor(
    private val hapticMapper: HapticMapper,
    private val connectionManager: ButtplugConnectionManager
) {
    private var job: Job? = null
    private var watchdogJob: Job? = null
    private var lastIntensity = 0f
    private var lastSignalMs = 0L
    private var currentDeviceIndex = 0
    private val _activeDeviceIndex = MutableStateFlow<Int?>(null)
    val activeDeviceIndex: StateFlow<Int?> = _activeDeviceIndex.asStateFlow()

    // Scope for fire-and-forget stop commands
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope, deviceIndex: Int = 0) {
        stop(sendStopCommand = job != null)
        currentDeviceIndex = deviceIndex
        lastIntensity = 0f
        lastSignalMs = SystemClock.elapsedRealtime()
        _activeDeviceIndex.value = deviceIndex
        this.scope = scope
        job = scope.launch {
            hapticMapper.intensity.collectLatest { intensity ->
                lastSignalMs = SystemClock.elapsedRealtime()
                applyIntensity(intensity)
            }
        }
        startSilenceWatchdog(scope)
        sendTestPulse(scope)
    }

    fun stop() {
        stop(sendStopCommand = true)
    }

    private fun stop(sendStopCommand: Boolean) {
        job?.cancel()
        job = null
        watchdogJob?.cancel()
        watchdogJob = null
        if (sendStopCommand) {
            val s = scope
            if (s != null) {
                s.launch { sendStopDeviceCommand() }
            } else {
                // Fallback: create a one-shot scope for the stop
                CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    sendStopDeviceCommand()
                }
            }
        }
        _activeDeviceIndex.value = null
        scope = null
    }

    private fun startSilenceWatchdog(scope: CoroutineScope) {
        watchdogJob = scope.launch {
            while (isActive) {
                delay(SILENCE_TIMEOUT_MS / 2)
                if (lastIntensity > 0f && SystemClock.elapsedRealtime() - lastSignalMs >= SILENCE_TIMEOUT_MS) {
                    sendScalarCommand(0f)
                    lastIntensity = 0f
                }
            }
        }
    }

    private suspend fun applyIntensity(intensity: Float) {
        val normalizedIntensity = intensity.coerceIn(0f, 1f)
        val targetIntensity = if (normalizedIntensity <= SILENCE_THRESHOLD) 0f else normalizedIntensity

        if (targetIntensity == 0f) {
            if (lastIntensity > 0f) {
                sendScalarCommand(0f)
                lastIntensity = 0f
            }
            return
        }

        if (abs(targetIntensity - lastIntensity) >= CHANGE_THRESHOLD) {
            sendScalarCommand(targetIntensity)
            lastIntensity = targetIntensity
        }
    }

    private fun sendTestPulse(scope: CoroutineScope) {
        scope.launch {
            sendScalarCommand(TEST_PULSE_INTENSITY)
            delay(TEST_PULSE_MS)
            sendScalarCommand(0f)
            lastIntensity = 0f
        }
    }

    private suspend fun sendScalarCommand(intensity: Float) {
        val normalizedIntensity = intensity.coerceIn(0f, 1f)
        connectionManager.sendVibrate(
            deviceIndex = currentDeviceIndex,
            featureIndex = 0,
            speed = normalizedIntensity.toDouble()
        )
    }

    private suspend fun sendStopDeviceCommand() {
        connectionManager.sendStop(deviceIndex = currentDeviceIndex)
        lastIntensity = 0f
    }

    companion object {
        private const val TEST_PULSE_INTENSITY = 0.35f
        private const val TEST_PULSE_MS = 250L
        private const val CHANGE_THRESHOLD = 0.02f
        private const val SILENCE_THRESHOLD = 0.03f
        private const val SILENCE_TIMEOUT_MS = 350L
    }
}
