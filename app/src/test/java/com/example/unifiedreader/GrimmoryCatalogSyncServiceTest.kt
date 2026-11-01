package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.grimmory.GrimmoryApi
import com.example.unifiedreader.data.grimmory.GrimmoryBookCatalogEntry
import com.example.unifiedreader.data.grimmory.GrimmoryNotebookEntryDto
import com.example.unifiedreader.data.grimmory.GrimmoryProgressUpdate
import com.example.unifiedreader.data.grimmory.GrimmoryReadingSessionDto
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.data.grimmory.GrimmoryUnauthorizedException
import com.example.unifiedreader.journal.ItemJournalEntry
import com.example.unifiedreader.journal.JournalItemRef
import com.example.unifiedreader.journal.JournalProgressInput
import com.example.unifiedreader.journal.JournalReadingEvent
import com.example.unifiedreader.journal.JournalReadingEventType
import com.example.unifiedreader.journal.JournalReadingSession
import com.example.unifiedreader.journal.JournalRepository
import com.example.unifiedreader.journal.JournalSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryCatalogSyncServiceTest {
    @Test
    fun `connect validates Grimmory access without writing the catalog`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books"),
                    progress = null,
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        val result = service.connect(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertEquals("user" to "password", api.lastLogin)
        assertEquals(1, result.libraryCount)
        assertEquals("access-token", result.session?.accessToken)
        assertEquals("refresh-token", result.session?.refreshToken)
        assertEquals(listOf("access-token"), api.libraryAccessTokens)
        assertTrue(writer.libraries.isEmpty())
        assertTrue(writer.items.isEmpty())
        assertEquals(null, writer.replacedSourceType)
    }

    @Test
    fun `sync fetches grimmory catalog and writes remote room entities`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val library = grimmoryLibrary("g-books")
        val item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val api = FakeGrimmoryApi(
            libraries = listOf(library),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            nowEpochMillis = { 1775046600000L },
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertEquals("user" to "password", api.lastLogin)
        assertEquals(1, result.libraryCount)
        assertEquals(1, result.itemCount)
        assertEquals(mapOf(FormatType.EPUB to 1), result.supportedFormatCounts)
        assertEquals("access-token", result.session?.accessToken)
        assertEquals("refresh-token", result.session?.refreshToken)
        assertEquals(SourceType.GRIMMORY, writer.replacedSourceType)
        assertEquals(listOf(SourceType.GRIMMORY.remoteLibraryId("g-books")), writer.libraries.map(LibraryEntity::id))
        assertEquals(listOf(SourceType.GRIMMORY.remoteItemId("book-1")), writer.items.map(ReadableItemEntity::id))
        assertEquals(1775046600000L, writer.items.single().updatedAtEpochMillis)
    }

    @Test
    fun `sync enriches grimmory journal books with detail genres before writing catalog`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val library = grimmoryLibrary("g-books")
        val listItem = grimmoryItem(
            remoteId = "book-1",
            libraryRemoteId = "g-books",
            title = "Book without list genres",
        )
        val detailItem = listItem.copy(
            bookMetadata = BookMetadata(
                authors = listOf("Ada Author"),
                genres = listOf("Classics", "Fiction"),
            ),
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(library),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = listItem,
                    progress = null,
                    personalRating = 8,
                    lastReadTime = "2026-04-21T10:00:00Z",
                ),
            ),
            detailedBookEntries = mapOf(
                "book-1" to GrimmoryBookCatalogEntry(
                    item = detailItem,
                    progress = null,
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertEquals(listOf("access-token:book-1"), api.detailBookRequests)
        assertEquals("Classics\u001FFiction", writer.items.single().bookGenresJson)
    }

    @Test
    fun `sync with nothing new on the server writes nothing and keeps the covers`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val writer = RecordingRemoteCatalogWriter(
            stored = StoredRemoteCatalog(
                libraries = listOf(grimmoryLibrary("g-books").toEntity()),
                items = listOf(item.toEntity(updatedAtEpochMillis = 5L)),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ ->
                FakeGrimmoryApi(
                    libraries = listOf(grimmoryLibrary("g-books")),
                    allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
                )
            },
            remoteCatalogWriter = writer,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertFalse(result.hasCatalogChanges)
        assertEquals(0, writer.replaceCalls)
        assertTrue(writer.deltas.isEmpty())
        assertEquals(1, result.itemCount)
    }

    @Test
    fun `sync keeps the saved catalog and reports the upload when the server stops answering after the save`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", format = FormatType.PDF, pageCount = 120)
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PAGE, page = 9, percent = 0.4),
            syncState = SyncState.DIRTY,
            pageCount = item.pageCount,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        ).apply { progressFailure = java.net.ConnectException("No route to host") }
        val writer = RecordingRemoteCatalogWriter()
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertEquals(listOf(item.id), writer.items.map(ReadableItemEntity::id))
        assertTrue(result.progressPushFailures.single() is java.net.ConnectException)
        assertEquals("access-token", result.session?.accessToken)
        // Still waiting to upload, not failed.
        assertEquals(SyncState.DIRTY, readerSessionStore.getProgress(item)?.syncState)
        assertEquals(1, api.bookAccessTokens.size)
    }

    @Test
    fun `an upload the main address drops is retried on the backup without fetching the catalog again`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", format = FormatType.PDF, pageCount = 120)
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PAGE, page = 9, percent = 0.4),
            syncState = SyncState.DIRTY,
            pageCount = item.pageCount,
        )
        val entries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null))
        val home = FakeGrimmoryApi(libraries = listOf(grimmoryLibrary("g-books")), allBookEntries = entries)
            .apply { progressFailure = java.net.ConnectException("No route to host") }
        val backup = FakeGrimmoryApi(libraries = listOf(grimmoryLibrary("g-books")), allBookEntries = entries)
        val writer = RecordingRemoteCatalogWriter()
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { baseUrl ->
                if (baseUrl == "https://backup.example/") backup else home
            },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                backupBaseUrl = "https://backup.example/",
                username = "user",
                password = "password",
            ),
        )

        assertTrue(result.progressPushFailures.isEmpty())
        assertEquals("https://backup.example/", result.activeBaseUrl)
        assertEquals("42", backup.progressUpdates.single().bookId)
        assertTrue(backup.bookAccessTokens.isEmpty())
        assertEquals(1, writer.replaceCalls)
        assertEquals(SyncState.CLEAN, readerSessionStore.getProgress(item)?.syncState)
    }

    @Test
    fun `refresh loads reading history only for a book read on the server since the last sync`() = runBlocking {
        val readBook = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val otherBook = grimmoryItem(remoteId = "book-2", libraryRemoteId = "g-books")
        var entries = listOf(
            GrimmoryBookCatalogEntry(item = readBook, progress = null, lastReadTime = "2026-10-30T20:00:00Z"),
            GrimmoryBookCatalogEntry(item = otherBook, progress = null, lastReadTime = "2026-10-29T20:00:00Z"),
        )
        val journalRepository = GrimmoryRecordingJournalRepository()
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ ->
                FakeGrimmoryApi(libraries = listOf(grimmoryLibrary("g-books")), allBookEntries = entries)
            },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            journalRepository = journalRepository,
        )
        val request = GrimmoryCatalogSyncRequest(
            displayName = "Grimmory",
            baseUrl = "https://grimmory.example/",
            username = "user",
            password = "password",
        )
        service.sync(request)
        // Read on Grimmory's web reader after that sync.
        entries = listOf(entries[0].copy(lastReadTime = "2026-11-01T09:00:00Z"), entries[1])
        val api = FakeGrimmoryApi(libraries = listOf(grimmoryLibrary("g-books")), allBookEntries = entries)
        val refreshService = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            journalRepository = journalRepository,
        )

        refreshService.refresh(request)

        assertEquals(listOf("book-1"), api.sessionRequests)
        assertEquals(2, journalRepository.replaceCount)
    }

    @Test
    fun `refresh after a sync with nothing new writes nothing and loads no journal history`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter()
        val listItem = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(item = listItem, progress = null, personalRating = 8),
                GrimmoryBookCatalogEntry(item = grimmoryItem(remoteId = "book-2", libraryRemoteId = "g-books"), progress = null),
            ),
            detailedBookEntries = mapOf(
                "book-1" to GrimmoryBookCatalogEntry(
                    item = listItem.copy(bookMetadata = BookMetadata(genres = listOf("Classics"))),
                    progress = null,
                ),
            ),
        )
        val journalRepository = GrimmoryRecordingJournalRepository()
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            journalRepository = journalRepository,
            nowEpochMillis = { 1_000L },
        )
        val request = GrimmoryCatalogSyncRequest(
            displayName = "Grimmory",
            baseUrl = "https://grimmory.example/",
            username = "user",
            password = "password",
        )
        service.sync(request)
        writer.stored = StoredRemoteCatalog(libraries = writer.libraries, items = writer.items)
        val notebookCallsAfterSync = api.notebookAccessTokens.size

        val result = service.refresh(request)

        assertFalse(result.hasCatalogChanges)
        assertEquals(1, writer.replaceCalls)
        assertTrue(writer.deltas.isEmpty())
        assertEquals(2, result.itemCount)
        assertEquals(notebookCallsAfterSync, api.notebookAccessTokens.size)
        assertEquals(1, journalRepository.replaceCount)
    }

    @Test
    fun `refresh writes only the book added on the server`() = runBlocking {
        val first = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val added = grimmoryItem(remoteId = "book-2", libraryRemoteId = "g-books", title = "New book")
        val writer = RecordingRemoteCatalogWriter(
            stored = StoredRemoteCatalog(
                libraries = listOf(grimmoryLibrary("g-books").toEntity()),
                items = listOf(first.toEntity(updatedAtEpochMillis = 5L)),
            ),
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(item = first, progress = null),
                GrimmoryBookCatalogEntry(item = added, progress = null),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
        )

        val result = service.refresh(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertTrue(result.hasCatalogChanges)
        assertEquals(0, writer.replaceCalls)
        assertEquals(listOf(added.id), writer.deltas.single().upsertItems.map(ReadableItemEntity::id))
    }

    @Test
    fun `sync reports grimmory journal push failures`() = runBlocking {
        val library = grimmoryLibrary("g-books")
        val item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val api = FakeGrimmoryApi(
            libraries = listOf(library),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            journalRepository = GrimmoryRecordingJournalRepository(
                dirtyEntries = listOf(journalEntry(item, rating = 8)),
            ),
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                password = "password",
            ),
        )

        assertEquals(1, result.progressPushFailures.size)
        assertEquals(
            "Updating Grimmory rating is not implemented.",
            result.progressPushFailures.single().message,
        )
    }

    @Test
    fun `sync's bar moves until the book list is in, then fills by the counted work`() = runBlocking {
        val readBooks = (1..3).map { number -> grimmoryItem(remoteId = "book-$number", libraryRemoteId = "g-books") }
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = readBooks.map { item ->
                GrimmoryBookCatalogEntry(item = item, progress = null, lastReadTime = "2026-10-30T20:00:00Z")
            },
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            journalRepository = GrimmoryRecordingJournalRepository(),
        )
        val progressUpdates = mutableListOf<GrimmorySyncProgress>()

        service.sync(
            request = GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
            onProgress = progressUpdates::add,
        )

        assertEquals(
            listOf(
                "Connecting",
                "Getting the book list",
                "Saving the library",
                "Uploading reading progress",
                "Reading history",
                "Reading history · 0 of 3 books",
                "Reading history · 1 of 3 books",
                "Reading history · 2 of 3 books",
                "Reading history · 3 of 3 books",
                "Uploading journal changes",
            ),
            progressUpdates.map(GrimmorySyncProgress::message).distinct(),
        )
        // Moving (no fraction) only before the first counted step, and never going back after it.
        val fractions = progressUpdates.map(GrimmorySyncProgress::fraction)
        val firstCounted = fractions.indexOfFirst { fraction -> fraction != null }
        assertEquals(listOf(null, null), fractions.take(firstCounted))
        val counted = fractions.drop(firstCounted).map { fraction -> checkNotNull(fraction) }
        assertEquals(counted.sorted(), counted)
        assertTrue(counted.last() < 1f)
    }

    @Test
    fun `reading history counts one book without a plural and large counts with separators`() {
        assertEquals("Reading history · 1 of 1 book", GrimmorySyncSteps.readingHistory(loaded = 1, total = 1).message)
        assertEquals("Reading history · 1,200 of 2,161 books", GrimmorySyncSteps.readingHistory(loaded = 1200, total = 2161).message)
    }

    @Test
    fun `sync can reuse saved access token without logging in`() = runBlocking {
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = emptyList(),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(null, api.lastLogin)
        assertEquals(listOf("saved-access-token"), api.libraryAccessTokens)
        assertEquals(listOf("saved-access-token"), api.bookAccessTokens)
        assertEquals(null, result.session)
    }

    @Test
    fun `sync refreshes saved token when catalog fetch is unauthorized`() = runBlocking {
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = emptyList(),
        ).apply {
            unauthorizedLibraryTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "expired-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(listOf("expired-access-token", "fresh-access-token"), api.libraryAccessTokens)
        assertEquals(listOf("fresh-access-token"), api.bookAccessTokens)
        assertEquals(listOf("saved-refresh-token"), api.refreshTokens)
        assertEquals("fresh-access-token", result.session?.accessToken)
        assertEquals("fresh-refresh-token", result.session?.refreshToken)
    }

    @Test
    fun `sync refreshes saved token when progress upload is unauthorized`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(
            remoteId = "42",
            libraryRemoteId = "g-books",
            format = FormatType.PDF,
            remoteFileId = "77",
            remoteFileType = "PDF",
            pageCount = 120,
            title = "Remote PDF",
        )
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(
                locatorType = LocatorType.PAGE,
                page = 9,
                percent = 0.4,
            ),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
            pageCount = item.pageCount,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PAGE,
                        page = 9,
                        percent = 0.4,
                        updatedAtEpochMillis = 1L,
                    ),
                ),
            ),
        ).apply {
            unauthorizedProgressTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "expired-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(listOf("expired-access-token"), api.libraryAccessTokens)
        assertEquals(listOf("expired-access-token"), api.bookAccessTokens)
        assertEquals(listOf("expired-access-token", "fresh-access-token"), api.progressAccessTokens)
        assertEquals(listOf("saved-refresh-token"), api.refreshTokens)
        val syncedProgress = readerSessionStore.getProgress(item)
        assertEquals(LocatorType.PAGE, syncedProgress?.locatorType)
        assertEquals(9, syncedProgress?.page)
        assertEquals(0.4, syncedProgress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, syncedProgress?.syncState)
        assertEquals("42", api.progressUpdates.single().bookId)
        assertEquals("77", api.progressUpdates.single().bookFileId)
        assertEquals("fresh-access-token", result.session?.accessToken)
        assertEquals("fresh-refresh-token", result.session?.refreshToken)
    }

    @Test
    fun `sync refreshes saved token when journal pull is unauthorized`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        ).apply {
            unauthorizedNotebookTokens += "expired-access-token"
            refreshedSession = GrimmorySession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
            )
        }
        val journalRepository = GrimmoryRecordingJournalRepository()
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            journalRepository = journalRepository,
        )

        val result = service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "expired-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )

        assertEquals(listOf("expired-access-token", "fresh-access-token"), api.notebookAccessTokens)
        assertEquals(listOf("saved-refresh-token"), api.refreshTokens)
        assertEquals("fresh-access-token", result.session?.accessToken)
        assertEquals(1, journalRepository.replaceCount)
    }

    @Test
    fun `sync maps Grimmory read status to finished journal cache`() = runBlocking {
        val item = grimmoryItem(remoteId = "book-1", libraryRemoteId = "g-books")
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    readStatus = "READ",
                ),
            ),
        )
        val journalRepository = GrimmoryRecordingJournalRepository()
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            journalRepository = journalRepository,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(item.id, journalRepository.lastEntries.single().itemId)
        assertEquals(JournalReadingEventType.FINISHED, journalRepository.lastEvents.single().eventType)
        assertEquals(item.id, journalRepository.lastEvents.single().itemId)
    }

    @Test
    fun `sync pulls remote progress from grimmory books endpoint`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(
            remoteId = "42",
            libraryRemoteId = "g-books",
            remoteFileId = "77",
            remoteFileType = "EPUB",
            pageCount = 321,
            title = "Remote EPUB",
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = ReadingProgress(
                        itemId = item.id,
                        locatorType = LocatorType.PERCENT,
                        percent = 0.42,
                        updatedAtEpochMillis = 1_775_046_600_000L,
                    ),
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val syncedProgress = readerSessionStore.getProgress(item)
        assertEquals(listOf("saved-access-token"), api.bookAccessTokens)
        assertEquals(LocatorType.PERCENT, syncedProgress?.locatorType)
        assertEquals(0.42, syncedProgress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, syncedProgress?.syncState)
    }

    @Test
    fun `sync clears progress for grimmory item no longer in catalog`() = runBlocking {
        val writer = RecordingRemoteCatalogWriter().apply {
            sourceItems = mapOf(
                SourceType.GRIMMORY to listOf(
                    grimmoryItem(remoteId = "legacy-book", libraryRemoteId = "legacy-library", title = "Legacy Book"),
                ),
            )
        }
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val legacyItem = writer.sourceItems.getValue(SourceType.GRIMMORY).single()
        readerSessionStore.saveProgress(
            item = legacyItem,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = emptyList(),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = writer,
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "New Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(null, readerSessionStore.getProgress(legacyItem))
    }

    @Test
    fun `sync keeps clean progress when grimmory omits book progress`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        readerSessionStore.updateSession(
            item = item,
            pageCount = 321,
            preferences = mapOf("theme" to "dark"),
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val progress = readerSessionStore.getProgress(item)
        assertEquals(0.75, progress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, progress?.syncState)
        val session = readerSessionStore.getSession(item)
        assertEquals(321, session?.pageCount)
        assertEquals("dark", session?.preferences?.get("theme"))
        assertEquals(emptyList<GrimmoryProgressUpdate>(), api.progressUpdates)
    }

    @Test
    fun `sync clears clean progress when grimmory marks book unread`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.CLEAN,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    isExplicitUnreadReset = true,
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        assertEquals(null, readerSessionStore.getProgress(item))
        assertEquals(emptyList<GrimmoryProgressUpdate>(), api.progressUpdates)
    }

    @Test
    fun `sync keeps dirty progress when grimmory omits book progress`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(GrimmoryBookCatalogEntry(item = item, progress = null)),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val progress = readerSessionStore.getProgress(item)
        assertEquals(0.75, progress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, progress?.syncState)
        assertEquals(1, api.progressUpdates.size)
    }

    @Test
    fun `sync keeps dirty progress when grimmory marks book unread`() = runBlocking {
        val readerSessionStore = GrimmoryTestReaderSessionStore()
        val item = grimmoryItem(remoteId = "42", libraryRemoteId = "g-books", title = "Remote EPUB")
        readerSessionStore.saveProgress(
            item = item,
            locator = ReaderLocator(locatorType = LocatorType.PERCENT, percent = 0.75),
            syncState = com.example.unifiedreader.core.model.SyncState.DIRTY,
        )
        val api = FakeGrimmoryApi(
            libraries = listOf(grimmoryLibrary("g-books")),
            allBookEntries = listOf(
                GrimmoryBookCatalogEntry(
                    item = item,
                    progress = null,
                    isExplicitUnreadReset = true,
                ),
            ),
        )
        val service = GrimmoryCatalogSyncService(
            grimmoryApiFactory = GrimmoryApiFactory { _ -> api },
            remoteCatalogWriter = RecordingRemoteCatalogWriter(),
            readerSessionStore = readerSessionStore,
        )

        service.sync(
            GrimmoryCatalogSyncRequest(
                displayName = "Grimmory",
                baseUrl = "https://grimmory.example/",
                username = "user",
                accessToken = "saved-access-token",
            ),
        )

        val progress = readerSessionStore.getProgress(item)
        assertEquals(0.75, progress?.percent)
        assertEquals(com.example.unifiedreader.core.model.SyncState.CLEAN, progress?.syncState)
        assertEquals(1, api.progressUpdates.size)
    }

}

private fun grimmoryLibrary(
    remoteId: String,
    title: String = "Books",
): Library = Library(
    id = SourceType.GRIMMORY.remoteLibraryId(remoteId),
    sourceType = SourceType.GRIMMORY,
    title = title,
    remoteId = remoteId,
)

private fun grimmoryItem(
    remoteId: String,
    libraryRemoteId: String,
    format: FormatType = FormatType.EPUB,
    remoteFileId: String? = null,
    remoteFileType: String? = format.name,
    pageCount: Int? = null,
    title: String = "Book",
): ReadableItem = ReadableItem(
    id = SourceType.GRIMMORY.remoteItemId(remoteId),
    sourceType = SourceType.GRIMMORY,
    libraryId = SourceType.GRIMMORY.remoteLibraryId(libraryRemoteId),
    remoteId = remoteId,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = format,
    pageCount = pageCount,
)

private class RecordingRemoteCatalogWriter(
    // What a refresh compares with; null keeps the writer without stored rows.
    var stored: StoredRemoteCatalog? = null,
) : RemoteCatalogWriter {
    var replacedSourceType: SourceType? = null
    var libraries: List<LibraryEntity> = emptyList()
    var items: List<ReadableItemEntity> = emptyList()
    var sourceItems: Map<SourceType, List<ReadableItem>> = emptyMap()
    var replaceCalls = 0
    val deltas = mutableListOf<RemoteCatalogDelta>()

    override suspend fun getItemsForSourceType(
        sourceType: SourceType,
    ): List<ReadableItem> = sourceItems[sourceType].orEmpty()

    override suspend fun getStoredSourceCatalog(
        sourceType: SourceType,
    ): StoredRemoteCatalog? = stored

    override suspend fun replaceSourceCatalog(
        sourceType: SourceType,
        libraries: List<LibraryEntity>,
        items: List<ReadableItemEntity>,
        syncGroups: List<RemoteCatalogSyncGroupEntity>,
    ) {
        replaceCalls += 1
        replacedSourceType = sourceType
        this.libraries = libraries
        this.items = items
    }

    override suspend fun applySourceCatalogDelta(
        delta: RemoteCatalogDelta,
    ) {
        deltas += delta
        replacedSourceType = delta.sourceType
        libraries = delta.libraries
        items = delta.upsertItems
    }
}

private class GrimmoryRecordingJournalRepository(
    private val dirtyEntries: List<ItemJournalEntry> = emptyList(),
) : JournalRepository {
    private val snapshot = MutableStateFlow(JournalSnapshot())
    var replaceCount = 0
    var lastEvents = emptyList<JournalReadingEvent>()
    var lastEntries = emptyList<ItemJournalEntry>()
    var lastSessions = emptyList<JournalReadingSession>()

    override fun observeSnapshot(): Flow<JournalSnapshot> = snapshot

    override suspend fun recordReaderProgress(input: JournalProgressInput) = Unit

    override suspend fun saveJournalEntry(item: ReadableItem, rating: Int?, reviewText: String) = Unit

    override suspend fun saveJournalEntry(item: JournalItemRef, rating: Int?, reviewText: String) = Unit

    override suspend fun deleteItemJournal(itemId: String) = Unit

    override suspend fun getDirtyJournalEntriesForSource(
        sourceType: SourceType,
    ): List<ItemJournalEntry> = dirtyEntries.filter { entry -> entry.sourceType == sourceType }

    // As the database would: the last pull's entries are the clean ones.
    override suspend fun getCleanJournalEntriesForSource(
        sourceType: SourceType,
    ): List<ItemJournalEntry> = lastEntries.filter { entry -> entry.sourceType == sourceType }

    override suspend fun replaceCleanSourceJournalCache(
        sourceType: SourceType,
        events: List<JournalReadingEvent>,
        entries: List<ItemJournalEntry>,
        sessions: List<JournalReadingSession>,
        preserveReviewFieldsWhenIncomingBlank: Boolean,
        preserveRatingWhenIncomingBlank: Boolean,
        preserveReadingEventsWhenIncomingIncomplete: Boolean,
        preserveReadingSessionsWhenIncomingIncomplete: Boolean,
        readingEventReplacementItemIds: Set<String>?,
        readingSessionReplacementItemIds: Set<String>?,
        journalEntryReplacementItemIds: Set<String>?,
        preserveRatingOnlyEntriesWhenIncomingMissing: Boolean,
        ratingOnlyPreservationExcludedItemIds: Set<String>?,
    ) {
        replaceCount += 1
        lastEvents = events
        lastEntries = entries
        lastSessions = sessions
    }
}

private fun journalEntry(
    item: ReadableItem,
    rating: Int? = null,
    reviewText: String = "",
): ItemJournalEntry = ItemJournalEntry(
    itemId = item.id,
    sourceType = item.sourceType,
    libraryId = item.libraryId,
    itemTitle = item.title,
    coverUrl = item.coverUrl,
    itemKind = item.kind,
    format = item.format,
    rating = rating,
    reviewText = reviewText,
    reviewedAtEpochMillis = null,
    updatedAtEpochMillis = 1_000L,
    syncState = SyncState.DIRTY,
)

private class FakeGrimmoryApi(
    private val libraries: List<Library>,
    private val allBookEntries: List<GrimmoryBookCatalogEntry>,
    private val detailedBookEntries: Map<String, GrimmoryBookCatalogEntry> = emptyMap(),
) : GrimmoryApi {
    var lastLogin: Pair<String, String>? = null
    var refreshedSession = GrimmorySession(
        accessToken = "refreshed-access-token",
        refreshToken = "refreshed-refresh-token",
    )
    val libraryAccessTokens = mutableListOf<String>()
    val bookAccessTokens = mutableListOf<String>()
    val progressAccessTokens = mutableListOf<String>()
    val notebookAccessTokens = mutableListOf<String>()
    val refreshTokens = mutableListOf<String>()
    val progressUpdates = mutableListOf<GrimmoryProgressUpdate>()
    val detailBookRequests = mutableListOf<String>()
    val unauthorizedLibraryTokens = mutableSetOf<String>()
    val unauthorizedProgressTokens = mutableSetOf<String>()
    val unauthorizedNotebookTokens = mutableSetOf<String>()
    val sessionRequests = mutableListOf<String>()
    // Thrown by every progress upload, such as a server that stopped answering.
    var progressFailure: Throwable? = null

    override suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession {
        lastLogin = username to password
        return GrimmorySession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
        )
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> {
        libraryAccessTokens += accessToken
        if (accessToken in unauthorizedLibraryTokens) {
            throw GrimmoryUnauthorizedException()
        }
        return libraries
    }

    override suspend fun refreshSession(
        refreshToken: String,
    ): GrimmorySession {
        refreshTokens += refreshToken
        return refreshedSession
    }

    override suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem> {
        bookAccessTokens += accessToken
        return allBookEntries
            .map(GrimmoryBookCatalogEntry::item)
            .filter { item -> item.libraryId == SourceType.GRIMMORY.remoteLibraryId(libraryId) }
    }

    override suspend fun getAllBookEntries(
        accessToken: String,
    ): List<GrimmoryBookCatalogEntry> {
        bookAccessTokens += accessToken
        return allBookEntries
    }

    override suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? {
        detailBookRequests += "$accessToken:$bookId"
        return detailedBookEntries[bookId]
            ?: allBookEntries.firstOrNull { entry -> entry.item.remoteId == bookId }
    }

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = error("Not used")

    override fun bookCoverUrl(
        bookId: String,
    ): String = error("Not used")

    override suspend fun updateProgress(
        accessToken: String,
        progress: GrimmoryProgressUpdate,
    ) {
        progressAccessTokens += accessToken
        if (accessToken in unauthorizedProgressTokens) {
            throw GrimmoryUnauthorizedException()
        }
        progressFailure?.let { throw it }
        progressUpdates += progress
    }

    override suspend fun getNotebookEntriesExport(
        accessToken: String,
        types: List<String>,
        search: String?,
        sort: String,
    ): List<GrimmoryNotebookEntryDto> {
        notebookAccessTokens += accessToken
        if (accessToken in unauthorizedNotebookTokens) {
            throw GrimmoryUnauthorizedException()
        }
        return emptyList()
    }

    override suspend fun getReadingSessionsForBook(
        accessToken: String,
        bookId: String,
        pageSize: Int,
    ): List<GrimmoryReadingSessionDto> {
        sessionRequests += bookId
        return emptyList()
    }
}

private class GrimmoryTestReaderSessionStore : ReaderSessionStore {
    private val progressByItemId = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    private val sessionByItemId = MutableStateFlow<Map<String, ReaderSessionSnapshot>>(emptyMap())

    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressByItemId
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionByItemId

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressByItemId.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        isExplicitUnreadReset: Boolean,
        syncState: com.example.unifiedreader.core.model.SyncState,
        pageCount: Int?,
        updatedAtEpochMillis: Long,
    ) {
        progressByItemId.value = progressByItemId.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                isExplicitUnreadReset = isExplicitUnreadReset,
                syncState = syncState,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionByItemId.value[item.id]

    override suspend fun replaceProgressIfCurrent(
        expected: ReaderProgressSnapshot,
        replacement: ReaderProgressSnapshot?,
    ): Boolean {
        val current = progressByItemId.value[expected.itemId] ?: return false
        if (current != expected) return false
        progressByItemId.value = if (replacement == null) {
            progressByItemId.value - expected.itemId
        } else {
            progressByItemId.value + (expected.itemId to replacement)
        }
        return true
    }

    override fun updateSession(
        itemId: String,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionByItemId.value[itemId] ?: ReaderSessionSnapshot(itemId = itemId)
        sessionByItemId.value = sessionByItemId.value + (
            itemId to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearProgress(itemId: String) {
        progressByItemId.value = progressByItemId.value - itemId
    }

    override fun clearItem(itemId: String) {
        clearProgress(itemId)
        sessionByItemId.value = sessionByItemId.value - itemId
    }

    override fun restoreItem(itemId: String) = Unit
}
