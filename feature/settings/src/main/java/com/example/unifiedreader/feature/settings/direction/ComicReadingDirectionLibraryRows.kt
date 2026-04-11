package com.example.unifiedreader.feature.settings.direction

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences

fun buildComicReadingDirectionLibraryRows(
    availableLibraries: List<Library>,
    preferences: ComicReadingDirectionPreferences,
    sourceType: SourceType,
): List<ReadingDirectionLibraryUiState> {
    return availableLibraries
        .filter { library -> library.sourceType == sourceType }
        .asSequence()
        .filter { library -> library.contentType == LibraryContentType.COMICS }
        .sortedBy { library -> library.title.lowercase() }
        .map { library ->
            ReadingDirectionLibraryUiState(
                libraryId = library.id,
                title = library.title,
                direction = preferences.libraryDirectionsById[library.id] ?: ComicReadingDirection.LTR,
            )
        }
        .toList()
}
