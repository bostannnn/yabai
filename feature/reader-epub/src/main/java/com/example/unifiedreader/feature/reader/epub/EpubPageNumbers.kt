package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.example.unifiedreader.feature.reader.core.ReaderPageNumber
import com.example.unifiedreader.feature.reader.core.ReaderPageNumberStyle
import com.example.unifiedreader.feature.reader.core.ReaderPageNumbers
import kotlin.math.ceil
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.ReadingProgression
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation

/** Where Readium's positions of one reading-order resource start, and how many it has. */
internal data class EpubResourcePositions(
    val firstPosition: Int,
    val count: Int,
)

internal fun List<List<Locator>>.toEpubResourcePositions(): List<EpubResourcePositions> = map { positions ->
    EpubResourcePositions(
        firstPosition = positions.firstOrNull()?.locations?.position ?: 0,
        count = positions.size,
    )
}

/**
 * Where the navigator is inside its resource: page [pageIndex] of [totalPages], a spread
 * counting as one. [isSecondColumnBlank]: the spread's second column is the empty one Readium
 * adds when the resource ends on an odd column.
 */
internal data class EpubResourcePage(
    val pageIndex: Int,
    val totalPages: Int?,
    val isSecondColumnBlank: Boolean = false,
)

/**
 * The number of a spread's second page (the right one in a left-to-right book), found the way
 * Readium finds the first: the resource's position at ceil(progression × (count − 1)), with the
 * progression moved on by one column, half a spread. Null when that column is blank or the
 * resource's positions are unknown.
 */
internal fun epubSecondSpreadPage(
    locator: Locator,
    resourcePage: EpubResourcePage,
    resource: EpubResourcePositions?,
): Int? {
    val totalPages = resourcePage.totalPages ?: return null
    val progression = locator.locations.progression ?: return null
    // Without Readium's own position the first number is a fallback, and the second would not match it.
    if (locator.locations.position == null) return null
    if (resourcePage.isSecondColumnBlank || resource == null) return null
    if (totalPages <= 0 || resource.count <= 0 || resource.firstPosition <= 0) return null
    val secondColumnProgression = (progression + 0.5 / totalPages).coerceIn(0.0, 1.0)
    val index = ceil(secondColumnProgression * (resource.count - 1))
        .toInt()
        .coerceIn(0, resource.count - 1)
    return resource.firstPosition + index
}

/** [buildEpubPageNumbers] for this book: a spread only in a reflowable book, whose columns Readium lays out. */
internal fun Publication.buildEpubPageNumbers(
    currentPage: Int,
    pageCount: Int,
    layoutMode: EpubReaderLayoutMode,
    secondSpreadPage: Int?,
): List<ReaderPageNumber> = buildEpubPageNumbers(
    currentPage = currentPage,
    pageCount = pageCount,
    isSpread = layoutMode == EpubReaderLayoutMode.DoublePage &&
        metadata.presentation.layout != EpubLayout.FIXED,
    isRightToLeft = metadata.readingProgression == ReadingProgression.RTL,
    secondSpreadPage = secondSpreadPage,
)

/** [epubSecondSpreadPage] for this book, from the positions of the locator's resource. */
internal fun Publication.epubSecondSpreadPage(
    locator: Locator,
    resourcePage: EpubResourcePage,
    resourcePositions: List<EpubResourcePositions>,
): Int? = epubSecondSpreadPage(
    locator = locator,
    resourcePage = resourcePage,
    resource = readingOrderIndexOf(locator)?.let(resourcePositions::getOrNull),
)

/**
 * The numbers under the text: one in the middle, or on a spread one under each page, the
 * reading order's first page on the left unless the book reads right to left. Empty until the
 * book's page count is known.
 */
internal fun buildEpubPageNumbers(
    currentPage: Int,
    pageCount: Int,
    isSpread: Boolean,
    isRightToLeft: Boolean,
    secondSpreadPage: Int?,
): List<ReaderPageNumber> {
    if (currentPage <= 0 || pageCount <= 0) return emptyList()
    val firstLabel = epubPageNumberLabel(currentPage, pageCount)
    if (!isSpread) return listOf(ReaderPageNumber(firstLabel))
    val firstCenter = if (isRightToLeft) RightPageCenter else LeftPageCenter
    return listOfNotNull(
        ReaderPageNumber(firstLabel, firstCenter),
        secondSpreadPage?.let { page ->
            ReaderPageNumber(epubPageNumberLabel(page, pageCount), 1f - firstCenter)
        },
    )
}

internal fun epubPageNumberLabel(page: Int, pageCount: Int): String = "${page.coerceAtMost(pageCount)} / $pageCount"

/** The numbers in the blank strip the reader keeps under the text, in that strip's height. */
@Composable
internal fun BoxScope.EpubPageNumbers(
    numbers: List<ReaderPageNumber>,
    theme: EpubReaderTheme,
    isLandscape: Boolean,
) {
    ReaderPageNumbers(
        numbers = numbers,
        style = ReaderPageNumberStyle.OnPaper(epubPageNumberColor(theme)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(defaultEpubViewportSpacing(isLandscape).containerInset)
            .testTag(EpubPageNumbersTag),
    )
}

// About half-way between the page and its ink: readable, quieter than the text.
private fun epubPageNumberColor(theme: EpubReaderTheme): Color = when (theme) {
    EpubReaderTheme.Light -> Color(0xFF8A8985)
    EpubReaderTheme.Dark -> Color(0xFF7A7873)
}

// Readium lays a spread out as two equal columns across the page view.
private const val LeftPageCenter = 0.25f
private const val RightPageCenter = 0.75f

internal const val EpubPageNumbersTag = "epub-page-numbers"

/** True when the resource ends on the empty column Readium appends after an odd last column of a spread. */
internal val EpubSecondColumnBlankScript = """
    (function() {
      return document.getElementById('readium-virtual-page') !== null;
    })();
""".trimIndent()
