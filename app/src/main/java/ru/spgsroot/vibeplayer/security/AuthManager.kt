package ru.spgsroot.vibeplayer.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.authDataStore by preferencesDataStore("auth")

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val passwordKey = stringPreferencesKey("password_hash")
    private val onboardingKey = booleanPreferencesKey("onboarding_completed")
    private val failuresKey = intPreferencesKey("pin_failures")
    private val lockedUntilKey = longPreferencesKey("pin_locked_until")
    private val resetRequiredKey = booleanPreferencesKey("pin_reset_required")
    suspend fun setPassword(password: String) {
        val hash = withContext(Dispatchers.Default) { hashPassword(password) }
        context.authDataStore.edit { prefs ->
            prefs[passwordKey] = hash
            prefs[onboardingKey] = true
            prefs[failuresKey] = 0
            prefs[lockedUntilKey] = 0L
            prefs[resetRequiredKey] = false
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(currentPassword)) {
            return false
        }
        val hash = withContext(Dispatchers.Default) { hashPassword(newPassword) }
        context.authDataStore.edit { prefs ->
            prefs[passwordKey] = hash
            prefs[failuresKey] = 0
            prefs[lockedUntilKey] = 0L
            prefs[resetRequiredKey] = false
        }
        return true
    }

    suspend fun skipOnboarding() {
        context.authDataStore.edit { prefs ->
            prefs[onboardingKey] = true
        }
    }

    suspend fun verifyPassword(password: String): Boolean {
        val prefs = context.authDataStore.data.first()
        val stored = prefs[passwordKey] ?: return false
        val now = System.currentTimeMillis()
        val lockout = PinLockoutState(
            failures = prefs[failuresKey] ?: 0,
            lockedUntilEpochMs = prefs[lockedUntilKey] ?: 0L
        )
        if (PinLockout.remainingMs(lockout, now) > 0L) return false
        if (StoredPassword.isLegacy(stored)) {
            context.authDataStore.edit { it[resetRequiredKey] = true }
            return false
        }

        val isValid = withContext(Dispatchers.Default) { verifyStoredPassword(password, stored) }
        val next = if (isValid) PinLockout.registerSuccess() else PinLockout.registerFailure(lockout, now)
        context.authDataStore.edit { editable ->
            editable[failuresKey] = next.failures
            editable[lockedUntilKey] = next.lockedUntilEpochMs
        }
        return isValid
    }

    suspend fun isResetRequired(): Boolean {
        return context.authDataStore.data.map { it[resetRequiredKey] ?: false }.first()
    }

    suspend fun lockRemainingMs(now: Long = System.currentTimeMillis()): Long {
        val prefs = context.authDataStore.data.first()
        return PinLockout.remainingMs(
            PinLockoutState(
                failures = prefs[failuresKey] ?: 0,
                lockedUntilEpochMs = prefs[lockedUntilKey] ?: 0L
            ),
            now
        )
    }

    fun isPasswordSetFlow(): Flow<Boolean> {
        return context.authDataStore.data.map { it[passwordKey] != null }
    }

    suspend fun isPasswordSet(): Boolean {
        return context.authDataStore.data.map { it[passwordKey] }.first() != null
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return context.authDataStore.data.map { it[onboardingKey] ?: false }.first()
    }

    private fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
        val hash = deriveHash(password, salt, PBKDF2_ITERATIONS)
        return listOf(
            HASH_PREFIX,
            PBKDF2_ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString(DELIMITER)
    }

    private fun verifyStoredPassword(password: String, stored: String): Boolean {
        if (StoredPassword.isLegacy(stored)) return false
        val parts = stored.split(DELIMITER)
        if (parts.size != HASH_PARTS || parts[0] != HASH_PREFIX) return false
        return runCatching {
            val iterations = parts[1].toInt()
            if (iterations !in 1..PBKDF2_ITERATIONS) return false
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expectedHash = Base64.decode(parts[3], Base64.NO_WRAP)
            val actualHash = deriveHash(password, salt, iterations)
            MessageDigest.isEqual(actualHash, expectedHash)
        }.getOrDefault(false)
    }

    private fun deriveHash(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory
                .getInstance(PBKDF2_ALGORITHM)
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }


    companion object {
        private const val HASH_PREFIX = "pbkdf2_sha256"
        private const val DELIMITER = ":"
        private const val HASH_PARTS = 4
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256

        private val secureRandom = SecureRandom()
    }
}
