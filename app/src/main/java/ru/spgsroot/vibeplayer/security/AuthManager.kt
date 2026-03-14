package ru.spgsroot.vibeplayer.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore("auth")

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val passwordKey = stringPreferencesKey("password_hash")
    private val onboardingKey = booleanPreferencesKey("onboarding_completed")

    suspend fun setPassword(password: String) {
        val hash = password.hashCode().toString()
        context.authDataStore.edit { prefs ->
            prefs[passwordKey] = hash
            prefs[onboardingKey] = true
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(currentPassword)) {
            return false
        }
        val hash = newPassword.hashCode().toString()
        context.authDataStore.edit { prefs ->
            prefs[passwordKey] = hash
        }
        return true
    }

    suspend fun skipOnboarding() {
        context.authDataStore.edit { prefs ->
            prefs[onboardingKey] = true
        }
    }

    suspend fun verifyPassword(password: String): Boolean {
        val hash = password.hashCode().toString()
        val stored = context.authDataStore.data.map { it[passwordKey] }.first()
        return hash == stored
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
}
