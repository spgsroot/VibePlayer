package ru.spgsroot.peachy.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    private fun engine(vararg rules: String): FilterEngine =
        FilterEngine.Builder().addList(rules.joinToString("\n")).build()

    private fun FilterEngine.blocks(url: String, page: String? = "example.com", type: Int = ResourceType.SCRIPT) =
        shouldBlock(url, page, type)

    @Test
    fun hostAnchorMatchesTheDomainAndItsSubdomainsButNotLookalikes() {
        val e = engine("||ads.example.net^")
        assertTrue(e.blocks("https://ads.example.net/x.js"))
        assertTrue(e.blocks("https://cdn.ads.example.net/x.js"))
        assertFalse(e.blocks("https://badads.example.net/x.js"))
        assertFalse(e.blocks("https://example.net/ads.example.net"))
    }

    @Test
    fun separatorMatchesPunctuationAndEndButNotLettersOrDots() {
        val e = engine("||tracker.io^")
        assertTrue(e.blocks("https://tracker.io"))
        assertTrue(e.blocks("https://tracker.io:8080/a"))
        assertFalse(e.blocks("https://tracker.io.evil.org/a"))
        assertFalse(e.blocks("https://tracker.iox/a"))
    }

    @Test
    fun wildcardsAndAnchorsFollowAdblockPlusSemantics() {
        val e = engine("/banner*/ad_", "|https://x.org/start", "end.gif|")
        assertTrue(e.blocks("https://a.com/banner300/ad_1.png"))
        assertFalse(e.blocks("https://a.com/banner300/other"))
        assertTrue(e.blocks("https://x.org/start/1"))
        assertFalse(e.blocks("https://y.org/?u=https://x.org/start"))
        assertTrue(e.blocks("https://a.com/p/end.gif"))
        assertFalse(e.blocks("https://a.com/p/end.gif?x=1"))
    }

    @Test
    fun exceptionRulesOverrideBlocks() {
        val e = engine("||cdn.test^", "@@||cdn.test/allowed/")
        assertTrue(e.blocks("https://cdn.test/other.js"))
        assertFalse(e.blocks("https://cdn.test/allowed/lib.js"))
    }

    @Test
    fun thirdPartyOptionComparesRegistrableDomains() {
        val e = engine("||metrics.test^\$third-party")
        assertTrue(e.blocks("https://metrics.test/p", page = "example.com"))
        assertFalse(e.blocks("https://metrics.test/p", page = "www.metrics.test"))
        assertFalse(e.blocks("https://metrics.test/p", page = null))
        val first = engine("||site.test/ads^\$~third-party")
        assertTrue(first.blocks("https://site.test/ads/x", page = "m.site.test"))
        assertFalse(first.blocks("https://site.test/ads/x", page = "other.com"))
    }

    @Test
    fun typeOptionsRestrictAndNegate() {
        val onlyImages = engine("||pix.test^\$image")
        assertTrue(onlyImages.blocks("https://pix.test/a", type = ResourceType.IMAGE))
        assertFalse(onlyImages.blocks("https://pix.test/a", type = ResourceType.SCRIPT))
        val notScripts = engine("||all.test^\$~script")
        assertFalse(notScripts.blocks("https://all.test/a", type = ResourceType.SCRIPT))
        assertTrue(notScripts.blocks("https://all.test/a", type = ResourceType.IMAGE))
    }

    @Test
    fun domainOptionLimitsByPageHostWithExclusions() {
        val e = engine("/ad.js\$domain=a.com|~b.a.com")
        assertTrue(e.blocks("https://x.org/ad.js", page = "a.com"))
        assertTrue(e.blocks("https://x.org/ad.js", page = "m.a.com"))
        assertFalse(e.blocks("https://x.org/ad.js", page = "b.a.com"))
        assertFalse(e.blocks("https://x.org/ad.js", page = "other.com"))
    }

    @Test
    fun unsupportedRulesAreDroppedNotHalfApplied() {
        assertNull(NetworkRule.parse("/ads\\d+/"))
        assertNull(NetworkRule.parse("||x.test^\$popup"))
        assertNull(NetworkRule.parse("||x.test^\$redirect=noop.js"))
        assertNull(NetworkRule.parse("||x.test^\$document"))
        assertNull(NetworkRule.parse("! comment"))
        assertNull(NetworkRule.parse("example.com##.ad"))
        assertEquals(0, engine("||x.test^\$popup").networkRuleCount)
    }

    @Test
    fun ruleWithoutAnIndexableTokenStillMatches() {
        val e = engine("-ad-")
        assertTrue(e.blocks("https://a.com/x-ad-y.js"))
        assertFalse(e.blocks("https://a.com/xady.js"))
    }

    @Test
    fun tokenIndexDoesNotMissPrefixMatches() {
        // `ads` inside `ads.example.com^` is a safe key; `||adserv` is a prefix and must still match adserver.
        val e = engine("||adserv")
        assertTrue(e.blocks("https://adserver.example.com/x.js"))
    }

    @Test
    fun cosmeticRulesApplyPerDomainWithExceptions() {
        val e = engine("##.banner", "site.com##.promo", "site.com#@#.banner", "~safe.com##.popup-x")
        val site = e.cosmeticCss("www.site.com")
        assertTrue(site.contains(".promo"))
        assertFalse(site.contains(".banner"))
        val other = e.cosmeticCss("other.com")
        assertTrue(other.contains(".banner"))
        assertTrue(other.contains(".popup-x"))
        assertFalse(other.contains(".promo"))
        assertFalse(e.cosmeticCss("safe.com").contains(".popup-x"))
    }

    @Test
    fun unsafeSelectorsAreRejectedBeforeTheyReachAStyleSheet() {
        assertTrue(CosmeticParser.isSafeSelector("div[class^=\"ad-\"] > a:not(.keep)"))
        assertFalse(CosmeticParser.isSafeSelector("div:has-text(ad)"))
        assertFalse(CosmeticParser.isSafeSelector(".a{color:red}"))
        assertFalse(CosmeticParser.isSafeSelector(".a::before"))
        assertFalse(CosmeticParser.isSafeSelector("+js(abort-on-property-read, x)"))
        assertFalse(CosmeticParser.isSafeSelector("a[href=\"x]"))
    }

    @Test
    fun mainFrameBlockingIsNotPerformedAndEmptyEngineBlocksNothing() {
        assertFalse(FilterEngine.EMPTY.blocks("https://ads.example.net/x"))
    }

    @Test
    fun registrableDomainHandlesTwoPartSuffixes() {
        assertEquals("example.co.uk", Hosts.registrable("a.b.example.co.uk"))
        assertEquals("example.com", Hosts.registrable("cdn.example.com"))
        assertTrue(Hosts.isThirdParty("a.co.uk", "b.co.uk"))
        assertFalse(Hosts.isThirdParty("x.example.co.uk", "y.example.co.uk"))
        assertEquals("host.test", Hosts.hostOf("https://user:pw@Host.Test:8443/p?q#f"))
    }
}
