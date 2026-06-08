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
                    gridSizeOptions = listOf(
                        LibraryGridSizeOption(cellSizeDp = 140, columnCount = 5),
                        LibraryGridSizeOption(cellSizeDp = 180, columnCount = 4),
                        LibraryGridSizeOption(cellSizeDp = 240, columnCount = 3),
                    ),
                    fadeReadItems = false,
                    showSortOptions = true,
                    onSortModeChanged = {},
                    onGridCellSizeDpChanged = { selectedGridCellSizeDp = it },
                    onFadeReadItemsChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Browse: Last read · 180dp")
            .assertExists()
            .performClick()

        composeRule.onNodeWithText("Grid size").assertExists()
        composeRule.onNodeWithText("180dp cards · 4 cols").assertExists()
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
                    gridSizeOptions = listOf(
                        LibraryGridSizeOption(cellSizeDp = 180, columnCount = 4),
                    ),
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

        composeRule.onNodeWithText("Browse: Last read · 180dp")
            .assertExists()
            .performClick()
        composeRule.onNodeWithText("Number of issues")
            .assertExists()
            .performClick()

        assertEquals(LibrarySortMode.ISSUE_COUNT, selectedSortMode)
    }
}
