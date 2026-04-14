package com.example.unifiedreader.feature.settings.mode

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferences

fun buildComicReaderModeLibraryRows(
    availableLibraries: List<Library>,
    preferences: ComicReaderModePreferences,
    sourceType: SourceType,
): List<ReaderModeLibraryUiState> {
    return availableLibraries
        .filter { library -> library.sourceType == sourceType }
        .asSequence()
        .sortedBy { library -> library.title.lowercase() }
        .map { library ->
            ReaderModeLibraryUiState(
                libraryId = library.id,
                title = library.title,
                mode = preferences.libraryModesById[library.id] ?: ComicReaderMode.Paged,
            )
        }
        .toList()
}
