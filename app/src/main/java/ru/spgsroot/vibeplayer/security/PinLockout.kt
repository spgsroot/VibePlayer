package ru.spgsroot.vibeplayer.security

data class PinLockoutState(
    val failures: Int = 0,
    val lockedUntilEpochMs: Long = 0L
)

object PinLockout {
    const val FAILURES_BEFORE_LOCK = 5
    private const val FIRST_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 15 * 60_000L

    fun registerFailure(state: PinLockoutState, now: Long): PinLockoutState {
        val failures = state.failures + 1
        if (failures < FAILURES_BEFORE_LOCK) {
            return PinLockoutState(failures = failures)
        }
        val stepsPastThreshold = failures - FAILURES_BEFORE_LOCK
        val delay = FIRST_LOCK_MS shl stepsPastThreshold.coerceAtMost(5)
        return PinLockoutState(
            failures = failures,
            lockedUntilEpochMs = now + delay.coerceAtMost(MAX_LOCK_MS)
        )
    }

    fun registerSuccess(): PinLockoutState = PinLockoutState()

    fun remainingMs(state: PinLockoutState, now: Long): Long =
        (state.lockedUntilEpochMs - now).coerceAtLeast(0L)
}

object StoredPassword {
    private const val PREFIX = "pbkdf2_sha256:"

    fun isLegacy(stored: String): Boolean = !stored.startsWith(PREFIX)
}
