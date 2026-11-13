package ru.spgsroot.peachy.translate

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val DEFAULT_BASE_URL = "https://opencode.ai/inference/go/v1"
const val DEFAULT_MODEL = "glm-5.3-flash"

/** Where page images go and who pays for it. The key is the user's own and never leaves the phone except in requests. */
data class ProviderSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String = "",
    /** The user has read which host receives page images and agreed. */
    val consented: Boolean = false
) {
    val isConfigured: Boolean
        get() = ProviderKey.isValid(apiKey) && model.isNotBlank() && ProviderUrl.isAllowed(baseUrl)

    override fun toString(): String =
        "ProviderSettings(baseUrl=$baseUrl, model=$model, apiKey=${if (apiKey.isEmpty()) "" else "***"}, consented=$consented)"

    /**
     * Applies what the provider dialog collected. A blank key keeps the stored one; a different host
     * drops the consent, because the user agreed to send pages to the host that was named, not to this one.
     */
    fun edited(baseUrl: String, model: String, apiKey: String): ProviderSettings {
        val url = ProviderUrl.normalized(baseUrl)
        return copy(
            baseUrl = url,
            model = model.trim(),
            apiKey = if (apiKey.isBlank()) this.apiKey else apiKey.trim(),
            consented = consented && ProviderUrl.host(url) == ProviderUrl.host(this.baseUrl)
        )
    }
}

/** What an API key may look like: printable ASCII, so it is always a legal header value. */
object ProviderKey {
    fun isValid(key: String): Boolean = key.isNotEmpty() && key.all { it in '!'..'~' }
}

object ProviderUrl {
    /** `https`, or plain `http` only to the user's own machine or network. */
    fun isAllowed(url: String): Boolean {
        val uri = parse(url) ?: return false
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
        if (uri.userInfo != null) return false
        // Whatever java.net.URI accepts, the request builder must accept too (a port above 65535 is not).
        if (url.trim().toHttpUrlOrNull() == null) return false
        return when (uri.scheme?.lowercase()) {
            "https" -> true
            "http" -> host == "localhost" || isPrivateIpv4(host)
            else -> false
        }
    }

    fun normalized(url: String): String = url.trim().trimEnd('/')

    /** The lower-case host of [url], or null when it has none. */
    fun host(url: String): String? = parse(url)?.host?.lowercase()?.takeIf { it.isNotEmpty() }

    /** Only the OpenCode service asks clients to identify a conversation. */
    fun sendsSessionHeader(url: String): Boolean {
        val host = parse(url)?.host?.lowercase() ?: return false
        return host == "opencode.ai" || host.endsWith(".opencode.ai")
    }

    private fun parse(url: String): URI? = try {
        URI(url.trim())
    } catch (e: URISyntaxException) {
        null
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return false
            // "010" is octal to some resolvers: not the same address as 10.
            if (part.length > 1 && part[0] == '0') return false
            part.toInt().also { if (it > 255) return false }
        }
        return when (octets[0]) {
            10, 127 -> true
            192 -> octets[1] == 168
            172 -> octets[1] in 16..31
            else -> false
        }
    }
}

/** The provider choice, kept encrypted on the device; it is never written to the database or the vault. */
@Singleton
class ProviderSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<ProviderSettings> = _settings.asStateFlow()

    @Synchronized
    fun update(change: (ProviderSettings) -> ProviderSettings) {
        val next = change(_settings.value)
        prefs.edit()
            .putString(BASE_URL, next.baseUrl)
            .putString(MODEL, next.model)
            .putString(API_KEY, next.apiKey)
            .putBoolean(CONSENTED, next.consented)
            .apply()
        _settings.value = next
    }

    private fun read(): ProviderSettings = ProviderSettings(
        baseUrl = prefs.getString(BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL,
        model = prefs.getString(MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
        apiKey = prefs.getString(API_KEY, null).orEmpty(),
        consented = prefs.getBoolean(CONSENTED, false)
    )

    private companion object {
        const val PREFS_NAME = "translate_provider"
        const val BASE_URL = "base_url"
        const val MODEL = "model"
        const val API_KEY = "api_key"
        const val CONSENTED = "consented"
    }
}
