package ru.spgsroot.peachy.translate

import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface PageTranslator {
    /** Reads one page image and returns its text translated into [targetLanguage]; throws [TranslationException]. */
    suspend fun translate(galleryId: Int, jpeg: ByteArray, targetLanguage: String): PageTranslation
}

/** Identifies the conversation to OpenCode; other hosts do not get the header. */
internal fun sessionHeader(baseUrl: String, galleryId: Int): String? =
    if (ProviderUrl.sendsSessionHeader(baseUrl)) "peachy-$galleryId" else null

/** One OpenAI-compatible `chat/completions` call per page, with the page as a data URI. */
class TranslationClient(
    http: OkHttpClient,
    private val settings: () -> ProviderSettings,
    private val userAgent: String
) : PageTranslator {
    // The model can take 100 s on a busy page.
    private val http = http.newBuilder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun translate(galleryId: Int, jpeg: ByteArray, targetLanguage: String): PageTranslation =
        withContext(Dispatchers.IO) {
            val provider = configured()
            val body = pageBody(provider, jpeg, targetLanguage)
            // A reply that is not valid JSON is usually a one-off: ask again once.
            repeat(2) {
                val content = send(provider, body, sessionHeader(provider.baseUrl, galleryId))
                PageTranslation.parse(content)?.let { return@withContext it }
            }
            throw TranslationException(TranslationError.BadResponse)
        }

    /** A tiny text-only request to see whether the address, key and model work. */
    suspend fun check() {
        withContext(Dispatchers.IO) {
            val provider = configured()
            val body = JSONObject()
                .put("model", provider.model)
                .put("max_tokens", 8)
                .put("messages", JSONArray().put(message("user", PagePrompt.CHECK)))
            send(provider, body, null)
        }
    }

    private fun configured(): ProviderSettings {
        val provider = settings()
        if (!provider.isConfigured) throw TranslationException(TranslationError.NotConfigured)
        return provider
    }

    private fun message(role: String, content: Any) = JSONObject().put("role", role).put("content", content)

    private fun pageBody(provider: ProviderSettings, jpeg: ByteArray, targetLanguage: String): JSONObject {
        val uri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
        val user = JSONArray()
            .put(JSONObject().put("type", "text").put("text", PagePrompt.USER))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", uri)))
        return JSONObject()
            .put("model", provider.model)
            .put("temperature", 0.2)
            .put("max_tokens", 4096)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray().put(message("system", PagePrompt.system(targetLanguage))).put(message("user", user))
            )
    }

    /** Posts [body] and returns `choices[0].message.content`. */
    private suspend fun send(provider: ProviderSettings, body: JSONObject, session: String?): String {
        val request = Request.Builder()
            .url(ProviderUrl.normalized(provider.baseUrl) + "/chat/completions")
            .header("Authorization", "Bearer ${provider.apiKey}")
            .header("User-Agent", userAgent)
            .apply { session?.let { header("x-opencode-session", it) } }
            .post(body.toString().toRequestBody(JSON))
            .build()
        val text = try {
            execute(http.newCall(request))
        } catch (e: CancellationException) {
            throw e
        } catch (e: TranslationException) {
            throw e
        } catch (e: IOException) {
            throw TranslationException(TranslationError.Network, e)
        } catch (e: IllegalArgumentException) {
            throw TranslationException(TranslationError.NotConfigured, e)
        }
        return contentOf(text)
    }

    private suspend fun execute(call: Call): String = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        when {
                            it.code == 401 || it.code == 403 -> cont.resumeWithException(TranslationException(TranslationError.Auth))
                            it.code == 429 -> cont.resumeWithException(TranslationException(TranslationError.RateLimit))
                            !it.isSuccessful -> cont.resumeWithException(TranslationException(TranslationError.Server(it.code)))
                            else -> cont.resume(it.body?.string().orEmpty())
                        }
                    } catch (e: IOException) {
                        cont.resumeWithException(e)
                    }
                }
            }
        })
    }

    /** The reply text, or [TranslationError.Refused] when the model gave none or the provider filtered it. */
    private fun contentOf(envelope: String): String {
        val choice = try {
            JSONObject(envelope).optJSONArray("choices")?.optJSONObject(0)
        } catch (e: JSONException) {
            throw TranslationException(TranslationError.BadResponse, e)
        }
        if (choice == null || choice.optString("finish_reason") == "content_filter") {
            throw TranslationException(TranslationError.Refused)
        }
        val content = choice.optJSONObject("message")?.takeIf { !it.isNull("content") }?.optString("content").orEmpty()
        if (content.isBlank()) throw TranslationException(TranslationError.Refused)
        return content
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
