package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * The first position after the chapter the reader is in (the one the chapter line names): where
 * the next entry at its level or above starts, or one past [pageCount] after the book's last
 * chapter. Null outside the contents or without positions.
 */
internal fun List<EpubTocEntry>.epubChapterEndPosition(
    currentProgress: Double,
    currentHref: String?,
    pageCount: Int,
): Int? {
    val chapter = epubTocChapterEntry(activeEpubTocEntry(currentProgress = currentProgress, currentHref = currentHref))
    val start = chapter?.position
    val index = indexOfFirst { it.id == chapter?.id }
    if (chapter == null || start == null || index < 0) return null
    val next = drop(index + 1).firstOrNull { entry -> entry.depth <= chapter.depth && (entry.position ?: start) > start }
    return next?.position ?: (pageCount + 1).takeIf { pageCount >= start }
}

/**
 * The page turns left in the chapter after this page, a spread counting as one: the rest of the
 * open file's pages, and the chapter's later files estimated at the open file's pages per
 * position. Null until the file's pages are known, or when [currentPosition] is outside [resource].
 */
internal fun epubChapterPagesLeft(
    resourcePage: EpubResourcePage,
    resource: EpubResourcePositions?,
    currentPosition: Int,
    chapterEndPosition: Int?,
): Int? {
    val totalPages = resourcePage.totalPages ?: 0
    val pageKnown = totalPages > 0 && resourcePage.pageIndex >= 0
    val file = resource?.takeIf { it.count > 0 && currentPosition in it.firstPosition until it.firstPosition + it.count }
    if (file == null || chapterEndPosition == null || !pageKnown) return null
    val fileEnd = file.firstPosition + file.count
    val leftInFile = (totalPages - 1 - resourcePage.pageIndex).coerceAtLeast(0)
    val pagesPerPosition = totalPages.toDouble() / file.count
    return if (chapterEndPosition >= fileEnd) {
        leftInFile + ((chapterEndPosition - fileEnd) * pagesPerPosition).roundToInt()
    } else {
        // The next chapter starts inside this file: count up to it.
        ((chapterEndPosition - 1 - currentPosition) * pagesPerPosition).roundToInt().coerceIn(0, leftInFile)
    }
}

/** The file whose positions hold [position]. */
internal fun List<EpubResourcePositions>.epubResourceAt(position: Int): EpubResourcePositions? =
    firstOrNull { resource -> position >= resource.firstPosition && position < resource.firstPosition + resource.count }

/** The corner's words: "6 left in chapter" ("6 left" when [short]), "End of chapter" on its last page. */
internal fun epubChapterPagesLeftLabel(pagesLeft: Int, short: Boolean): String = when {
    pagesLeft <= 0 -> "End of chapter"
    short -> "$pagesLeft left"
    else -> "$pagesLeft left in chapter"
}

/** What TalkBack reads for the corner. */
internal fun epubChapterPagesLeftDescription(pagesLeft: Int): String = when {
    pagesLeft <= 0 -> "Last page of the chapter"
    pagesLeft == 1 -> "1 page left in the chapter"
    else -> "$pagesLeft pages left in the chapter"
}

/**
 * The page turns left in the chapter at the bottom strip's outer corner: the end one, the start
 * one in a right-to-left book; on a spread that is under its last page.
 */
@Composable
internal fun BoxScope.EpubChapterPagesLeft(
    pagesLeft: Int,
    short: Boolean,
    color: Color,
    height: Dp,
    isRightToLeft: Boolean,
) {
    Text(
        text = epubChapterPagesLeftLabel(pagesLeft, short),
        // Tabular figures, as the page number has.
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1,
        // The book's direction picks the corner, not the app's.
        textAlign = if (isRightToLeft) TextAlign.Left else TextAlign.Right,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(height)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = EpubPageHeadSideInset)
            .semantics { contentDescription = epubChapterPagesLeftDescription(pagesLeft) }
            .testTag(EpubChapterPagesLeftTag),
    )
}

internal const val EpubChapterPagesLeftTag = "epub-chapter-pages-left"
