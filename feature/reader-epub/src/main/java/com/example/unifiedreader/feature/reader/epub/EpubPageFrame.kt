package com.example.unifiedreader.feature.reader.epub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppWindowHeight
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowHeight
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.ReaderPageColor
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.ReadingProgression

/**
 * The page's frame: [containerInset] is the strip above the text and the one below it, in the
 * page colour, the number in the bottom one and the chapter line in the top. [contentPaddingPx]
 * pads the book's body inside the web view.
 */
internal data class EpubViewportSpacing(
    val containerInset: Dp,
    val contentPaddingPx: Int,
)

/** 56 dp strips on a tablet, portrait or spread; 40 dp on a phone, where height is short. */
internal fun epubViewportSpacing(
    isLandscape: Boolean,
    isCompact: Boolean,
): EpubViewportSpacing = EpubViewportSpacing(
    containerInset = if (isCompact) 40.dp else 56.dp,
    contentPaddingPx = if (isLandscape) 24 else 12,
)

@Composable
internal fun currentEpubViewportSpacing(isLandscape: Boolean): EpubViewportSpacing {
    val isCompact = currentAppWindowWidth() == AppWindowWidth.Compact ||
        currentAppWindowHeight() == AppWindowHeight.Compact
    return remember(isLandscape, isCompact) { epubViewportSpacing(isLandscape, isCompact) }
}

/**
 * The lines in the top strip, in reading order: the chapter over a page, or on a spread the book
 * over the first page and the chapter over the second. None on a chapter's first page, which
 * carries the chapter's own heading, or when the book has no chapter for this place.
 */
internal fun buildEpubPageHeads(
    bookTitle: String,
    chapterTitle: String?,
    isSpread: Boolean,
    isChapterStart: Boolean,
): List<String> {
    val chapter = chapterTitle?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
    if (isChapterStart) return emptyList()
    return if (isSpread) listOf(bookTitle.trim(), chapter) else listOf(chapter)
}

/** [heads] centred in the top strip, one over each page of a spread; a right-to-left book starts on the right. */
@Composable
internal fun BoxScope.EpubPageHeads(
    heads: List<String>,
    color: Color,
    height: Dp,
    isRightToLeft: Boolean,
) {
    if (heads.isEmpty()) return
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .testTag(EpubPageHeadsTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (if (isRightToLeft) heads.asReversed() else heads).forEach { head ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = EpubPageHeadSideInset),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = head,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Where the reader is, as the page's frame labels need it. [resourcePage] index 0 is a chapter's first page. */
internal data class EpubFrameLocation(
    val currentHref: String?,
    val currentProgress: Double,
    val currentPage: Int,
    val pageCount: Int,
    val secondSpreadPage: Int?,
    val resourcePage: EpubResourcePage,
)

/**
 * The page number in the bottom strip with the pages left in the chapter at its corner, and the
 * chapter line in the top one, in the page's number colour.
 */
@Composable
internal fun BoxScope.EpubPageFrameLabels(
    publication: Publication,
    bookTitle: String,
    tocEntries: List<EpubTocEntry>,
    resourcePositions: List<EpubResourcePositions>,
    location: EpubFrameLocation,
    layoutMode: EpubReaderLayoutMode,
    pageColor: ReaderPageColor,
    stripHeight: Dp,
) {
    val color = pageColor.colors.number
    EpubPageNumbers(
        numbers = publication.buildEpubPageNumbers(
            currentPage = location.currentPage,
            pageCount = location.pageCount,
            layoutMode = layoutMode,
            secondSpreadPage = location.secondSpreadPage,
        ),
        color = color,
        height = stripHeight,
    )
    val isRightToLeft = publication.metadata.readingProgression == ReadingProgression.RTL
    val isScrolled = layoutMode == EpubReaderLayoutMode.Scroll
    val chapterPagesLeft = remember(tocEntries, resourcePositions, location) {
        if (isScrolled) return@remember null
        epubChapterPagesLeft(
            resourcePage = location.resourcePage,
            resource = resourcePositions.epubResourceAt(location.currentPage),
            currentPosition = location.currentPage,
            chapterEndPosition = tocEntries.epubChapterEndPosition(location.currentProgress, location.currentHref, location.pageCount),
        )
    }
    if (chapterPagesLeft != null) {
        EpubChapterPagesLeft(
            pagesLeft = chapterPagesLeft,
            short = currentAppWindowWidth() == AppWindowWidth.Compact,
            color = color,
            height = stripHeight,
            isRightToLeft = isRightToLeft,
        )
    }
    val chapterTitle = remember(tocEntries, location.currentHref, location.currentProgress) {
        tocEntries.currentEpubChapterTitle(currentProgress = location.currentProgress, currentHref = location.currentHref)
    }
    EpubPageHeads(
        heads = buildEpubPageHeads(
            bookTitle = bookTitle,
            chapterTitle = chapterTitle,
            isSpread = publication.isEpubReflowableSpread(layoutMode),
            // A scrolled chapter has no pages; its line stays.
            isChapterStart = !isScrolled && location.resourcePage.pageIndex == 0,
        ),
        color = color,
        height = stripHeight,
        isRightToLeft = isRightToLeft,
    )
}

// Keeps a long chapter title, and the pages left in the chapter, inside the page's side margins.
internal val EpubPageHeadSideInset = 48.dp

internal const val EpubPageHeadsTag = "epub-page-heads"
