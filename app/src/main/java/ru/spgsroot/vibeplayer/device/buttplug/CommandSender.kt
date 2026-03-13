package ru.spgsroot.vibeplayer.device.buttplug

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.spgsroot.vibeplayer.domain.dsp.HapticMapper
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandSender @Inject constructor(
    private val hapticMapper: HapticMapper,
    private val connectionManager: ButtplugConnectionManager
) {
    private var job: Job? = null
    private var lastIntensity = 0f
    private val messageIdCounter = AtomicInteger(1)
    private var currentDeviceIndex = 0

    fun start(scope: CoroutineScope, deviceIndex: Int = 0) {
        currentDeviceIndex = deviceIndex
        job = scope.launch {
            hapticMapper.intensity.collectLatest { intensity ->
                if (kotlin.math.abs(intensity - lastIntensity) > 0.02f) {
                    sendVibrateCommand(intensity)
                    lastIntensity = intensity
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun sendVibrateCommand(intensity: Float) {
        val id = messageIdCounter.getAndIncrement()
        val command = """{"VibrateCmd":{"Id":$id,"DeviceIndex":$currentDeviceIndex,"Speeds":[{"Index":0,"Speed":$intensity}]}}"""
        connectionManager.sendCommand(command)
    }
}
