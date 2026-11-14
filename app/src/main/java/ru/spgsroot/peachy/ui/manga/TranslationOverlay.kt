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
    BoxWithConstraints(modifier.fillMaxSize()) {
        val page = fittedPage(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), pageAspect)
        placed.forEach { line ->
            val plate = remember(line, page, density) { plateFor(line, page, measurer, density) }
            Box(
                Modifier
                    .offset { IntOffset(plate.rect.left.toInt(), plate.rect.top.toInt()) }
                    .size(with(density) { plate.rect.width.toDp() }, with(density) { plate.rect.height.toDp() })
                    .background(Color.White.copy(alpha = 0.93f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = line.translation,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    fontSize = plate.fontSize.sp,
                    lineHeight = (plate.fontSize * 1.1f).sp,
                    fontWeight = if (line.kind == LineKind.Sign) FontWeight.Bold else FontWeight.Medium,
                    fontStyle = if (line.kind == LineKind.Thought) FontStyle.Italic else FontStyle.Normal
                )
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
private val WIDENING = floatArrayOf(1f, 1.4f, 1.9f, 2.6f)

/**
 * Original Japanese text is often a narrow vertical strip; a translation set in a strip that narrow
 * would be unreadably small. The plate keeps its centre and widens (inside the page) until the text
 * reaches a readable size, or until the widest allowed plate, whichever comes first.
 */
private fun plateFor(line: TranslatedLine, page: Rect, measurer: TextMeasurer, density: Density): Plate {
    val box: PageBox = line.box!!
    val original = Rect(
        page.left + box.left * page.width,
        page.top + box.top * page.height,
        page.left + box.right * page.width,
        page.top + box.bottom * page.height
    )
    var best: Plate? = null
    for (factor in WIDENING) {
        val width = min(original.width * factor, page.width)
        val left = (original.center.x - width / 2f).coerceIn(page.left, page.right - width)
        val rect = Rect(left, original.top, left + width, original.bottom)
        val size = fit(line.translation, rect, measurer, density)
        val candidate = Plate(rect, size)
        if (best == null || size > best.fontSize) best = candidate
        if (size >= MIN_READABLE_SP) break
    }
    return best!!
}

/** Largest font size in sp, up to [MAX_SP], at which [text] fits [rect] without overflowing. */
private fun fit(text: String, rect: Rect, measurer: TextMeasurer, density: Density): Float {
    val inner = Constraints(
        maxWidth = max((rect.width - with(density) { 4.dp.toPx() }).toInt(), 1),
        maxHeight = max((rect.height - with(density) { 2.dp.toPx() }).toInt(), 1)
    )
    fun fits(sp: Float): Boolean {
        val result: TextLayoutResult = measurer.measure(
            text = text,
            style = TextStyle(fontSize = sp.sp, lineHeight = (sp * 1.1f).sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            constraints = inner
        )
        return !result.didOverflowHeight && !result.didOverflowWidth
    }
    var low = MIN_SP
    var high = MAX_SP
    if (fits(high)) return high
    repeat(6) {
        val mid = (low + high) / 2f
        if (fits(mid)) low = mid else high = mid
    }
    return low
}
