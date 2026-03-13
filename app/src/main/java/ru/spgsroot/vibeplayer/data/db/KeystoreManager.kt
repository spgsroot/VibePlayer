package ru.spgsroot.vibeplayer.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object KeystoreManager {
    private const val KEYSTORE_ALIAS = "vibeplayer_db_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFS_NAME = "db_encryption_key"
    private const val KEY_FALLBACK = "db_key_fallback"

    fun getOrCreateKey(context: Context): ByteArray {
        // Try to get from EncryptedSharedPreferences first (more reliable)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val storedKey = prefs.getString(KEY_FALLBACK, null)
        if (storedKey != null) {
            return Base64.decode(storedKey, Base64.DEFAULT)
        }

        // Generate new key
        val keyBytes = generateOrRetrieveKey()

        // Store fallback
        prefs.edit().putString(KEY_FALLBACK, Base64.encodeToString(keyBytes, Base64.DEFAULT)).apply()

        return keyBytes
    }

    private fun generateOrRetrieveKey(): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        val key = if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        } else {
            generateKey()
        }

        // If key.encoded is null, generate a new random key
        return key?.encoded ?: generateFallbackKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun generateFallbackKey(): ByteArray {
        val randomKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomKey)
        return randomKey
    }
}
