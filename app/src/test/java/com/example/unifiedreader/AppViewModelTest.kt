package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryFileLocation
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.isKavitaSeriesContainer
import com.example.unifiedreader.core.model.isStumpSeriesContainer
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.data.grimmory.GrimmorySession
import com.example.unifiedreader.feature.library.LibraryBrowsePreferences
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesSnapshot
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.library.LibraryScrollPosition
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppViewModelTest {
    @Test
    fun `on item selected opens reader and back returns to library`() {
        val sampleItem = sampleItem(format = FormatType.EPUB)
        val viewModel = createViewModel(
            sampleItems = listOf(sampleItem),
        )

        viewModel.onItemSelected(sampleItem)
        assertEquals(sampleItem.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.EPUB, viewModel.uiState.activeReader)

        viewModel.onBackPressed()
        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
    }

    @Test
    fun `on item selected opens reader immediately while remote progress sync runs in background`() {
        val remoteBook = sampleItem(
            id = "grimmory-epub",
            title = "Remote Book",
            format = FormatType.EPUB,
        ).copy(
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("grimmory-books"),
            remoteId = "grimmory-epub",
        )
        val progressSync = BlockingSourceProgressSync(sourceType = SourceType.GRIMMORY)
        val viewModel = createViewModel(
            sampleItems = listOf(remoteBook),
            sourceProgressSyncs = listOf(progressSync),
        )

        viewModel.onItemSelected(remoteBook)

        assertEquals(remoteBook.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.EPUB, viewModel.uiState.activeReader)
        progressSync.allowCompletion()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, progressSync.syncItemCalls)
    }

    @Test
    fun `import success adds item and opens reader`() = runBlocking {
        val importedItem = sampleItem(
            id = "imported-cbz",
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 12 pages.",
            isLocalImport = true,
        )
        val importedRepository = FakeLocalDocumentRepository(
            importResult = importedItem,
            pageCounts = mutableMapOf(importedItem.id to 12),
        )
        val viewModel = createViewModel(
            importedRepository = importedRepository,
        )

        viewModel.importDocument(Uri.parse("content://imports/akira.cbz"))

        assertEquals(importedItem.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.CBZ, viewModel.uiState.activeReader)
        assertEquals(SourceType.LOCAL, viewModel.uiState.selectedSource)
        assertEquals(listOf(importedItem), viewModel.uiState.items)
    }

    @Test
    fun `import failure leaves selection closed`() = runBlocking {
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(importResult = null),
        )

        viewModel.importDocument(Uri.parse("content://imports/bad-file.bin"))

        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
    }

    @Test
    fun `import failure from settings stays on settings`() = runBlocking {
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(importResult = null),
        )
        viewModel.onTopLevelSelected(TopLevelDestination.Settings)

        viewModel.importDocument(Uri.parse("content://imports/bad-file.bin"))

        assertEquals(TopLevelDestination.Settings, viewModel.uiState.topLevel)
        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
    }

    @Test
    fun `imported epub opens local reader`() = runBlocking {
        val importedItem = ReadableItem(
            id = "document-abcd",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "The Dispossessed",
            format = FormatType.EPUB,
            description = "Imported local EPUB book.",
            coverUrl = null,
        )
        val importedRepository = FakeLocalDocumentRepository(
            importResult = importedItem,
            importedDocumentUris = mutableMapOf(
                importedItem.id to Uri.parse("content://imports/the-dispossessed.epub"),
            ),
        )
        val viewModel = createViewModel(
            importedRepository = importedRepository,
        )

        viewModel.importDocument(Uri.parse("content://imports/the-dispossessed.epub"))

        assertEquals(importedItem.id, viewModel.uiState.selectedItemId)
        assertEquals(FormatType.EPUB, viewModel.uiState.activeReader)
        assertEquals(SourceType.LOCAL, viewModel.uiState.selectedSource)
        assertEquals(SourceType.LOCAL, viewModel.uiState.selectedItem?.sourceType)
    }

    @Test
    fun `import success preserves comic metadata on imported item`() = runBlocking {
        val importedItem = sampleItem(
            id = "imported-carmilla",
            title = "Carmilla 03 (2025) (Collection) (Dark Horse Comics) (Digital-HD) (LeDuch)",
            format = FormatType.CBZ,
            isLocalImport = true,
        ).copy(
            comicMetadata = ComicMetadata(
                series = "Carmilla: The Eternal",
                volume = "2025",
                issueNumber = "3",
            ),
        )
        val importedRepository = FakeLocalDocumentRepository(
            importResult = importedItem,
            pageCounts = mutableMapOf(importedItem.id to 112),
        )
        val viewModel = createViewModel(
            importedRepository = importedRepository,
        )

        viewModel.importDocument(Uri.parse("content://imports/carmilla-03.cbz"))

        assertEquals(importedItem.id, viewModel.uiState.selectedItemId)
        assertEquals("Carmilla: The Eternal", viewModel.uiState.selectedItem?.comicMetadata?.series)
    }

    @Test
    fun `refresh progress rebuilds summaries from stored progress`() {
        val epubItem = sampleItem(
            id = "epub-1",
            title = "Left Hand",
            format = FormatType.EPUB,
        )
        val cbzItem = sampleItem(
            id = "cbz-1",
            title = "Akira",
            format = FormatType.CBZ,
        )
        val progressStore = FakeComicReaderProgressStore().apply {
            savePage(cbzItem, pageIndex = 3, pageCount = 12)
        }
        val importedRepository = FakeLocalDocumentRepository(
            importedItems = mutableListOf(cbzItem),
            pageCounts = mutableMapOf(cbzItem.id to 12),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = epubItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 1,
                ),
                pageCount = 4,
            )
            saveProgress(
                item = cbzItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 3,
                ),
                pageCount = 12,
            )
        }
        val pageSource = FakeComicPageSource(
            pageCounts = mapOf(epubItem.id to 4),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            importedRepository = importedRepository,
            pageSource = pageSource,
            progressStore = progressStore,
            readerSessionStore = readerSessionStore,
        )

        viewModel.refreshProgress()

        assertEquals("Resume on page 2 of 4", viewModel.uiState.progressByItemId[epubItem.id]?.label)
        assertEquals("Resume on page 4 of 12", viewModel.uiState.progressByItemId[cbzItem.id]?.label)
    }

    @Test
    fun `refresh progress uses session page count for epub page locator`() {
        val epubItem = sampleItem(
            id = "epub-1",
            title = "Left Hand",
            format = FormatType.EPUB,
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = epubItem.id,
                    locatorType = LocatorType.PAGE,
                    page = 2,
                    syncState = SyncState.CLEAN,
                ),
            )
            seedSession(
                com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(
                    itemId = epubItem.id,
                    pageCount = 10,
                ),
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            readerSessionStore = readerSessionStore,
            pageSource = FakeComicPageSource(),
        )

        viewModel.refreshProgress()

        assertEquals("Resume on page 3 of 10", viewModel.uiState.progressByItemId[epubItem.id]?.label)
    }

    @Test
    fun `refresh progress shows percent summary from generic reader session store`() {
        val epubItem = sampleItem(
            id = "epub-2",
            title = "Dune",
            format = FormatType.EPUB,
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = epubItem.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.42,
                    syncState = SyncState.CLEAN,
                ),
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            readerSessionStore = readerSessionStore,
        )

        viewModel.refreshProgress()

        assertEquals("Resume at 42%", viewModel.uiState.progressByItemId[epubItem.id]?.label)
    }

    @Test
    fun `refresh progress keeps epub page count for percent progress`() {
        val epubItem = sampleItem(
            id = "epub-3",
            title = "Hyperion",
            format = FormatType.EPUB,
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = epubItem.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.42,
                    syncState = SyncState.CLEAN,
                ),
            )
            seedSession(
                com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot(
                    itemId = epubItem.id,
                    pageCount = 19,
                ),
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(epubItem),
            readerSessionStore = readerSessionStore,
        )

        viewModel.refreshProgress()

        assertEquals(19, viewModel.uiState.progressByItemId[epubItem.id]?.pageCount)
    }

    @Test
    fun `repository flow updates refresh ui state`() {
        val itemRepository = FakeReadableItemRepository()
        val libraryRepository = FakeLibraryRepository()
        val viewModel = createViewModel(
            libraryRepository = libraryRepository,
            readableItemRepository = itemRepository,
        )

        val updatedLibrary = Library(
            id = SourceType.GRIMMORY.remoteLibraryId("g-books"),
            sourceType = SourceType.GRIMMORY,
            title = "Books",
            remoteId = "g-books",
        )
        val updatedItem = sampleItem(
            id = "epub-live",
            title = "Live Catalog Item",
            format = FormatType.EPUB,
            description = "Added through repository flow.",
        ).copy(
            id = SourceType.GRIMMORY.remoteItemId("epub-live"),
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("g-books"),
            remoteId = "epub-live",
        )

        libraryRepository.emitLibraries(listOf(updatedLibrary))
        itemRepository.emitItems(listOf(updatedItem))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(updatedLibrary), viewModel.uiState.libraries)
        assertEquals(listOf(updatedItem), viewModel.uiState.items)
    }

    @Test
    fun `library filters combine groups with and and values with or`() {
        val akira = sampleItem(
            id = "akira-1",
            title = "Akira 1",
            format = FormatType.CBZ,
            isLocalImport = true,
        ).copy(
            comicMetadata = ComicMetadata(series = "Akira"),
        )
        val dune = sampleItem(
            id = "dune",
            title = "Dune",
            format = FormatType.EPUB,
            isLocalImport = true,
        ).copy(
            bookMetadata = BookMetadata(author = "Frank Herbert"),
        )
        val leftHand = sampleItem(
            id = "left-hand",
            title = "The Left Hand of Darkness",
            format = FormatType.EPUB,
            isLocalImport = true,
        ).copy(
            bookMetadata = BookMetadata(author = "Ursula K. Le Guin"),
        )
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(
                importedItems = mutableListOf(akira, dune, leftHand),
            ),
        )
        viewModel.onSourceSelected(SourceType.LOCAL)

        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(
                formats = setOf(FormatType.EPUB),
                authors = setOf("Frank Herbert", "Ursula K. Le Guin"),
            ),
        )

        assertEquals(listOf(dune, leftHand), viewModel.uiState.visibleItems)

        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(
                formats = setOf(FormatType.EPUB),
                authors = setOf("Frank Herbert"),
            ),
        )

        assertEquals(listOf(dune), viewModel.uiState.visibleItems)
        assertEquals(listOf(FormatType.CBZ, FormatType.EPUB), viewModel.uiState.selectedLibraryFilterFacets.formats)
        assertEquals(listOf("Frank Herbert", "Ursula K. Le Guin"), viewModel.uiState.selectedLibraryFilterFacets.authors)
        assertEquals(listOf("Akira"), viewModel.uiState.selectedLibraryFilterFacets.series)
    }

    @Test
    fun `library filters are scoped per source`() {
        val remoteComic = sampleItem(
            id = "remote-comic",
            title = "Remote Comic",
            format = FormatType.CBZ,
        )
        val localBook = sampleItem(
            id = "local-book",
            title = "Local Book",
            format = FormatType.EPUB,
            isLocalImport = true,
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteComic),
            importedRepository = FakeLocalDocumentRepository(
                importedItems = mutableListOf(localBook),
            ),
        )

        viewModel.onLibraryFiltersChanged(LibraryFilterState(formats = setOf(FormatType.EPUB)))

        assertTrue(viewModel.uiState.visibleItems.isEmpty())

        viewModel.onSourceSelected(SourceType.LOCAL)

        assertEquals(listOf(localBook), viewModel.uiState.visibleItems)
        assertTrue(viewModel.uiState.selectedLibraryFilters.isEmpty)
    }

    @Test
    fun `library filter narrows kavita items by selected remote library`() {
        val comicsIssue = sampleItem(
            id = "comics-1",
            title = "Saga 001",
            format = FormatType.CBZ,
        ).copy(
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
            remoteFileType = "series-1",
            comicMetadata = ComicMetadata(series = "Saga", issueTitle = "Issue 1"),
        )
        val mangaIssue = sampleItem(
            id = "manga-1",
            title = "Berserk 001",
            format = FormatType.CBZ,
        ).copy(
            libraryId = SourceType.KAVITA.remoteLibraryId("k-manga"),
            remoteFileType = "series-2",
            comicMetadata = ComicMetadata(series = "Berserk", issueTitle = "Episode 1"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(comicsIssue, mangaIssue),
            libraryRepository = FakeLibraryRepository(
                libraries = listOf(
                    Library(
                        id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                        sourceType = SourceType.KAVITA,
                        title = "Comics",
                        remoteId = "k-comics",
                    ),
                    Library(
                        id = SourceType.KAVITA.remoteLibraryId("k-manga"),
                        sourceType = SourceType.KAVITA,
                        title = "Manga",
                        remoteId = "k-manga",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                SourceType.KAVITA.remoteLibraryId("k-comics"),
                SourceType.KAVITA.remoteLibraryId("k-manga"),
            ),
            viewModel.uiState.selectedLibraryFilterFacets.libraries,
        )

        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(
                libraries = setOf(SourceType.KAVITA.remoteLibraryId("k-manga")),
            ),
        )

        assertEquals(1, viewModel.uiState.visibleItems.size)
        assertEquals("Berserk", viewModel.uiState.visibleItems.single().title)
    }

    @Test
    fun `library search matches item metadata and is scoped per source`() {
        val remoteComic = sampleItem(
            id = "remote-comic",
            title = "Remote Comic",
            format = FormatType.CBZ,
        ).copy(
            comicMetadata = ComicMetadata(series = "Akira"),
        )
        val russianBook = sampleItem(
            id = "russian-book",
            title = "Некрополи Петербурга",
            format = FormatType.EPUB,
        ).copy(
            bookMetadata = BookMetadata(author = "Маргарита Николаева"),
        )
        val localBook = sampleItem(
            id = "local-book",
            title = "Local Book",
            format = FormatType.EPUB,
            isLocalImport = true,
        ).copy(
            bookMetadata = BookMetadata(author = "Ursula K. Le Guin"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(remoteComic, russianBook),
            importedRepository = FakeLocalDocumentRepository(
                importedItems = mutableListOf(localBook),
            ),
        )

        viewModel.onLibrarySearchQueryChanged("akira")

        assertEquals(1, viewModel.uiState.visibleItems.size)
        assertTrue(viewModel.uiState.visibleItems.single().isKavitaSeriesContainer())
        assertEquals("Akira", viewModel.uiState.visibleItems.single().title)

        viewModel.onLibrarySearchQueryChanged("петербурга")

        assertEquals(listOf(russianBook), viewModel.uiState.visibleItems)

        viewModel.onLibrarySearchQueryChanged("МАРГАРИТА")

        assertEquals(listOf(russianBook), viewModel.uiState.visibleItems)

        viewModel.onSourceSelected(SourceType.LOCAL)

        assertEquals(listOf(localBook), viewModel.uiState.visibleItems)
        assertEquals("", viewModel.uiState.selectedLibrarySearchQuery)

        viewModel.onLibrarySearchQueryChanged("le guin")

        assertEquals(listOf(localBook), viewModel.uiState.visibleItems)
    }

    @Test
    fun `library browse preferences restore persisted filters and sort per source`() {
        val localBook = sampleItem(
            id = "local-book",
            title = "A Wizard of Earthsea",
            format = FormatType.EPUB,
            isLocalImport = true,
        ).copy(
            bookMetadata = BookMetadata(author = "Ursula K. Le Guin"),
        )
        val localComic = sampleItem(
            id = "local-comic",
            title = "Akira 001",
            format = FormatType.CBZ,
            isLocalImport = true,
        )
        val browsePreferencesStore = FakeLibraryBrowsePreferencesStore(
            initialPreferences = LibraryBrowsePreferences(
                filtersBySource = mapOf(
                    SourceType.LOCAL to LibraryFilterState(formats = setOf(FormatType.EPUB)),
                ),
                sortsBySource = mapOf(
                    SourceType.LOCAL to LibrarySortMode.TITLE,
                ),
            ),
        )
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(
                importedItems = mutableListOf(localComic, localBook),
            ),
            libraryBrowsePreferencesStore = browsePreferencesStore,
        )

        viewModel.onSourceSelected(SourceType.LOCAL)

        assertEquals(LibrarySortMode.TITLE, viewModel.uiState.selectedLibrarySortMode)
        assertEquals(setOf(FormatType.EPUB), viewModel.uiState.selectedLibraryFilters.formats)
        assertEquals(listOf(localBook), viewModel.uiState.visibleItems)
    }

    @Test
    fun `library browse changes persist into preferences store`() {
        val localBook = sampleItem(
            id = "local-book",
            title = "A Wizard of Earthsea",
            format = FormatType.EPUB,
            isLocalImport = true,
        )
        val browsePreferencesStore = FakeLibraryBrowsePreferencesStore()
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(
                importedItems = mutableListOf(localBook),
            ),
            libraryBrowsePreferencesStore = browsePreferencesStore,
        )

        viewModel.onSourceSelected(SourceType.LOCAL)
        viewModel.onLibraryFiltersChanged(
            LibraryFilterState(
                formats = setOf(FormatType.EPUB),
                authors = setOf("Ursula K. Le Guin"),
            ),
        )
        viewModel.onLibrarySortModeChanged(LibrarySortMode.TITLE)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            LibraryFilterState(
                formats = setOf(FormatType.EPUB),
                authors = setOf("Ursula K. Le Guin"),
            ),
            browsePreferencesStore.preferencesFlow.value.filtersBySource[SourceType.LOCAL],
        )
        assertEquals(
            LibrarySortMode.TITLE,
            browsePreferencesStore.preferencesFlow.value.sortsBySource[SourceType.LOCAL],
        )
    }

    @Test
    fun `library sort modes order filtered visible items`() {
        val neuromancer = sampleItem(
            id = "neuromancer",
            title = "Neuromancer",
            format = FormatType.EPUB,
            isLocalImport = true,
        ).copy(
            addedAtEpochMillis = 100L,
            bookMetadata = BookMetadata(author = "William Gibson"),
        )
        val leftHand = sampleItem(
            id = "left-hand",
            title = "The Left Hand of Darkness",
            format = FormatType.EPUB,
            isLocalImport = true,
        ).copy(
            addedAtEpochMillis = 300L,
            bookMetadata = BookMetadata(author = "Ursula K. Le Guin"),
        )
        val akira = sampleItem(
            id = "akira",
            title = "Akira 001",
            format = FormatType.CBZ,
            isLocalImport = true,
        ).copy(
            addedAtEpochMillis = 200L,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "The Bike Chase"),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = leftHand.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.2,
                    updatedAtEpochMillis = 1_000L,
                ),
            )
            seedProgress(
                com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                    itemId = akira.id,
                    locatorType = LocatorType.PERCENT,
                    percent = 0.4,
                    updatedAtEpochMillis = 3_000L,
                ),
            )
        }
        val viewModel = createViewModel(
            importedRepository = FakeLocalDocumentRepository(
                importedItems = mutableListOf(neuromancer, leftHand, akira),
            ),
            readerSessionStore = readerSessionStore,
        )
        viewModel.onSourceSelected(SourceType.LOCAL)

        assertEquals(LibrarySortMode.LAST_READ, viewModel.uiState.selectedLibrarySortMode)
        assertEquals(listOf(akira, leftHand, neuromancer), viewModel.uiState.visibleItems)

        viewModel.onLibrarySortModeChanged(LibrarySortMode.LAST_READ)

        assertEquals(listOf(akira, leftHand, neuromancer), viewModel.uiState.visibleItems)

        viewModel.onLibrarySortModeChanged(LibrarySortMode.AUTHOR)

        assertEquals(listOf(akira, neuromancer, leftHand), viewModel.uiState.visibleItems)

        viewModel.onLibrarySortModeChanged(LibrarySortMode.TITLE)

        assertEquals(listOf(akira, neuromancer, leftHand), viewModel.uiState.visibleItems)

        viewModel.onLibrarySortModeChanged(LibrarySortMode.SERIES)

        assertEquals(listOf(akira, neuromancer, leftHand), viewModel.uiState.visibleItems)
    }

    @Test
    fun `kavita comics are grouped by series on top level and open issue list on tap`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
        )

        assertEquals(1, viewModel.uiState.visibleItems.size)
        val seriesItem = viewModel.uiState.visibleItems.single()
        assertTrue(seriesItem.isKavitaSeriesContainer())
        assertEquals("Akira", seriesItem.title)
        assertNull(viewModel.uiState.activeReader)

        viewModel.onItemSelected(seriesItem)

        assertEquals("Akira", viewModel.uiState.selectedKavitaSeriesTitle)
        assertEquals(listOf(firstIssue, secondIssue), viewModel.uiState.visibleItems)
        assertNull(viewModel.uiState.activeReader)

        viewModel.onBackPressed()

        assertEquals(1, viewModel.uiState.visibleItems.size)
        assertTrue(viewModel.uiState.visibleItems.single().isKavitaSeriesContainer())
    }

    @Test
    fun `library scroll positions are kept per browse context when entering and leaving kavita series`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
        )
        val rootKey = viewModel.uiState.libraryBrowseContextKey
        val seriesItem = viewModel.uiState.visibleItems.single()

        viewModel.onLibraryScrollPositionChanged(
            contextKey = rootKey,
            position = LibraryScrollPosition(index = 9, offset = 24),
        )
        viewModel.onItemSelected(seriesItem)

        val seriesKey = viewModel.uiState.libraryBrowseContextKey
        assertTrue(seriesKey.startsWith("kavita:series:"))
        assertTrue(seriesKey != rootKey)
        assertEquals(LibraryScrollPosition(index = 9, offset = 24), viewModel.libraryScrollPositionFor(rootKey))
        assertEquals(LibraryScrollPosition(), viewModel.libraryScrollPositionFor(seriesKey))

        viewModel.onLibraryScrollPositionChanged(
            contextKey = seriesKey,
            position = LibraryScrollPosition(index = 1, offset = 12),
        )
        viewModel.onBackPressed()

        assertEquals(rootKey, viewModel.uiState.libraryBrowseContextKey)
        assertEquals(LibraryScrollPosition(index = 9, offset = 24), viewModel.libraryScrollPositionFor(rootKey))
        assertEquals(LibraryScrollPosition(index = 1, offset = 12), viewModel.libraryScrollPositionFor(seriesKey))
    }

    @Test
    fun `changing search in a series creates a fresh root browse context`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
        )
        val originalRootKey = viewModel.uiState.libraryBrowseContextKey
        val seriesItem = viewModel.uiState.visibleItems.single()
        viewModel.onLibraryScrollPositionChanged(
            contextKey = originalRootKey,
            position = LibraryScrollPosition(index = 9, offset = 24),
        )

        viewModel.onItemSelected(seriesItem)
        viewModel.onLibrarySearchQueryChanged("issue 2")
        viewModel.onBackPressed()

        val filteredRootKey = viewModel.uiState.libraryBrowseContextKey
        assertTrue(filteredRootKey != originalRootKey)
        assertEquals(LibraryScrollPosition(), viewModel.libraryScrollPositionFor(filteredRootKey))
        assertEquals(LibraryScrollPosition(index = 9, offset = 24), viewModel.libraryScrollPositionFor(originalRootKey))
    }

    @Test
    fun `library scroll position cache evicts oldest contexts`() {
        val viewModel = createViewModel()

        repeat(65) { index ->
            viewModel.onLibraryScrollPositionChanged(
                contextKey = "context-$index",
                position = LibraryScrollPosition(index = index, offset = index),
            )
        }

        assertEquals(LibraryScrollPosition(), viewModel.libraryScrollPositionFor("context-0"))
        assertEquals(LibraryScrollPosition(index = 64, offset = 64), viewModel.libraryScrollPositionFor("context-64"))
    }

    @Test
    fun `browse context key distinguishes delimiter heavy filter values`() {
        val firstViewModel = createViewModel()
        firstViewModel.onLibraryFiltersChanged(
            LibraryFilterState(authors = setOf("A,B", "C")),
        )

        val secondViewModel = createViewModel()
        secondViewModel.onLibraryFiltersChanged(
            LibraryFilterState(authors = setOf("A", "B,C")),
        )

        assertTrue(firstViewModel.uiState.libraryBrowseContextKey != secondViewModel.uiState.libraryBrowseContextKey)
    }

    @Test
    fun `kavita series progress uses total issue count and avoids continue on finished issue`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            pageCount = 10,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            pageCount = 10,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = firstIssue,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 9,
                ),
                isFinished = true,
                pageCount = 10,
                updatedAtEpochMillis = 1_000L,
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            readerSessionStore = readerSessionStore,
        )

        val seriesItem = viewModel.uiState.visibleItems.single()
        val progress = viewModel.uiState.displayProgressByItemId[seriesItem.id]

        assertEquals(true, seriesItem.isKavitaSeriesContainer())
        assertEquals("Read 1 of 2 issues", progress?.label)
        assertEquals(0.5f, progress?.fraction ?: -1f, 0.001f)
    }

    @Test
    fun `kavita series progress keeps last read timestamp from latest issue activity`() {
        val firstIssue = sampleItem(
            id = "chapter-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-1",
            remoteFileType = "series-7",
            pageCount = 10,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "chapter-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            remoteId = "chapter-2",
            remoteFileType = "series-7",
            pageCount = 10,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = firstIssue,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 3,
                ),
                pageCount = 10,
                updatedAtEpochMillis = 1_000L,
            )
            saveProgress(
                item = secondIssue,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 9,
                ),
                isFinished = true,
                pageCount = 10,
                updatedAtEpochMillis = 2_000L,
            )
        }
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            readerSessionStore = readerSessionStore,
        )

        val seriesItem = viewModel.uiState.visibleItems.single()
        val progress = viewModel.uiState.displayProgressByItemId[seriesItem.id]

        assertEquals("Continue Issue 1", progress?.label)
        assertEquals(2_000L, progress?.lastReadAtEpochMillis)
    }

    @Test
    fun `stump series progress is marked finished when all issues are finished`() {
        val firstIssue = sampleItem(
            id = "stump-issue-1",
            title = "Akira 001",
            format = FormatType.CBZ,
        ).copy(
            sourceType = SourceType.STUMP,
            remoteId = "stump-issue-1",
            remoteFileType = "series-7",
            pageCount = 10,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 1"),
        )
        val secondIssue = sampleItem(
            id = "stump-issue-2",
            title = "Akira 002",
            format = FormatType.CBZ,
        ).copy(
            sourceType = SourceType.STUMP,
            remoteId = "stump-issue-2",
            remoteFileType = "series-7",
            pageCount = 10,
            comicMetadata = ComicMetadata(series = "Akira", issueTitle = "Issue 2"),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            listOf(firstIssue, secondIssue).forEachIndexed { index, issue ->
                saveProgress(
                    item = issue,
                    locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                        locatorType = LocatorType.PAGE,
                        page = 9,
                    ),
                    isFinished = true,
                    pageCount = 10,
                    updatedAtEpochMillis = 1_000L + index,
                )
            }
        }
        val viewModel = createViewModel(
            sampleItems = listOf(firstIssue, secondIssue),
            readerSessionStore = readerSessionStore,
        )

        viewModel.onSourceSelected(SourceType.STUMP)
        shadowOf(Looper.getMainLooper()).idle()

        val seriesItem = viewModel.uiState.visibleItems.single()
        val progress = viewModel.uiState.displayProgressByItemId[seriesItem.id]

        assertTrue(seriesItem.isStumpSeriesContainer())
        assertEquals("Finished all 2 issues", progress?.label)
        assertEquals(1f, progress?.fraction ?: -1f, 0.001f)
        assertTrue(progress?.isFinished ?: false)
    }

    @Test
    fun `theme mode selection updates ui state`() {
        val preferencesStore = FakeSettingsPreferencesStore()
        val viewModel = createViewModel(
            settingsPreferencesStore = preferencesStore,
        )

        viewModel.onThemeModeSelected(ThemeMode.Dark)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)
        assertEquals(ThemeMode.Dark, preferencesStore.preferencesFlow.value.themeMode)
    }

    @Test
    fun `selected source hydration reflects remote catalog readiness`() {
        val remoteCatalogReadiness = FakeRemoteCatalogReadiness(initialHydrated = false)
        val viewModel = createViewModel(
            remoteCatalogReadiness = remoteCatalogReadiness,
        )

        viewModel.onSourceSelected(SourceType.GRIMMORY)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(false, viewModel.uiState.isSelectedSourceHydrated)

        remoteCatalogReadiness.emit(true)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(true, viewModel.uiState.isSelectedSourceHydrated)
    }

    @Test
    fun `connect kavita authenticates quickly and waits for explicit sync`() {
        val syncer = FakeKavitaCatalogSyncer(
            connectionResult = KavitaCatalogConnectionResult(
                libraryCount = 2,
                session = com.example.unifiedreader.data.kavita.KavitaSession(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                    apiKey = "api-key",
                ),
            ),
        )
        val credentialStore = FakeKavitaCredentialStore()
        val viewModel = createViewModel(
            kavitaCatalogSyncer = syncer,
            kavitaCredentialStore = credentialStore,
        )

        viewModel.onKavitaDisplayNameChanged("Home Kavita")
        viewModel.onKavitaBaseUrlChanged("https://kavita.example/")
        viewModel.onKavitaUsernameChanged("reader")
        viewModel.onKavitaPasswordChanged("secret")
        viewModel.connectKavitaServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Home Kavita", syncer.lastConnectRequest?.displayName)
        assertEquals("https://kavita.example", syncer.lastConnectRequest?.baseUrl)
        assertEquals("reader", syncer.lastConnectRequest?.username)
        assertEquals("secret", syncer.lastConnectRequest?.password)
        assertNull(syncer.lastSyncRequest)
        assertEquals("access-token", credentialStore.sessionFlow.value?.accessToken)
        assertEquals("refresh-token", credentialStore.sessionFlow.value?.refreshToken)
        assertEquals("api-key", credentialStore.sessionFlow.value?.apiKey)
        assertEquals(TopLevelDestination.Library, viewModel.uiState.topLevel)
        assertEquals(
            "Connected to Kavita. Tap Sync Kavita to import the library.",
            viewModel.uiState.kavitaConnection.message,
        )
        assertEquals(true, viewModel.uiState.kavitaConnection.hasStoredSession)
        assertEquals(false, viewModel.uiState.kavitaConnection.isError)
        assertEquals("", viewModel.uiState.kavitaConnection.password)
    }

    @Test
    fun `connect kavita with stored session runs full sync`() {
        val credentialStore = FakeKavitaCredentialStore(
            KavitaStoredSession(
                displayName = "Home Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
                apiKey = "saved-api-key",
            ),
        )
        val syncer = FakeKavitaCatalogSyncer(
            result = KavitaCatalogSyncResult(
                libraryCount = 2,
                itemCount = 12,
                supportedFormatCounts = mapOf(FormatType.CBZ to 12),
            ),
        )
        val viewModel = createViewModel(
            kavitaCatalogSyncer = syncer,
            kavitaCredentialStore = credentialStore,
        )
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.connectKavitaServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastConnectRequest)
        assertNull(syncer.lastSyncRequest?.password)
        assertEquals("saved-access-token", syncer.lastSyncRequest?.accessToken)
        assertEquals("saved-refresh-token", syncer.lastSyncRequest?.refreshToken)
        assertEquals("saved-api-key", syncer.lastSyncRequest?.apiKey)
        assertEquals(SourceType.KAVITA, viewModel.uiState.selectedSource)
        assertEquals("Kavita library synced.", viewModel.uiState.kavitaConnection.message)
    }

    @Test
    fun `refresh kavita requires password when stored session has no api key`() {
        val credentialStore = FakeKavitaCredentialStore(
            KavitaStoredSession(
                displayName = "Home Kavita",
                baseUrl = "https://kavita.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
                apiKey = null,
            ),
        )
        val syncer = FakeKavitaCatalogSyncer()
        val viewModel = createViewModel(
            kavitaCatalogSyncer = syncer,
            kavitaCredentialStore = credentialStore,
        )
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.connectKavitaServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastConnectRequest)
        assertNull(syncer.lastSyncRequest)
        assertEquals(
            "Re-enter the Kavita password once to refresh the API key for covers and reading.",
            viewModel.uiState.kavitaConnection.message,
        )
        assertEquals(true, viewModel.uiState.kavitaConnection.isError)
    }

    @Test
    fun `connect stump authenticates quickly and waits for explicit sync`() {
        val syncer = FakeStumpCatalogSyncer(
            connectionResult = StumpCatalogConnectionResult(
                libraryCount = 3,
                session = com.example.unifiedreader.data.stump.StumpSession(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
        )
        val credentialStore = FakeStumpCredentialStore()
        val viewModel = createViewModel(
            stumpCatalogSyncer = syncer,
            stumpCredentialStore = credentialStore,
        )

        viewModel.onStumpDisplayNameChanged("Home Stump")
        viewModel.onStumpBaseUrlChanged("https://stump.example/")
        viewModel.onStumpUsernameChanged("reader")
        viewModel.onStumpPasswordChanged("secret")
        viewModel.connectStumpServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Home Stump", syncer.lastConnectRequest?.displayName)
        assertEquals("https://stump.example", syncer.lastConnectRequest?.baseUrl)
        assertEquals("reader", syncer.lastConnectRequest?.username)
        assertEquals("secret", syncer.lastConnectRequest?.password)
        assertNull(syncer.lastSyncRequest)
        assertEquals("access-token", credentialStore.sessionFlow.value?.accessToken)
        assertEquals("refresh-token", credentialStore.sessionFlow.value?.refreshToken)
        assertEquals(
            "Connected to Stump. Tap Sync Stump to import the library.",
            viewModel.uiState.stumpConnection.message,
        )
        assertTrue(viewModel.uiState.stumpConnection.hasStoredSession)
        assertFalse(viewModel.uiState.stumpConnection.isError)
        assertEquals("", viewModel.uiState.stumpConnection.password)
    }

    @Test
    fun `refresh stump uses stored session and clears stump caches`() {
        val credentialStore = FakeStumpCredentialStore(
            StumpStoredSession(
                displayName = "Home Stump",
                baseUrl = "https://stump.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )
        val syncer = FakeStumpCatalogSyncer(
            result = StumpCatalogSyncResult(
                libraryCount = 2,
                itemCount = 7,
                supportedFormatCounts = mapOf(FormatType.CBZ to 7),
                session = com.example.unifiedreader.data.stump.StumpSession(
                    accessToken = "refreshed-access-token",
                    refreshToken = "refreshed-refresh-token",
                ),
            ),
        )
        val stumpCoverRepository = FakeStumpCoverRepository(
            coverUri = Uri.parse("file:///tmp/stump-cover.png"),
        )
        val pageCacheController = FakeComicPageSourceCacheController()
        val stumpItem = sampleItem(
            id = "stump-issue-1",
            title = "Stump Issue",
            format = FormatType.CBZ,
        ).copy(
            sourceType = SourceType.STUMP,
            libraryId = SourceType.STUMP.remoteLibraryId("stump-library"),
            remoteId = "stump-issue-1",
        )
        val viewModel = createViewModel(
            sampleItems = listOf(stumpItem),
            stumpCatalogSyncer = syncer,
            stumpCredentialStore = credentialStore,
            stumpCoverRepository = stumpCoverRepository,
            comicPageSourceCacheController = pageCacheController,
        )
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.ensureCoverCached(stumpItem)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("file:///tmp/stump-cover.png", viewModel.coverImageUrlFor(stumpItem))

        viewModel.refreshStumpLibrary()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastConnectRequest)
        assertNull(syncer.lastSyncRequest?.password)
        assertEquals("saved-access-token", syncer.lastSyncRequest?.accessToken)
        assertEquals("saved-refresh-token", syncer.lastSyncRequest?.refreshToken)
        assertEquals("refreshed-access-token", credentialStore.sessionFlow.value?.accessToken)
        assertEquals("refreshed-refresh-token", credentialStore.sessionFlow.value?.refreshToken)
        assertEquals(SourceType.STUMP, viewModel.uiState.selectedSource)
        assertEquals("Stump library synced.", viewModel.uiState.stumpConnection.message)
        assertEquals(listOf(SourceType.STUMP), pageCacheController.clearedSourceTypes)
        assertEquals(1, stumpCoverRepository.clearCachedCoversCalls)
        assertNull(viewModel.coverImageUrlFor(stumpItem))
    }

    @Test
    fun `connect grimmory syncs catalog and switches to grimmory library`() {
        val syncer = FakeGrimmoryCatalogSyncer(
            result = GrimmoryCatalogSyncResult(
                libraryCount = 2,
                itemCount = 12,
                supportedFormatCounts = mapOf(FormatType.EPUB to 12),
                session = GrimmorySession(
                    accessToken = "access-token",
                    refreshToken = "refresh-token",
                ),
            ),
        )
        val credentialStore = FakeGrimmoryCredentialStore()
        val viewModel = createViewModel(
            grimmoryCatalogSyncer = syncer,
            grimmoryCredentialStore = credentialStore,
        )

        viewModel.onGrimmoryDisplayNameChanged("Home Grimmory")
        viewModel.onGrimmoryBaseUrlChanged("https://grimmory.example/")
        viewModel.onGrimmoryUsernameChanged("reader")
        viewModel.onGrimmoryPasswordChanged("secret")
        viewModel.connectGrimmoryServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Home Grimmory", syncer.lastRequest?.displayName)
        assertEquals("https://grimmory.example", syncer.lastRequest?.baseUrl)
        assertEquals("reader", syncer.lastRequest?.username)
        assertEquals("secret", syncer.lastRequest?.password)
        assertNull(syncer.lastRequest?.accessToken)
        assertEquals("access-token", credentialStore.sessionFlow.value?.accessToken)
        assertEquals("refresh-token", credentialStore.sessionFlow.value?.refreshToken)
        assertEquals(TopLevelDestination.Library, viewModel.uiState.topLevel)
        assertEquals(SourceType.GRIMMORY, viewModel.uiState.selectedSource)
        assertEquals("", viewModel.uiState.grimmoryConnection.password)
        assertEquals(true, viewModel.uiState.grimmoryConnection.hasStoredSession)
        assertEquals(false, viewModel.uiState.grimmoryConnection.isError)
    }

    @Test
    fun `connect grimmory reuses saved token when password is blank`() {
        val credentialStore = FakeGrimmoryCredentialStore(
            GrimmoryStoredSession(
                displayName = "Home Grimmory",
                baseUrl = "https://grimmory.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )
        val syncer = FakeGrimmoryCatalogSyncer(
            result = GrimmoryCatalogSyncResult(
                libraryCount = 1,
                itemCount = 4,
                supportedFormatCounts = mapOf(FormatType.EPUB to 4),
            ),
        )
        val viewModel = createViewModel(
            grimmoryCatalogSyncer = syncer,
            grimmoryCredentialStore = credentialStore,
        )

        viewModel.connectGrimmoryServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastRequest?.password)
        assertEquals("saved-access-token", syncer.lastRequest?.accessToken)
        assertEquals("saved-refresh-token", syncer.lastRequest?.refreshToken)
        assertEquals("Home Grimmory", credentialStore.sessionFlow.value?.displayName)
        assertEquals("Connected to Grimmory.", viewModel.uiState.grimmoryConnection.message)
        assertEquals(true, viewModel.uiState.grimmoryConnection.hasStoredSession)
    }

    @Test
    fun `refresh grimmory library uses stored session and keeps source on grimmory`() {
        val credentialStore = FakeGrimmoryCredentialStore(
            GrimmoryStoredSession(
                displayName = "Home Grimmory",
                baseUrl = "https://grimmory.example",
                username = "reader",
                accessToken = "saved-access-token",
                refreshToken = "saved-refresh-token",
            ),
        )
        val syncer = FakeGrimmoryCatalogSyncer(
            result = GrimmoryCatalogSyncResult(
                libraryCount = 1,
                itemCount = 9,
                supportedFormatCounts = mapOf(FormatType.EPUB to 9),
            ),
        )
        val viewModel = createViewModel(
            grimmoryCatalogSyncer = syncer,
            grimmoryCredentialStore = credentialStore,
        )

        viewModel.refreshGrimmoryLibrary()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastRequest?.password)
        assertEquals("saved-access-token", syncer.lastRequest?.accessToken)
        assertEquals("saved-refresh-token", syncer.lastRequest?.refreshToken)
        assertEquals(SourceType.GRIMMORY, viewModel.uiState.selectedSource)
        assertEquals("Grimmory library synced.", viewModel.uiState.grimmoryConnection.message)
    }

    @Test
    fun `connect grimmory validates server url before syncing`() {
        val syncer = FakeGrimmoryCatalogSyncer()
        val viewModel = createViewModel(
            grimmoryCatalogSyncer = syncer,
        )

        viewModel.onGrimmoryDisplayNameChanged("Home Grimmory")
        viewModel.onGrimmoryBaseUrlChanged("grimmory.example")
        viewModel.onGrimmoryUsernameChanged("reader")
        viewModel.onGrimmoryPasswordChanged("secret")
        viewModel.connectGrimmoryServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastRequest)
        assertEquals(true, viewModel.uiState.grimmoryConnection.isError)
        assertEquals(
            "Enter a server URL that starts with http:// or https://.",
            viewModel.uiState.grimmoryConnection.message,
        )
    }

    @Test
    fun `connect grimmory blocks public http server url`() {
        val syncer = FakeGrimmoryCatalogSyncer()
        val viewModel = createViewModel(
            grimmoryCatalogSyncer = syncer,
        )

        viewModel.onGrimmoryDisplayNameChanged("Home Grimmory")
        viewModel.onGrimmoryBaseUrlChanged("http://grimmory.example")
        viewModel.onGrimmoryUsernameChanged("reader")
        viewModel.onGrimmoryPasswordChanged("secret")
        viewModel.connectGrimmoryServer()
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(syncer.lastRequest)
        assertEquals(true, viewModel.uiState.grimmoryConnection.isError)
        assertEquals(
            LOCAL_HTTP_ONLY_MESSAGE,
            viewModel.uiState.grimmoryConnection.message,
        )
    }

    @Test
    fun `delete imported item removes it clears selection and clears reader state`() = runBlocking {
        val importedItem = sampleItem(
            id = "imported-cbz",
            title = "Imported Akira",
            format = FormatType.CBZ,
            isLocalImport = true,
        )
        val importedRepository = FakeLocalDocumentRepository(
            importedItems = mutableListOf(importedItem),
            pageCounts = mutableMapOf(importedItem.id to 12),
        )
        val readerSessionStore = FakeReaderSessionStore().apply {
            saveProgress(
                item = importedItem,
                locator = com.example.unifiedreader.feature.reader.core.ReaderLocator(
                    locatorType = LocatorType.PAGE,
                    page = 3,
                ),
                pageCount = 12,
            )
        }
        val viewModel = createViewModel(
            importedRepository = importedRepository,
            readerSessionStore = readerSessionStore,
        )
        viewModel.onItemSelected(importedItem)

        viewModel.deleteImportedItem(importedItem)

        assertTrue(viewModel.uiState.items.none { it.id == importedItem.id })
        assertNull(viewModel.uiState.selectedItemId)
        assertNull(viewModel.uiState.activeReader)
        assertNull(readerSessionStore.getProgress(importedItem))
        assertNull(readerSessionStore.getSession(importedItem))
    }

}
