package com.example.unifiedreader.feature.library.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.libraryEraPeriods
import com.example.unifiedreader.feature.library.LibraryGridHeading
import com.example.unifiedreader.feature.library.LibraryGridHeadingText
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.appGutter
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Browse with nothing picked: each shown row under its name and how many books have it, in the
 * shape of what is in it. Set in is a rail of country cards, When a shelf of piles, Genre list
 * covers, Your reading the books you are in; About and the rest are chips.
 */
@Composable
internal fun LibraryBrowseLanding(
    rows: List<LibraryBrowseRow>,
    context: LibraryBrowseRowContext,
    contentTopInset: Dp,
    onScrolledChanged: (Boolean) -> Unit,
    prompts: LibraryBrowseLandingPrompts = LibraryBrowseLandingPrompts(),
) {
    val view = context.view
    val actions = context.actions
    val listState = rememberLazyListState()
    val latestOnScrolledChanged by rememberUpdatedState(onScrolledChanged)
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollBackward }
            .distinctUntilChanged()
            .collect { scrolled -> latestOnScrolledChanged(scrolled) }
    }
    val gutter = appGutter()
    val shownRows = rows.filter { row -> view.result.facets[row].orEmpty().isNotEmpty() }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = gutter,
            end = gutter,
            top = contentTopInset + AppLayout.contentTop,
            bottom = AppLayout.contentBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap),
    ) {
        val checkGuesses = view.tagPrompt as? LibraryBrowseTagPrompt.CheckGuesses
        val onCheckGuesses = prompts.onCheckGuesses
        if (checkGuesses != null && onCheckGuesses != null) {
            item(key = "check-guesses") {
                LibraryBrowseCheckGuessesPanel(prompt = checkGuesses, onCheck = onCheckGuesses, onNotNow = prompts.onNotNow)
            }
        }
        shownRows.forEach { row ->
            val facets = view.result.facets[row].orEmpty()
            item(key = row.name) {
                Column(verticalArrangement = Arrangement.spacedBy(AppLayout.headingGap)) {
                    LibraryGridHeadingText(
                        heading = LibraryGridHeading(title = row.label, count = bookCountLabel(view.coverage[row] ?: 0)),
                    )
                    val newTags = view.tagPrompt as? LibraryBrowseTagPrompt.NewTags
                    if (row == LibraryBrowseRow.ABOUT && newTags != null) {
                        LibraryBrowseNewTagsLine(prompt = newTags, onSort = prompts.onSortNewTags)
                    }
                    when (row) {
                        LibraryBrowseRow.SET_IN -> LibraryBrowsePlaceRail(
                            countries = view.countries,
                            picked = view.picked,
                            books = context.books,
                            onToggle = actions.onPick,
                            onOpenPlaces = context.onOpenPlaces,
                            onSortTag = prompts.onSortTag,
                        )
                        // Named periods have no pile; with only those, they stay chips.
                        LibraryBrowseRow.WHEN -> if (view.timeline.isNotEmpty()) {
                            LibraryBrowseShelf(bars = view.timeline, picked = view.picked, onToggle = actions.onPick)
                        } else {
                            LibraryBrowseChipFlow(
                                row = row,
                                facets = libraryEraPeriods(facets),
                                picked = view.picked,
                                onToggle = actions.onPick,
                                foldedCount = LandingFoldedChipCount,
                                onSortTag = prompts.onSortTag,
                            )
                        }
                        LibraryBrowseRow.GENRE -> {
                            val genres = remember(facets, view.genreBooks) { LibraryBrowseGenres(facets, view.genreBooks) }
                            Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
                                LibraryBrowseGenreSplit(genres = genres, picked = view.picked, onToggle = actions.onPick)
                                LibraryBrowseGenreTiles(genres = genres, books = context.books, picked = view.picked, onToggle = actions.onPick)
                            }
                        }
                        LibraryBrowseRow.READING -> LibraryBrowseReadingPanels(
                            facets = facets,
                            inProgress = view.inProgress,
                            statusBooks = view.statusBooks,
                            books = context.books,
                            onToggle = actions.onPick,
                            onItemSelected = actions.onItemSelected,
                        )
                        else -> LibraryBrowseChipFlow(
                            row = row,
                            facets = facets,
                            picked = view.picked,
                            onToggle = actions.onPick,
                            foldedCount = if (row == LibraryBrowseRow.ABOUT) AboutFoldedChipCount else LandingFoldedChipCount,
                            moreLabel = { "All ${facets.size}" },
                            onSortTag = prompts.onSortTag,
                        )
                    }
                }
            }
        }
    }
}

/** What the landing page can do about unsorted tags: open Settings › Tags, hide the panel, sort a held chip. */
internal class LibraryBrowseLandingPrompts(
    val onCheckGuesses: (() -> Unit)? = null,
    val onNotNow: () -> Unit = {},
    val onSortNewTags: (() -> Unit)? = null,
    val onSortTag: ((LibraryBrowseChip) -> Unit)? = null,
)

private const val LandingFoldedChipCount = 20
private const val AboutFoldedChipCount = 14
