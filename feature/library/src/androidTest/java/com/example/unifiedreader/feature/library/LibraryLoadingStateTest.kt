package com.example.unifiedreader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.SourceType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryLoadingStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun grid_library_loading_state_uses_grid_skeleton() {
        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "kavita:root",
                    items = emptyList(),
                    viewMode = LibraryViewMode.Grid,
                    isLoadingSource = true,
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryGridLoadingTag).assertExists()
    }

    @Test
    fun detailed_library_loading_state_uses_detailed_skeleton() {
        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "kavita:root",
                    items = emptyList(),
                    viewMode = LibraryViewMode.Detailed,
                    isLoadingSource = true,
                    onItemSelected = {},
                )
            }
        }

        composeRule.onNodeWithTag(LibraryDetailedLoadingTag).assertExists()
    }
}
