package ru.spgsroot.peachy.ui.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentsTest {
    private val photo = Uri.parse("content://photos/1")
    private val video = Uri.parse("content://videos/2")

    @Test fun singleSendIsForwardedWithAnExplicitReadGrantAndClipData() {
        val source = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, photo)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val forwarded = ShareIntents.forward(InstrumentationRegistry.getInstrumentation().targetContext, source)!!
        assertEquals(listOf(photo), ShareIntents.streams(forwarded))
        assertEquals("image/jpeg", forwarded.type)
        assertEquals(photo, forwarded.clipData!!.getItemAt(0).uri)
        assertTrue(forwarded.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun multipleAndClipOnlyStreamsArePreservedOnceAndInOrder() {
        val source = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(photo, video))
            clipData = ClipData.newRawUri("photo", photo).apply { addItem(ClipData.Item(video)) }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        val forwarded = ShareIntents.forward(InstrumentationRegistry.getInstrumentation().targetContext, source)!!
        assertEquals(listOf(photo, video), ShareIntents.streams(forwarded))
        assertEquals(2, forwarded.clipData!!.itemCount)
        assertEquals(video, forwarded.clipData!!.getItemAt(1).uri)
        assertTrue(forwarded.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        val clipOnly = Intent(Intent.ACTION_SEND_MULTIPLE).apply { clipData = source.clipData }
        assertEquals(listOf(photo, video), ShareIntents.streams(clipOnly))
    }

    @Test fun clipUrisMissingFromExtrasStillReceiveForwardedReadGrants() {
        val source = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(photo))
            clipData = ClipData.newRawUri("video", video)
        }
        val forwarded = ShareIntents.forward(InstrumentationRegistry.getInstrumentation().targetContext, source)!!
        assertEquals(listOf(photo, video), ShareIntents.streams(forwarded))
        val granted = (0 until forwarded.clipData!!.itemCount).map { forwarded.clipData!!.getItemAt(it).uri }.toSet()
        assertEquals(setOf(photo, video), granted)
    }

    @Test fun onlyContentStreamsFromSupportedActionsAreAccepted() {
        val source = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(Uri.parse("file:///private"), photo))
        }
        assertEquals(listOf(photo), ShareIntents.streams(source))
        source.action = Intent.ACTION_EDIT
        assertTrue(ShareIntents.streams(source).isEmpty())
        assertNull(ShareIntents.forward(InstrumentationRegistry.getInstrumentation().targetContext, source))
    }
}
