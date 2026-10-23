package com.example.unifiedreader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenGridActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gridModeExposesVisibleActionsMenu() {
        val item = ReadableItem(
            id = "kavita:item:1",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:comics",
            remoteId = "1",
            title = "Akira 1",
            format = FormatType.CBZ,
        )

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "grid-actions",
                    items = listOf(item),
                    viewMode = LibraryViewMode.Grid,
                    progressByItemId = mapOf(
                        item.id to ItemProgressSummary(
                            label = "42%",
                            fraction = 0.42f,
                        ),
                    ),
                    downloadableItemIds = setOf(item.id),
                    onItemSelected = {},
                    onDownloadItem = {},
                )
            }
        }

        composeRule.onNodeWithText("Akira 1").assertExists()
        composeRule.onNodeWithText("42%").assertExists()
        composeRule.onNodeWithContentDescription("More actions").assertExists().performClick()
        composeRule.onNodeWithText("Download file").assertExists()
        composeRule.onNodeWithText("Mark as read").assertExists()
    }

    @Test
    fun gridModeShowsSeriesIssueCountUnderTheTitle() {
        val item = ReadableItem(
            id = "kavita:series:1",
            sourceType = SourceType.KAVITA,
            libraryId = "kavita:library:comics",
            remoteId = "series-1",
            title = "Akira",
            format = FormatType.CBZ,
            issueCount = 12,
        )

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "grid-issue-count",
                    items = listOf(item),
                    viewMode = LibraryViewMode.Grid,
                    sortMode = LibrarySortMode.ISSUE_COUNT,
                    onItemSelected = {},
                )
            }
        }

        // A series tile is drawn as a stack with its count on the meta line (step 8.3), not a badge.
        composeRule.onNodeWithText("Akira").assertExists()
        composeRule.onNodeWithText("12 issues").assertExists()
    }

    @Test
    fun alphabetNavigationHiddenDuringSearch() {
        val items = listOf(
            ReadableItem(
                id = "kavita:item:batman",
                sourceType = SourceType.KAVITA,
                libraryId = "kavita:library:comics",
                remoteId = "batman",
                title = "Batman",
                format = FormatType.CBZ,
            ),
            ReadableItem(
                id = "kavita:item:catwoman",
                sourceType = SourceType.KAVITA,
                libraryId = "kavita:library:comics",
                remoteId = "catwoman",
                title = "Catwoman",
                format = FormatType.CBZ,
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "grid-search-alphabet",
                    items = items,
                    viewMode = LibraryViewMode.Grid,
                    sortMode = LibrarySortMode.TITLE,
                    searchQuery = "bat",
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Jump to B").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Jump to C").assertDoesNotExist()
    }

    @Test
    fun alphabetNavigationShownWhenSearchIsBlank() {
        val items = listOf(
            ReadableItem(
                id = "kavita:item:batman",
                sourceType = SourceType.KAVITA,
                libraryId = "kavita:library:comics",
                remoteId = "batman",
                title = "Batman",
                format = FormatType.CBZ,
            ),
            ReadableItem(
                id = "kavita:item:catwoman",
                sourceType = SourceType.KAVITA,
                libraryId = "kavita:library:comics",
                remoteId = "catwoman",
                title = "Catwoman",
                format = FormatType.CBZ,
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "grid-blank-search-alphabet",
                    items = items,
                    viewMode = LibraryViewMode.Grid,
                    sortMode = LibrarySortMode.TITLE,
                    searchQuery = "",
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Jump to B").assertExists()
        composeRule.onNodeWithContentDescription("Jump to C").assertExists()
    }
}
