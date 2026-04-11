package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.reader.comics.ComicsReadingDirectionKey
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppComicReadingDirectionControllerTest {
    @Test
    fun `library direction change clears redundant child overrides`() = runTest {
        val libraryId = SourceType.KAVITA.remoteLibraryId("comics")
        val item = ReadableItem(
            id = "kavita:item:akira-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "akira-1",
            remoteFileType = "Akira",
            title = "Akira 001",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(series = "Akira"),
        )
        val preferencesStore = FakeComicReadingDirectionPreferencesStore()
        preferencesStore.setLibraryDirection(libraryId, ComicReadingDirection.LTR)
        preferencesStore.setSeriesDirectionOverride("$libraryId::Akira", ComicReadingDirection.RTL)
        val sessionStore = FakeReaderSessionStore().apply {
            updateSession(
                item = item,
                pageCount = null,
                preferences = mapOf(
                    ComicsReadingDirectionKey to ComicReadingDirection.RTL.name,
                ),
            )
        }
        val controller = AppComicReadingDirectionController(
            preferencesStore = preferencesStore,
            readerSessionStore = sessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
        )
        controller.observePreferences(
            scope = this,
            updateState = {},
        )

        controller.onLibraryDirectionSelected(
            scope = this,
            libraryId = libraryId,
            direction = ComicReadingDirection.RTL,
        )
        advanceUntilIdle()

        val preferences = preferencesStore.preferencesFlow.value
        assertEquals(ComicReadingDirection.RTL, preferences.libraryDirectionsById[libraryId])
        assertTrue(preferences.seriesDirectionsByKey.isEmpty())
        assertEquals(
            "",
            sessionStore.getSession(item)?.preferences?.get(ComicsReadingDirectionKey),
        )
    }

    @Test
    fun `library direction change reconciles hidden item override after items hydrate`() = runTest {
        val libraryId = SourceType.KAVITA.remoteLibraryId("comics")
        val item = ReadableItem(
            id = "kavita:item:akira-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "akira-1",
            remoteFileType = "Akira",
            title = "Akira 001",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(series = "Akira"),
        )
        val readableItemRepository = FakeReadableItemRepository()
        val preferencesStore = FakeComicReadingDirectionPreferencesStore()
        preferencesStore.setLibraryDirection(libraryId, ComicReadingDirection.LTR)
        val sessionStore = FakeReaderSessionStore().apply {
            updateSession(
                item = item,
                preferences = mapOf(
                    ComicsReadingDirectionKey to ComicReadingDirection.RTL.name,
                ),
            )
        }
        val controller = AppComicReadingDirectionController(
            preferencesStore = preferencesStore,
            readerSessionStore = sessionStore,
            readableItemRepository = readableItemRepository,
        )
        controller.observePreferences(
            scope = this,
            updateState = {},
        )

        controller.onLibraryDirectionSelected(
            scope = this,
            libraryId = libraryId,
            direction = ComicReadingDirection.RTL,
        )
        advanceUntilIdle()

        assertEquals(
            ComicReadingDirection.RTL.name,
            sessionStore.getSession(item)?.preferences?.get(ComicsReadingDirectionKey),
        )

        readableItemRepository.emitItems(listOf(item))
        advanceUntilIdle()

        assertEquals(
            "",
            sessionStore.getSession(item)?.preferences?.get(ComicsReadingDirectionKey),
        )
    }
}
