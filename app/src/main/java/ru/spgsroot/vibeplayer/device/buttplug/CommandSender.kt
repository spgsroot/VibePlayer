package ru.spgsroot.vibeplayer.device.buttplug

import android.os.SystemClock
import io.github.spgsroot.buttplug.device.ActuatorType
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

/**
 * Target specification for a single actuator on a device feature.
 */
data class ActuatorTarget(
    val deviceIndex: Int,
    val featureIndex: Int,
    val actuatorType: ActuatorType
)

@Singleton
class CommandSender @Inject constructor(
    private val hapticMapper: HapticMapper,
    private val connectionManager: ButtplugConnectionManager
) {
    private var job: Job? = null
    private var watchdogJob: Job? = null
    private var lastIntensity = 0f
    private var lastSignalMs = 0L

    /** Current actuator targets. Empty list = nothing to control. */
    private var targets: List<ActuatorTarget> = emptyList()

    private val _activeDeviceIndex = MutableStateFlow<Int?>(null)
    val activeDeviceIndex: StateFlow<Int?> = _activeDeviceIndex.asStateFlow()

    // Scope for fire-and-forget stop commands
    private var scope: CoroutineScope? = null

    /**
     * Start sending haptic commands to a single device feature with default Vibrate actuator.
     * Backward-compatible convenience method.
     */
    fun start(scope: CoroutineScope, deviceIndex: Int = 0) {
        start(scope, listOf(ActuatorTarget(deviceIndex, 0, ActuatorType.Vibrate)))
    }

    /**
     * Start sending haptic commands to multiple targets.
     * Each intensity value from the DSP pipeline is sent to ALL targets.
     */
    fun start(scope: CoroutineScope, targets: List<ActuatorTarget>) {
        stop(sendStopCommand = job != null)
        this.targets = targets
        lastIntensity = 0f
        lastSignalMs = SystemClock.elapsedRealtime()
        _activeDeviceIndex.value = targets.firstOrNull()?.deviceIndex
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
                s.launch { sendStopToAll() }
            } else {
                CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    sendStopToAll()
                }
            }
        }
        _activeDeviceIndex.value = null
        scope = null
        targets = emptyList()
    }

    /**
     * Send stop to all targets' devices. Only sends once per unique device.
     */
    private suspend fun sendStopToAll() {
        val processedDevices = mutableSetOf<Int>()
        targets.forEach { target ->
            if (processedDevices.add(target.deviceIndex)) {
                connectionManager.sendStop(target.deviceIndex)
            }
        }
        lastIntensity = 0f
    }

    private fun startSilenceWatchdog(scope: CoroutineScope) {
        watchdogJob = scope.launch {
            while (isActive) {
                delay(SILENCE_TIMEOUT_MS / 2)
                if (lastIntensity > 0f && SystemClock.elapsedRealtime() - lastSignalMs >= SILENCE_TIMEOUT_MS) {
                    sendScalarToAll(0f)
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
                sendScalarToAll(0f)
                lastIntensity = 0f
            }
            return
        }

        if (abs(targetIntensity - lastIntensity) >= CHANGE_THRESHOLD) {
            sendScalarToAll(targetIntensity)
            lastIntensity = targetIntensity
        }
    }

    private fun sendTestPulse(scope: CoroutineScope) {
        scope.launch {
            sendScalarToAll(TEST_PULSE_INTENSITY)
            delay(TEST_PULSE_MS)
            sendScalarToAll(0f)
            lastIntensity = 0f
        }
    }

    /** Power boost multiplier: 1.0 = 100%, 2.0 = 200%. Applied to all intensities. */
    @Volatile var powerBoost: Float = 1.0f

    /** Send intensity to ALL configured actuator targets with power boost applied. */
    private suspend fun sendScalarToAll(intensity: Float) {
        val boosted = (intensity * powerBoost).coerceIn(0f, 1f)
        targets.forEach { target ->
            connectionManager.sendActuator(
                deviceIndex = target.deviceIndex,
                featureIndex = target.featureIndex,
                actuator = target.actuatorType,
                speed = boosted.toDouble()
            )
        }
    }

    companion object {
        private const val TEST_PULSE_INTENSITY = 0.35f
        private const val TEST_PULSE_MS = 250L
        private const val CHANGE_THRESHOLD = 0.02f
        private const val SILENCE_THRESHOLD = 0.03f
        private const val SILENCE_TIMEOUT_MS = 350L
    }
}
