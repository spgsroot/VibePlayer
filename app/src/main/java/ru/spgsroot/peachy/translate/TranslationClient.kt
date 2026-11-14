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
    /** Reads one page using [request] and returns translated text; throws [TranslationException]. */
    suspend fun translate(galleryId: Int, jpeg: ByteArray, request: TranslateRequest): PageTranslation
}

/** A translated OCR block, identified by its zero-based position in the input. */
data class IndexedTranslation(val index: Int, val kind: LineKind, val translation: String)

/** Identifies the conversation to OpenCode; other hosts do not get the header. */
internal fun sessionHeader(baseUrl: String, galleryId: Int): String? =
    if (ProviderUrl.sendsSessionHeader(baseUrl)) "peachy-$galleryId" else null

/** The test request belongs to no gallery; OpenCode still wants every request to name a session. */
private const val CHECK_SESSION_ID = 0

/** OpenAI-compatible image and OCR-text translation calls sharing the same provider policy. */
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
        // A page must not follow a redirect to a host (or to plain http) the user never named.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun translate(galleryId: Int, jpeg: ByteArray, request: TranslateRequest): PageTranslation =
        complete(galleryId, { pageBody(it, jpeg, request) }, PageTranslation::parse)

    suspend fun translateBlocks(
        galleryId: Int,
        blocks: List<OcrBlock>,
        source: String,
        target: String
    ): List<IndexedTranslation> = complete(
        galleryId,
        { chatBody(it, PagePrompt.blocks(target, source), PagePrompt.blocksUser(blocks)) },
        ::parseBlocks
    )

    private suspend fun <T : Any> complete(
        galleryId: Int,
        bodyFor: (ProviderSettings) -> JSONObject,
        parse: (String) -> T?
    ): T =
        withContext(Dispatchers.IO) {
            // Read at send time, so a host changed or consent withdrawn mid-batch stops the next page.
            val provider = settings()
            if (!provider.isConfigured || !provider.consented) throw TranslationException(TranslationError.NotConfigured)
            val body = bodyFor(provider)
            // A reply that is not valid JSON is usually a one-off: ask again once.
            repeat(2) {
                val content = send(provider, body, sessionHeader(provider.baseUrl, galleryId))
                parse(content)?.let { return@withContext it }
            }
            throw TranslationException(TranslationError.BadResponse)
        }

    /**
     * A tiny text-only request to see whether the address, key and model of [provider] work. It
     * carries no page, so it needs no consent, and [provider] may be a draft that is not saved.
     */
    suspend fun check(provider: ProviderSettings) {
        withContext(Dispatchers.IO) {
            if (!provider.isConfigured) throw TranslationException(TranslationError.NotConfigured)
            val body = JSONObject()
                .put("model", provider.model)
                .put("max_tokens", 8)
                .put("messages", JSONArray().put(message("user", PagePrompt.CHECK)))
            try {
                send(provider, body, sessionHeader(provider.baseUrl, CHECK_SESSION_ID))
            } catch (e: TranslationException) {
                // A model that spent its whole small budget thinking still proves address, key and model work.
                if (e.error != TranslationError.Refused) throw e
            }
        }
    }

    private fun message(role: String, content: Any) = JSONObject().put("role", role).put("content", content)

    private fun pageBody(provider: ProviderSettings, jpeg: ByteArray, request: TranslateRequest): JSONObject {
        val uri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)
        val user = JSONArray()
            .put(JSONObject().put("type", "text").put("text", PagePrompt.USER))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", uri)))
        return chatBody(provider, PagePrompt.system(request.target, request.source), user)
    }

    private fun chatBody(provider: ProviderSettings, system: String, user: Any): JSONObject =
        JSONObject()
            .put("model", provider.model)
            .put("temperature", 0.2)
            .put("max_tokens", 4096)
            .put("response_format", JSONObject().put("type", "json_object"))
            .put(
                "messages",
                JSONArray().put(message("system", system)).put(message("user", user))
            )

    /** Posts [body] and returns `choices[0].message.content`. */
    private suspend fun send(provider: ProviderSettings, body: JSONObject, session: String?): String {
        val text = try {
            val request = Request.Builder()
                .url(ProviderUrl.normalized(provider.baseUrl) + "/chat/completions")
                .header("Authorization", "Bearer ${provider.apiKey}")
                .header("User-Agent", userAgent)
                .apply { session?.let { header("x-opencode-session", it) } }
                .post(adaptLimits(body, provider.baseUrl).toString().toRequestBody(JSON))
                .build()
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
                            it.code == 429 -> cont.resumeWithException(
                                TranslationException(TranslationError.RateLimit(retryAfterSeconds(it)))
                            )
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

    /** Seconds from a numeric `Retry-After`; the HTTP-date form is not worth parsing for a message. */
    private fun retryAfterSeconds(response: Response): Int? =
        response.header("Retry-After")?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    /**
     * The reply text. [TranslationError.BadResponse] when the provider sent an error object instead
     * of a completion; [TranslationError.Refused] when the model gave none or the provider filtered it.
     */
    private fun contentOf(envelope: String): String {
        val root = try {
            JSONObject(envelope)
        } catch (e: JSONException) {
            throw TranslationException(TranslationError.BadResponse, e)
        }
        val choices = root.optJSONArray("choices")
        if (choices == null && root.has("error")) throw TranslationException(TranslationError.BadResponse)
        val choice = choices?.optJSONObject(0)
        if (choice == null || choice.optString("finish_reason") == "content_filter") {
            throw TranslationException(TranslationError.Refused)
        }
        val content = choice.optJSONObject("message")?.takeIf { !it.isNull("content") }?.optString("content").orEmpty()
        if (content.isBlank()) throw TranslationException(TranslationError.Refused)
        return content
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        private fun parseBlocks(raw: String): List<IndexedTranslation>? {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                val items = JSONObject(raw.substring(start, end + 1)).getJSONArray("lines")
                buildList {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        // optInt coerces strings and fractions; neither identifies an OCR block.
                        val value = (item.opt("i") as? Number)?.toDouble() ?: continue
                        if (!value.isFinite() || value < Int.MIN_VALUE || value > Int.MAX_VALUE) continue
                        val index = value.toInt()
                        if (index.toDouble() != value) continue
                        val translation = item.optString("translation").trim()
                        if (translation.isEmpty()) continue
                        add(IndexedTranslation(index, LineKind.fromWire(item.optString("kind")), translation))
                    }
                }
            } catch (e: JSONException) {
                null
            }
        }

        /** Renames the output limit and drops the temperature for providers that reject the older fields. */
        internal fun adaptLimits(body: JSONObject, baseUrl: String): JSONObject {
            if (!ProviderUrl.usesCompletionTokens(baseUrl)) return body
            val limit = body.opt("max_tokens")
            body.remove("max_tokens")
            body.remove("temperature")
            if (limit != null) body.put("max_completion_tokens", limit)
            return body
        }
    }
}
