package ru.spgsroot.vibeplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.data.repository.SettingsRepository
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugDevice
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.vibeplayer.device.buttplug.CommandSender
import ru.spgsroot.vibeplayer.device.buttplug.DeviceState
import ru.spgsroot.vibeplayer.domain.model.Settings
import ru.spgsroot.vibeplayer.security.AuthManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager,
    val connectionManager: ButtplugConnectionManager,
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
        // Sync powerBoost from settings to CommandSender
        viewModelScope.launch {
            settingsRepository.getSettings().collect { s ->
                s?.let { commandSender.powerBoost = it.dspConfig.powerBoost }
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
        // Collect all actuator features of the selected device
        val libDevice = connectionManager.rawDevices.firstOrNull { it.index == deviceIndex }
        val targets = if (libDevice != null) {
            libDevice.features.flatMap { feature ->
                feature.outputTypes.map { actuator ->
                    ru.spgsroot.vibeplayer.device.buttplug.ActuatorTarget(
                        deviceIndex = deviceIndex,
                        featureIndex = feature.index,
                        actuatorType = actuator
                    )
                }
            }
        } else {
            // Fallback: default single Vibrate on feature 0
            listOf(
                ru.spgsroot.vibeplayer.device.buttplug.ActuatorTarget(
                    deviceIndex = deviceIndex,
                    featureIndex = 0,
                    actuatorType = io.github.spgsroot.buttplug.device.ActuatorType.Vibrate
                )
            )
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
