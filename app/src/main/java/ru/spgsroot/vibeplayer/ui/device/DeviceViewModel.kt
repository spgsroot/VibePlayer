package ru.spgsroot.vibeplayer.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.device.buttplug.ButtplugConnectionManager
import ru.spgsroot.vibeplayer.device.buttplug.CommandSender
import ru.spgsroot.vibeplayer.device.buttplug.DeviceState
import javax.inject.Inject

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val connectionManager: ButtplugConnectionManager,
    private val commandSender: CommandSender
) : ViewModel() {

    val deviceState: StateFlow<DeviceState> = connectionManager.state

    fun connect() {
        connectionManager.connect()
        commandSender.start(viewModelScope)
    }

    fun disconnect() {
        commandSender.stop()
        connectionManager.disconnect()
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
