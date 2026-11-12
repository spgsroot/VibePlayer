package ru.spgsroot.peachy.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMotionTest {
    @Test
    fun firstIdlePageDoesNotPulse() {
        assertFalse(PageTurnGate().onPage(page = 3, idle = true))
    }

    @Test
    fun laterIdleChangePulsesOnlyOnce() {
        val gate = PageTurnGate()
        assertFalse(gate.onPage(page = 0, idle = true))
        assertTrue(gate.onPage(page = 1, idle = true))
        assertFalse(gate.onPage(page = 1, idle = true))
        assertTrue(gate.onPage(page = 0, idle = true))
    }

    @Test
    fun movingPagesDoNotEstablishTheInitialIdlePage() {
        val gate = PageTurnGate()
        assertFalse(gate.onPage(page = 1, idle = false))
        assertFalse(gate.onPage(page = 2, idle = false))
        assertFalse(gate.onPage(page = 2, idle = true))
    }

    @Test
    fun flingPulsesOnceAfterSettling() {
        val gate = PageTurnGate()
        assertFalse(gate.onPage(page = 0, idle = true))
        assertFalse(gate.onPage(page = 1, idle = false))
        assertFalse(gate.onPage(page = 2, idle = false))
        assertTrue(gate.onPage(page = 2, idle = true))
        assertFalse(gate.onPage(page = 2, idle = true))
    }

    @Test
    fun movingAwayAndSettlingOnTheSamePageDoesNotPulse() {
        val gate = PageTurnGate()
        assertFalse(gate.onPage(page = 0, idle = true))
        assertFalse(gate.onPage(page = 1, idle = false))
        assertFalse(gate.onPage(page = 0, idle = true))
    }

    @Test
    fun centerPageUsesTheNearestPageCenter() {
        assertEquals(1, centerPage(listOf(100, 300, 500), 310))
        assertEquals(0, centerPage(listOf(100, 300, 500), 90))
        assertEquals(2, centerPage(listOf(100, 300, 500), 510))
    }

    @Test
    fun tapsUseLeftCenterAndRightThirds() {
        val width = 300f
        assertEquals(TapZone.Back, tapZone(width / 3 - 1, width, mirror = false))
        assertEquals(TapZone.Chrome, tapZone(width / 2, width, mirror = false))
        assertEquals(TapZone.Forward, tapZone(width * 2 / 3 + 1, width, mirror = false))
    }

    @Test
    fun mirroredTapsSwapOnlyBackAndForward() {
        val width = 300f
        assertEquals(TapZone.Forward, tapZone(width / 3 - 1, width, mirror = true))
        assertEquals(TapZone.Chrome, tapZone(width / 2, width, mirror = true))
        assertEquals(TapZone.Back, tapZone(width * 2 / 3 + 1, width, mirror = true))
    }

    @Test
    fun missingSavedPageStartsAtZero() {
        assertEquals(0, startPage(null, 10))
    }

    @Test
    fun validSavedPageIsResumed() {
        assertEquals(3, startPage(3, 10))
        assertEquals(9, startPage(9, 10))
    }

    @Test
    fun savedPageAtOrPastThePageCountStartsAtZero() {
        assertEquals(0, startPage(10, 10))
        assertEquals(0, startPage(11, 10))
    }

    @Test
    fun negativeSavedPageStartsAtZero() {
        assertEquals(0, startPage(-1, 10))
    }
}
