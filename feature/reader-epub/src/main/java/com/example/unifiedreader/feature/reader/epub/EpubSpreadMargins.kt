package com.example.unifiedreader.feature.reader.epub

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.readium.r2.shared.publication.Publication

/**
 * Whether a spread gets book margins. Readium pads both sides of each of its two pages alike, so the
 * middle came out twice as wide as an outer edge, the reverse of a printed book. On a window wide enough
 * to keep Readium's two columns once the strips are taken off it, the reader paints a strip of page
 * colour outside each outer edge ([epubSpreadSideStrip]) and Readium pads each page by
 * [EpubSpreadPageGutterPx] only: the middle is then as wide as one outer edge.
 */
internal fun usesEpubSpreadMargins(
    isReflowableSpread: Boolean,
    windowWidthDp: Int,
): Boolean = isReflowableSpread && windowWidthDp >= EpubSpreadMarginsMinWindowWidthDp

/**
 * The strip outside each outer edge of a spread, as wide as Readium's padding at a page's side, so an outer
 * edge has twice a page's inner side: at Normal margins 64 dp at each edge and 64 dp in the middle.
 * [pageMargins] is the Margins setting, null when the book keeps its own styling (Readium's 1.0).
 */
internal fun epubSpreadSideStrip(
    usesSpreadMargins: Boolean,
    pageMargins: Double?,
): Dp = if (usesSpreadMargins) {
    (EpubSpreadPageGutterPx * (pageMargins ?: 1.0).coerceIn(0.0, EpubSpreadMaxPageMargins)).dp
} else {
    0.dp
}

/** [epubSpreadSideStrip] for this book with these [preferences] in the current window. */
@Composable
internal fun Publication.epubSpreadSideStrip(preferences: EpubReaderPreferences): Dp = epubSpreadSideStrip(
    usesSpreadMargins = usesEpubSpreadMargins(
        isReflowableSpread = isEpubReflowableSpread(preferences.layoutMode),
        windowWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() },
    ),
    // The book's own styling leaves Readium's margins at 1.0.
    pageMargins = preferences.pageMargins.takeIf { preferences.typographyMode == EpubTypographyMode.Custom },
)

/** Readium's padding at each side of a page of a spread, before the Margins setting; its own grows to 50 px on a tablet. */
internal const val EpubSpreadPageGutterPx = 32.0

private val EpubSpreadMaxPageMargins = EpubMarginSteps.maxOf { step -> step.value }

// Readium shows two columns from a 960 px viewport; the widest strips (Wide) take 2 x 64 dp off it.
private val EpubSpreadMarginsMinWindowWidthDp = 960 + 2 * (EpubSpreadPageGutterPx * EpubSpreadMaxPageMargins).toInt()
