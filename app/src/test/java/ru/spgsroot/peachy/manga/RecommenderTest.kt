package ru.spgsroot.peachy.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private val now = 1_000L * 86_400_000L
    private val day = 86_400_000L

    private fun tag(id: Int, name: String, type: String = "tag", count: Int? = 10_000) = GalleryTag(id, type, name, count)
    private val schoolgirl = tag(1, "schoolgirl uniform")
    private val glasses = tag(2, "glasses")
    private val netorare = tag(3, "netorare")
    private val chinese = tag(4, "chinese", "language", null)
    private val english = tag(5, "english", "language", null)

    private fun card(id: Int, vararg tags: GalleryTag, favorites: Int? = null) =
        GallerySummary(id, "g$id", "t.jpg", 20, tags = tags.toList(), numFavorites = favorites)

    private fun signal(vararg tags: GalleryTag, weight: Double, at: Long = now, id: Int = 100) =
        TasteSignal(id, tags.toList(), weight, at)

    private fun profile(vararg signals: TasteSignal, explicit: Map<TagRef, Double> = emptyMap()) =
        Recommender.profile(signals.toList(), explicit, now)

    private fun ref(tag: GalleryTag) = TagRef(tag.type, tag.name)

    @Test
    fun favouriteOutweighsReadingWhichOutweighsOpening() {
        val opened = Recommender.interactionWeight(0.0, saved = false, favorite = false, dismissed = false)
        val half = Recommender.interactionWeight(0.5, saved = false, favorite = false, dismissed = false)
        val finished = Recommender.interactionWeight(1.0, saved = false, favorite = false, dismissed = false)
        val loved = Recommender.interactionWeight(1.0, saved = true, favorite = true, dismissed = false)
        assertTrue(opened < half && half < finished && finished < loved)
        assertEquals(-5.0, Recommender.interactionWeight(1.0, saved = true, favorite = true, dismissed = true), 0.0)
    }

    @Test
    fun readProgressBeyondTheRangeIsClamped() {
        assertEquals(
            Recommender.interactionWeight(1.0, false, false, false),
            Recommender.interactionWeight(7.0, false, false, false),
            0.0
        )
        assertEquals(
            Recommender.interactionWeight(0.0, false, false, false),
            Recommender.interactionWeight(-1.0, false, false, false),
            0.0
        )
    }

    @Test
    fun anInteractionLosesHalfItsWeightEveryHalfLife() {
        val fresh = profile(signal(schoolgirl, weight = 4.0)).tagWeights.getValue(ref(schoolgirl))
        val aged = profile(
            signal(schoolgirl, weight = 4.0, at = now - (Recommender.HALF_LIFE_DAYS * day).toLong())
        ).tagWeights.getValue(ref(schoolgirl))
        assertEquals(fresh / 2, aged, 1e-9)
    }

    @Test
    fun aSignalFromTheFutureDoesNotGrowBeyondItsWeight() {
        val future = profile(signal(schoolgirl, weight = 4.0, at = now + 30 * day)).tagWeights.getValue(ref(schoolgirl))
        val current = profile(signal(schoolgirl, weight = 4.0)).tagWeights.getValue(ref(schoolgirl))
        assertEquals(current, future, 1e-9)
    }

    @Test
    fun aTagHeavyGalleryGivesEachTagLessThanAFocusedOne() {
        val focused = profile(signal(schoolgirl, glasses, weight = 4.0)).tagWeights.getValue(ref(schoolgirl))
        val crowded = profile(
            signal(schoolgirl, glasses, netorare, tag(6, "a"), tag(7, "b"), tag(8, "c"), weight = 4.0)
        ).tagWeights.getValue(ref(schoolgirl))
        assertTrue(crowded < focused)
    }

    @Test
    fun aRareTagCountsForMoreThanAnUbiquitousOne() {
        val p = profile(
            signal(tag(1, "common", count = 300_000), tag(2, "rare", count = 300), weight = 4.0)
        )
        assertTrue(p.tagWeights.getValue(TagRef("tag", "rare")) > 2 * p.tagWeights.getValue(TagRef("tag", "common")))
    }

    @Test
    fun languageAndCategoryDoNotBecomeTasteTags() {
        val p = profile(signal(schoolgirl, chinese, tag(9, "doujinshi", "category"), weight = 4.0))
        assertEquals(setOf(ref(schoolgirl)), p.tagWeights.keys)
        assertEquals(mapOf("chinese" to 1.0), p.languageShare)
    }

    @Test
    fun translatedIsNotALanguage() {
        val p = profile(signal(schoolgirl, tag(10, "translated", "language"), chinese, weight = 4.0))
        assertEquals(setOf("chinese"), p.languageShare.keys)
    }

    @Test
    fun aDismissalPullsItsTagsDownAndLeavesLanguageShareAlone() {
        val p = profile(
            signal(schoolgirl, glasses, weight = 4.0),
            signal(netorare, english, weight = Recommender.DISMISSED, id = 101)
        )
        assertTrue(p.tagWeights.getValue(ref(netorare)) < 0)
        assertTrue(p.tagWeights.getValue(ref(schoolgirl)) > 0)
        assertFalse("english" in p.languageShare)
    }

    @Test
    fun favouriteTagsAndCollectionsSeedAProfileThatHasNoHistory() {
        val p = profile(
            explicit = mapOf(
                TagRef("tag", "Glasses") to Recommender.FAVORITE_TAG,
                TagRef("tag", "glasses") to Recommender.COLLECTION_TAG
            )
        )
        assertTrue(p.hasTaste)
        assertEquals(12.0, p.tagWeights.getValue(TagRef("tag", "glasses")), 1e-9)
    }

    @Test
    fun withoutPositiveTasteTheMostPopularCandidatesComeFirst() {
        val result = Recommender.rank(
            listOf(card(1, glasses, favorites = 5), card(2, glasses, favorites = 900), card(3, glasses)),
            profile(),
            excluded = emptySet(),
            limit = 2
        )
        assertEquals(listOf(2, 1), result.map { it.gallery.id })
        assertTrue(result.all { it.because.isEmpty() })
    }

    @Test
    fun aProfileOfOnlyDislikesIsStillColdStart() {
        val onlyDislikes = profile(signal(netorare, weight = Recommender.DISMISSED))
        assertFalse(onlyDislikes.hasTaste)
        val result = Recommender.rank(listOf(card(1, netorare, favorites = 10)), onlyDislikes, emptySet(), 5)
        assertEquals(listOf(1), result.map { it.gallery.id })
    }

    @Test
    fun excludedAndRepeatedIdsNeverAppear() {
        val p = profile(signal(schoolgirl, weight = 4.0))
        val result = Recommender.rank(
            listOf(card(1, schoolgirl), card(2, schoolgirl), card(2, schoolgirl), card(3, schoolgirl)),
            p,
            excluded = setOf(1),
            limit = 10
        )
        assertEquals(listOf(2, 3), result.map { it.gallery.id }.sorted())
    }

    @Test
    fun aCandidateWithNoLikedTagIsNotRecommendedEvenIfPopularAndInTheRightLanguage() {
        val p = profile(signal(schoolgirl, chinese, weight = 4.0))
        val result = Recommender.rank(
            listOf(card(1, glasses, chinese, favorites = 90_000), card(2, schoolgirl, chinese)),
            p,
            emptySet(),
            10
        )
        assertEquals(listOf(2), result.map { it.gallery.id })
    }

    @Test
    fun aCandidateWithADislikedTagAndNothingElseIsDropped() {
        val p = profile(
            signal(schoolgirl, weight = 4.0),
            signal(netorare, weight = Recommender.DISMISSED, id = 101)
        )
        val result = Recommender.rank(listOf(card(1, netorare), card(2, netorare, schoolgirl)), p, emptySet(), 10)
        assertFalse(1 in result.map { it.gallery.id })
    }

    @Test
    fun aDislikedTagOutweighedByManyLikedOnesDoesNotSinkTheGallery() {
        val p = profile(
            signal(schoolgirl, glasses, weight = 8.5),
            signal(netorare, weight = Recommender.DISMISSED, id = 101)
        )
        val result = Recommender.rank(listOf(card(1, schoolgirl, glasses, netorare)), p, emptySet(), 10)
        assertEquals(listOf(1), result.map { it.gallery.id })
    }

    @Test
    fun theLanguageYouReadBreaksTiesBetweenOtherwiseEqualGalleries() {
        val p = profile(signal(schoolgirl, chinese, weight = 4.0))
        val result = Recommender.rank(
            listOf(card(1, schoolgirl, english), card(2, schoolgirl, chinese)),
            p,
            emptySet(),
            2
        )
        assertEquals(2, result.first().gallery.id)
    }

    @Test
    fun aStrongerTagMatchBeatsPopularity() {
        val p = profile(signal(schoolgirl, glasses, weight = 4.0))
        val result = Recommender.rank(
            listOf(card(1, schoolgirl, favorites = 90_000), card(2, schoolgirl, glasses, favorites = 3)),
            p,
            emptySet(),
            2
        )
        assertEquals(2, result.first().gallery.id)
    }

    @Test
    fun aNearCopyIsPushedBehindADifferentGallery() {
        val p = profile(
            signal(schoolgirl, glasses, weight = 4.0),
            signal(netorare, tag(6, "teacher"), weight = 3.5, id = 101)
        )
        val result = Recommender.rank(
            listOf(
                card(1, schoolgirl, glasses),
                card(2, schoolgirl, glasses),
                card(3, netorare, tag(6, "teacher"))
            ),
            p,
            emptySet(),
            3
        )
        assertEquals(listOf(1, 3, 2), result.map { it.gallery.id })
    }

    @Test
    fun theLimitIsRespected() {
        val p = profile(signal(schoolgirl, weight = 4.0))
        val cards = (1..30).map { card(it, schoolgirl) }
        assertEquals(10, Recommender.rank(cards, p, emptySet(), 10).size)
        assertTrue(Recommender.rank(cards, p, emptySet(), 0).isEmpty())
    }

    @Test
    fun theReasonNamesTheStrongestLikedTagsOnly() {
        val p = profile(
            signal(schoolgirl, weight = 8.0),
            signal(glasses, weight = 3.0, id = 101),
            signal(netorare, weight = Recommender.DISMISSED, id = 102)
        )
        val result = Recommender.rank(listOf(card(1, glasses, netorare, schoolgirl, tag(9, "unseen"))), p, emptySet(), 1)
        assertEquals(listOf("schoolgirl uniform", "glasses"), result.single().because)
    }

    @Test
    fun seedsAreTheTopLikedTagsAlonePlusTheTopPairAndTheStrongestGalleries() {
        val signals = listOf(
            signal(schoolgirl, weight = 8.0, id = 11),
            signal(glasses, weight = 6.0, id = 12),
            signal(netorare, weight = 4.0, id = 13),
            signal(tag(6, "teacher"), weight = 1.0, id = 14),
            signal(tag(7, "bad"), weight = Recommender.DISMISSED, id = 15)
        )
        val p = Recommender.profile(signals, emptyMap(), now)
        val seeds = Recommender.seeds(p, signals, now)

        assertEquals(
            listOf(
                listOf(ref(schoolgirl)), listOf(ref(glasses)), listOf(ref(netorare)),
                listOf(ref(schoolgirl), ref(glasses))
            ),
            seeds.queries
        )
        assertEquals(listOf(11, 12), seeds.relatedIds)
    }

    @Test
    fun noTasteMeansNoSeeds() {
        val seeds = Recommender.seeds(profile(), emptyList(), now)
        assertTrue(seeds.queries.isEmpty() && seeds.relatedIds.isEmpty())
    }

    @Test
    fun aSingleLikedTagHasNoPairQuery() {
        val signals = listOf(signal(schoolgirl, weight = 4.0))
        val seeds = Recommender.seeds(Recommender.profile(signals, emptyMap(), now), signals, now)
        assertEquals(listOf(listOf(ref(schoolgirl))), seeds.queries)
    }

    @Test
    fun aSignalIsBuiltFromWhatTheUserDidWithAGallery() {
        val remembered = RememberedGallery(7, "t", "p", 10, listOf(schoolgirl), null, null, seenAt = 50, dismissed = false)
        val signal = Recommender.signalFor(remembered, readFraction = 0.5, readAt = 90L, saved = true, favorite = false)
        assertEquals(90L, signal.at)
        assertEquals(Recommender.VIEW + Recommender.READ / 2 + Recommender.SAVED, signal.weight, 1e-9)
        assertEquals(50L, Recommender.signalFor(remembered, 0.0, null, false, false).at)
    }
}
