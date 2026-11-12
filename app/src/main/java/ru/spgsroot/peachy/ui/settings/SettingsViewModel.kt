package ru.spgsroot.peachy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.data.repository.SettingsRepository
import ru.spgsroot.peachy.device.buttplug.ButtplugDevice
import ru.spgsroot.peachy.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.peachy.device.buttplug.CommandSender
import ru.spgsroot.peachy.device.buttplug.DeviceState
import ru.spgsroot.peachy.device.buttplug.isAudioDrivable
import ru.spgsroot.peachy.domain.dsp.HapticRuntimeConfig
import ru.spgsroot.peachy.domain.model.Settings
import ru.spgsroot.peachy.security.AuthManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager,
    val connectionManager: ButtplugConnectionManager,
    private val hapticRuntimeConfig: HapticRuntimeConfig,
    private val commandSender: CommandSender
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPasswordSet: StateFlow<Boolean> = authManager.isPasswordSetFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val deviceState: StateFlow<DeviceState> = connectionManager.state
    val devices: StateFlow<List<ButtplugDevice>> = connectionManager.devices
    val activeDeviceIndex: StateFlow<Int?> = commandSender.activeDeviceIndex

    init {
        // Keep one shared DSP runtime config for all haptic sources (Player and WebView).
        viewModelScope.launch {
            settingsRepository.getSettings().collect { s ->
                s?.let { hapticRuntimeConfig.update(it.dspConfig) }
            }
        }
    }

    fun updateTimer(timerMs: Long) {
        viewModelScope.launch {
            settingsRepository.updateTimer(timerMs)
        }
    }

    fun updatePlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsRepository.updatePlaybackSpeed(speed)
        }
    }

    fun updateDspLowFreq(freq: Int) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            settingsRepository.updateDspConfig(
                current.dspConfig.copy(lowFreqHz = freq)
            )
        }
    }

    fun updateDspHighFreq(freq: Int) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            settingsRepository.updateDspConfig(
                current.dspConfig.copy(highFreqHz = freq)
            )
        }
    }

    fun updateDspSmoothing(alpha: Float) {
        viewModelScope.launch {
            val current = settings.value ?: return@launch
            settingsRepository.updateDspConfig(
                current.dspConfig.copy(smoothingAlpha = alpha)
            )
        }
    }

    fun updateDspPowerBoost(powerBoost: Float) {
        viewModelScope.launch {
            settingsRepository.updateDspPowerBoost(powerBoost)
        }
    }

    fun updateDspThreshold(threshold: Float) {
        viewModelScope.launch {
            settingsRepository.updateDspThreshold(threshold)
        }
    }

    fun updateLanguage(languageCode: String) {
        viewModelScope.launch {
            settingsRepository.updateLanguage(languageCode)
        }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            authManager.setPassword(password)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val success = authManager.changePassword(currentPassword, newPassword)
            if (success) {
                onSuccess()
            } else {
                onError()
            }
        }
    }

    fun connectDevice(deviceIndex: Int) {
        android.util.Log.d("SettingsViewModel", "Device selected: $deviceIndex")
        val libDevice = connectionManager.rawDevices.firstOrNull { it.index == deviceIndex }
        val targets = libDevice?.features?.flatMap { feature ->
            feature.outputTypes
                .filter { it.isAudioDrivable() }
                .map { actuator ->
                    ru.spgsroot.peachy.device.buttplug.ActuatorTarget(
                        deviceIndex = deviceIndex,
                        featureIndex = feature.index,
                        actuatorType = actuator
                    )
                }
        }.orEmpty()
        if (targets.isEmpty()) {
            android.util.Log.w(
                "SettingsViewModel",
                "No audio-drivable actuators on device $deviceIndex; CommandSender not started"
            )
            return
        }
        commandSender.start(viewModelScope, targets)
    }

    fun disconnectDevice() {
        commandSender.stop()
        connectionManager.disconnect()
    }

    override fun onCleared() {
        commandSender.stop()
        super.onCleared()
    }
}
