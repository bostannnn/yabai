package com.example.unifiedreader.feature.settings.mode

import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode

data class ReaderModeLibraryUiState(
    val libraryId: String,
    val title: String,
    val mode: ComicReaderMode,
)
