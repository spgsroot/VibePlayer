package ru.spgsroot.peachy.ui.share

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareImportQueueTest {
    @Test fun routesEachKindOnlyToItsOwnImporter() = runTest {
        val calls = mutableListOf<String>()
        assertEquals(SharedMediaKind.PHOTO, dispatchSharedImport(SharedMediaKind.PHOTO,
            { calls += "photo" }, { calls += "video" }))
        assertEquals(SharedMediaKind.VIDEO, dispatchSharedImport(SharedMediaKind.VIDEO,
            { calls += "photo" }, { calls += "video" }))
        assertEquals(listOf("photo", "video"), calls)
        val failure = runCatching { dispatchSharedImport(null, { calls += "wrong" }, { calls += "wrong" }) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf("photo", "video"), calls)
    }

    @Test fun cancellationKeepsCurrentItemAndRestoringSnapshotResumesItOnce() = runTest {
        var saved = emptyList<SharedStream>()
        var rejected = 0
        val queue = ShareImportQueue(emptyList(), 0) { streams, failed -> saved = streams; rejected = failed }
        queue.enqueue(listOf(SharedStream("content://one", "image/png"), SharedStream("content://two", "video/mp4")))
        val job = launch {
            queue.drain { stream ->
                if (stream.uri.endsWith("one")) SharedMediaKind.PHOTO else awaitCancellation()
            }
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf(SharedStream("content://two", "video/mp4")), saved)
        assertTrue(queue.hasPending.value)
        assertEquals(ShareImportSummary(1, 0, true, false), queue.results.first())
        val restored = ShareImportQueue(saved, rejected) { streams, failed -> saved = streams; rejected = failed }
        val resumed = mutableListOf<String>()
        restored.drain { resumed += it.uri; SharedMediaKind.VIDEO }
        assertEquals(listOf("content://two"), resumed)
        assertEquals(ShareImportSummary(1, 0, false, true), restored.results.first())
        assertFalse(restored.hasPending.value)
        assertTrue(saved.isEmpty())
    }

    @Test fun enqueueDuringImportAndConcurrentDrainNeverDuplicateItems() = runTest {
        val queue = ShareImportQueue(emptyList(), 0) { _, _ -> }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val visited = mutableListOf<String>()
        queue.enqueue(listOf(SharedStream("one", null)))
        val first = launch { queue.drain {
            visited += it.uri
            if (it.uri == "one") { started.complete(Unit); release.await() }
            SharedMediaKind.PHOTO
        } }
        started.await()
        queue.enqueue(listOf(SharedStream("two", null)))
        val second = launch { queue.drain { visited += "duplicate"; SharedMediaKind.VIDEO } }
        runCurrent()
        release.complete(Unit)
        joinAll(first, second)
        assertEquals(listOf("one", "two"), visited)
        assertEquals(ShareImportSummary(2, 0, true, false), queue.results.first())
        assertFalse(queue.hasPending.value)
    }

    @Test fun rejectedUrisAndImportFailuresAreReportedWithoutDroppingReadableItems() = runTest {
        val queue = ShareImportQueue(emptyList(), 0) { _, _ -> }
        queue.enqueue(listOf(SharedStream("ok", null), SharedStream("bad", null)), rejected = 2)
        queue.drain { if (it.uri == "bad") throw java.io.IOException("unreadable") else SharedMediaKind.PHOTO }
        assertEquals(ShareImportSummary(1, 3, true, false), queue.results.first())
        assertFalse(queue.hasPending.value)
    }
}
