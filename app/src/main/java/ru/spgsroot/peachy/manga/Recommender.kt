package ru.spgsroot.peachy.manga

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One interaction with one gallery, already turned into a signed weight ([weight] < 0 means "not interested"). */
data class TasteSignal(val galleryId: Int, val tags: List<GalleryTag>, val weight: Double, val at: Long)

/**
 * What the user likes, as one weight per tag. Positive weights attract, negative repel.
 * [languageShare] is the share of positive interest per language, 0..1 and summing to 1.
 */
data class TasteProfile(
    val tagWeights: Map<TagRef, Double>,
    val languageShare: Map<String, Double>
) {
    val hasTaste: Boolean get() = tagWeights.values.any { it > 0.0 }
}

/** A recommended gallery with the tags that earned it a place, strongest first, for the "because" line. */
data class Recommendation(val gallery: GallerySummary, val because: List<String>)

/** Where to look for candidates: tag combinations to search, and galleries whose "related" list to read. */
data class RecommendationSeeds(val queries: List<List<TagRef>>, val relatedIds: List<Int>)

/**
 * On-device recommender. Every interaction becomes a [TasteSignal]; signals fade with a half-life,
 * are spread over the gallery's tags (so a tag-heavy gallery does not drown a focused one) and favour
 * rare tags over ubiquitous ones. Candidates are scored against the profile, then re-ranked so the
 * list is not ten near-copies of one gallery.
 */
object Recommender {
    const val HALF_LIFE_DAYS = 45.0

    const val VIEW = 0.4
    const val READ = 2.6
    const val SAVED = 1.5
    const val FAVORITE = 4.0
    const val DISMISSED = -5.0

    /** A favourite tag counts like a favourited gallery's tag; a collection tag, half of that. */
    const val FAVORITE_TAG = 8.0
    const val COLLECTION_TAG = 4.0

    /** Signals at least this strong make their gallery a source of "related" candidates. */
    const val RELATED_MIN_WEIGHT = 2.5

    private const val MS_PER_DAY = 86_400_000.0
    private const val CATALOG_SIZE = 700_000.0
    private const val IDF_SMOOTHING = 50.0
    private const val IDF_CAP = 6.0
    private const val UNKNOWN_IDF = 3.0
    private const val LANGUAGE_WEIGHT = 4.0
    private const val POPULARITY_WEIGHT = 0.5
    private const val POPULARITY_SCALE = 100_000.0
    private const val DIVERSITY = 0.7
    private const val BECAUSE_TAGS = 2
    private const val SEED_TAGS = 3
    private const val RELATED_SEEDS = 2

    fun interactionWeight(readFraction: Double, saved: Boolean, favorite: Boolean, dismissed: Boolean): Double {
        if (dismissed) return DISMISSED
        return VIEW + READ * readFraction.coerceIn(0.0, 1.0) +
            (if (saved) SAVED else 0.0) + (if (favorite) FAVORITE else 0.0)
    }

    fun signalFor(
        gallery: RememberedGallery,
        readFraction: Double,
        readAt: Long?,
        saved: Boolean,
        favorite: Boolean
    ) = TasteSignal(
        galleryId = gallery.id,
        tags = gallery.tags,
        weight = interactionWeight(readFraction, saved, favorite, gallery.dismissed),
        at = max(gallery.seenAt, readAt ?: 0L)
    )

    fun profile(signals: List<TasteSignal>, explicitTags: Map<TagRef, Double>, now: Long): TasteProfile {
        val weights = HashMap<TagRef, Double>()
        val languages = HashMap<String, Double>()
        for (signal in signals) {
            val weight = signal.weight * decay(now - signal.at)
            val usable = signal.tags.filter { typeFactor(it.type) > 0.0 }
            if (usable.isNotEmpty()) {
                val spread = 1.0 / sqrt(usable.size.toDouble())
                for (tag in usable) {
                    val contribution = weight * spread * typeFactor(tag.type) * idf(tag.count)
                    weights.merge(ref(tag), contribution, Double::plus)
                }
            }
            if (signal.weight > 0.0) signal.tags.language()?.let { languages.merge(it, weight, Double::plus) }
        }
        for ((tag, weight) in explicitTags) weights.merge(tag.lowered(), weight, Double::plus)
        val total = languages.values.sum()
        val share = if (total > 0.0) languages.mapValues { it.value / total } else emptyMap()
        return TasteProfile(weights, share)
    }

    fun seeds(profile: TasteProfile, signals: List<TasteSignal>, now: Long): RecommendationSeeds {
        val top = profile.tagWeights.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(SEED_TAGS)
            .map { it.key }
        val queries = top.map { listOf(it) } + if (top.size >= 2) listOf(top.take(2)) else emptyList()
        val related = signals
            .filter { it.weight >= RELATED_MIN_WEIGHT }
            .sortedByDescending { it.weight * decay(now - it.at) }
            .map { it.galleryId }
            .distinct()
            .take(RELATED_SEEDS)
        return RecommendationSeeds(queries, related)
    }

    /**
     * Picks up to [limit] galleries. [excluded] are ids the user already has (read, saved, dismissed)
     * or must not see. Without any positive taste the list is simply the most popular candidates.
     */
    fun rank(
        candidates: List<GallerySummary>,
        profile: TasteProfile,
        excluded: Set<Int>,
        limit: Int
    ): List<Recommendation> {
        val seen = HashSet<Int>()
        val pool = candidates.filter { it.id !in excluded && seen.add(it.id) }
        if (!profile.hasTaste) {
            return pool.sortedByDescending { it.numFavorites ?: 0 }.take(limit).map { Recommendation(it, emptyList()) }
        }
        val scored = pool.mapNotNull { score(it, profile) }.sortedByDescending { it.total }
        if (scored.isEmpty()) return emptyList()
        val best = scored.first().total
        val chosen = ArrayList<Scored>()
        val remaining = scored.toMutableList()
        while (chosen.size < limit && remaining.isNotEmpty()) {
            val next = remaining.maxBy { candidate ->
                val overlap = chosen.maxOfOrNull { jaccard(candidate.refs, it.refs) } ?: 0.0
                DIVERSITY * (candidate.total / best) - (1.0 - DIVERSITY) * overlap
            }
            remaining.remove(next)
            chosen += next
        }
        return chosen.map { Recommendation(it.gallery, because(it.refs, profile)) }
    }

    private class Scored(val gallery: GallerySummary, val refs: List<TagRef>, val total: Double)

    /** Null when the gallery has no tag the user likes: a popular or same-language gallery alone is no evidence. */
    private fun score(gallery: GallerySummary, profile: TasteProfile): Scored? {
        val refs = gallery.tags.filter { typeFactor(it.type) > 0.0 }.map(::ref).distinct()
        if (refs.isEmpty()) return null
        val evidence = refs.sumOf { profile.tagWeights[it] ?: 0.0 } / sqrt(refs.size.toDouble())
        if (evidence <= 0.0) return null
        val language = gallery.tags.language()?.let { profile.languageShare[it] } ?: 0.0
        val popularity = gallery.numFavorites?.let { min(1.0, ln(1.0 + it) / ln(1.0 + POPULARITY_SCALE)) } ?: 0.0
        return Scored(gallery, refs, evidence + LANGUAGE_WEIGHT * language + POPULARITY_WEIGHT * popularity)
    }

    private fun because(refs: List<TagRef>, profile: TasteProfile): List<String> = refs
        .filter { (profile.tagWeights[it] ?: 0.0) > 0.0 }
        .sortedByDescending { profile.tagWeights.getValue(it) }
        .take(BECAUSE_TAGS)
        .map { it.name }

    private fun jaccard(a: List<TagRef>, b: List<TagRef>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.count { it in b }
        return common.toDouble() / (a.size + b.size - common)
    }

    private fun decay(ageMs: Long): Double = 0.5.pow(max(0L, ageMs) / (HALF_LIFE_DAYS * MS_PER_DAY))

    /** Rare tags say more than ubiquitous ones; a tag of unknown size gets a middling weight. */
    private fun idf(count: Int?): Double =
        if (count == null) UNKNOWN_IDF else min(IDF_CAP, ln(1.0 + CATALOG_SIZE / (count + IDF_SMOOTHING)))

    /** Language and category say little about taste (language has its own term); artists say the most. */
    private fun typeFactor(type: String): Double = when (type) {
        "artist" -> 1.3
        "tag", "character", "group" -> 1.0
        "parody" -> 0.9
        else -> 0.0
    }

    private fun ref(tag: GalleryTag) = TagRef(tag.type, tag.name.lowercase())

    private fun TagRef.lowered() = copy(name = name.lowercase())
}
