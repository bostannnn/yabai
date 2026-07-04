package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageFitMode
import com.example.unifiedreader.feature.reader.comics.ComicReadingDirection
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomReaderSessionStoreTest {
    @Test
    fun `back to back session updates persist latest merged preferences`() = runTest {
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = FakeReadingProgressDaoForStore(),
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        assertTrue(!store.isHydratedFlow.value)
        store.hydrate()
        store.updateSession(
            item = item,
            preferences = mapOf("readingDirection" to ComicReadingDirection.RTL.name),
        )
        store.updateSession(
            item = item,
            preferences = mapOf(
                "pageFitMode" to ComicPageFitMode.FitWidth.name,
                "spreadMode" to ComicSpreadMode.Auto.name,
            ),
        )

        advanceUntilIdle()

        val persisted = sessionDao.entities.single()
        assertEquals(ComicReadingDirection.RTL.name, persisted.readingDirection)
        assertEquals(ComicPageFitMode.FitWidth.name, persisted.pageFitMode)
        assertEquals(ComicSpreadMode.Auto.name, persisted.spreadMode)
        assertEquals(
            setOf("readingDirection", "pageFitMode", "spreadMode"),
            org.json.JSONObject(persisted.preferencesJson).keys().asSequence().toSet(),
        )
    }

    @Test
    fun `clear item removes stored progress and session`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.hydrate()
        store.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PAGE,
                page = 2,
            ),
            pageCount = 12,
        )
        advanceUntilIdle()

        store.clearItem(item.id)
        advanceUntilIdle()

        assertEquals(null, store.getProgress(item))
        assertEquals(null, store.getSession(item))
        assertTrue(progressDao.entities.isEmpty())
        assertTrue(sessionDao.entities.isEmpty())
    }

    @Test
    fun `flush pending writes persists queued progress`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = RecordingReaderSessionStateDao(),
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.hydrate()
        store.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PAGE,
                page = 4,
            ),
            pageCount = 12,
        )
        store.flushPendingWrites()

        assertEquals(4, progressDao.entities.single().page)
    }

    @Test
    fun `clear item drops queued session upsert instead of recreating default row`() = runTest {
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = FakeReadingProgressDaoForStore(),
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.hydrate()
        store.updateSession(
            item = item,
            pageCount = 12,
        )
        store.clearItem(item.id)
        advanceUntilIdle()

        assertTrue(sessionDao.insertCalls.isEmpty())
        assertTrue(sessionDao.entities.isEmpty())
    }

    @Test
    fun `replace session persists cleared local reader position without dropping other preferences`() = runTest {
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = FakeReadingProgressDaoForStore(),
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.hydrate()
        store.updateSession(
            item = item,
            preferences = mapOf(
                "readerMode" to "VerticalScroll",
                "resumeAnchorPage" to "17",
                "resumeSegment" to "Full",
            ),
        )
        advanceUntilIdle()

        store.replaceSession(
            itemId = item.id,
            replacement = com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(
                itemId = item.id,
                preferences = mapOf("readerMode" to "VerticalScroll"),
            ),
        )
        advanceUntilIdle()

        val persisted = sessionDao.entities.single()
        val persistedPreferences = org.json.JSONObject(persisted.preferencesJson)
        assertEquals("VerticalScroll", persistedPreferences.getString("readerMode"))
        assertTrue(!persistedPreferences.has("resumeAnchorPage"))
        assertTrue(!persistedPreferences.has("resumeSegment"))
    }

    @Test
    fun `save progress initializes startup tasks before persisting room data`() = runTest {
        var initialized = false
        val progressDao = FakeReadingProgressDaoForStore().apply {
            beforeInsert = {
                assertTrue(initialized)
            }
        }
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = RecordingReaderSessionStateDao(),
            appStartupTasks = AppStartupTasks { initialized = true },
            writeScope = CoroutineScope(coroutineContext),
        )

        store.saveProgress(
            item = testItem(),
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = com.example.unifiedreader.core.model.LocatorType.PAGE,
                page = 1,
            ),
            pageCount = 8,
        )
        advanceUntilIdle()

        assertTrue(initialized)
        assertEquals(1, progressDao.entities.size)
    }

    @Test
    fun `replace progress persists newer local snapshot when save arrives before writer runs`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        var store: RoomReaderSessionStore? = null
        var initializeCalls = 0
        val item = testItem()
        val readerStore = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = RecordingReaderSessionStateDao(),
            appStartupTasks = AppStartupTasks {
                initializeCalls += 1
                if (initializeCalls == 2) {
                    checkNotNull(store).saveProgress(
                        item = item,
                        locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                            locatorType = LocatorType.PAGE,
                            page = 7,
                        ),
                        syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
                        updatedAtEpochMillis = 456L,
                    )
                    kotlinx.coroutines.yield()
                }
            },
            writeScope = CoroutineScope(coroutineContext),
        )
        store = readerStore

        readerStore.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 3,
            ),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
            updatedAtEpochMillis = 123L,
        )
        advanceUntilIdle()

        val expected = checkNotNull(readerStore.getProgress(item))
        val replaced = readerStore.replaceProgressIfCurrent(
            expected = expected,
            replacement = expected.copy(syncState = com.example.unifiedreader.core.model.SyncState.CLEAN),
        )
        advanceUntilIdle()

        assertTrue(replaced)
        assertEquals(7, progressDao.entities.single().page)
        assertEquals(
            com.example.unifiedreader.core.model.SyncState.DIRTY.name,
            progressDao.entities.single().syncState,
        )
        assertEquals(456L, progressDao.entities.single().updatedAtEpochMillis)
    }

    @Test
    fun `clear item wins when it lands during session merge`() = runTest {
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = FakeReadingProgressDaoForStore(),
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()
        val mergeStarted = CountDownLatch(1)
        val allowMergeToFinish = CountDownLatch(1)
        val preferences = BlockingPreferenceMap(
            backingMap = mapOf("pageFitMode" to ComicPageFitMode.FitWidth.name),
            started = mergeStarted,
            release = allowMergeToFinish,
        )

        val updateJob = launch(Dispatchers.Default) {
            store.updateSession(
                itemId = item.id,
                pageCount = 12,
                preferences = preferences,
            )
        }

        assertTrue(mergeStarted.await(2, TimeUnit.SECONDS))
        val clearJob = launch(Dispatchers.Default) {
            store.clearItem(item.id)
        }
        allowMergeToFinish.countDown()
        updateJob.join()
        clearJob.join()
        advanceUntilIdle()

        assertEquals(null, store.getSession(item))
        assertTrue(sessionDao.entities.isEmpty())
    }

    @Test
    fun `save progress after clear item is ignored`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.hydrate()
        store.clearItem(item.id)
        store.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 4,
            ),
            pageCount = 12,
        )
        advanceUntilIdle()

        assertEquals(null, store.getProgress(item))
        assertEquals(null, store.getSession(item))
        assertTrue(progressDao.entities.isEmpty())
        assertTrue(sessionDao.entities.isEmpty())
    }

    @Test
    fun `update session after clear item is ignored until item is restored`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        val sessionDao = RecordingReaderSessionStateDao()
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.hydrate()
        store.clearItem(item.id)
        store.updateSession(
            itemId = item.id,
            pageCount = 12,
            preferences = mapOf("pageFitMode" to ComicPageFitMode.FitWidth.name),
        )
        advanceUntilIdle()

        assertEquals(null, store.getSession(item))
        assertTrue(sessionDao.entities.isEmpty())

        store.restoreItem(item.id)
        store.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 5,
            ),
            pageCount = 12,
        )
        advanceUntilIdle()

        assertEquals(5, store.getProgress(item)?.page)
        assertEquals(12, store.getSession(item)?.pageCount)
        assertEquals(1, progressDao.entities.size)
        assertEquals(1, sessionDao.entities.size)
    }

    @Test
    fun `restore before queued delete drains keeps newly persisted reader state`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        val sessionDao = RecordingReaderSessionStateDao()
        val firstInitializeStarted = CompletableDeferred<Unit>()
        val allowFirstInitialize = CompletableDeferred<Unit>()
        var firstInitializePending = true
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            appStartupTasks = AppStartupTasks {
                if (firstInitializePending) {
                    firstInitializePending = false
                    firstInitializeStarted.complete(Unit)
                    allowFirstInitialize.await()
                }
            },
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.clearItem(item.id)
        runCurrent()
        assertTrue(firstInitializeStarted.isCompleted)

        store.restoreItem(item.id)
        store.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 6,
            ),
            pageCount = 12,
        )
        runCurrent()

        allowFirstInitialize.complete(Unit)
        advanceUntilIdle()

        assertEquals(6, store.getProgress(item)?.page)
        assertEquals(12, store.getSession(item)?.pageCount)
        assertEquals(1, progressDao.entities.size)
        assertEquals(6, progressDao.entities.single().page)
        assertEquals(1, sessionDao.entities.size)
        assertEquals(12, sessionDao.entities.single().pageCount)
    }

    @Test
    fun `restore before queued delete drains still clears stale persisted reader state when no new writes arrive`() = runTest {
        val progressDao = FakeReadingProgressDaoForStore()
        val sessionDao = RecordingReaderSessionStateDao()
        val firstInitializeStarted = CompletableDeferred<Unit>()
        val allowFirstInitialize = CompletableDeferred<Unit>()
        var initializeCalls = 0
        val store = RoomReaderSessionStore(
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            appStartupTasks = AppStartupTasks {
                initializeCalls += 1
                if (initializeCalls == 2) {
                    firstInitializeStarted.complete(Unit)
                    allowFirstInitialize.await()
                }
            },
            writeScope = CoroutineScope(coroutineContext),
        )
        val item = testItem()

        store.saveProgress(
            item = item,
            locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 4,
            ),
            pageCount = 12,
        )
        advanceUntilIdle()

        store.clearItem(item.id)
        runCurrent()
        assertTrue(firstInitializeStarted.isCompleted)

        store.restoreItem(item.id)
        allowFirstInitialize.complete(Unit)
        advanceUntilIdle()

        assertEquals(null, store.getProgress(item))
        assertEquals(null, store.getSession(item))
        assertTrue(progressDao.entities.isEmpty())
        assertTrue(sessionDao.entities.isEmpty())
    }
}

private class FakeReadingProgressDaoForStore : ReadingProgressDao {
    val entities = mutableListOf<ReadingProgressEntity>()
    var beforeInsert: (() -> Unit)? = null

    override fun getAll(): MutableList<ReadingProgressEntity> = entities.toMutableList()

    override fun hasAny(): Boolean = entities.isNotEmpty()

    override fun getByItemId(itemId: String): ReadingProgressEntity? = null

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ReadingProgressEntity) {
        beforeInsert?.invoke()
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }
}

private class RecordingReaderSessionStateDao : ReaderSessionStateDao {
    val entities = mutableListOf<ReaderSessionStateEntity>()
    val insertCalls = mutableListOf<ReaderSessionStateEntity>()

    override fun getAll(): MutableList<ReaderSessionStateEntity> = entities.toMutableList()

    override fun hasAny(): Boolean = entities.isNotEmpty()

    override fun getByItemId(itemId: String): ReaderSessionStateEntity? =
        entities.firstOrNull { it.itemId == itemId }

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ReaderSessionStateEntity) {
        insertCalls += entity
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }
}

private class BlockingPreferenceMap(
    private val backingMap: Map<String, String>,
    private val started: CountDownLatch,
    private val release: CountDownLatch,
) : Map<String, String> by backingMap {
    override val entries: Set<Map.Entry<String, String>>
        get() {
            started.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            return backingMap.entries
        }
}

private fun testItem(): com.example.unifiedreader.core.model.ReadableItem =
    com.example.unifiedreader.core.model.ReadableItem(
        id = "comic-1",
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:k-comics",
        remoteId = "comic-1",
        title = "Akira",
        format = FormatType.CBZ,
        coverUrl = null,
        description = null,
    )
