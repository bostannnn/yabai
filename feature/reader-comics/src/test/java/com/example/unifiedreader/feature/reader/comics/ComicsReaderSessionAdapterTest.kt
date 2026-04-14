package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.preferences.ComicReaderMode
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirection
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferences
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicsReaderSessionAdapterTest {
    @Test
    fun `preferences hydration waits for session and inherited preference stores`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sessionStore = TestReaderSessionStore(isHydrated = true)
        val directionStore = TestComicReadingDirectionPreferencesStore(isHydrated = false)
        val modeStore = TestComicReaderModePreferencesStore(isHydrated = true)
        val adapter = ComicsReaderSessionAdapter(
            sessionStore = sessionStore,
            directionPreferencesStore = directionStore,
            readerModePreferencesStore = modeStore,
            scope = scope,
        )

        assertFalse(adapter.isHydratedFlow.value)

        directionStore.setHydrated(true)

        assertTrue(adapter.isHydratedFlow.value)
        scope.cancel()
    }
}

private class TestReaderSessionStore(
    isHydrated: Boolean,
) : ReaderSessionStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(isHydrated)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = MutableStateFlow(emptyMap())
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = MutableStateFlow(emptyMap())

    override fun getProgress(
        item: ReadableItem,
    ): ReaderProgressSnapshot? = null

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) = Unit

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean = false

    override fun getSession(
        item: ReadableItem,
    ): ReaderSessionSnapshot? = null

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) = Unit

    override fun clearProgress(
        itemId: String,
    ) = Unit

    override fun clearItem(
        itemId: String,
    ) = Unit

    override fun restoreItem(
        itemId: String,
    ) = Unit
}

private class TestComicReadingDirectionPreferencesStore(
    isHydrated: Boolean,
) : ComicReadingDirectionPreferencesStore {
    private val hydrationState = MutableStateFlow(isHydrated)
    override val isHydratedFlow: StateFlow<Boolean> = hydrationState
    override val preferencesFlow: StateFlow<ComicReadingDirectionPreferences> =
        MutableStateFlow(ComicReadingDirectionPreferences())

    fun setHydrated(
        isHydrated: Boolean,
    ) {
        hydrationState.value = isHydrated
    }

    override suspend fun setLibraryDirection(
        libraryId: String,
        direction: ComicReadingDirection,
    ) = Unit

    override suspend fun setSeriesDirectionOverride(
        seriesKey: String,
        direction: ComicReadingDirection?,
    ) = Unit
}

private class TestComicReaderModePreferencesStore(
    isHydrated: Boolean,
) : ComicReaderModePreferencesStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(isHydrated)
    override val preferencesFlow: StateFlow<ComicReaderModePreferences> =
        MutableStateFlow(ComicReaderModePreferences())

    override suspend fun setLibraryMode(
        libraryId: String,
        mode: ComicReaderMode,
    ) = Unit

    override suspend fun setSeriesModeOverride(
        seriesKey: String,
        mode: ComicReaderMode?,
    ) = Unit
}
