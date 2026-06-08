package ru.spgsroot.vibeplayer.device.buttplug

import android.os.SystemClock
import android.util.Log
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
import ru.spgsroot.vibeplayer.domain.dsp.HapticRuntimeConfig
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
    private val runtimeConfig: HapticRuntimeConfig,
    private val connectionManager: ButtplugConnectionManager
) {
    private var job: Job? = null
    private var watchdogJob: Job? = null
    private var lastIntensity = 0f
    private var lastSignalMs = 0L
    private var lastLogMs = 0L
    private var lastSendLogMs = 0L

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
        Log.d(TAG, "CommandSender started with ${targets.size} target(s): $targets")

        job = scope.launch {
            hapticMapper.intensity.collectLatest { intensity ->
                lastSignalMs = SystemClock.elapsedRealtime()
                logIntensity(intensity)
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
        if (job != null || targets.isNotEmpty()) {
            Log.d(TAG, "CommandSender stopped")
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
        val threshold = runtimeConfig.threshold
        val targetIntensity = if (normalizedIntensity <= threshold) 0f else normalizedIntensity

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

    /** Send intensity to ALL configured actuator targets with power boost applied. */
    private suspend fun sendScalarToAll(intensity: Float) {
        val boosted = (intensity * runtimeConfig.powerBoost).coerceIn(0f, 1f)
        logSend(intensity, boosted)
        targets.forEach { target ->
            connectionManager.sendActuator(
                deviceIndex = target.deviceIndex,
                featureIndex = target.featureIndex,
                actuator = target.actuatorType,
                speed = boosted.toDouble()
            )
        }
    }

    private fun logSend(intensity: Float, boosted: Float) {
        val now = SystemClock.elapsedRealtime()
        if (targets.isEmpty()) {
            if (now - lastSendLogMs >= LOG_INTERVAL_MS) {
                Log.w(TAG, "No actuator targets; intensity=$intensity boosted=$boosted was not sent")
                lastSendLogMs = now
            }
            return
        }
        if (boosted > 0f && now - lastSendLogMs >= LOG_INTERVAL_MS) {
            Log.d(TAG, "Sending haptic intensity=$intensity boosted=$boosted to ${targets.size} target(s)")
            lastSendLogMs = now
        }
    }

    private fun logIntensity(intensity: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogMs >= LOG_INTERVAL_MS) {
            val boosted = (intensity * runtimeConfig.powerBoost).coerceIn(0f, 1f)
            Log.d(
                TAG,
                "Collected haptic intensity=$intensity boosted=$boosted threshold=${runtimeConfig.threshold} targets=${targets.size}"
            )
            lastLogMs = now
        }
    }

    companion object {
        private const val TAG = "CommandSender"
        private const val TEST_PULSE_INTENSITY = 0.35f
        private const val TEST_PULSE_MS = 250L
        private const val CHANGE_THRESHOLD = 0.02f
        private const val SILENCE_TIMEOUT_MS = 350L
        private const val LOG_INTERVAL_MS = 1_000L
    }
}
