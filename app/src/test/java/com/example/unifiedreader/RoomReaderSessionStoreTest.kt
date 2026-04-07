package com.example.unifiedreader

import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageFitMode
import com.example.unifiedreader.feature.reader.comics.ComicReadingDirection
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

        advanceUntilIdle()
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

        advanceUntilIdle()
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
}

private class FakeReadingProgressDaoForStore : ReadingProgressDao {
    val entities = mutableListOf<ReadingProgressEntity>()

    override fun getAll(): MutableList<ReadingProgressEntity> = entities.toMutableList()

    override fun getByItemId(itemId: String): ReadingProgressEntity? = null

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ReadingProgressEntity) {
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }
}

private class RecordingReaderSessionStateDao : ReaderSessionStateDao {
    val entities = mutableListOf<ReaderSessionStateEntity>()

    override fun getAll(): MutableList<ReaderSessionStateEntity> = entities.toMutableList()

    override fun getByItemId(itemId: String): ReaderSessionStateEntity? =
        entities.firstOrNull { it.itemId == itemId }

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ReaderSessionStateEntity) {
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }
}

private fun testItem(): com.example.unifiedreader.core.model.ReadableItem =
    com.example.unifiedreader.core.model.ReadableItem(
        id = "comic-1",
        serverId = "local-kavita",
        sourceType = SourceType.KAVITA,
        libraryId = "k-comics",
        title = "Akira",
        format = FormatType.CBZ,
        coverUrl = null,
        description = null,
    )
