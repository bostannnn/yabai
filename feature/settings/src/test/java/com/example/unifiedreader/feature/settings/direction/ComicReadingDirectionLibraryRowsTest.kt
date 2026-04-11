package com.example.unifiedreader.feature.settings.direction

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class ComicReadingDirectionLibraryRowsTest {
    @Test
    fun `available libraries build direction rows`() {
        val rows = buildComicReadingDirectionLibraryRows(
            availableLibraries = listOf(
                library(
                    sourceType = SourceType.KAVITA,
                    remoteId = "k-empty",
                    title = "Empty Comics",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            preferences = ComicReadingDirectionPreferences(
                libraryDirectionsById = mapOf(
                    SourceType.KAVITA.remoteLibraryId("k-empty") to ComicReadingDirection.RTL,
                ),
            ),
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            listOf(
                ReadingDirectionLibraryUiState(
                    libraryId = SourceType.KAVITA.remoteLibraryId("k-empty"),
                    title = "Empty Comics",
                    direction = ComicReadingDirection.RTL,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `non comic available libraries still create direction rows`() {
        val rows = buildComicReadingDirectionLibraryRows(
            availableLibraries = listOf(
                library(
                    sourceType = SourceType.KAVITA,
                    remoteId = "k-books",
                    title = "Books",
                    contentType = LibraryContentType.BOOKS,
                ),
            ),
            preferences = ComicReadingDirectionPreferences(),
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            listOf(
                ReadingDirectionLibraryUiState(
                    libraryId = SourceType.KAVITA.remoteLibraryId("k-books"),
                    title = "Books",
                    direction = ComicReadingDirection.LTR,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `empty available libraries do not fall back to stale rows`() {
        val rows = buildComicReadingDirectionLibraryRows(
            availableLibraries = emptyList(),
            preferences = ComicReadingDirectionPreferences(),
            sourceType = SourceType.KAVITA,
        )

        assertEquals(emptyList<ReadingDirectionLibraryUiState>(), rows)
    }

    @Test
    fun `rows are limited to the requested source`() {
        val rows = buildComicReadingDirectionLibraryRows(
            availableLibraries = listOf(
                library(
                    sourceType = SourceType.KAVITA,
                    remoteId = "k-comics",
                    title = "Kavita Comics",
                    contentType = LibraryContentType.COMICS,
                ),
                library(
                    sourceType = SourceType.STUMP,
                    remoteId = "s-comics",
                    title = "Stump Comics",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            preferences = ComicReadingDirectionPreferences(),
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            listOf(
                ReadingDirectionLibraryUiState(
                    libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
                    title = "Kavita Comics",
                    direction = ComicReadingDirection.LTR,
                ),
            ),
            rows,
        )
    }

    @Test
    fun `rows include mixed library types for the same source`() {
        val rows = buildComicReadingDirectionLibraryRows(
            availableLibraries = listOf(
                library(
                    sourceType = SourceType.KAVITA,
                    remoteId = "k-books",
                    title = "Books",
                    contentType = LibraryContentType.BOOKS,
                ),
                library(
                    sourceType = SourceType.KAVITA,
                    remoteId = "k-manga",
                    title = "Manga",
                    contentType = LibraryContentType.COMICS,
                ),
            ),
            preferences = ComicReadingDirectionPreferences(
                libraryDirectionsById = mapOf(
                    SourceType.KAVITA.remoteLibraryId("k-manga") to ComicReadingDirection.RTL,
                ),
            ),
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            listOf(
                ReadingDirectionLibraryUiState(
                    libraryId = SourceType.KAVITA.remoteLibraryId("k-books"),
                    title = "Books",
                    direction = ComicReadingDirection.LTR,
                ),
                ReadingDirectionLibraryUiState(
                    libraryId = SourceType.KAVITA.remoteLibraryId("k-manga"),
                    title = "Manga",
                    direction = ComicReadingDirection.RTL,
                ),
            ),
            rows,
        )
    }

    private fun library(
        sourceType: SourceType,
        remoteId: String,
        title: String,
        contentType: LibraryContentType,
    ): Library = Library(
        id = sourceType.remoteLibraryId(remoteId),
        sourceType = sourceType,
        title = title,
        remoteId = remoteId,
        contentType = contentType,
    )
}
