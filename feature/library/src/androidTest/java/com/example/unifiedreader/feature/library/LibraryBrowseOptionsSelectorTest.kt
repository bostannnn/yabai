package com.example.unifiedreader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.LibrarySortMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBrowseOptionsSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun browseMenuShowsAndUpdatesGridCellSize() {
        var selectedGridCellSizeDp by mutableIntStateOf(defaultLibraryGridCellSizeDp)

        composeRule.setContent {
            MaterialTheme {
                LibraryBrowseOptionsSelector(
                    sortMode = LibrarySortMode.LAST_READ,
                    gridCellSizeDp = selectedGridCellSizeDp,
                    fadeReadItems = false,
                    showSortOptions = true,
                    onSortModeChanged = {},
                    onGridCellSizeDpChanged = { selectedGridCellSizeDp = it },
                    onFadeReadItemsChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Last read")
            .assertExists()
            .performClick()

        composeRule.onNodeWithText("Cover size").assertExists()
        composeRule.onNodeWithText("Medium").assertExists()
        composeRule.onNodeWithTag(LibraryGridSizeOptionTagPrefix + "140")
            .performClick()

        assertEquals(140, selectedGridCellSizeDp)
    }

    @Test
    fun browseMenuShowsIssueCountSortWhenAvailable() {
        var selectedSortMode by mutableStateOf(LibrarySortMode.LAST_READ)

        composeRule.setContent {
            MaterialTheme {
                LibraryBrowseOptionsSelector(
                    sortMode = selectedSortMode,
                    gridCellSizeDp = defaultLibraryGridCellSizeDp,
                    fadeReadItems = false,
                    showSortOptions = true,
                    availableSortModes = listOf(
                        LibrarySortMode.LAST_READ,
                        LibrarySortMode.ISSUE_COUNT,
                    ),
                    onSortModeChanged = { selectedSortMode = it },
                    onGridCellSizeDpChanged = {},
                    onFadeReadItemsChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Last read")
            .assertExists()
            .performClick()
        composeRule.onNodeWithText("Number of issues")
            .assertExists()
            .performClick()

        assertEquals(LibrarySortMode.ISSUE_COUNT, selectedSortMode)
    }

    @Test
    fun browseMenuShowsLockedSortLabel() {
        var selectedSortMode by mutableStateOf(LibrarySortMode.LAST_READ)

        composeRule.setContent {
            MaterialTheme {
                LibraryBrowseOptionsSelector(
                    sortMode = selectedSortMode,
                    gridCellSizeDp = defaultLibraryGridCellSizeDp,
                    fadeReadItems = false,
                    showSortOptions = true,
                    lockedSortLabel = "Manual order",
                    onSortModeChanged = { selectedSortMode = it },
                    onGridCellSizeDpChanged = {},
                    onFadeReadItemsChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Order")
            .assertExists()
            .performClick()

        composeRule.onNodeWithText("Manual order").assertExists()
        assertEquals(LibrarySortMode.LAST_READ, selectedSortMode)
    }

    @Test
    fun browseMenuTogglesFadeReadItems() {
        var fadeReadItems by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                LibraryBrowseOptionsSelector(
                    sortMode = LibrarySortMode.LAST_READ,
                    gridCellSizeDp = defaultLibraryGridCellSizeDp,
                    fadeReadItems = fadeReadItems,
                    showSortOptions = true,
                    onSortModeChanged = {},
                    onGridCellSizeDpChanged = {},
                    onFadeReadItemsChanged = { fadeReadItems = it },
                )
            }
        }

        composeRule.onNodeWithText("Last read")
            .assertExists()
            .performClick()
        composeRule.onNodeWithText("Fade read books")
            .assertExists()
            .performClick()

        assertEquals(true, fadeReadItems)
    }

    @Test
    fun genericBrowseMenuSelectsOptionById() {
        var selectedOption = ""

        composeRule.setContent {
            MaterialTheme {
                LibraryBrowseOptionsMenu(
                    label = "Custom order",
                    selected = false,
                    optionSections = listOf(
                        LibraryBrowseOptionSection(
                            id = "sort",
                            title = "Sort",
                            options = listOf(
                                LibraryBrowseOption(
                                    id = "shelf",
                                    label = "Custom order",
                                    selected = true,
                                ),
                                LibraryBrowseOption(
                                    id = "title",
                                    label = "Title",
                                    selected = false,
                                ),
                            ),
                        ),
                    ),
                    gridCellSizeDp = defaultLibraryGridCellSizeDp,
                    fadeReadItems = false,
                    showFadeReadItems = false,
                    onOptionSelected = { sectionId, optionId -> selectedOption = "$sectionId:$optionId" },
                    onGridCellSizeDpChanged = {},
                    onFadeReadItemsChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Custom order")
            .assertExists()
            .performClick()
        composeRule.onNodeWithText("Title")
            .assertExists()
            .performClick()

        assertEquals("sort:title", selectedOption)
    }
}
