package ru.spgsroot.peachy.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import ru.spgsroot.peachy.translate.Box as PageBox
import ru.spgsroot.peachy.translate.LineKind
import ru.spgsroot.peachy.translate.TranslatedLine
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the translated lines over the page, each on a pale plate at the place of the original text.
 * The overlay has the size of the image view; the page itself may be letterboxed inside it (the
 * image is fitted, not stretched), so boxes, which are fractions of the page, are mapped onto the
 * fitted picture, not onto the view. Lines without a box and sound effects stay in the panel only.
 */
@Composable
fun TranslationOverlay(lines: List<TranslatedLine>, pageAspect: Float, modifier: Modifier = Modifier) {
    val placed = lines.filter { it.box != null && it.kind != LineKind.Sfx }
    if (placed.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = LocalTextStyle.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val page = fittedPage(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pageAspect)
        placed.forEach { line ->
            val plate = remember(line, page, density, base) { plateFor(line, page, measurer, density, base) }
            Box(
                Modifier
                    .offset { IntOffset(plate.rect.left.toInt(), plate.rect.top.toInt()) }
                    .size(with(density) { plate.rect.width.toDp() }, with(density) { plate.rect.height.toDp() })
                    .background(Color.White.copy(alpha = 0.93f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = line.translation, style = plateStyle(base, line.kind, plate.fontSize))
            }
        }
    }
}

/** Where the page sits inside a view of [width] x [height] when it is fitted, in view pixels. */
internal fun fittedPage(width: Float, height: Float, aspect: Float): Rect {
    if (width <= 0f || height <= 0f || aspect <= 0f) return Rect(0f, 0f, max(width, 0f), max(height, 0f))
    return if (width / height > aspect) {
        val fitted = height * aspect
        Rect((width - fitted) / 2f, 0f, (width + fitted) / 2f, height)
    } else {
        val fitted = width / aspect
        Rect(0f, (height - fitted) / 2f, width, (height + fitted) / 2f)
    }
}

private class Plate(val rect: Rect, val fontSize: Float)

private const val MIN_READABLE_SP = 13f
private const val MAX_SP = 22f
private const val MIN_SP = 6f
/** Width and height growth steps; each keeps the centre of the original text and stays on the page. */
private val GROWTH = arrayOf(1f to 1f, 1.4f to 1.15f, 1.9f to 1.3f, 2.6f to 1.5f, 3.2f to 1.8f)

/**
 * Original Japanese text is often a narrow vertical strip; a translation set in a strip that narrow
 * would be unreadably small or break words in the middle. The plate keeps its centre and grows
 * (inside the page, in both directions) until the text reaches a readable size, or until the
 * largest allowed plate, whichever comes first. The text always fits the plate it is given.
 */
private fun plateFor(
    line: TranslatedLine, page: Rect, measurer: TextMeasurer, density: Density, base: TextStyle
): Plate {
    val box: PageBox = line.box!!
    val original = Rect(
        page.left + box.left * page.width,
        page.top + box.top * page.height,
        page.left + box.right * page.width,
        page.top + box.bottom * page.height
    )
    var best: Plate? = null
    for ((widthFactor, heightFactor) in GROWTH) {
        val width = min(original.width * widthFactor, page.width)
        val height = min(original.height * heightFactor, page.height)
        val left = (original.center.x - width / 2f).coerceIn(page.left, page.right - width)
        val top = (original.center.y - height / 2f).coerceIn(page.top, page.bottom - height)
        val rect = Rect(left, top, left + width, top + height)
        val candidate = Plate(rect, fit(line, rect, measurer, density, base))
        if (best == null || candidate.fontSize > best.fontSize) best = candidate
        if (candidate.fontSize >= MIN_READABLE_SP) break
    }
    // Nothing fit (a very long text on a tiny plate): draw the largest plate at the smallest size.
    return best!!.let { if (it.fontSize > 0f) it else Plate(it.rect, MIN_SP) }
}

/**
 * The one style used both to measure a plate's text and to draw it. Measuring with a different style
 * than the drawn one (the theme's default adds letter spacing, Sign lines are bold) lets drawn text
 * overflow the plate it was fitted to.
 */
private fun plateStyle(base: TextStyle, kind: LineKind, sp: Float) = base.merge(
    TextStyle(
        color = Color.Black,
        fontSize = sp.sp,
        lineHeight = (sp * 1.1f).sp,
        letterSpacing = 0.sp,
        fontWeight = if (kind == LineKind.Sign) FontWeight.Bold else FontWeight.Medium,
        fontStyle = if (kind == LineKind.Thought) FontStyle.Italic else FontStyle.Normal,
        textAlign = TextAlign.Center
    )
)

/**
 * Largest font size in sp, up to [MAX_SP], at which [line] fits [rect] without overflowing and
 * without a word being cut; 0 when even [MIN_SP] does not fit.
 */
private fun fit(line: TranslatedLine, rect: Rect, measurer: TextMeasurer, density: Density, base: TextStyle): Float {
    val inner = Constraints(
        maxWidth = max((rect.width - with(density) { 4.dp.toPx() }).toInt(), 1),
        maxHeight = max((rect.height - with(density) { 2.dp.toPx() }).toInt(), 1)
    )
    val longestWord = line.translation.split(' ', '\n').maxByOrNull { it.length }.orEmpty()
    fun fits(sp: Float): Boolean {
        val style = plateStyle(base, line.kind, sp)
        val word = measurer.measure(longestWord, style, softWrap = false, maxLines = 1)
        if (word.size.width > inner.maxWidth) return false
        val result: TextLayoutResult = measurer.measure(text = line.translation, style = style, constraints = inner)
        return !result.didOverflowHeight && !result.didOverflowWidth
    }
    if (!fits(MIN_SP)) return 0f
    var low = MIN_SP
    var high = MAX_SP
    if (fits(high)) return high
    repeat(6) {
        val mid = (low + high) / 2f
        if (fits(mid)) low = mid else high = mid
    }
    return low
}
