package ru.spgsroot.peachy.translate

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TranslationClientTest {
    private lateinit var server: MockWebServer
    private var settings = ProviderSettings()

    private val validReply = """{"sourceLanguage":"ja","lines":[{"kind":"speech","text":"ハ？","translation":"А?"}]}"""
    private val agent = "Peachy/test (+https://example.test)"

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
        settings = ProviderSettings(baseUrl = "http://127.0.0.1:${server.port}/v1", apiKey = "k")
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun client() = TranslationClient(OkHttpClient(), { settings }, agent)

    private fun envelope(content: String, finish: String = "stop") = JSONObject()
        .put(
            "choices",
            JSONArray().put(
                JSONObject()
                    .put("message", JSONObject().put("role", "assistant").put("content", content))
                    .put("finish_reason", finish)
            )
        ).toString()

    private fun reply(content: String) = server.enqueue(MockResponse().setBody(envelope(content)))

    private fun translate() = runBlocking { client().translate(7, byteArrayOf(1, 2, 3), "ru") }

    private fun failureOf(block: () -> Any?): TranslationError {
        try {
            block()
        } catch (e: TranslationException) {
            return e.error
        }
        fail("expected a TranslationException")
        throw AssertionError()
    }

    @Test
    fun requestHasExpectedShape() {
        reply(validReply)
        assertEquals(1, translate().lines.size)

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer k", request.getHeader("Authorization"))
        assertEquals(agent, request.getHeader("User-Agent"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(DEFAULT_MODEL, body.getString("model"))
        assertEquals(0.2, body.getDouble("temperature"), 0.0001)
        assertEquals(4096, body.getInt("max_tokens"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        val messages = body.getJSONArray("messages")
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("Russian"))
        val parts = messages.getJSONObject(1).getJSONArray("content")
        val image = (0 until parts.length()).map { parts.getJSONObject(it) }.first { it.getString("type") == "image_url" }
        assertTrue(image.getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
    }

    @Test
    fun noSessionHeaderForLocalHost() {
        reply(validReply)
        translate()
        assertNull(server.takeRequest().getHeader("x-opencode-session"))
    }

    @Test
    fun sessionHeaderValue() {
        assertEquals("peachy-7", sessionHeader("https://opencode.ai/inference/go/v1", 7))
        assertNull(sessionHeader("http://10.0.2.2:8766/v1", 7))
    }

    @Test
    fun fencedReplyIsParsed() {
        reply("```json\n$validReply\n```")
        assertEquals("А?", translate().lines.single().translation)
    }

    @Test
    fun http401And403AreAuth() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(TranslationError.Auth, failureOf { translate() })
        assertEquals(TranslationError.Auth, failureOf { translate() })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun http429IsRateLimit() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30"))
        assertEquals(TranslationError.RateLimit, failureOf { translate() })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun http500IsServerWithCode() {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(TranslationError.Server(500), failureOf { translate() })
    }

    @Test
    fun connectionRefusedIsNetwork() {
        server.shutdown()
        assertEquals(TranslationError.Network, failureOf { translate() })
    }

    @Test
    fun invalidJsonIsRetriedOnceThenBadResponse() {
        reply("I am sorry")
        reply("still not json")
        assertEquals(TranslationError.BadResponse, failureOf { translate() })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun secondAttemptSuccessIsReturned() {
        reply("""{"sourceLanguage":"ja","lines":[{"kind":"speech","te""")
        reply(validReply)
        assertEquals(1, translate().lines.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun emptyChoicesIsRefused() {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        assertEquals(TranslationError.Refused, failureOf { translate() })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun contentFilterIsRefused() {
        server.enqueue(MockResponse().setBody(envelope("", finish = "content_filter")))
        assertEquals(TranslationError.Refused, failureOf { translate() })
    }

    @Test
    fun notConfiguredWithoutKey() {
        settings = settings.copy(apiKey = "")
        assertEquals(TranslationError.NotConfigured, failureOf { translate() })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun checkSendsTextOnlyRequestAndReturnsOnOk() {
        reply("ok")
        runBlocking { client().check() }
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val content = body.getJSONArray("messages").getJSONObject(0).get("content")
        assertTrue(content is String)
        assertFalse(body.has("response_format"))
    }

    @Test
    fun checkReportsAuth() {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(TranslationError.Auth, failureOf { runBlocking { client().check() } })
    }
}
