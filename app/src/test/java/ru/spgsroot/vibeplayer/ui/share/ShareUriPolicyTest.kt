package ru.spgsroot.vibeplayer.ui.share

import org.junit.Assert.assertFalse
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
}
