package ru.spgsroot.peachy.translate

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnginePageTranslatorTest {
    private lateinit var server: MockWebServer
    private lateinit var provider: ProviderSettings
    private val jpeg = byteArrayOf(1, 2, 3)
    private val blocks = listOf(
        OcrBlock("最初の吹き出し", Box(0.1f, 0.2f, 0.3f, 0.4f)),
        OcrBlock("次の吹き出し", Box(0.5f, 0.6f, 0.8f, 0.9f))
    )

    @Before
    fun start() {
        server = MockWebServer().also { it.start() }
        provider = ProviderSettings(baseUrl = "http://127.0.0.1:${server.port}/v1", apiKey = "k", consented = true)
    }

    @After
    fun stop() {
        server.shutdown()
    }

    private fun translator(ocr: PageOcr): PageTranslator = EnginePageTranslator(
        TranslationClient(OkHttpClient(), { provider }, "Peachy/test"), ocr
    )

    private fun reply(content: String) {
        server.enqueue(MockResponse().setBody(JSONObject().put(
            "choices", JSONArray().put(JSONObject()
                .put("message", JSONObject().put("role", "assistant").put("content", content))
                .put("finish_reason", "stop"))
        ).toString()))
    }

    private class FakeOcr(private val result: List<OcrBlock>) : PageOcr {
        var calls = 0
        var source: String? = null
        var image: ByteArray? = null

        override suspend fun recognize(jpeg: ByteArray, source: String): List<OcrBlock> {
            calls++
            this.source = source
            image = jpeg
            return result
        }
    }

    @Test
    fun modelDispatchSendsTheImageAndRequestsTightBubbleBoxesWithoutCallingOcr() = runBlocking {
        val ocr = FakeOcr(blocks)
        reply("""{"sourceLanguage":"ja","lines":[{"kind":"speech","text":"ハ？","translation":"А?","box":[100,200,300,400]}]}""")
        val result = translator(ocr).translate(7, jpeg, TranslateRequest(TranslateEngine.Model, "ja", "ru"))
        assertEquals("ja", result.sourceLanguage)
        assertEquals(listOf(TranslatedLine(LineKind.Speech, "ハ？", "А?", Box(0.1f, 0.2f, 0.3f, 0.4f))), result.lines)
        assertEquals(0, ocr.calls)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val messages = body.getJSONArray("messages")
        val system = messages.getJSONObject(0).getString("content")
        assertTrue(system.contains("Russian"))
        assertTrue(system.contains("Japanese"))
        assertTrue(system.contains("\"box\""))
        assertTrue(system.contains("0..1000"))
        assertTrue(system.contains("width"))
        assertTrue(system.contains("height"))
        assertTrue(system.contains("bubble"))
        assertTrue(system.contains("caption"))
        assertTrue(system.contains("fiction for adults"))
        assertTrue(system.contains("faithfully and completely"))
        val parts = messages.getJSONObject(1).getJSONArray("content")
        val image = (0 until parts.length()).map { parts.getJSONObject(it) }.single { it.getString("type") == "image_url" }
        assertEquals("data:image/jpeg;base64,AQID", image.getJSONObject("image_url").getString("url"))
    }

    @Test
    fun mlKitPassesSourceAndMapsReorderedIndicesToOriginalTextAndBox() = runBlocking {
        val ocr = FakeOcr(blocks)
        reply("""{"lines":[{"i":1,"kind":"thought","translation":"второй"},{"i":0,"kind":"speech","translation":"первый"}]}""")
        val result = translator(ocr).translate(7, jpeg, TranslateRequest(TranslateEngine.MlKit, "ja", "ru"))
        assertEquals("ja", result.sourceLanguage)
        assertEquals(listOf(
            TranslatedLine(LineKind.Thought, "次の吹き出し", "второй", Box(0.5f, 0.6f, 0.8f, 0.9f)),
            TranslatedLine(LineKind.Speech, "最初の吹き出し", "первый", Box(0.1f, 0.2f, 0.3f, 0.4f))
        ), result.lines)
        assertEquals(1, ocr.calls)
        assertEquals("ja", ocr.source)
        assertArrayEquals(jpeg, ocr.image)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        val user = body.getJSONArray("messages").getJSONObject(1).get("content")
        assertTrue(user is String)
        assertTrue((user as String).contains("0: 最初の吹き出し"))
        assertTrue(user.contains("1: 次の吹き出し"))
        assertFalse(body.toString().contains("image_url"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun emptyOcrReturnsChosenLanguageWithoutNetworkEvenWithoutProviderConsent() = runBlocking {
        provider = ProviderSettings()
        val ocr = FakeOcr(emptyList())
        val result = translator(ocr).translate(7, jpeg, TranslateRequest(TranslateEngine.MlKit, "ko", "en"))
        assertEquals(PageTranslation("ko", emptyList()), result)
        assertEquals("ko", ocr.source)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptyAutoOcrReturnsEmptyLanguageWithoutNetwork() = runBlocking {
        val ocr = FakeOcr(emptyList())
        val result = translator(ocr).translate(7, jpeg, TranslateRequest(TranslateEngine.MlKit, "auto", "ru"))
        assertEquals(PageTranslation("", emptyList()), result)
        assertEquals("auto", ocr.source)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun autoSourceWithRecognizedBlocksStillReturnsEmptyLanguage() = runBlocking {
        val ocr = FakeOcr(blocks)
        reply("""{"sourceLanguage":"ja","lines":[{"i":0,"kind":"speech","translation":"первый"}]}""")
        val result = translator(ocr).translate(7, jpeg, TranslateRequest(TranslateEngine.MlKit, "auto", "ru"))
        assertEquals("", result.sourceLanguage)
        assertEquals("первый", result.lines.single().translation)
        assertEquals("auto", ocr.source)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun malformedMissingDuplicateAndOutOfRangeIndicesAreSkippedAndFirstAcceptedWins() = runBlocking {
        reply("""{"lines":[
            {"kind":"speech","translation":"missing"},
            {"i":"0","translation":"string index"},
            {"i":0.25,"translation":"fraction"},
            {"i":null,"translation":"null"},
            {"i":true,"translation":"boolean"},
            {"i":-1,"translation":"negative"},
            {"i":2,"translation":"extra"},
            {"i":99999999999999999999,"translation":"overflow"},
            {"i":0,"translation":" "},
            {"i":0,"kind":"sign","translation":"первый"},
            {"i":0,"kind":"thought","translation":"duplicate"},
            {"i":1,"kind":"narration","translation":"второй"},
            {"i":1,"translation":"duplicate again"}
        ]}""")
        val result = translator(FakeOcr(blocks)).translate(7, jpeg, TranslateRequest(TranslateEngine.MlKit, "ja", "ru"))
        assertEquals(listOf(
            TranslatedLine(LineKind.Sign, "最初の吹き出し", "первый", Box(0.1f, 0.2f, 0.3f, 0.4f)),
            TranslatedLine(LineKind.Narration, "次の吹き出し", "второй", Box(0.5f, 0.6f, 0.8f, 0.9f))
        ), result.lines)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun ocrFailurePropagatesWithoutSendingAnImageToTheProvider() {
        val ocr = object : PageOcr {
            override suspend fun recognize(jpeg: ByteArray, source: String): List<OcrBlock> {
                throw IOException("cannot decode")
            }
        }
        try {
            runBlocking { translator(ocr).translate(7, jpeg, TranslateRequest(TranslateEngine.MlKit, "auto", "en")) }
            org.junit.Assert.fail("expected OCR failure")
        } catch (e: IOException) {
            assertEquals("cannot decode", e.message)
        }
        assertEquals(0, server.requestCount)
    }
}
