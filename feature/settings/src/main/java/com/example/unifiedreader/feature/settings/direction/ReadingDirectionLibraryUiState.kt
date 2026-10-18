package com.example.unifiedreader.feature.settings.direction

import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection

data class ReadingDirectionLibraryUiState(
    val libraryId: String,
    val title: String,
    val direction: ComicReadingDirection,
)
