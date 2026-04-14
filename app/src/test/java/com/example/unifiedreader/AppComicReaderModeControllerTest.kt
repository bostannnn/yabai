package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.feature.reader.comics.ComicsReaderModeKey
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppComicReaderModeControllerTest {
    @Test
    fun `library reader mode change clears redundant child overrides`() = runTest {
        val libraryId = SourceType.KAVITA.remoteLibraryId("webtoons")
        val item = ReadableItem(
            id = "kavita:item:tog-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "tog-1",
            remoteFileType = "Tower of God",
            title = "Tower of God 001",
            format = FormatType.CBZ,
            comicMetadata = ComicMetadata(series = "Tower of God"),
        )
        val preferencesStore = FakeComicReaderModePreferencesStore()
        preferencesStore.setLibraryMode(libraryId, ComicReaderMode.Paged)
        preferencesStore.setSeriesModeOverride("$libraryId::Tower of God", ComicReaderMode.VerticalScroll)
        val sessionStore = FakeReaderSessionStore().apply {
            updateSession(
                item = item,
                pageCount = null,
                preferences = mapOf(
                    ComicsReaderModeKey to ComicReaderMode.VerticalScroll.name,
                ),
            )
        }
        val controller = AppComicReaderModeController(
            preferencesStore = preferencesStore,
            readerSessionStore = sessionStore,
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
        )
        val observationJob = controller.observePreferences(
            scope = this,
            updateState = {},
        )
        try {
            controller.onLibraryModeSelected(
                scope = this,
                libraryId = libraryId,
                mode = ComicReaderMode.VerticalScroll,
            )
            advanceUntilIdle()

            val preferences = preferencesStore.preferencesFlow.value
            assertEquals(ComicReaderMode.VerticalScroll, preferences.libraryModesById[libraryId])
            assertTrue(preferences.seriesModesByKey.isEmpty())
            assertEquals(
                "",
                sessionStore.getSession(item)?.preferences?.get(ComicsReaderModeKey),
            )
        } finally {
            observationJob.cancel()
        }
    }
}
