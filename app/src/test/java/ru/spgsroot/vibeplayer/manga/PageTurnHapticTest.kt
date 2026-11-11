package ru.spgsroot.vibeplayer.manga

import io.github.spgsroot.buttplug.device.ActuatorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.spgsroot.vibeplayer.device.buttplug.isAudioDrivable

class PageTurnHapticTest {
    @Test
    fun pulseRisesAndReturnsToZero() {
        val steps = PageTurnHaptic.steps(intensity = 0.4f, durationMs = 180L)
        assertEquals(0f, steps.first())
        assertEquals(0.4f, steps.maxOrNull())
        assertEquals(0f, steps.last())
        assertTrue(steps.size >= 3)
    }

    @Test
    fun pageTurnDoesNotDriveHeatConstrictSprayOrLed() {
        ActuatorType.entries.forEach { type ->
            val driven = PageTurnHaptic.drives(type)
            if (type.isAudioDrivable()) {
                assertTrue(driven)
            } else {
                assertFalse(driven)
            }
        }
    }
}
