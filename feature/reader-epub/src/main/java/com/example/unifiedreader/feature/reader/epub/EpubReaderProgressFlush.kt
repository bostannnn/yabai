package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.readium.r2.shared.publication.Locator

// Until the navigator reports its first location the book was never shown (it is still opening,
// or it failed to open), so the stored progress is already current: re-saving it would only
// stamp it DIRTY and push it over newer progress from another device.
internal fun shouldFlushEpubReaderProgress(
    navigatorShowedBook: Boolean,
    restoreProgressSnapshot: ReaderProgressSnapshot?,
    currentLocator: Locator?,
    currentPage: Int,
    currentProgress: Double,
): Boolean = navigatorShowedBook && (
    restoreProgressSnapshot != null ||
        currentLocator != null ||
        currentPage > 0 ||
        currentProgress > 0.0
    )
