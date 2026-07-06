package com.example.unifiedreader.feature.reader.comics

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `explicit unread reset suppresses saved page and resume restore`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        val sessionStore = TestReaderSessionStore(
            isHydrated = true,
            progressByItemId = mapOf(
                item.id to ReaderProgressSnapshot(
                    itemId = item.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.0,
                    isExplicitUnreadReset = true,
                    syncState = SyncState.DIRTY,
                ),
            ),
            sessionByItemId = mapOf(
                item.id to ReaderSessionSnapshot(
                    itemId = item.id,
                    preferences = buildComicReaderPreferencesPatch(
                        resumeAnchorPage = 17,
                        resumeSegment = ComicPageSegment.Full,
                        resumeScrollOffsetRatio = 0.6f,
                    ),
                ),
            ),
        )
        val adapter = ComicsReaderSessionAdapter(
            sessionStore = sessionStore,
            directionPreferencesStore = TestComicReadingDirectionPreferencesStore(isHydrated = true),
            readerModePreferencesStore = TestComicReaderModePreferencesStore(isHydrated = true),
            scope = scope,
        )

        assertNull(adapter.loadPage(item))
        assertNull(adapter.loadResumeAnchorPage(item))
        assertNull(adapter.loadResumeSegment(item))
        assertNull(adapter.loadResumeScrollOffsetRatio(item))
        val observed = adapter.observePreferences(item).first()
        assertNull(observed?.resumeAnchorPage)
        assertNull(observed?.resumeSegment)
        assertNull(observed?.resumeScrollOffsetRatio)
        scope.cancel()
    }

    @Test
    fun `saved session resume is ignored when page progress has been cleared`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        val sessionStore = TestReaderSessionStore(
            isHydrated = true,
            sessionByItemId = mapOf(
                item.id to ReaderSessionSnapshot(
                    itemId = item.id,
                    preferences = buildComicReaderPreferencesPatch(
                        resumeAnchorPage = 9,
                        resumeSegment = ComicPageSegment.Full,
                        resumeScrollOffsetRatio = 0.3f,
                    ),
                ),
            ),
        )
        val adapter = ComicsReaderSessionAdapter(
            sessionStore = sessionStore,
            directionPreferencesStore = TestComicReadingDirectionPreferencesStore(isHydrated = true),
            readerModePreferencesStore = TestComicReaderModePreferencesStore(isHydrated = true),
            scope = scope,
        )

        assertNull(adapter.loadResumeAnchorPage(item))
        assertEquals(
            null,
            adapter.savedPreferencesFlow.value[item.id]?.resumeAnchorPage,
        )
        scope.cancel()
    }

    @Test
    fun `page turn style defaults to slide for saved sessions without the preference`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        val sessionStore = TestReaderSessionStore(
            isHydrated = true,
            progressByItemId = mapOf(
                item.id to ReaderProgressSnapshot(
                    itemId = item.id,
                    locatorType = LocatorType.PAGE,
                    page = 1,
                    syncState = SyncState.CLEAN,
                ),
            ),
            sessionByItemId = mapOf(
                item.id to ReaderSessionSnapshot(
                    itemId = item.id,
                    preferences = buildComicReaderPreferencesPatch(
                        pageFitMode = ComicPageFitMode.FitWidth,
                    ),
                ),
            ),
        )
        val adapter = ComicsReaderSessionAdapter(
            sessionStore = sessionStore,
            directionPreferencesStore = TestComicReadingDirectionPreferencesStore(isHydrated = true),
            readerModePreferencesStore = TestComicReaderModePreferencesStore(isHydrated = true),
            scope = scope,
        )

        assertEquals(ComicPageTurnStyle.Slide, adapter.loadPageTurnStyle(item))
        assertEquals(ComicPageTurnStyle.Slide, adapter.observePreferences(item).first()?.pageTurnStyle)
        scope.cancel()
    }

    @Test
    fun `save page turn style writes existing session preference path`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        val sessionStore = TestReaderSessionStore(isHydrated = true)
        val adapter = ComicsReaderSessionAdapter(
            sessionStore = sessionStore,
            directionPreferencesStore = TestComicReadingDirectionPreferencesStore(isHydrated = true),
            readerModePreferencesStore = TestComicReaderModePreferencesStore(isHydrated = true),
            scope = scope,
        )

        adapter.savePageTurnStyle(item, ComicPageTurnStyle.Curl)

        assertEquals(
            ComicPageTurnStyle.Curl.name,
            sessionStore.getSession(item)?.preferences?.get(ComicsPageTurnStyleKey),
        )
        assertEquals(ComicPageTurnStyle.Curl, adapter.loadPageTurnStyle(item))
        scope.cancel()
    }
}

private class TestReaderSessionStore(
    isHydrated: Boolean,
    progressByItemId: Map<String, ReaderProgressSnapshot> = emptyMap(),
    sessionByItemId: Map<String, ReaderSessionSnapshot> = emptyMap(),
) : ReaderSessionStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(isHydrated)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> =
        MutableStateFlow(progressByItemId)
    private val sessions = MutableStateFlow(sessionByItemId)
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessions

    override fun getProgress(
        item: ReadableItem,
    ): ReaderProgressSnapshot? = progressByItemIdFlow.value[item.id]

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
    ): ReaderSessionSnapshot? = sessions.value[item.id]

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessions.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
        sessions.value = sessions.value + (itemId to current.copy(
            pageCount = pageCount ?: current.pageCount,
            preferences = current.preferences + preferences,
        ))
    }

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

private fun comicItem(): ReadableItem = ReadableItem(
    id = "kavita:item:comic-1",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:1",
    title = "One Piece",
    format = FormatType.CBZ,
)

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
