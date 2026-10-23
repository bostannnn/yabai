package com.example.unifiedreader.feature.library.shelves

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryShelvesZoneActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readingCardDetailsOpensTheBookPage() {
        val item = comicItem("continue-1")
        var opened: ReadableItem? = null

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
                    itemActionContext = LibraryItemActionContext(onOpenItemPage = { opened = it }),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("CONTINUE READING").assertExists()
        composeRule.onNodeWithText("Akira").assertExists()
        // The card's book is not repeated in a tab, so with nothing else there are no tabs.
        assertTrue(composeRule.onAllNodesWithText("In progress").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Details").assertExists().performClick()
        composeRule.runOnIdle { assertEquals(item, opened) }
    }

    @Test
    fun shelfTabsSwitchTheStrip() {
        val finishedItem = comicItem("finished-1")
        composeRule.setContent {
            MaterialTheme {
                LibraryShelvesZone(
                    state = LibraryShelvesState(
                        continueReading = listOf(
                            ContinueReadingShelfItem(item = comicItem("card"), progress = progress()),
                            ContinueReadingShelfItem(item = comicItem("strip"), progress = progress()),
                        ),
                        recentlyFinished = listOf(
                            RecentlyFinishedShelfItem(
                                item = finishedItem,
                                progress = ItemProgressSummary(
                                    label = "Finished",
                                    fraction = 1f,
                                    isFinished = true,
                                    lastReadAtEpochMillis = 100L,
                                ),
                            ),
                        ),
                    ),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Issue strip").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Issue finished-1").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("Finished").assertExists().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Issue finished-1").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Issue strip").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun shelfItemsBrowserMoreActionsOpensTheBookPage() {
        val item = comicItem("continue-2")
        var opened: ReadableItem? = null

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
                    itemActionContext = LibraryItemActionContext(onOpenItemPage = { opened = it }),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onBack = {},
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("In progress").assertExists()
        composeRule.onNodeWithContentDescription("More actions").assertExists().performClick()
        composeRule.runOnIdle { assertEquals(item, opened) }
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
        assertTrue(composeRule.onAllNodesWithText("Search shelf").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription("Search shelf").assertExists()
        composeRule.onNodeWithText("5 items").assertExists()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("See all").assertExists()
    }

    @Test
    fun shelfItemsBrowserExternalSearchFiltersWithoutEmbeddedSearch() {
        val firstItem = comicItem("alpha")
        val secondItem = comicItem("metro")

        composeRule.setContent {
            MaterialTheme {
                LibraryShelfItemsBrowser(
                    browser = LibraryShelfBrowserState.ContinueReading(
                        items = listOf(
                            ContinueReadingShelfItem(
                                item = firstItem,
                                progress = progress(),
                            ),
                            ContinueReadingShelfItem(
                                item = secondItem,
                                progress = progress(),
                            ),
                        ),
                    ),
                    itemActionContext = LibraryItemActionContext(),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onBack = {},
                    onItemSelected = {},
                    searchQuery = "metro",
                    onSearchQueryChanged = {},
                    showEmbeddedTopBar = false,
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Search shelf").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("Search shelf").fetchSemanticsNodes().isEmpty())
        // Entries show the series ("Akira") over the issue line; the query matched the title of "metro" only.
        assertTrue(composeRule.onAllNodesWithText("Issue alpha").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Issue metro").assertExists()
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
