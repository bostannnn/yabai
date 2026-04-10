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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadPriority
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import com.example.unifiedreader.feature.reader.comics.ComicsSpreadModeKey
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.ComicsReaderSessionAdapter
import com.example.unifiedreader.feature.reader.comics.comicsReaderFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
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
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
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
        val pageSource = FlowTestComicPageSource(pageCount = 3)
        val localDocumentRepository = importedLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = createViewModel(
            importedRepository = localDocumentRepository,
            pageSource = pageSource,
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
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

        composeRule.onNodeWithTag(comicGestureSurfaceTag("spread-0-full"))
            .performTouchInput { swipeLeft() }
        composeRule.waitForText("2 / 3")

        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Imported Akira")

        composeRule.onNodeWithText("Imported Akira").performClick()
        composeRule.waitForText("2 / 3")
    }

    @Test
    fun imported_item_can_be_removed_from_overflow_menu() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val pageSource = FlowTestComicPageSource(pageCount = 3)
        val localDocumentRepository = importedLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = createViewModel(
            importedRepository = localDocumentRepository,
            pageSource = pageSource,
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
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
        composeRule.onNodeWithContentDescription("Back to library").performClick()
        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Imported Akira")
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Remove local file").performClick()
        composeRule.onNodeWithText("Remove").performClick()

        composeRule.waitForTextGone("Imported Akira")
        composeRule.waitForTextGone("Removed Imported Akira.")
    }

    @Test
    fun library_search_filters_cyrillic_catalog_items_from_toolbar() {
        val russianBook = ReadableItem(
            id = "russian-book",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            remoteId = "russian-book",
            title = "Некрополи Петербурга",
            format = FormatType.EPUB,
            coverUrl = null,
        )
        val englishBook = ReadableItem(
            id = "english-book",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            remoteId = "english-book",
            title = "Remote Book",
            format = FormatType.EPUB,
            coverUrl = null,
        )
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val viewModel = createViewModel(
            readableItemRepository = FakeReadableItemRepository(
                items = listOf(russianBook, englishBook),
            ),
            importedRepository = importedLocalDocumentRepository(
                importedItem = englishBook.copy(
                    id = "unused-import",
                    sourceType = SourceType.LOCAL,
                    isLocalImport = true,
                ),
                importUri = Uri.parse("content://imports/unused.cbz"),
                pageCount = 1,
            ),
            pageSource = FlowTestComicPageSource(pageCount = 1),
            readerSessionStore = readerSessionStore,
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {},
            )
        }

        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Некрополи Петербурга")
        composeRule.waitForText("Remote Book")

        composeRule.onNodeWithTag("library-search-field").performTextInput("петербурга")

        composeRule.waitForText("Некрополи Петербурга")
        composeRule.waitForTextGone("Remote Book")
    }

    @Test
    fun library_error_messages_render() {
        val importedItem = ReadableItem(
            id = "imported-akira",
            sourceType = SourceType.LOCAL,
            libraryId = "local-imports",
            isLocalImport = true,
            title = "Imported Akira",
            format = FormatType.CBZ,
            description = "Imported local CBZ with 3 pages.",
            coverUrl = null,
        )
        val importUri = Uri.parse("content://imports/akira.cbz")
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val localDocumentRepository = importedLocalDocumentRepository(
            importedItem = importedItem,
            importUri = importUri,
            pageCount = 3,
        )
        val viewModel = createViewModel(
            importedRepository = localDocumentRepository,
            pageSource = FlowTestComicPageSource(pageCount = 3),
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {
                    importDocumentIntoApp(
                        contentResolver = ApplicationProvider.getApplicationContext<Application>().contentResolver,
                        viewModel = viewModel,
                        uri = Uri.parse("content://imports/bad-file.bin"),
                    )
                },
            )
        }

        composeRule.onNodeWithContentDescription("Import local file").performClick()
        composeRule.waitForText("Unable to import that file as a supported local document.")
    }

    @Test
    fun library_view_mode_buttons_switch_between_grid_and_detailed_cards() {
        val item = ReadableItem(
            id = "catalog-book",
            sourceType = SourceType.KAVITA,
            libraryId = "k-comics",
            remoteId = "catalog-book",
            title = "Catalog Book",
            format = FormatType.EPUB,
            coverUrl = null,
        )
        val readerSessionStore = FakeReaderSessionStore()
        val comicsSessionAdapter = ComicsReaderSessionAdapter(readerSessionStore)
        val viewModel = createViewModel(
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            importedRepository = importedLocalDocumentRepository(
                importedItem = item.copy(
                    id = "unused-import",
                    sourceType = SourceType.LOCAL,
                    isLocalImport = true,
                ),
                importUri = Uri.parse("content://imports/unused.cbz"),
                pageCount = 1,
            ),
            pageSource = FlowTestComicPageSource(pageCount = 1),
            progressStore = comicsSessionAdapter,
            preferencesStore = comicsSessionAdapter,
            readerSessionStore = readerSessionStore,
        )

        composeRule.setContent {
            UnifiedReaderAppContent(
                viewModel = viewModel,
                onImportLocalFileClick = {},
            )
        }

        composeRule.onNodeWithTag("library-grid-card-catalog-book").fetchSemanticsNode()
        composeRule.waitForTextGone("Catalog Book")

        composeRule.onNodeWithContentDescription("Detailed view").performClick()
        composeRule.waitForText("Catalog Book")

        composeRule.onNodeWithContentDescription("Grid view").performClick()
        composeRule.onNodeWithTag("library-grid-card-catalog-book").fetchSemanticsNode()
        composeRule.waitForTextGone("Catalog Book")
    }
}

private fun importedLocalDocumentRepository(
    importedItem: ReadableItem,
    importUri: Uri,
    pageCount: Int,
): FakeLocalDocumentRepository = FakeLocalDocumentRepository(
    importResult = importedItem,
    pageCounts = mutableMapOf(importedItem.id to pageCount),
    expectedImportUri = importUri,
)

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

private class FlowTestComicPageSource(
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
            pageIndex = index,
            contentDescription = "${item.title} page ${index + 1}",
        )
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
        priority: ComicPageLoadPriority,
    ): ComicPageLoadResult = ComicPageLoadResult.Success(
        image = imagesById.getValue(page.id),
    )
}
