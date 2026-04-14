package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

internal fun comicProgressBarDirection(
    readerMode: ComicReaderMode,
    readingDirection: ComicReadingDirection,
): ComicReadingDirection = when (readerMode) {
    ComicReaderMode.Paged -> readingDirection
    ComicReaderMode.VerticalScroll -> ComicReadingDirection.LTR
}
