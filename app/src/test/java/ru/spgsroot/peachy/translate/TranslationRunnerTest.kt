package ru.spgsroot.peachy.translate

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationRunnerTest {
    private fun line(text: String) = PageTranslation("ja", listOf(TranslatedLine(LineKind.Speech, "ハ", text)))

    /** Page number travels in the first byte of the "image". */
    private class FakeSource : PageSource {
        var failure: Exception? = null
        override suspend fun jpeg(galleryId: Int, page: Int): ByteArray {
            failure?.let { throw it }
            return byteArrayOf(page.toByte())
        }
    }

    private class FakeTranslator : PageTranslator {
        val requested = mutableListOf<Int>()
        private val inFlight = AtomicInteger()
        var maxInFlight = 0
        var behaviour: suspend (Int) -> PageTranslation = { page ->
            delay(100)
            PageTranslation("ja", listOf(TranslatedLine(LineKind.Speech, "ハ", "p$page")))
        }

        override suspend fun translate(galleryId: Int, jpeg: ByteArray, targetLanguage: String): PageTranslation {
            val page = jpeg[0].toInt()
            requested += page
            maxInFlight = maxOf(maxInFlight, inFlight.incrementAndGet())
            try {
                return behaviour(page)
            } finally {
                inFlight.decrementAndGet()
            }
        }
    }

    private class FakeCache : TranslationCache {
        val rows = mutableMapOf<Triple<Int, Int, String>, PageTranslation>()
        var puts = 0
        override suspend fun get(galleryId: Int, page: Int, lang: String) = rows[Triple(galleryId, page, lang)]
        override suspend fun put(galleryId: Int, page: Int, lang: String, model: String, translation: PageTranslation) {
            puts++
            rows[Triple(galleryId, page, lang)] = translation
        }

        override suspend fun cachedPages(galleryId: Int, lang: String) =
            rows.keys.filter { it.first == galleryId && it.third == lang }.map { it.second }.toSet()
    }

    private class Rig(scope: TestScope) {
        val source = FakeSource()
        val translator = FakeTranslator()
        val cache = FakeCache()
        val runner = TranslationRunner(source, translator, cache, { "m" }, scope)
    }

    private fun TestScope.rig() = Rig(this)

    private fun failWith(error: TranslationError): suspend (Int) -> PageTranslation = { throw TranslationException(error) }

    @Test
    fun cacheHitSkipsTheTranslator() = runTest {
        val r = rig()
        r.cache.rows[Triple(1, 2, "ru")] = line("кэш")
        assertEquals(PageState.Done(line("кэш")), r.runner.translate(1, 2, "ru"))
        assertTrue(r.translator.requested.isEmpty())
    }

    @Test
    fun successIsCachedAndPublished() = runTest {
        val r = rig()
        val state = r.runner.translate(1, 2, "ru")
        assertEquals(PageState.Done(line("p2")), state)
        assertEquals(line("p2"), r.cache.rows[Triple(1, 2, "ru")])
        assertEquals(state, r.runner.pages.value[2])
    }

    @Test
    fun forceIgnoresCacheAndReplacesIt() = runTest {
        val r = rig()
        r.cache.rows[Triple(1, 2, "ru")] = line("старый")
        assertEquals(PageState.Done(line("p2")), r.runner.translate(1, 2, "ru", force = true))
        assertEquals(listOf(2), r.translator.requested)
        assertEquals(line("p2"), r.cache.rows[Triple(1, 2, "ru")])
    }

    @Test
    fun concurrentRequestsForOnePageCallTheTranslatorOnce() = runTest {
        val r = rig()
        val gate = CompletableDeferred<Unit>()
        r.translator.behaviour = { page -> gate.await(); line("p$page") }
        val a = async { r.runner.translate(1, 3, "ru") }
        val b = async { r.runner.translate(1, 3, "ru") }
        advanceUntilIdle()
        assertEquals(listOf(3), r.translator.requested)
        gate.complete(Unit)
        assertEquals(PageState.Done(line("p3")), a.await())
        assertEquals(PageState.Done(line("p3")), b.await())
        assertEquals(listOf(3), r.translator.requested)
    }

    @Test
    fun failureBecomesFailedState() = runTest {
        val r = rig()
        r.translator.behaviour = failWith(TranslationError.Network)
        assertEquals(PageState.Failed(TranslationError.Network), r.runner.translate(1, 2, "ru"))
        assertEquals(PageState.Failed(TranslationError.Network), r.runner.pages.value[2])
        assertEquals(0, r.cache.puts)
    }

    @Test
    fun pageFetchFailureIsNetworkAndNonImageIsBadResponse() = runTest {
        val r = rig()
        r.source.failure = IOException("down")
        assertEquals(PageState.Failed(TranslationError.Network), r.runner.translate(1, 2, "ru"))
        r.source.failure = IllegalArgumentException("not an image")
        assertEquals(PageState.Failed(TranslationError.BadResponse), r.runner.translate(1, 2, "ru"))
        assertTrue(r.translator.requested.isEmpty())
    }

    @Test
    fun cancelledRequestDoesNotWriteTheCache() = runTest {
        val r = rig()
        r.translator.behaviour = { delay(1_000); line("late") }
        val call = async { r.runner.translate(1, 4, "ru") }
        advanceTimeBy(100)
        assertEquals(PageState.Loading, r.runner.pages.value[4])
        call.cancel()
        advanceUntilIdle()
        assertEquals(0, r.cache.puts)
        assertNull(r.runner.pages.value[4])
    }

    @Test
    fun batchSkipsCachedPages() = runTest {
        val r = rig()
        r.cache.rows[Triple(1, 1, "ru")] = line("a")
        r.cache.rows[Triple(1, 3, "ru")] = line("b")
        val seen = mutableListOf<BatchState?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { r.runner.batch.collect { seen += it } }
        r.runner.startBatch(1, 5, "ru")
        advanceUntilIdle()
        assertEquals(listOf(0, 2, 4), r.translator.requested.sorted())
        assertEquals(BatchState(done = 2, total = 5), seen.filterNotNull().first())
        assertEquals(BatchState(done = 5, total = 5), seen.filterNotNull().last())
        assertNull(r.runner.batch.value)
    }

    @Test
    fun batchRunsAtMostTwoAtOnce() = runTest {
        val r = rig()
        r.runner.startBatch(1, 6, "ru")
        advanceUntilIdle()
        assertEquals(6, r.translator.requested.size)
        assertEquals(2, r.translator.maxInFlight)
    }

    @Test
    fun batchStopsOnAuth() = runTest {
        val r = rig()
        r.translator.behaviour = { page ->
            if (page == 0) throw TranslationException(TranslationError.Auth)
            delay(100)
            line("p$page")
        }
        r.runner.startBatch(1, 6, "ru")
        advanceUntilIdle()
        assertTrue(r.translator.requested.toString(), r.translator.requested.all { it < 2 })
        assertEquals(TranslationError.Auth, r.runner.batch.value?.stopped)
        assertEquals(PageState.Failed(TranslationError.Auth), r.runner.pages.value[0])
    }

    @Test
    fun batchStopsOnRateLimit() = runTest {
        val r = rig()
        r.translator.behaviour = { page ->
            if (page == 0) throw TranslationException(TranslationError.RateLimit)
            delay(100)
            line("p$page")
        }
        r.runner.startBatch(1, 6, "ru")
        advanceUntilIdle()
        assertTrue(r.translator.requested.toString(), r.translator.requested.all { it < 2 })
        assertEquals(TranslationError.RateLimit, r.runner.batch.value?.stopped)
    }

    @Test
    fun batchContinuesAfterBadResponse() = runTest {
        val r = rig()
        r.translator.behaviour = { page ->
            delay(100)
            if (page == 2) throw TranslationException(TranslationError.BadResponse)
            line("p$page")
        }
        val seen = mutableListOf<BatchState?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { r.runner.batch.collect { seen += it } }
        r.runner.startBatch(1, 5, "ru")
        advanceUntilIdle()
        assertEquals(PageState.Failed(TranslationError.BadResponse), r.runner.pages.value[2])
        assertEquals(listOf(0, 1, 2, 3, 4), r.translator.requested.sorted())
        assertEquals(setOf(0, 1, 3, 4), r.cache.rows.keys.map { it.second }.toSet())
        assertEquals(BatchState(done = 5, total = 5), seen.filterNotNull().last())
        assertNull(r.runner.batch.value)
    }

    @Test
    fun cancelBatchStopsQueuedPages() = runTest {
        val r = rig()
        r.runner.startBatch(1, 6, "ru")
        advanceTimeBy(50)
        r.runner.cancelBatch()
        advanceUntilIdle()
        assertEquals(listOf(0, 1), r.translator.requested.sorted())
        assertNull(r.runner.batch.value)
        assertEquals(0, r.cache.puts)
    }

    @Test
    fun startingABatchWhileOneRunsDoesNothing() = runTest {
        val r = rig()
        r.runner.startBatch(1, 4, "ru")
        advanceTimeBy(50)
        r.runner.startBatch(1, 4, "ru")
        advanceUntilIdle()
        assertEquals(listOf(0, 1, 2, 3), r.translator.requested.sorted())
    }

    @Test
    fun batchAndTurnShareOneRequestPerPage() = runTest {
        val r = rig()
        val turn = async { r.runner.translate(1, 3, "ru") }
        r.runner.startBatch(1, 4, "ru")
        advanceUntilIdle()
        assertEquals(PageState.Done(line("p3")), turn.await())
        assertEquals(1, r.translator.requested.count { it == 3 })
    }

    @Test
    fun aTurnWaitingOnAPageTheBatchHasStartedDoesNotRepeatIt() = runTest {
        val r = rig()
        r.runner.startBatch(1, 2, "ru")
        advanceTimeBy(10)
        val turn = async { r.runner.translate(1, 1, "ru") }
        advanceUntilIdle()
        assertEquals(PageState.Done(line("p1")), turn.await())
        assertEquals(1, r.translator.requested.count { it == 1 })
    }
}
