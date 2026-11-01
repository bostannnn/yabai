package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.LibraryFilterState

/**
 * What the library grid does about a saved scroll position it has not restored yet. A pager that
 * starts empty, or with placeholders off loads only its first pages, leaves the grid fewer slots
 * than the saved index: the grid clamps, and reporting that clamped spot would overwrite the real one.
 */
internal sealed interface LibraryScrollRestoreStep {
    /** The grid can reach the saved index: scroll there. */
    data object ScrollToSaved : LibraryScrollRestoreStep

    /** Items are still loading. */
    data object Wait : LibraryScrollRestoreStep

    /** Loading stopped short of the saved index but more pages exist: read the last loaded slot. */
    data object LoadMore : LibraryScrollRestoreStep

    /** Everything is loaded and the saved index is past it (the view shrank): stay where the grid is. */
    data object GiveUp : LibraryScrollRestoreStep
}

internal fun libraryScrollRestoreStep(
    saved: LibraryScrollPosition,
    gridItemCount: Int,
    isLoading: Boolean,
    canLoadMore: Boolean,
): LibraryScrollRestoreStep = when {
    gridItemCount > saved.index -> LibraryScrollRestoreStep.ScrollToSaved
    isLoading -> LibraryScrollRestoreStep.Wait
    canLoadMore -> LibraryScrollRestoreStep.LoadMore
    else -> LibraryScrollRestoreStep.GiveUp
}

/**
 * Where a browse context opens: where it was left, except that after a change of picks a spot above All
 * books' row ([resultsRowIndex]) becomes that row, so the books the picks found are in view.
 */
internal fun libraryStartScrollPosition(
    saved: LibraryScrollPosition,
    picksChanged: Boolean,
    resultsRowIndex: Int,
): LibraryScrollPosition = if (picksChanged && saved.index < resultsRowIndex) {
    LibraryScrollPosition(index = resultsRowIndex, offset = 0)
} else {
    saved
}

/** The picks the library's last browse context was drawn with, to tell a change of picks from any other new context. */
internal class LibraryContextPicks {
    private var contextKey: String? = null
    private var picks: LibraryFilterState? = null

    fun changedFor(contextKey: String, filters: LibraryFilterState): Boolean =
        this.contextKey != null && this.contextKey != contextKey && picks != filters

    /** Called after each drawn frame; keeps the picks a context had when it arrived. */
    fun record(contextKey: String, filters: LibraryFilterState) {
        if (this.contextKey == contextKey) return
        this.contextKey = contextKey
        picks = filters
    }
}
