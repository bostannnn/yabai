package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot

internal fun savedComicReaderPage(
    progress: ReaderProgressSnapshot?,
): Int? = progress
    ?.takeIf(::hasRestorableComicReaderPosition)
    ?.page

internal fun effectiveComicReaderPreferences(
    session: ReaderSessionSnapshot?,
    progress: ReaderProgressSnapshot?,
): ComicReaderPreferencesSnapshot? {
    val preferences = restoreComicReaderPreferences(session) ?: return null
    return if (hasRestorableComicReaderPosition(progress) && preferences.resumePositionBelongsTo(progress?.page)) {
        preferences
    } else {
        preferences.withClearedResumePosition()
    }
}

// The resume position refines the saved page (the spread it opens on, the webtoon scroll offset).
// Progress pulled from another device moves the page but not the session, so a position saved with
// another page is stale and would reopen the book there, then push that page back. Sessions saved
// before the page was kept with it fall back to the anchor itself, which webtoon mode always sets to
// the page; in spread mode dropping it still opens the spread that holds the page.
private fun ComicReaderPreferencesSnapshot.resumePositionBelongsTo(
    page: Int?,
): Boolean = when {
    resumeProgressPage != null -> resumeProgressPage == page
    resumeAnchorPage != null -> resumeAnchorPage == page
    else -> true
}

private fun hasRestorableComicReaderPosition(
    progress: ReaderProgressSnapshot?,
): Boolean = progress != null &&
    !progress.isExplicitUnreadReset &&
    progress.locatorType == LocatorType.PAGE &&
    progress.page != null

private fun ComicReaderPreferencesSnapshot.withClearedResumePosition(): ComicReaderPreferencesSnapshot = copy(
    resumeAnchorPage = null,
    resumeSegment = null,
    resumeScrollOffsetRatio = null,
    resumeProgressPage = null,
)
