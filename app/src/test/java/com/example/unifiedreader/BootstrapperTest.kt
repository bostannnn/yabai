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
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.downloads.DownloadEntriesSnapshot
import com.example.unifiedreader.feature.downloads.DownloadEntry
import com.example.unifiedreader.feature.downloads.DownloadEntryStatus
import com.example.unifiedreader.feature.downloads.DownloadQueueStore
import com.example.unifiedreader.feature.downloads.DownloadWorkScheduler
import com.example.unifiedreader.feature.downloads.QueuedDownloadItem
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import com.example.unifiedreader.feature.reader.comics.ComicPageFitMode
import com.example.unifiedreader.feature.reader.comics.ComicReadingDirection
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
    fun `local document bootstrapper migrates legacy records into dao and clears prefs`() = runBlocking {
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
    fun `reader state bootstrapper migrates page and preferences into room daos and clears prefs`() = runBlocking {
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

    @Test
    fun `app startup initializer runs room bootstraps before repositories are created`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("unified_reader_imported_cbz", Context.MODE_PRIVATE)
            .edit()
            .putStringSet(
                "records",
                linkedSetOf(
                    legacyImportedArchiveRecord(
                        id = "comic-3",
                        uri = "content://imports/nausicaa.cbz",
                        displayName = "Nausicaa.cbz",
                        format = FormatType.CBZ,
                        pageCount = 2,
                        pageEntryNames = listOf("001.jpg", "002.jpg"),
                    ),
                ),
            )
            .apply()
        context.getSharedPreferences("unified_reader_local_progress", Context.MODE_PRIVATE)
            .edit()
            .putInt("reader.cbz.comic-3.page", 4)
            .apply()
        context.getSharedPreferences("unified_reader_reader_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("reader.cbz.comic-3.direction", ComicReadingDirection.RTL.name)
            .putString("reader.cbz.comic-3.fit_mode", ComicPageFitMode.FitWidth.name)
            .apply()
        context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val archiveDao = FakeImportedComicArchiveDao()
        val progressDao = FakeReadingProgressDao()
        val sessionDao = FakeReaderSessionStateDao()

        AppStartupInitializer(
            context = context,
            importedComicArchiveDao = archiveDao,
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
            remoteDownloadQueueController = RemoteDownloadQueueController(
                store = BootstrapDownloadQueueStore(),
                scheduler = BootstrapRecordingDownloadWorkScheduler(),
                remoteDownloadDataSources = emptyList(),
            ),
        ).initialize()

        assertEquals(1, archiveDao.entities.size)
        assertEquals("comic-3", archiveDao.entities.first().itemId)
        assertEquals(1, progressDao.entities.size)
        assertEquals(4, progressDao.entities.first().page)
        assertEquals(1, sessionDao.entities.size)
        assertTrue(
            context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
                .getBoolean("imported_archives_room_bootstrap_v1", false),
        )
        assertTrue(
            context.getSharedPreferences("unified_reader_bootstrap_state", Context.MODE_PRIVATE)
                .getBoolean("reader_state_room_bootstrap_v1", false),
        )
    }

    @Test
    fun `app startup initializer finalizes canceling downloads and reschedules active entries`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val archiveDao = FakeImportedComicArchiveDao()
        val progressDao = FakeReadingProgressDao()
        val sessionDao = FakeReaderSessionStateDao()
        val downloadStore = BootstrapDownloadQueueStore(
            initialEntries = listOf(
                DownloadEntry(
                    id = "canceling-entry",
                    selectedItemId = "book-1",
                    selectedItemTitle = "book-1",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedDownloadItem("book-1")),
                    trackedItemIds = setOf("book-1"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Canceling,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "running-entry",
                    selectedItemId = "book-2",
                    selectedItemTitle = "book-2",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedDownloadItem("book-2")),
                    trackedItemIds = setOf("book-2"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Running,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
                DownloadEntry(
                    id = "queued-entry",
                    selectedItemId = "book-3",
                    selectedItemTitle = "book-3",
                    sourceType = SourceType.KAVITA,
                    actionItems = listOf(testQueuedDownloadItem("book-3")),
                    trackedItemIds = setOf("book-3"),
                    completedItemIds = emptySet(),
                    status = DownloadEntryStatus.Queued,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
        )
        val scheduler = BootstrapRecordingDownloadWorkScheduler()
        val remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.KAVITA)
        }

        AppStartupInitializer(
            context = context,
            importedComicArchiveDao = archiveDao,
            readingProgressDao = progressDao,
            readerSessionStateDao = sessionDao,
            remoteSourceConnectivityPolicy = remoteSourceConnectivityPolicy,
            remoteDownloadQueueController = RemoteDownloadQueueController(
                store = downloadStore,
                scheduler = scheduler,
                remoteDownloadDataSources = listOf(BootstrapRemoteItemDownloadDataSource()),
            ),
        ).initialize()

        assertEquals(
            DownloadEntryStatus.Canceled,
            downloadStore.entries.first { it.id == "canceling-entry" }.status,
        )
        assertEquals(
            listOf("running-entry", "queued-entry"),
            scheduler.scheduledEntryIds,
        )
    }
}

private class FakeImportedComicArchiveDao : ImportedComicArchiveDao {
    val entities = mutableListOf<ImportedComicArchiveEntity>()

    override fun getAll(): MutableList<ImportedComicArchiveEntity> = entities.toMutableList()

    override fun hasAny(): Boolean = entities.isNotEmpty()

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

    override fun hasAny(): Boolean = entities.isNotEmpty()

    override fun getByItemId(itemId: String): ReadingProgressEntity? =
        entities.firstOrNull { it.itemId == itemId }

    override fun getByItemIds(itemIds: MutableList<String>): MutableList<ReadingProgressEntity> =
        entities.filter { entity -> entity.itemId in itemIds }.toMutableList()

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

    override fun hasAny(): Boolean = entities.isNotEmpty()

    override fun getByItemId(itemId: String): ReaderSessionStateEntity? =
        entities.firstOrNull { it.itemId == itemId }

    override fun getByItemIds(itemIds: MutableList<String>): MutableList<ReaderSessionStateEntity> =
        entities.filter { entity -> entity.itemId in itemIds }.toMutableList()

    override fun deleteByItemId(itemId: String) {
        entities.removeAll { it.itemId == itemId }
    }

    override fun insertOrReplace(entity: ReaderSessionStateEntity) {
        entities.removeAll { it.itemId == entity.itemId }
        entities += entity
    }
}

private class BootstrapDownloadQueueStore(
    initialEntries: List<DownloadEntry> = emptyList(),
) : DownloadQueueStore {
    private val state = MutableStateFlow(initialEntries)

    val entries: List<DownloadEntry>
        get() = state.value

    override fun observeEntries(): Flow<DownloadEntriesSnapshot> = MutableStateFlow(
        DownloadEntriesSnapshot(state.value),
    )

    override suspend fun getEntries(): List<DownloadEntry> = state.value

    override suspend fun getEntry(entryId: String): DownloadEntry? = state.value.firstOrNull { it.id == entryId }

    override suspend fun putEntry(entry: DownloadEntry) {
        state.value = state.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun putEntryIfNoActiveTrackedItems(entry: DownloadEntry): Boolean {
        val hasOverlap = state.value.any { existingEntry ->
            (existingEntry.status == DownloadEntryStatus.Queued ||
                existingEntry.status == DownloadEntryStatus.PausedConnectivity ||
                existingEntry.status == DownloadEntryStatus.Running ||
                existingEntry.status == DownloadEntryStatus.Canceling) &&
                existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
        }
        if (hasOverlap) return false
        putEntry(entry)
        return true
    }

    override suspend fun retryEntryIfNoOtherActiveTrackedItems(entryId: String): Boolean {
        val entry = state.value.firstOrNull { it.id == entryId } ?: return false
        val hasOverlap = state.value.any { existingEntry ->
            existingEntry.id != entryId &&
                (existingEntry.status == DownloadEntryStatus.Queued ||
                    existingEntry.status == DownloadEntryStatus.PausedConnectivity ||
                    existingEntry.status == DownloadEntryStatus.Running ||
                    existingEntry.status == DownloadEntryStatus.Canceling) &&
                existingEntry.trackedItemIds.any(entry.trackedItemIds::contains)
        }
        if (hasOverlap) return false
        putEntry(
            entry.copy(
                status = DownloadEntryStatus.Queued,
                errorMessage = null,
                currentItemId = null,
                currentItemTitle = null,
                completedAtEpochMillis = null,
            ),
        )
        return true
    }

    override suspend fun updateStatus(
        entryId: String,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ) {
        state.value = state.value.map { entry ->
            if (entry.id != entryId) return@map entry
            entry.copy(
                status = status,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
    }

    override suspend fun updateStatusIfCurrentStatusIn(
        entryId: String,
        expectedStatuses: Set<DownloadEntryStatus>,
        status: DownloadEntryStatus,
        currentItemId: String?,
        currentItemTitle: String?,
        errorMessage: String?,
        completedAtEpochMillis: Long?,
    ): Boolean {
        var updated = false
        state.value = state.value.map { entry ->
            if (entry.id != entryId || entry.status !in expectedStatuses) return@map entry
            updated = true
            entry.copy(
                status = status,
                currentItemId = currentItemId,
                currentItemTitle = currentItemTitle,
                errorMessage = errorMessage,
                completedAtEpochMillis = completedAtEpochMillis,
            )
        }
        return updated
    }

    override suspend fun updateProgress(
        entryId: String,
        completedItemIds: Set<String>,
        currentItemId: String?,
        currentItemTitle: String?,
    ) = Unit

    override suspend fun deleteEntry(entryId: String): Int = 0
}

private class BootstrapRecordingDownloadWorkScheduler : DownloadWorkScheduler {
    val scheduledEntryIds = mutableListOf<String>()

    override fun scheduleEntry(entryId: String) {
        scheduledEntryIds += entryId
    }

    override fun cancelEntry(entryId: String) = Unit
}

private class BootstrapRemoteItemDownloadDataSource : RemoteItemDownloadDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    override fun storedDocumentUri(item: ReadableItem): Uri? = null

    override suspend fun downloadToLocalStorage(item: ReadableItem): Uri? = null

    override suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String> = emptySet()

    override suspend fun deleteStoredDocument(item: ReadableItem): Boolean = false
}

private fun testQueuedDownloadItem(
    id: String,
): QueuedDownloadItem = QueuedDownloadItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = "library",
    remoteId = id,
    remoteFileId = null,
    remoteFileType = null,
    title = id,
    formatName = FormatType.CBZ.name,
    pageCount = null,
)

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
