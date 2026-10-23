package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryShelvesTopSlotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_top_slot_keeps_placeholder_stable() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                LibraryShelvesTopSlot(
                    browseContextKey = "kavita:root",
                    shouldReserveSlot = true,
                    state = LibraryShelvesState.placeholderLoading(),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryShelvesSlotPlaceholderTag).assertExists()

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LibraryShelvesSlotPlaceholderTag).assertExists()
    }

    @Test
    fun reading_card_shows_issue_title_and_page() {
        composeRule.setContent {
            MaterialTheme {
                LibraryShelvesZone(
                    state = LibraryShelvesState(
                        continueReading = listOf(
                            ContinueReadingShelfItem(
                                item = comicItem(
                                    remoteId = "chapter-7",
                                    issueTitle = "Chapter 7",
                                    title = "Akira Chapter 7",
                                ),
                                progress = ItemProgressSummary(
                                    label = "Resume on page 3",
                                    fraction = 0.3f,
                                    currentPage = 3,
                                    pageCount = 10,
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

        // One book in progress: it is the reading card, with its page and percentage.
        composeRule.onNodeWithText("Chapter 7").assertExists()
        composeRule.onNodeWithText("Page 3 of 10").assertExists()
        composeRule.onNodeWithText("30%").assertExists()
    }

    @Test
    fun loading_state_with_content_shows_placeholder_instead_of_ready_shelves() {
        composeRule.setContent {
            MaterialTheme {
                LibraryShelvesTopSlot(
                    browseContextKey = "kavita:root",
                    shouldReserveSlot = true,
                    state = LibraryShelvesState(
                        continueReading = listOf(
                            ContinueReadingShelfItem(
                                item = comicItem(
                                    remoteId = "chapter-8",
                                    issueTitle = "Chapter 8",
                                    title = "Akira Chapter 8",
                                ),
                                progress = ItemProgressSummary(
                                    label = "Resume on page 4",
                                    fraction = 0.4f,
                                    currentPage = 4,
                                    pageCount = 10,
                                    lastReadAtEpochMillis = 125L,
                                ),
                            ),
                        ),
                        isLoading = true,
                    ),
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryShelvesSlotPlaceholderTag).assertExists()
        composeRule.onAllNodesWithText("Chapter 8").assertCountEquals(0)
        composeRule.onAllNodesWithText("40% · 4/10").assertCountEquals(0)
    }

    @Test
    fun empty_ready_top_slot_does_not_move_grid_content_after_delay() {
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            MaterialTheme {
                Column {
                    LibraryShelvesTopSlot(
                        browseContextKey = "kavita:root",
                        shouldReserveSlot = true,
                        state = LibraryShelvesState(),
                        coverImageUrlProvider = { _, _ -> null },
                        onCoverVisible = { _, _ -> },
                        onItemSelected = {},
                    )
                    Text(
                        text = "Grid item",
                        modifier = Modifier.testTag(GridContentTag),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(LibraryShelvesSlotPlaceholderTag).assertCountEquals(0)
        val beforeTop = composeRule.onNodeWithTag(GridContentTag)
            .getUnclippedBoundsInRoot()
            .top

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(LibraryShelvesSlotPlaceholderTag).assertCountEquals(0)
        val afterTop = composeRule.onNodeWithTag(GridContentTag)
            .getUnclippedBoundsInRoot()
            .top
        assertEquals(beforeTop, afterTop)
    }

    @Test
    fun loading_transition_does_not_show_stale_shelves_for_new_browse_context() {
        val contentState = LibraryShelvesState(
            continueReading = listOf(
                ContinueReadingShelfItem(
                    item = comicItem(
                        remoteId = "chapter-9",
                        issueTitle = "Chapter 9",
                        title = "Akira Chapter 9",
                    ),
                    progress = ItemProgressSummary(
                        label = "Resume on page 5",
                        fraction = 0.5f,
                        currentPage = 5,
                        pageCount = 10,
                        lastReadAtEpochMillis = 150L,
                    ),
                ),
            ),
        )
        var browseContextKey by mutableStateOf("kavita:root")
        var slotState by mutableStateOf(contentState)

        composeRule.setContent {
            MaterialTheme {
                LibraryShelvesTopSlot(
                    browseContextKey = browseContextKey,
                    shouldReserveSlot = true,
                    state = slotState,
                    coverImageUrlProvider = { _, _ -> null },
                    onCoverVisible = { _, _ -> },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Chapter 9").assertExists()
        composeRule.onNodeWithText("Page 5 of 10").assertExists()

        composeRule.runOnIdle {
            browseContextKey = "kavita:collection:42"
            slotState = contentState.asLoading()
        }

        composeRule.onNodeWithTag(LibraryShelvesSlotPlaceholderTag).assertExists()
        composeRule.onAllNodesWithText("Chapter 9").assertCountEquals(0)
        composeRule.onAllNodesWithText("Page 5 of 10").assertCountEquals(0)
    }
}

private const val GridContentTag = "library-grid-content"

private fun comicItem(
    remoteId: String,
    issueTitle: String,
    title: String,
): ReadableItem = ReadableItem(
    id = "kavita:item:$remoteId",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:comics",
    remoteId = remoteId,
    title = title,
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Akira",
        issueTitle = issueTitle,
        issueNumber = remoteId,
    ),
)
