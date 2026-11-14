package ru.spgsroot.peachy.data.downloader

import android.content.SharedPreferences
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.reflect.Proxy
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.test.runTest

class DownloadOptionsTest {
    @Test fun externalRequestsCarryTheLinkWithoutPretendingItIsAVideoFile() {
        val requests = externalDownloadRequests(" https://example.com/watch?id=2 ")
        assertEquals(2, requests.size)
        assertEquals("android.intent.action.VIEW", requests[0].action)
        assertEquals("https://example.com/watch?id=2", requests[0].data)
        assertNull(requests[0].mimeType)
        assertEquals("android.intent.action.SEND", requests[1].action)
        assertNull(requests[1].data)
        assertEquals("text/plain", requests[1].mimeType)
        assertEquals(mapOf("android.intent.extra.TEXT" to "https://example.com/watch?id=2"), requests[1].extras)
        assertTrue(externalDownloadRequests("http://example.com/a.mp4").isNotEmpty())
    }

    @Test fun unsupportedOrMalformedLinksNeverReachAnotherApp() {
        for (url in listOf("file:///a.mp4", "content://video/1", "javascript:alert(1)", "ftp://example.com/v", "https://", "not a link")) {
            assertTrue(url, externalDownloadRequests(url).isEmpty())
        }
    }

    @Test fun dialogAndOutgoingRequestsRejectPortsTheDownloaderCannotUse() {
        for (url in listOf("http://example.com:99999/v.mp4", "http://example.com:0/")) {
            assertFalse(isDownloadUrl(url))
            assertTrue(externalDownloadRequests(url).isEmpty())
        }
        assertTrue(isDownloadUrl("https://example.com:8443/v.mp4"))
    }

    @Test fun copyRunsAfterSavingAndItsExceptionBecomesAWarningNotALostLibraryItem() = runTest {
        val file = Files.createTempFile("saved-download", ".mp4").toFile()
        val library = mutableListOf<String>()
        val events = mutableListOf<String>()
        try {
            file.writeText("video")
            val result = completeDownloadedVideo(file, save = {
                events += "save"
                library += file.path
                Result.success(Unit)
            }, copy = {
                events += "copy"
                throw IOException("destination full")
            })
            assertEquals(1, result.getOrThrow())
            assertEquals(listOf("save", "copy"), events)
            assertEquals(listOf(file.path), library)
            assertEquals("video", file.readText())
        } finally { file.delete() }
    }

    @Test fun unsuccessfulLibrarySaveNeverCopiesAndPreservesTheSaveError() = runTest {
        val file = Files.createTempFile("failed-download", ".mp4").toFile()
        val error = IOException("database failure")
        var copied = false
        try {
            val result = completeDownloadedVideo(file, save = { Result.failure(error) }, copy = {
                copied = true
            })
            assertSame(error, result.exceptionOrNull())
            assertFalse(copied)
        } finally { file.delete() }
    }

    @Test fun successfulCopyAndNoCopyBothReportNoWarning() = runTest {
        val file = Files.createTempFile("download", ".mp4").toFile()
        try {
            assertEquals(0, completeDownloadedVideo(file, save = { Result.success(Unit) }).getOrThrow())
            val output = ByteArrayOutputStream()
            file.writeText("video")
            assertEquals(0, completeDownloadedVideo(file, save = { Result.success(Unit) },
                copy = { copyDownloadedFile(it) { output }.getOrThrow() }).getOrThrow())
            assertEquals("video", output.toString("UTF-8"))
        } finally { file.delete() }
    }

    @Test fun freshAndUnknownPreferencesKeepDownloadsInTheAppWithoutACopy() {
        assertEquals(DownloadOptions(), DownloadOptionsStore.load(prefs()))
        assertEquals(DownloadMethod.InApp, DownloadOptionsStore.load(prefs(mapOf("method" to "future"))).method)
    }

    @Test fun methodFolderAndClearingTheFolderSurviveReload() {
        val prefs = prefs()
        val options = DownloadOptions(DownloadMethod.AnotherApp, "content://provider/tree/folder", "Movies")
        DownloadOptionsStore.save(prefs, options)
        assertEquals(options, DownloadOptionsStore.load(prefs))
        DownloadOptionsStore.save(prefs, options.copy(method = DownloadMethod.InApp))
        assertEquals(options.copy(method = DownloadMethod.InApp), DownloadOptionsStore.load(prefs))
        DownloadOptionsStore.save(prefs, DownloadOptions())
        assertEquals(DownloadOptions(), DownloadOptionsStore.load(prefs))
    }

    @Test fun successfulCopyStreamsAndClosesTheOutputWithoutChangingTheLibraryFile() {
        val file = Files.createTempFile("download", ".mp4").toFile()
        try {
            val bytes = ByteArray(300_000) { (it % 251).toByte() }
            file.writeBytes(bytes)
            var closed = false
            val output = object : ByteArrayOutputStream() {
                override fun write(b: ByteArray, off: Int, len: Int) {
                    assertTrue("copy must use bounded chunks", len <= 8192)
                    super.write(b, off, len)
                }
                override fun close() { closed = true; super.close() }
            }
            assertTrue(copyDownloadedFile(file) { output }.isSuccess)
            assertArrayEquals(bytes, output.toByteArray())
            assertArrayEquals(bytes, file.readBytes())
            assertTrue(closed)
        } finally { file.delete() }
    }

    @Test fun failedCopyOrOutputCreationKeepsTheLibraryFile() {
        val file = Files.createTempFile("download", ".mp4").toFile()
        try {
            file.writeText("library video")
            val broken = object : OutputStream() {
                override fun write(b: Int) { throw IOException("full") }
            }
            assertTrue(copyDownloadedFile(file) { broken }.isFailure)
            assertEquals("library video", file.readText())
            assertTrue(copyDownloadedFile(file) { throw SecurityException("revoked") }.isFailure)
            assertEquals("library video", file.readText())
        } finally { file.delete() }
    }

    private fun prefs(initial: Map<String, String> = emptyMap()): SharedPreferences {
        val values = initial.toMutableMap()
        lateinit var editor: Any
        editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { val value = args[1] as String?; if (value == null) values.remove(args[0]) else values[args[0] as String] = value; editor }
                "apply" -> null
                else -> error("unexpected ${method.name}")
            }
        }
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args[0]] ?: args[1]
                "edit" -> editor
                else -> error("unexpected ${method.name}")
            }
        } as SharedPreferences
    }
}
