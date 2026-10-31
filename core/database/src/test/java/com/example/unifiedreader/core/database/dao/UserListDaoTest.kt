package com.example.unifiedreader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.UserListEntity
import com.example.unifiedreader.core.database.entity.UserListEntryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserListDaoTest {
    private lateinit var database: UnifiedReaderDatabase
    private lateinit var dao: UserListDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.userListDao()
        dao.upsertList(list("list:a"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `added books go to the end and a book already there keeps its place`() {
        dao.addEntries("list:a", listOf(entry("b1"), entry("b2")), atEpochMillis = 5)
        dao.addEntries("list:a", listOf(entry("b2"), entry("b3")), atEpochMillis = 6)

        assertEquals(listOf("b1", "b2", "b3"), dao.getEntries("list:a").map { it.itemId })
        assertEquals(listOf(0, 1, 2), dao.getEntries("list:a").map { it.position })
        assertEquals(6L, dao.getList("list:a")?.updatedAtEpochMillis)
    }

    @Test
    fun `reorder rewrites positions and keeps books it was not given after them`() {
        dao.addEntries("list:a", listOf(entry("b1"), entry("b2"), entry("b3")), atEpochMillis = 5)

        dao.reorder("list:a", listOf("b3", "b1"), atEpochMillis = 7)

        assertEquals(listOf("b3", "b1", "b2"), dao.getEntries("list:a").map { it.itemId })
        assertEquals(listOf(0, 1, 2), dao.getEntries("list:a").map { it.position })
    }

    @Test
    fun `deleting a list deletes its books and leaves other lists alone`() {
        dao.upsertList(list("list:b"))
        dao.addEntries("list:a", listOf(entry("b1")), atEpochMillis = 5)
        dao.addEntries("list:b", listOf(entry("b1")), atEpochMillis = 5)

        dao.deleteList("list:a")

        assertNull(dao.getList("list:a"))
        assertEquals(emptyList<UserListEntryEntity>(), dao.getEntries("list:a"))
        assertEquals(listOf("b1"), dao.getEntries("list:b").map { it.itemId })
    }

    @Test
    fun `a built-in list inserted when missing does not overwrite the saved one`() {
        dao.upsertList(list("list:want-to-read").copy(name = "Want to read", hideFinished = true))

        dao.insertListIfMissing(list("list:want-to-read").copy(name = "Want to read", hideFinished = false))

        assertEquals(true, dao.getList("list:want-to-read")?.hideFinished)
    }

    private fun list(id: String) = UserListEntity(
        id = id,
        name = "List $id",
        description = null,
        kind = "USER",
        orderMode = "CUSTOM",
        hideFinished = false,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 1,
        lastOpenedAtEpochMillis = 0,
    )

    private fun entry(itemId: String) = UserListEntryEntity(
        listId = "",
        itemId = itemId,
        position = 0,
        addedAtEpochMillis = 2,
        sourceType = "KAVITA",
        libraryId = "kavita:library:1",
        itemTitle = "Book $itemId",
        itemPrimaryText = null,
        itemSecondaryText = null,
        coverUrl = null,
        itemKind = "COMIC",
        format = "CBZ",
    )
}
