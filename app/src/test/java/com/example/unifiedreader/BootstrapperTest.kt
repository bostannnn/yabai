package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.feature.reader.comics.ComicPageFitMode
import com.example.unifiedreader.feature.reader.comics.ComicReadingDirection
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BootstrapperTest {
    @Test
    fun `local document bootstrapper migrates legacy records into dao and clears prefs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("unified_reader_imported_cbz", Context.MODE_PRIVATE)
            .edit()
            .putStringSet(
                "records",
                linkedSetOf(
                    legacyImportedArchiveRecord(
                        id = "comic-1",
                        uri = "content://imports/akira.cbz",
                        displayName = "Akira.cbz",
                        format = FormatType.CBZ,
                        pageCount = 3,
                        pageEntryNames = listOf("001.jpg", "002.jpg", "003.jpg"),
                    ),
                ),
            )
            .apply()
        context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val dao = FakeImportedComicArchiveDao()
        LocalDocumentBootstrapper(
            context = context,
            importedComicArchiveDao = dao,
        ).bootstrapIfNeeded()

        assertEquals(1, dao.entities.size)
        assertEquals("comic-1", dao.entities.first().itemId)
        assertEquals("content://imports/akira.cbz", dao.entities.first().documentUri)
        assertNull(
            context.getSharedPreferences("unified_reader_imported_cbz", Context.MODE_PRIVATE)
                .getStringSet("records", null),
        )
        assertTrue(
            context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
                .getBoolean("imported_archives_room_bootstrap_v1", false),
        )
    }

    @Test
    fun `reader state bootstrapper migrates page and preferences into room daos and clears prefs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("unified_reader_local_progress", Context.MODE_PRIVATE)
            .edit()
            .putInt("reader.cbz.comic-2.page", 6)
            .apply()
        context.getSharedPreferences("unified_reader_reader_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("reader.cbz.comic-2.direction", ComicReadingDirection.RTL.name)
            .putString("reader.cbz.comic-2.fit_mode", ComicPageFitMode.FitWidth.name)
            .apply()
        context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
            .edit()
            .remove("reader_state_room_bootstrap_v1")
            .apply()

        val progressDao = FakeReadingProgressDao()
        val sessionDao = FakeReaderSessionStateDao()
        ReaderStateBootstrapper(
            context = context,
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
        ).bootstrapIfNeeded()

        assertEquals(1, progressDao.entities.size)
        assertEquals(6, progressDao.entities.first().page)
        assertEquals(1, sessionDao.entities.size)
        assertEquals(ComicReadingDirection.RTL.name, sessionDao.entities.first().readingDirection)
        assertEquals(ComicPageFitMode.FitWidth.name, sessionDao.entities.first().pageFitMode)
        assertEquals(ComicSpreadMode.Auto.name, sessionDao.entities.first().spreadMode)
        assertTrue(sessionDao.entities.first().preferencesJson.contains(ComicReadingDirection.RTL.name))
        assertFalse(
            context.getSharedPreferences("unified_reader_local_progress", Context.MODE_PRIVATE)
                .contains("reader.cbz.comic-2.page"),
        )
        assertFalse(
            context.getSharedPreferences("unified_reader_reader_preferences", Context.MODE_PRIVATE)
                .contains("reader.cbz.comic-2.direction"),
        )
        assertTrue(
            context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
                .getBoolean("reader_state_room_bootstrap_v1", false),
        )
    }
}

private class FakeImportedComicArchiveDao : ImportedComicArchiveDao {
    val entities = mutableListOf<ImportedComicArchiveEntity>()

    override fun getAll(): MutableList<ImportedComicArchiveEntity> = entities.toMutableList()

    override fun getByItemId(itemId: String): ImportedComicArchiveEntity? =
        entities.firstOrNull { it.itemId == itemId }

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ImportedComicArchiveEntity) {
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }

    override fun insertOrReplace(entities: MutableList<ImportedComicArchiveEntity>) {
        entities.forEach(::insertOrReplace)
    }
}

private class FakeReadingProgressDao : ReadingProgressDao {
    val entities = mutableListOf<ReadingProgressEntity>()

    override fun getAll(): MutableList<ReadingProgressEntity> = entities.toMutableList()

    override fun getByItemId(itemId: String): ReadingProgressEntity? =
        entities.firstOrNull { it.itemId == itemId }

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ReadingProgressEntity) {
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }
}

private class FakeReaderSessionStateDao : ReaderSessionStateDao {
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

private fun legacyImportedArchiveRecord(
    id: String,
    uri: String,
    displayName: String,
    format: FormatType,
    pageCount: Int,
    pageEntryNames: List<String>,
): String = listOf(
    Uri.encode(id),
    Uri.encode(uri),
    Uri.encode(displayName),
    Uri.encode(format.name),
    Uri.encode(pageCount.toString()),
    Uri.encode(pageEntryNames.joinToString(",") { Uri.encode(it) }),
).joinToString("|")
