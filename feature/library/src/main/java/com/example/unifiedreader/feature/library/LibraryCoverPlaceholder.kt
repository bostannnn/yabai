package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.primaryAuthor
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * What a book without a cover image shows instead: EPUB and PDF get a title cover (colored from
 * the title, title and author set like a book jacket); CBZ and CBR get a series cover (one color
 * per series, issue or volume number large). A comic without a series and number falls back to the
 * title cover.
 */
internal enum class LibraryCoverPlaceholderStyle {
    TitleCover,
    SeriesNumber,
}

internal data class LibraryCoverPlaceholderPalette(
    val container: Color,
    val content: Color,
)

internal data class LibraryCoverPlaceholderSpec(
    val style: LibraryCoverPlaceholderStyle,
    /** The title on a title cover, the series name on a series cover. */
    val heading: String,
    /** The author on a title cover, the issue title on a series cover. */
    val byline: String?,
    /** The issue or volume number on a series cover, e.g. "#52" or "4". */
    val number: String?,
    /** "Vol." when [number] is a volume number. */
    val numberPrefix: String?,
    val formatLabel: String,
    val palette: LibraryCoverPlaceholderPalette,
)

internal fun libraryCoverPlaceholderSpec(
    item: ReadableItem,
): LibraryCoverPlaceholderSpec {
    val formatLabel = item.format.name
    val comic = item.comicMetadata
    val series = comic?.series?.coverDisplayText()
    val issueNumber = issueNumberLabel(comic?.issueNumber)
    val volumeNumber = volumeNumberLabel(comic?.volume)
    val isComicArchive = item.format == FormatType.CBZ || item.format == FormatType.CBR
    if (isComicArchive && series != null && (issueNumber != null || volumeNumber != null)) {
        return LibraryCoverPlaceholderSpec(
            style = LibraryCoverPlaceholderStyle.SeriesNumber,
            heading = series,
            byline = comic?.issueTitle?.coverDisplayText()?.takeUnless { it.equals(series, ignoreCase = true) },
            number = issueNumber ?: volumeNumber,
            numberPrefix = if (issueNumber == null) "Vol." else null,
            formatLabel = formatLabel,
            palette = placeholderPalette(series),
        )
    }
    val title = item.title.coverDisplayText() ?: formatLabel
    return LibraryCoverPlaceholderSpec(
        style = LibraryCoverPlaceholderStyle.TitleCover,
        heading = title,
        byline = (item.bookMetadata?.primaryAuthor() ?: comic?.writers?.firstOrNull())?.coverDisplayText(),
        number = null,
        numberPrefix = null,
        formatLabel = formatLabel,
        palette = placeholderPalette(title),
    )
}

/**
 * Whether a line break between [text]`[index - 1]` and [text]`[index]` cuts a word in two. Breaks
 * between CJK characters are normal, so they don't count.
 */
internal fun splitsWordAt(
    text: String,
    index: Int,
): Boolean {
    if (index <= 0 || index >= text.length) return false
    return text[index - 1].isWordCharacter() && text[index].isWordCharacter()
}

@Composable
internal fun LibraryCoverPlaceholder(
    item: ReadableItem,
    reserveTopBadge: Boolean,
    reserveBottomBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    val spec = remember(item) { libraryCoverPlaceholderSpec(item) }
    val textMeasurer = rememberTextMeasurer()
    val labelFamily = MaterialTheme.typography.labelLarge.fontFamily ?: FontFamily.Default
    // Decorative: the card's caption already says what the book is.
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .drawWithCache {
                val texts = placeholderTexts(
                    spec = spec,
                    textMeasurer = textMeasurer,
                    labelFamily = labelFamily,
                    reserveTopBadge = reserveTopBadge,
                    reserveBottomBadge = reserveBottomBadge,
                )
                onDrawBehind {
                    drawRect(spec.palette.container)
                    texts.forEach { placed -> drawText(placed.layout, topLeft = placed.topLeft) }
                }
            },
    )
}

private class PlacedText(
    val layout: TextLayoutResult,
    val topLeft: Offset,
)

/** Lays out the placeholder's text inside its safe zone. All sizes scale with the cover width. */
private fun CacheDrawScope.placeholderTexts(
    spec: LibraryCoverPlaceholderSpec,
    textMeasurer: TextMeasurer,
    labelFamily: FontFamily,
    reserveTopBadge: Boolean,
    reserveBottomBadge: Boolean,
): List<PlacedText> {
    val width = size.width
    val height = size.height
    val compact = width < CompactCoverWidth.toPx()
    val left = width * SafeZoneStart
    val right = width * (1f - SafeZoneEnd)
    var top = height * SafeZoneTop
    var bottom = height * (1f - SafeZoneBottom)
    if (!compact && reserveTopBadge) top = max(top, BadgeReserve.toPx())
    if (!compact && reserveBottomBadge) bottom = min(bottom, height - BadgeReserve.toPx())
    val safeWidth = right - left
    val safeHeight = bottom - top
    if (safeWidth < 1f || safeHeight < 1f) return emptyList()

    val content = spec.palette.content
    val muted = content.copy(alpha = 0.85f)
    val measure = PlaceholderTextMeasure(textMeasurer, this, safeWidth)

    if (compact) {
        // Too small for words: just the number, or the title's first letter.
        val glyph = spec.number ?: spec.heading.firstCoverGlyph()
        val layout = measure.fitLine(
            text = glyph,
            style = TextStyle(color = content, fontFamily = labelFamily, fontWeight = FontWeight.Medium),
            sizesPx = fontSteps(from = min(safeWidth, safeHeight) * 0.7f, to = min(safeWidth, safeHeight) * 0.3f),
        )
        return listOf(
            PlacedText(
                layout = layout,
                topLeft = Offset(
                    x = left + (safeWidth - layout.size.width) / 2f,
                    y = top + (safeHeight - layout.size.height) / 2f,
                ),
            ),
        )
    }

    val gap = safeWidth * 0.05f
    val tag = measure.line(
        text = spec.formatLabel,
        style = TextStyle(
            color = content,
            fontFamily = labelFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.06.em,
        ),
        sizePx = max(safeWidth * 0.095f, MinLabelSize.toPx()),
    )
    val placed = mutableListOf(PlacedText(tag, Offset(left, bottom - tag.size.height)))
    val bylineStyle = TextStyle(color = muted, fontFamily = labelFamily)
    val bylineSizePx = max(safeWidth * 0.10f, MinLabelSize.toPx())

    when (spec.style) {
        LibraryCoverPlaceholderStyle.TitleCover -> {
            val titleStyle = TextStyle(color = content, fontFamily = FontFamily.Serif)
            val smallestTitle = max(safeWidth * 0.10f, MinTitleSize.toPx())
            val titleSizes = fontSteps(from = max(safeWidth * 0.15f, smallestTitle), to = smallestTitle)
            fun titleFor(reservedBylineLines: Int): FittedParagraph {
                val reserved = spec.byline?.let { measure.paragraph(it, bylineStyle, bylineSizePx, reservedBylineLines) }
                return measure.fitParagraph(
                    text = spec.heading,
                    style = titleStyle,
                    sizesPx = titleSizes,
                    lineHeight = TitleLineHeight,
                    maxLines = TitleMaxLines,
                    maxHeight = safeHeight - tag.size.height - gap - (reserved?.let { it.size.height + gap } ?: 0f),
                )
            }
            // The title fits first, keeping room for two lines of author (one if the title needs it);
            // the author then takes whatever height is left, so a long name wraps instead of being cut.
            var title = titleFor(reservedBylineLines = 2)
            if (!title.fitted) title = titleFor(reservedBylineLines = 1)
            val bylineTop = top + title.layout.size.height + gap
            val byline = spec.byline?.let {
                measure.paragraphWithin(it, bylineStyle, bylineSizePx, maxHeight = bottom - tag.size.height - gap - bylineTop)
            }
            placed += PlacedText(title.layout, Offset(left, top))
            byline?.let { placed += PlacedText(it, Offset(left, bylineTop)) }
        }

        LibraryCoverPlaceholderStyle.SeriesNumber -> {
            val seriesStyle = TextStyle(color = muted, fontFamily = labelFamily, letterSpacing = 0.05.em)
            val seriesSizePx = max(safeWidth * 0.095f, MinLabelSize.toPx())
            var series = measure.paragraph(spec.heading.uppercase(), seriesStyle, seriesSizePx, maxLines = 2)
            val prefix = spec.numberPrefix?.let { measure.line(it, bylineStyle, bylineSizePx) }
            val number = measure.fitLine(
                text = spec.number.orEmpty(),
                style = TextStyle(color = content, fontFamily = labelFamily, fontWeight = FontWeight.Medium),
                sizesPx = fontSteps(from = safeWidth * 0.42f, to = safeWidth * 0.19f),
            )
            val numberStack = tag.size.height + gap + number.size.height + (prefix?.size?.height ?: 0)
            // The issue title sits between the series name and the number and takes the height in
            // between; the series name gives up its second line before the issue title is dropped.
            fun bylineRoom() = safeHeight - series.size.height - gap - numberStack - gap * 0.5f
            var byline = spec.byline?.let { measure.paragraphWithin(it, bylineStyle, bylineSizePx, bylineRoom()) }
            if (spec.byline != null && byline == null && series.lineCount > 1) {
                series = measure.paragraph(spec.heading.uppercase(), seriesStyle, seriesSizePx, maxLines = 1)
                byline = measure.paragraphWithin(spec.byline, bylineStyle, bylineSizePx, bylineRoom())
            }
            placed += PlacedText(series, Offset(left, top))
            var y = bottom - tag.size.height - gap
            byline?.let {
                y -= it.size.height
                placed += PlacedText(it, Offset(left, y))
                y -= gap * 0.5f
            }
            y -= number.size.height
            placed += PlacedText(number, Offset(left, y))
            prefix?.let { placed += PlacedText(it, Offset(left, y - it.size.height)) }
        }
    }
    return placed
}

/** [fitted] is false when the text only fit by breaking a word or being cut off with an ellipsis. */
private class FittedParagraph(
    val layout: TextLayoutResult,
    val fitted: Boolean,
)

private class PlaceholderTextMeasure(
    private val textMeasurer: TextMeasurer,
    private val density: CacheDrawScope,
    width: Float,
) {
    private val constraints = Constraints(maxWidth = width.toInt().coerceAtLeast(1))

    fun line(text: String, style: TextStyle, sizePx: Float): TextLayoutResult = textMeasurer.measure(
        text = AnnotatedString(text),
        style = style.sized(sizePx),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        constraints = constraints,
    )

    fun paragraph(text: String, style: TextStyle, sizePx: Float, maxLines: Int): TextLayoutResult =
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style.sized(sizePx),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            constraints = constraints,
        )

    /**
     * [text] wrapped onto as many lines as fit in [maxHeight] (at most [BylineMaxLines]), ellipsized
     * only if it needs more; null when not even one line fits.
     */
    fun paragraphWithin(text: String, style: TextStyle, sizePx: Float, maxHeight: Float): TextLayoutResult? {
        for (lines in BylineMaxLines downTo 1) {
            val layout = paragraph(text, style, sizePx, maxLines = lines)
            if (layout.size.height <= maxHeight) return layout
        }
        return null
    }

    /** The largest size at which [text] fits on one line; ellipsized at the smallest size. */
    fun fitLine(text: String, style: TextStyle, sizesPx: List<Float>): TextLayoutResult {
        sizesPx.forEach { sizePx ->
            val layout = line(text, style, sizePx)
            if (layout.lineCount > 0 && !layout.isLineEllipsized(0) && !layout.hasVisualOverflow) return layout
        }
        return line(text, style, sizesPx.last())
    }

    /**
     * The largest size at which [text] fits in [maxLines] lines and [maxHeight] without cutting a
     * word in two. At the smallest size words may break, and what still doesn't fit is ellipsized.
     */
    fun fitParagraph(
        text: String,
        style: TextStyle,
        sizesPx: List<Float>,
        lineHeight: Float,
        maxLines: Int,
        maxHeight: Float,
    ): FittedParagraph {
        sizesPx.forEach { sizePx ->
            val layout = textMeasurer.measure(
                text = AnnotatedString(text),
                style = style.sized(sizePx, lineHeight),
                constraints = constraints,
            )
            if (layout.lineCount <= maxLines && layout.size.height <= maxHeight && !layout.splitsAWord(text)) {
                return FittedParagraph(layout, fitted = true)
            }
        }
        val smallest = sizesPx.last()
        val linesThatFit = floor(maxHeight / (smallest * lineHeight)).toInt().coerceIn(1, maxLines)
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style.sized(smallest, lineHeight),
            overflow = TextOverflow.Ellipsis,
            maxLines = linesThatFit,
            constraints = constraints,
        )
        return FittedParagraph(layout, fitted = false)
    }

    private fun TextStyle.sized(sizePx: Float, lineHeight: Float? = null): TextStyle = with(density) {
        copy(
            fontSize = sizePx.toSp(),
            lineHeight = lineHeight?.let { (sizePx * it).toSp() } ?: this@sized.lineHeight,
        )
    }
}

private fun TextLayoutResult.splitsAWord(text: String): Boolean =
    (0 until lineCount - 1).any { line -> splitsWordAt(text, getLineEnd(line)) }

private fun fontSteps(from: Float, to: Float): List<Float> {
    val steps = FontSizeSteps
    return List(steps) { index -> from + (to - from) * index / (steps - 1) }
}

private fun Char.isWordCharacter(): Boolean {
    if (!isLetterOrDigit()) return false
    if (Character.isIdeographic(code)) return false
    val block = Character.UnicodeBlock.of(this)
    return block != Character.UnicodeBlock.HIRAGANA &&
        block != Character.UnicodeBlock.KATAKANA &&
        block != Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS
}

private fun String.firstCoverGlyph(): String =
    trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/** Filenames often use underscores for spaces; on a cover they read better as spaces. */
private fun String.coverDisplayText(): String? =
    replace('_', ' ').replace(Whitespace, " ").trim().takeIf(String::isNotEmpty)

private fun issueNumberLabel(
    raw: String?,
): String? = raw?.trim()?.removePrefix("#")?.trim()?.takeIf(String::isNotEmpty)?.let { "#$it" }

private fun volumeNumberLabel(
    raw: String?,
): String? = raw?.trim()?.replace(VolumePrefix, "")?.trim()?.takeIf(String::isNotEmpty)

private fun placeholderPalette(
    key: String,
): LibraryCoverPlaceholderPalette =
    PlaceholderPalettes[Math.floorMod(key.trim().lowercase().hashCode(), PlaceholderPalettes.size)]

/** Muted, dark-enough fills with light text, so they read on light and dark themes alike. */
private val PlaceholderPalettes = listOf(
    LibraryCoverPlaceholderPalette(Color(0xFF0F6E56), Color(0xFFE1F5EE)),
    LibraryCoverPlaceholderPalette(Color(0xFF993C1D), Color(0xFFFAECE7)),
    LibraryCoverPlaceholderPalette(Color(0xFF534AB7), Color(0xFFEEEDFE)),
    LibraryCoverPlaceholderPalette(Color(0xFF185FA5), Color(0xFFE6F1FB)),
    LibraryCoverPlaceholderPalette(Color(0xFF993556), Color(0xFFFBEAF0)),
    LibraryCoverPlaceholderPalette(Color(0xFF854F0B), Color(0xFFFAEEDA)),
    LibraryCoverPlaceholderPalette(Color(0xFF3B6D11), Color(0xFFEAF3DE)),
    LibraryCoverPlaceholderPalette(Color(0xFF5F5E5A), Color(0xFFF1EFE8)),
    LibraryCoverPlaceholderPalette(Color(0xFF0C447C), Color(0xFFE6F1FB)),
    LibraryCoverPlaceholderPalette(Color(0xFF3C3489), Color(0xFFEEEDFE)),
    LibraryCoverPlaceholderPalette(Color(0xFF72243E), Color(0xFFFBEAF0)),
    LibraryCoverPlaceholderPalette(Color(0xFF085041), Color(0xFFE1F5EE)),
)

// Safe zone as fractions of the cover: clear of the spine on the left, margins elsewhere.
private const val SafeZoneStart = 0.15f
private const val SafeZoneEnd = 0.09f
private const val SafeZoneTop = 0.08f
private const val SafeZoneBottom = 0.07f

// Room for the read/rating/download chips LibraryCover and the grid card draw on the cover.
private val BadgeReserve = 32.dp
// Below this the full layout's text gets too small to read, so only one big glyph is drawn.
private val CompactCoverWidth = 110.dp
private val MinLabelSize = 9.sp
private val MinTitleSize = 11.sp

private const val TitleMaxLines = 4
private const val BylineMaxLines = 4
private const val TitleLineHeight = 1.15f
private const val FontSizeSteps = 6
private val Whitespace = Regex("\\s+")
private val VolumePrefix = Regex("^(vol(ume)?\\.?|v)\\s*(?=\\d)", RegexOption.IGNORE_CASE)
