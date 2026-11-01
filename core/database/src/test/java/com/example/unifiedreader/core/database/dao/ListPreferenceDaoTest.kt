package com.example.unifiedreader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.ListPreferenceEntity
import com.example.unifiedreader.core.database.entity.ListSettingEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListPreferenceDaoTest {
    private lateinit var database: UnifiedReaderDatabase
    private lateinit var dao: ListPreferenceDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.listPreferenceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `pins keep the order they were made in, a hide unpins, and opening keeps both`() {
        dao.setPinned(listOf("a"), pinned = true, atEpochMillis = 100)
        dao.setPinned(listOf("c", "b", "a"), pinned = true, atEpochMillis = 200)
        dao.markOpened("b", atEpochMillis = 300)
        dao.setHidden(listOf("c"), hidden = true)

        val rows = dao.getPreferences(listOf("a", "b", "c")).associateBy(ListPreferenceEntity::listId)
        assertEquals(100L, rows.getValue("a").pinnedAtEpochMillis)
        assertEquals(201L, rows.getValue("b").pinnedAtEpochMillis)
        assertEquals(300L, rows.getValue("b").openedAtEpochMillis)
        assertEquals(null, rows.getValue("c").pinnedAtEpochMillis)
        assertEquals(true, rows.getValue("c").hidden)

        dao.setPinned(listOf("c"), pinned = true, atEpochMillis = 400)
        assertEquals(false, dao.getPreferences(listOf("c")).single().hidden)
    }

    @Test
    fun `a setting is replaced by its key`() = runBlocking {
        dao.upsertSetting(ListSettingEntity("show:finished", "Fold"))
        dao.upsertSetting(ListSettingEntity("show:finished", "Hide"))

        assertEquals(listOf(ListSettingEntity("show:finished", "Hide")), dao.observeSettings().first())
    }
}
