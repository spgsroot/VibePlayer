package ru.spgsroot.vibeplayer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.data.repository.SettingsRepository
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.vibeplayer.domain.model.Settings
import ru.spgsroot.vibeplayer.security.AuthManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager,
    val connectionManager: ButtplugConnectionManager
) : ViewModel() {

    val settings: StateFlow<Settings?> = settingsRepository.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    fun startDeviceScan() {
        viewModelScope.launch {
            connectionManager.connect()
            connectionManager.startScanning()
        }
    }

    fun connectDevice(deviceName: String) {
        viewModelScope.launch {
            // Device connection is handled automatically when user selects from DeviceScanDialog
            android.util.Log.d("SettingsViewModel", "Device selected: $deviceName")
        }
    }
}
