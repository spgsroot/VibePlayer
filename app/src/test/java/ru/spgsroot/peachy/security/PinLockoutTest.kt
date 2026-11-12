package ru.spgsroot.peachy.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockoutTest {
    @Test
    fun fifthFailureLocksForThirtySeconds() {
        var state = PinLockoutState()
        repeat(5) { state = PinLockout.registerFailure(state, now = 1_000L) }

        assertEquals(30_000L, PinLockout.remainingMs(state, now = 1_000L))
    }

    @Test
    fun successClearsFailures() {
        val locked = PinLockout.registerFailure(
            PinLockoutState(failures = 5, lockedUntilEpochMs = 31_000L),
            now = 1_000L
        )

        val cleared = PinLockout.registerSuccess()

        assertEquals(0, cleared.failures)
        assertEquals(0L, PinLockout.remainingMs(cleared, now = 1_000L))
        assertTrue(PinLockout.remainingMs(locked, now = 1_000L) > 0L)
    }

    @Test
    fun legacyHashIsNotAValidScheme() {
        assertTrue(StoredPassword.isLegacy("123456"))
        assertTrue(!StoredPassword.isLegacy("pbkdf2_sha256:120000:salt:hash"))
    }
}
