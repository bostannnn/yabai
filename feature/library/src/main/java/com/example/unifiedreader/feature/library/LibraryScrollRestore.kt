package com.example.unifiedreader.feature.library

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
