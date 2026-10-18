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
    fun `resume position saved with an older page is dropped once synced progress moves on`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        // Progress pulled from another device is on page 30; the session still holds this device's spot.
        val sessionStore = TestReaderSessionStore(
            isHydrated = true,
            progressByItemId = mapOf(item.id to pageProgress(item, page = 30)),
        )
        val adapter = comicsSessionAdapter(sessionStore, scope)
        adapter.saveResumeAnchorPage(item, pageIndex = 9, progressPage = 10)
        adapter.saveResumeScrollOffsetRatio(item, 0.4f)

        assertEquals(30, adapter.loadPage(item))
        assertNull(adapter.loadResumeAnchorPage(item))
        assertNull(adapter.loadResumeScrollOffsetRatio(item))
        assertNull(adapter.observePreferences(item).first()?.resumeAnchorPage)
        scope.cancel()
    }

    @Test
    fun `resume position saved with the current page is kept`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        val sessionStore = TestReaderSessionStore(
            isHydrated = true,
            progressByItemId = mapOf(item.id to pageProgress(item, page = 10)),
        )
        val adapter = comicsSessionAdapter(sessionStore, scope)
        // A two-page spread: the anchor is its left page, the progress page its right one.
        adapter.saveResumeAnchorPage(item, pageIndex = 9, progressPage = 10)

        assertEquals(9, adapter.loadResumeAnchorPage(item))
        assertEquals(9, adapter.observePreferences(item).first()?.resumeAnchorPage)
        scope.cancel()
    }

    @Test
    fun `older sessions without the saved page keep only an anchor on the progress page`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val item = comicItem()
        val legacySession = mapOf(
            item.id to ReaderSessionSnapshot(
                itemId = item.id,
                preferences = buildComicReaderPreferencesPatch(resumeAnchorPage = 12),
            ),
        )
        val unchanged = comicsSessionAdapter(
            TestReaderSessionStore(
                isHydrated = true,
                progressByItemId = mapOf(item.id to pageProgress(item, page = 12)),
                sessionByItemId = legacySession,
            ),
            scope,
        )
        val moved = comicsSessionAdapter(
            TestReaderSessionStore(
                isHydrated = true,
                progressByItemId = mapOf(item.id to pageProgress(item, page = 30)),
                sessionByItemId = legacySession,
            ),
            scope,
        )

        assertEquals(12, unchanged.loadResumeAnchorPage(item))
        assertNull(moved.loadResumeAnchorPage(item))
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

    @Test
    fun `async flush request uses adapter scope`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sessionStore = TestReaderSessionStore(isHydrated = true)
        val adapter = ComicsReaderSessionAdapter(
            sessionStore = sessionStore,
            directionPreferencesStore = TestComicReadingDirectionPreferencesStore(isHydrated = true),
            readerModePreferencesStore = TestComicReaderModePreferencesStore(isHydrated = true),
            scope = scope,
        )

        adapter.requestFlushPendingWrites()

        assertEquals(1, sessionStore.flushPendingWritesCalls)
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
    var flushPendingWritesCalls: Int = 0
        private set

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

    override suspend fun flushPendingWrites() {
        flushPendingWritesCalls += 1
    }

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

private fun comicsSessionAdapter(
    sessionStore: TestReaderSessionStore,
    scope: CoroutineScope,
): ComicsReaderSessionAdapter = ComicsReaderSessionAdapter(
    sessionStore = sessionStore,
    directionPreferencesStore = TestComicReadingDirectionPreferencesStore(isHydrated = true),
    readerModePreferencesStore = TestComicReaderModePreferencesStore(isHydrated = true),
    scope = scope,
)

private fun pageProgress(
    item: ReadableItem,
    page: Int,
): ReaderProgressSnapshot = ReaderProgressSnapshot(
    itemId = item.id,
    locatorType = LocatorType.PAGE,
    page = page,
    syncState = SyncState.CLEAN,
)

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
