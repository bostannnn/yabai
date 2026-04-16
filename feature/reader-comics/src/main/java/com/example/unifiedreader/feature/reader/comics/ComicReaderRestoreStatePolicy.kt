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
    return if (hasRestorableComicReaderPosition(progress)) {
        preferences
    } else {
        preferences.withClearedResumePosition()
    }
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
)
