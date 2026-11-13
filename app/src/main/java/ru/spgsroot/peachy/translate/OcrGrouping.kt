package ru.spgsroot.peachy.translate

internal data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class RawOcrLine(val text: String, val rect: PixelRect, val confidence: Float = 1f)
internal data class RawOcrBlock(val lines: List<RawOcrLine>, val rect: PixelRect)

/**
 * Keeps ML Kit's block boundaries (one bubble is not split into words). Lines below 0.3 confidence,
 * blank lines and symbol-only lines are discarded; a single letter, ideograph or digit is retained.
 * Boxes are clipped to the image and zero-area boxes are discarded.
 *
 * Reading order is geometric, not based on page-height buckets: scan blocks by top edge and form
 * rows when their vertical intervals overlap by at least half the shorter interval. Every member
 * must overlap every existing member of a row, so a tall bridging box cannot chain unrelated rows.
 * Rows read top-to-bottom. Within a ja/zh row, form columns by the same rule on horizontal
 * intervals, read columns right-to-left and their members top-to-bottom. Other languages read each
 * row left-to-right. Japanese/Chinese lines within a block read in columns with no joining space;
 * Latin/Korean lines read in rows with a joining space. This simple rectangle heuristic cannot
 * infer panel borders or distinguish horizontal Chinese prose from vertical manga.
 */
internal fun groupOcrBlocks(
    blocks: List<RawOcrBlock>,
    imageWidth: Int,
    imageHeight: Int,
    source: String
): List<OcrBlock> {
    require(imageWidth > 0 && imageHeight > 0)
    val vertical = source == "ja" || source == "zh"
    val cleaned = blocks.mapNotNull { block ->
        val rect = PixelRect(
            block.rect.left.coerceIn(0, imageWidth), block.rect.top.coerceIn(0, imageHeight),
            block.rect.right.coerceIn(0, imageWidth), block.rect.bottom.coerceIn(0, imageHeight)
        )
        if (rect.left >= rect.right || rect.top >= rect.bottom) return@mapNotNull null
        val lines = block.lines.mapNotNull { line ->
            val text = line.text.trim()
            if (line.confidence < 0.3f || !text.codePoints().anyMatch { Character.isLetterOrDigit(it) }) null
            else line.copy(text = text)
        }
        if (lines.isEmpty()) return@mapNotNull null
        val ordered = if (vertical) columns(lines) { it.rect } else rows(lines) { it.rect }.flatten()
        RawOcrBlock(listOf(RawOcrLine(ordered.joinToString(if (vertical) "" else " ") { it.text }, rect)), rect)
    }
    return rows(cleaned) { it.rect }.flatMap { row ->
        if (vertical) columns(row) { it.rect } else row.sortedWith(compareBy({ it.rect.left }, { it.rect.top }))
    }.map { block ->
        val rect = block.rect
        OcrBlock(block.lines.single().text, Box(
            rect.left.toFloat() / imageWidth, rect.top.toFloat() / imageHeight,
            rect.right.toFloat() / imageWidth, rect.bottom.toFloat() / imageHeight
        ))
    }
}

private fun overlap(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
    val shorter = minOf(aEnd.toLong() - aStart, bEnd.toLong() - bStart)
    return shorter > 0 && (minOf(aEnd, bEnd).toLong() - maxOf(aStart, bStart)) * 2 >= shorter
}

private fun <T> rows(items: List<T>, rect: (T) -> PixelRect): List<List<T>> =
    bands(items.sortedWith(compareBy({ rect(it).top }, { rect(it).left }))) { a, b ->
        overlap(rect(a).top, rect(a).bottom, rect(b).top, rect(b).bottom)
    }.map { row -> row.sortedWith(compareBy({ rect(it).left }, { rect(it).top })) }

private fun <T> columns(items: List<T>, rect: (T) -> PixelRect): List<T> =
    bands(items.sortedWith(compareByDescending<T> { rect(it).right }.thenBy { rect(it).top })) { a, b ->
        overlap(rect(a).left, rect(a).right, rect(b).left, rect(b).right)
    }.flatMap { column -> column.sortedWith(compareBy({ rect(it).top }, { rect(it).left })) }

private fun <T> bands(items: List<T>, overlaps: (T, T) -> Boolean): List<List<T>> {
    val groups = mutableListOf<MutableList<T>>()
    for (item in items) {
        val group = groups.firstOrNull { members -> members.all { overlaps(it, item) } }
        if (group == null) groups.add(mutableListOf(item)) else group.add(item)
    }
    return groups
}
