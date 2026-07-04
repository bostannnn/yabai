package com.example.unifiedreader.feature.library.shelves

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryShelvesZoneActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shelfPreviewBodyWaitsBeforeShowingItemActions() {
        val item = comicItem("continue-1")
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                LibraryShelvesZone(
                    state = LibraryShelvesState(
                        continueReading = listOf(
                            ContinueReadingShelfItem(
                                item = item,
                                progress = progress(),
                            ),
                        ),
                    ),
                    itemActionContext = LibraryItemActionContext(
                        downloadableItemIds = setOf(item.id),
                        onDownloadItem = {},
                    ),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Continue Reading").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Akira").fetchSemanticsNodes().isEmpty())

        composeRule.mainClock.advanceTimeBy(220L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Akira").assertExists()
        composeRule.onNodeWithContentDescription("More actions").assertExists().performClick()
        composeRule.onNodeWithText("Download file").assertExists()
    }

    @Test
    fun shelfItemsBrowserRowsShowVisibleActionsButton() {
        val item = comicItem("continue-2")

        composeRule.setContent {
            MaterialTheme {
                LibraryShelfItemsBrowser(
                    browser = LibraryShelfBrowserState.ContinueReading(
                        items = listOf(
                            ContinueReadingShelfItem(
                                item = item,
                                progress = progress(),
                            ),
                        ),
                    ),
                    itemActionContext = LibraryItemActionContext(
                        downloadableItemIds = setOf(item.id),
                        onDownloadItem = {},
                    ),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onBack = {},
                    onItemSelected = {},
                    onDeleteImportedItem = {},
                )
            }
        }

        composeRule.onNodeWithText("Continue Reading").assertExists()
        composeRule.onNodeWithContentDescription("More actions").assertExists().performClick()
        composeRule.onNodeWithText("Download file").assertExists()
    }

    @Test
    fun seeAllOpensBrowserAndBackReturnsToShelves() {
        val shelfItems = (1..5).map { index ->
            ContinueReadingShelfItem(
                item = comicItem("continue-see-all-$index"),
                progress = progress(),
            )
        }

        composeRule.setContent {
            var activeShelfKind by remember { mutableStateOf<LibraryShelfBrowserKind?>(null) }
            val shelvesState = LibraryShelvesState(continueReading = shelfItems)
            val browserState = activeShelfKind?.let(shelvesState::browserState)

            MaterialTheme {
                if (browserState != null) {
                    LibraryShelfItemsBrowser(
                        browser = browserState,
                        itemActionContext = LibraryItemActionContext(),
                        coverImageUrlProvider = { _, _ -> null },
                        onCoverVisible = { _, _ -> },
                        onBack = { activeShelfKind = null },
                        onItemSelected = {},
                        onDeleteImportedItem = {},
                    )
                } else {
                    LibraryShelvesZone(
                        state = shelvesState,
                        itemActionContext = LibraryItemActionContext(),
                        coverImageUrlProvider = { _, _ -> null },
                        onCoverVisible = { _, _ -> },
                        onItemSelected = {},
                        onBrowseShelf = { shelfKind -> activeShelfKind = shelfKind },
                    )
                }
            }
        }

        composeRule.onNodeWithText("See all").assertExists().performClick()

        composeRule.onNodeWithContentDescription("Back").assertExists()
        composeRule.onNodeWithText("Search shelf").assertExists()
        composeRule.onNodeWithText("5 items").assertExists()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("See all").assertExists()
    }
}

private fun comicItem(
    remoteId: String,
): ReadableItem = ReadableItem(
    id = "kavita:item:$remoteId",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:comics",
    remoteId = remoteId,
    title = "Akira $remoteId",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Akira",
        issueTitle = "Issue $remoteId",
        issueNumber = remoteId,
    ),
)

private fun progress(): ItemProgressSummary = ItemProgressSummary(
    label = "Resume on page 3",
    fraction = 0.3f,
    currentPage = 3,
    pageCount = 10,
    lastReadAtEpochMillis = 100L,
)
