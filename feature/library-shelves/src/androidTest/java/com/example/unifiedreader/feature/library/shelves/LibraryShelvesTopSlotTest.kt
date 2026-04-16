package com.example.unifiedreader.feature.library.shelves

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryShelvesTopSlotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loading_top_slot_shows_placeholder() {
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
    }

    @Test
    fun continue_reading_preview_shows_issue_title_and_progress_detail() {
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

        composeRule.onNodeWithText("Chapter 7").assertExists()
        composeRule.onNodeWithText("30% · 3/10").assertExists()
    }

    @Test
    fun loading_state_with_retained_content_keeps_continue_reading_visible() {
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

        composeRule.onNodeWithText("Chapter 8").assertExists()
        composeRule.onNodeWithText("40% · 4/10").assertExists()
    }

    @Test
    fun remount_while_loading_with_content_keeps_continue_reading_visible() {
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
        var isMounted by mutableStateOf(true)
        var slotState by mutableStateOf(contentState)

        composeRule.setContent {
            MaterialTheme {
                if (isMounted) {
                    LibraryShelvesTopSlot(
                        browseContextKey = "kavita:root",
                        shouldReserveSlot = true,
                        state = slotState,
                        coverImageUrlProvider = { _, _ -> null },
                        onCoverVisible = { _, _ -> },
                        onItemSelected = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText("Chapter 9").assertExists()
        composeRule.onNodeWithText("50% · 5/10").assertExists()

        composeRule.runOnIdle {
            slotState = contentState.asLoading()
        }

        composeRule.onNodeWithText("Chapter 9").assertExists()
        composeRule.onNodeWithText("50% · 5/10").assertExists()

        composeRule.runOnIdle {
            isMounted = false
        }

        composeRule.runOnIdle {
            isMounted = true
        }

        composeRule.onNodeWithText("Chapter 9").assertExists()
        composeRule.onNodeWithText("50% · 5/10").assertExists()
        composeRule.onAllNodesWithTag(LibraryShelvesSlotPlaceholderTag).assertCountEquals(0)
    }
}

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
