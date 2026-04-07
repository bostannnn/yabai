package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import com.example.unifiedreader.feature.reader.comics.ComicsSpreadModeKey
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicsReaderSessionAdapter
import com.example.unifiedreader.feature.reader.comics.comicsReaderFactory
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnifiedReaderAppFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun import_opens_reader_and_reopens_on_last_page() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            serverId = "local-kavita",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore(
            initialSessions = mapOf(
                importedItem.id to ReaderSessionSnapshot(
                    itemId = importedItem.id,
                    preferences = mapOf(
                        ComicsSpreadModeKey to ComicSpreadMode.Single.name,
                    ),
                ),
            ),
        )
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val pageSource = FakeComicPageSource(pageCount = 3)
        val localDocumentRepository = FakeLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = AppViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            appPreferencesStore = FakeAppPreferencesStore(),
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(),
            localDocumentRepository = localDocumentRepository,
            importedItemDeletionService = DefaultImportedItemDeletionService(
                localDocumentDataSource = localDocumentRepository,
                readerSessionStore = readerSessionStore,
            ),
            comicPageSource = pageSource,
            comicReaderProgressStore = comicsSessionAdapter,
            comicReaderPreferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
            readerFactories = listOf(
                comicsReaderFactory(
                    pageSource = pageSource,
                    progressStore = comicsSessionAdapter,
                    preferencesStore = comicsSessionAdapter,
                ),
            ),
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {
                    importDocumentIntoApp(
                        contentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver,
                        viewModel = viewModel,
                        uri = importUri,
                    )
                },
            )
        }

        composeRule.onNodeWithContentDescription("Import local file").performClick()
        composeRule.waitForText("1 / 3")

        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-0"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("2 / 3")

        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.waitForText("Imported Akira")
        composeRule.waitForText("Resume on page 2 of 3")

        composeRule.onNodeWithText("Imported Akira").performClick()
        composeRule.waitForText("2 / 3")
    }

    @Test
    fun imported_item_can_be_removed_from_overflow_menu() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            serverId = "local-kavita",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val pageSource = FakeComicPageSource(pageCount = 3)
        val localDocumentRepository = FakeLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = AppViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            appPreferencesStore = FakeAppPreferencesStore(),
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(),
            localDocumentRepository = localDocumentRepository,
            importedItemDeletionService = DefaultImportedItemDeletionService(
                localDocumentDataSource = localDocumentRepository,
                readerSessionStore = readerSessionStore,
            ),
            comicPageSource = pageSource,
            comicReaderProgressStore = comicsSessionAdapter,
            comicReaderPreferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
            readerFactories = listOf(
                comicsReaderFactory(
                    pageSource = pageSource,
                    progressStore = comicsSessionAdapter,
                    preferencesStore = comicsSessionAdapter,
                ),
            ),
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {
                    importDocumentIntoApp(
                        contentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver,
                        viewModel = viewModel,
                        uri = importUri,
                    )
                },
            )
        }

        composeRule.onNodeWithContentDescription("Import local file").performClick()
        composeRule.waitForText("Imported Akira")
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Remove imported file").performClick()
        composeRule.onNodeWithText("Remove").performClick()

        composeRule.waitForText("Removed Imported Akira.")
        composeRule.waitForTextGone("Imported Akira")
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForText(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.waitForTextGone(
    text: String,
    timeoutMillis: Long = 5_000,
) {
    waitUntil(timeoutMillis = timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
}

private fun comicGestureSurfaceTag(
    spreadId: String,
): String = "comic-gesture-surface-$spreadId"

private class FakeLibraryRepository : LibraryRepository {
    private val librariesState = MutableStateFlow(
        listOf(
        Library(
            id = "k-comics",
            serverId = "local-kavita",
            sourceType = SourceType.KAVITA,
            title = "Comics",
        ),
    )
    )

    override fun getLibraries(): List<Library> = librariesState.value

    override fun observeLibraries(): Flow<List<Library>> = librariesState
}

private class FakeReadableItemRepository : ReadableItemRepository {
    private val itemsState = MutableStateFlow(emptyList<ReadableItem>())

    override fun getItems(): List<ReadableItem> = itemsState.value

    override fun observeItems(): Flow<List<ReadableItem>> = itemsState
}

private class FakeAppPreferencesStore : AppPreferencesStore {
    private val state = MutableStateFlow(AppPreferences(themeMode = ThemeMode.Light))

    override val preferencesFlow: StateFlow<AppPreferences> = state

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        state.value = AppPreferences(themeMode = themeMode)
    }
}

private class FakeLocalDocumentRepository(
    private val importedItem: ReadableItem,
    private val importUri: Uri,
    private val pageCount: Int,
) : LocalDocumentDataSource {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val importedItemsState = MutableStateFlow(emptyList<ReadableItem>())
    override val importedItemsFlow: StateFlow<List<ReadableItem>> = importedItemsState

    override fun getImportedItems(): List<ReadableItem> = importedItemsState.value

    override suspend fun import(uri: Uri): ReadableItem? {
        if (uri != importUri) return null
        importedItemsState.value = listOf(importedItem)
        return importedItem
    }

    override suspend fun removeById(itemId: String): Boolean {
        if (itemId != importedItem.id) return false
        importedItemsState.value = emptyList()
        return true
    }

    override fun getPageCount(item: ReadableItem): Int = if (item.id == importedItem.id) pageCount else 0

    override fun getDocumentUri(item: ReadableItem): Uri? = if (item.id == importedItem.id) importUri else null
}

private class FakeComicPageSource(
    private val pageCount: Int,
) : ComicPageSource {
    private val imagesById = List(pageCount) { index ->
        "page-$index" to Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(
                when (index) {
                    0 -> android.graphics.Color.RED
                    1 -> android.graphics.Color.GREEN
                    else -> android.graphics.Color.BLUE
                },
            )
        }.asImageBitmap()
    }.toMap()

    override fun getPages(item: ReadableItem): List<ComicPageSpec> = List(pageCount) { index ->
        ComicPageSpec(
            id = "page-$index",
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(
        image = imagesById.getValue(page.id),
    )
}

private class FakeReaderSessionStore(
    initialSessions: Map<String, ReaderSessionSnapshot> = emptyMap(),
) : ReaderSessionStore {
    override val isHydratedFlow: StateFlow<Boolean> = MutableStateFlow(true)
    private val progressState = MutableStateFlow<Map<String, ReaderProgressSnapshot>>(emptyMap())
    override val progressByItemIdFlow: StateFlow<Map<String, ReaderProgressSnapshot>> = progressState
    private val sessionState = MutableStateFlow(initialSessions)
    override val sessionByItemIdFlow: StateFlow<Map<String, ReaderSessionSnapshot>> = sessionState

    override fun getProgress(item: ReadableItem): ReaderProgressSnapshot? = progressState.value[item.id]

    override fun saveProgress(
        item: ReadableItem,
        locator: ReaderLocator,
        isFinished: Boolean,
        syncState: SyncState,
        pageCount: Int?,
    ) {
        progressState.value = progressState.value + (
            item.id to ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = locator.locatorType,
                page = locator.page,
                cfi = locator.cfi,
                percent = locator.percent,
                isFinished = isFinished,
                syncState = syncState,
            )
        )
        if (pageCount != null) {
            updateSession(item = item, pageCount = pageCount)
        }
    }

    override fun getSession(item: ReadableItem): ReaderSessionSnapshot? = sessionState.value[item.id]

    override fun updateSession(
        item: ReadableItem,
        pageCount: Int?,
        preferences: Map<String, String>,
    ) {
        val current = sessionState.value[item.id] ?: ReaderSessionSnapshot(itemId = item.id)
        sessionState.value = sessionState.value + (
            item.id to current.copy(
                pageCount = pageCount ?: current.pageCount,
                preferences = current.preferences + preferences,
            )
        )
    }

    override fun clearItem(itemId: String) {
        progressState.value = progressState.value - itemId
        sessionState.value = sessionState.value - itemId
    }
}
