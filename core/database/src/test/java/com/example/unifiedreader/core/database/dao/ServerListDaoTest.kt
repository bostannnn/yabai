package com.example.unifiedreader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.ServerListEntity
import com.example.unifiedreader.core.database.entity.ServerListItemEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServerListDaoTest {
    private lateinit var database: UnifiedReaderDatabase
    private lateinit var dao: ServerListDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.serverListDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a refresh replaces only its own source's lists and books`() {
        dao.replaceSource("GRIMMORY", listOf(shelf("g:1"), shelf("g:2")), listOf(item("g:1", "b1"), item("g:2", "b2")))
        dao.upsertList(shelf("k:1", sourceType = "KAVITA"))
        dao.upsertItems(listOf(item("k:1", "k-book")))

        dao.replaceSource("GRIMMORY", listOf(shelf("g:2", name = "Renamed")), listOf(item("g:2", "b3")))

        assertNull(dao.getList("g:1"))
        assertEquals(emptyList<ServerListItemEntity>(), dao.getItems("g:1"))
        assertEquals("Renamed", dao.getList("g:2")?.name)
        assertEquals(listOf("b3"), dao.getItems("g:2").map { it.itemId })
        assertEquals(listOf("k-book"), dao.getItems("k:1").map { it.itemId })
    }

    @Test
    fun `added books go after the others and one already there keeps its place`() {
        dao.upsertList(shelf("g:1"))
        dao.addItems("g:1", listOf("b1", "b2"))
        dao.addItems("g:1", listOf("b2", "b3", "b3"))

        assertEquals(listOf("b1", "b2", "b3"), dao.getItems("g:1").map { it.itemId })
        assertEquals(listOf(0, 1, 2), dao.getItems("g:1").map { it.position })
    }

    @Test
    fun `deleting a list takes its books with it`() {
        dao.upsertList(shelf("g:1"))
        dao.addItems("g:1", listOf("b1"))

        dao.deleteList("g:1")

        assertNull(dao.getList("g:1"))
        assertEquals(emptyList<ServerListItemEntity>(), dao.getItems("g:1"))
    }

    private fun shelf(id: String, name: String = "Shelf", sourceType: String = "GRIMMORY") = ServerListEntity(
        id = id,
        sourceType = sourceType,
        kind = "GrimmoryShelf",
        remoteId = id.substringAfter(':'),
        name = name,
        icon = null,
        iconType = null,
        isPublic = false,
        isOwned = true,
        remoteSortField = null,
        remoteItemCount = 0,
        orderMode = "Title",
        hideFinished = false,
        syncedAtEpochMillis = 1,
    )

    private fun item(listId: String, itemId: String) = ServerListItemEntity(listId = listId, itemId = itemId, position = 0)
}
