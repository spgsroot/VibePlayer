package ru.spgsroot.peachy.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NhentaiCdnTest {
    @Test
    fun usesThePinnedImageHostWithoutRewritingThePath() {
        assertEquals(
            "https://i1.nhentai.net/galleries/9/1.jpg",
            NhentaiCdn.page("galleries/9/1.jpg")
        )
    }

    @Test
    fun usesThePinnedThumbHost() {
        assertEquals(
            "https://t1.nhentai.net/galleries/9/thumb.jpg",
            NhentaiCdn.thumb("galleries/9/thumb.jpg")
        )
    }

    @Test
    fun rejectsEmptyTraversalAndAbsolutePaths() {
        assertNull(NhentaiCdn.page(""))
        assertNull(NhentaiCdn.page("../secret.jpg"))
        assertNull(NhentaiCdn.page("https://evil.example/galleries/9/1.jpg"))
    }
}
