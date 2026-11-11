package ru.spgsroot.vibeplayer.device.buttplug

import io.github.spgsroot.buttplug.device.ActuatorType

fun ActuatorType.isAudioDrivable(): Boolean = when (this) {
    ActuatorType.Vibrate,
    ActuatorType.Oscillate,
    ActuatorType.Rotate,
    ActuatorType.Position,
    ActuatorType.HwPositionWithDuration -> true
    ActuatorType.Constrict,
    ActuatorType.Spray,
    ActuatorType.Temperature,
    ActuatorType.Led -> false
}
