package ru.spgsroot.peachy.translate

import com.google.android.gms.tasks.TaskCompletionSource
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTaskTest {
    @Test fun successfulTaskReturnsResultAndReleasesInputOnce() = runTest {
        val task = TaskCompletionSource<String>()
        var releases = 0
        val result = async(start = CoroutineStart.UNDISPATCHED) { awaitOcrTask(task.task) { releases++ } }
        assertEquals(0, releases)
        task.setResult("text")
        assertEquals("text", result.await())
        assertEquals(1, releases)
    }

    @Test fun failedTaskPropagatesTheCauseAndReleasesInput() = runTest {
        val task = TaskCompletionSource<String>()
        val failure = IOException("recognition failed")
        var released = false
        task.setException(failure)
        try {
            awaitOcrTask(task.task) { released = true }
            throw AssertionError("Expected the task failure")
        } catch (actual: IOException) {
            // Coroutine stack-trace recovery may copy IOException, retaining the original cause.
            assertEquals("recognition failed", actual.message)
            assertSame(failure, actual.cause ?: actual)
        }
        assertTrue(released)
    }

    @Test fun cancelledWaiterKeepsInputAliveUntilNativeTaskFinishes() = runTest {
        val task = TaskCompletionSource<String>()
        var releases = 0
        val result = async(start = CoroutineStart.UNDISPATCHED) { awaitOcrTask(task.task) { releases++ } }
        result.cancelAndJoin()
        assertTrue(result.isCancelled)
        assertEquals(0, releases)
        task.setResult("late text")
        assertEquals(1, releases)
    }

    @Test fun failedTaskAlsoReleasesInputAfterWaiterCancellation() = runTest {
        val task = TaskCompletionSource<String>()
        var released = false
        val result = async(start = CoroutineStart.UNDISPATCHED) { awaitOcrTask(task.task) { released = true } }
        result.cancelAndJoin()
        assertFalse(released)
        task.setException(IOException("late failure"))
        assertTrue(released)
    }
}
