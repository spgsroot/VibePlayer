package ru.spgsroot.peachy.ui.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareUriPolicyTest {
    @Test
    fun acceptsOnlyContentUris() {
        assertTrue(ShareUriPolicy.isImportable("content"))
        assertFalse(ShareUriPolicy.isImportable("file"))
        assertFalse(ShareUriPolicy.isImportable("http"))
        assertFalse(ShareUriPolicy.isImportable(null))
    }

    @Test fun perStreamMimeTakesPrecedenceForMixedShares() {
        assertEquals(SharedMediaKind.PHOTO, ShareUriPolicy.mediaKind("image/jpeg", "video/*"))
        assertEquals(SharedMediaKind.VIDEO, ShareUriPolicy.mediaKind("video/mp4", "image/*"))
        assertNull(ShareUriPolicy.mediaKind("text/plain", "image/*"))
    }

    @Test fun genericProviderMimeFallsBackToSpecificIntentMimeOnly() {
        assertEquals(SharedMediaKind.PHOTO, ShareUriPolicy.mediaKind(null, "image/*"))
        assertEquals(SharedMediaKind.VIDEO, ShareUriPolicy.mediaKind("application/octet-stream", "video/*"))
        assertNull(ShareUriPolicy.mediaKind(null, "*/*"))
        assertNull(ShareUriPolicy.mediaKind(null, null))
    }
}
