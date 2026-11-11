package ru.spgsroot.vibeplayer.device.buttplug

import io.github.spgsroot.buttplug.device.ActuatorType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivableActuatorTest {
    @Test
    fun audioSyncDoesNotDriveHeatConstrictSprayOrLed() {
        assertTrue(ActuatorType.Vibrate.isAudioDrivable())
        assertTrue(ActuatorType.Oscillate.isAudioDrivable())
        assertTrue(ActuatorType.Rotate.isAudioDrivable())
        assertTrue(ActuatorType.Position.isAudioDrivable())
        assertTrue(ActuatorType.HwPositionWithDuration.isAudioDrivable())

        assertFalse(ActuatorType.Temperature.isAudioDrivable())
        assertFalse(ActuatorType.Constrict.isAudioDrivable())
        assertFalse(ActuatorType.Spray.isAudioDrivable())
        assertFalse(ActuatorType.Led.isAudioDrivable())
    }
}
