package ru.spgsroot.vibeplayer.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
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

    suspend fun setPassword(password: String) {
        val hash = password.hashCode().toString()
        context.authDataStore.edit { prefs ->
            prefs[passwordKey] = hash
        }
    }

    suspend fun verifyPassword(password: String): Boolean {
        val hash = password.hashCode().toString()
        val stored = context.authDataStore.data.map { it[passwordKey] }.first()
        return hash == stored
    }

    suspend fun isPasswordSet(): Boolean {
        return context.authDataStore.data.map { it[passwordKey] }.first() != null
    }
}
