package com.example.unifiedreader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.SourceConnectivityState
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTopBarSearchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchModeSupportsOpenClearAndClose() {
        val queryState = mutableStateOf("")

        composeRule.setContent {
            LibraryTopBarUnderTest(
                searchQuery = queryState.value,
                onSearchQueryChanged = { queryState.value = it },
            )
        }

        composeRule.onNodeWithTag("library-search-field").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Search library").assertExists().performClick()
        composeRule.onNodeWithContentDescription("Close search").assertExists()

        composeRule.onNodeWithTag("library-search-field").assertExists().performTextInput("akira")
        composeRule.runOnIdle {
            assertEquals("akira", queryState.value)
        }

        composeRule.onNodeWithContentDescription("Clear search").performClick()
        composeRule.runOnIdle {
            assertEquals("", queryState.value)
        }
        composeRule.onNodeWithTag("library-search-field").assertExists()

        composeRule.onNodeWithContentDescription("Close search").performClick()
        composeRule.onNodeWithTag("library-search-field").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Search library").assertExists()
    }

    @Test
    fun existingSourceSearchStartsInSearchMode() {
        val longSeriesTitle = "The Incredibly Long Library Series Title That Should Never Compete With Search"
        val queryState = mutableStateOf("volume")

        composeRule.setContent {
            LibraryTopBarUnderTest(
                searchQuery = queryState.value,
                seriesTitle = longSeriesTitle,
                onSearchQueryChanged = { queryState.value = it },
            )
        }

        composeRule.onNodeWithTag("library-search-field").assertExists()
        composeRule.onNodeWithContentDescription("Search library").assertDoesNotExist()
        composeRule.onNodeWithText(longSeriesTitle).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Clear search").assertExists()

        composeRule.onNodeWithContentDescription("Close search").performClick()
        composeRule.runOnIdle {
            assertEquals("", queryState.value)
        }
        composeRule.onNodeWithTag("library-search-field").assertDoesNotExist()
        composeRule.onNodeWithText(longSeriesTitle).assertExists()
    }
}

@Composable
private fun LibraryTopBarUnderTest(
    searchQuery: String,
    seriesTitle: String? = null,
    onSearchQueryChanged: (String) -> Unit,
) {
    MaterialTheme {
        LibraryTopBar(
            selectedSource = SourceType.LOCAL,
            sourceConnectivity = SourceConnectivityState.Unknown,
            searchQuery = searchQuery,
            seriesTitle = seriesTitle,
            onSourceConnectivityClick = {},
            onDrawerClick = {},
            onSearchQueryChanged = onSearchQueryChanged,
            downloadBadgeCount = 0,
            onDownloadsClick = {},
            onImportLocalFileClick = {},
            onLeaveSeries = {},
        )
    }
}
