package com.example.unifiedreader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.feature.journal.JournalFinishedNext
import com.example.unifiedreader.feature.journal.JournalFinishedScreen
import com.example.unifiedreader.feature.journal.rememberJournalReviewState
import com.example.unifiedreader.feature.journal.shouldSaveJournalReview
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.VisibleResolvedLibraryCover
import com.example.unifiedreader.feature.library.animateCoverColors
import com.example.unifiedreader.feature.library.rememberLibraryCoverColors
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.ui.theme.AppCoverColors

/**
 * The Finished screen over the reader once a book is done (`readerCompletionState`): the rating
 * saves on tap; the note saves when the screen closes, whichever way it closes.
 */
@Composable
internal fun ReaderFinishedHost(
    uiState: AppUiState,
    viewModel: AppViewModel,
) {
    val completionState = uiState.readerCompletionState ?: return
    val item = uiState.items.firstOrNull { it.id == completionState.itemId }
        ?: uiState.selectedItem?.takeIf { it.id == completionState.itemId }
        ?: return
    val nextItem = completionState.nextItemId?.let { nextItemId ->
        uiState.items.firstOrNull { item -> item.id == nextItemId }
    }
    val entry = uiState.journalSnapshot.entryByItemId[item.id]
    val reviewState = rememberJournalReviewState(
        itemId = item.id,
        initialRating = entry?.rating,
        initialReviewText = entry?.reviewText.orEmpty(),
    )
    val journalItem = item.toJournalItemRef()
    val saveReviewIfNeeded = {
        if (shouldSaveJournalReview(
                initialRating = entry?.rating,
                initialReviewText = entry?.reviewText.orEmpty(),
                rating = reviewState.selectedRating,
                reviewText = reviewState.reviewText,
            )
        ) {
            viewModel.saveJournalEntry(
                item = journalItem,
                rating = reviewState.selectedRating,
                reviewText = reviewState.reviewText,
            )
        }
    }
    // The cover's colours, as on the book page: a quiet surface until they arrive or without any.
    val scheme = MaterialTheme.colorScheme
    val colors = animateCoverColors(
        rememberLibraryCoverColors(item, viewModel::coverImageUrlFor) ?: AppCoverColors(
            tintStart = scheme.surfaceContainerHigh,
            tintEnd = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            track = scheme.onSurface.copy(alpha = 0.12f),
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            accentContainer = scheme.primaryContainer,
            onAccentContainer = scheme.onPrimaryContainer,
        ),
    )

    JournalFinishedScreen(
        item = item,
        title = item.libraryPrimaryText(),
        subtitle = item.librarySecondaryText(),
        colors = colors,
        reviewState = reviewState,
        coverContent = { coverItem, modifier ->
            VisibleResolvedLibraryCover(
                item = coverItem,
                coverImageUrlProvider = viewModel::coverImageUrlFor,
                onCoverVisible = viewModel::ensureCoverCached,
                imageVariant = if (coverItem.id == item.id) LibraryCoverImageVariant.Grid else LibraryCoverImageVariant.ShelfRow,
                modifier = modifier,
            )
        },
        onRatingSelected = { rating ->
            viewModel.saveJournalEntry(
                item = journalItem,
                rating = rating,
                reviewText = reviewState.reviewText,
            )
        },
        onBackToLibrary = {
            saveReviewIfNeeded()
            // Leaving clears the completion too; the reader saves its place as it closes.
            viewModel.onBackPressed()
        },
        onDismiss = {
            saveReviewIfNeeded()
            viewModel.dismissReaderCompletion()
        },
        next = nextItem?.let { next ->
            val series = next.libraryPrimaryText()
            val name = next.librarySecondaryText()
            JournalFinishedNext(
                item = next,
                title = name ?: series,
                label = if (name != null) "Up next in $series" else "Up next",
                onRead = {
                    saveReviewIfNeeded()
                    viewModel.startReaderCompletionNextItem()
                },
            )
        },
    )
}

private fun ReadableItem.toJournalItemRef(): JournalItemRef = JournalItemRef(
    itemId = id,
    sourceType = sourceType,
    libraryId = libraryId,
    itemTitle = title,
    itemPrimaryText = libraryPrimaryText(),
    itemSecondaryText = librarySecondaryText(),
    coverUrl = coverUrl,
    itemKind = kind,
    format = format,
)
