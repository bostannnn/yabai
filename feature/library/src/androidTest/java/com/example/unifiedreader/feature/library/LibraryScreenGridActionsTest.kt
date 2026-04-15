package com.example.unifiedreader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
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
                    downloadableItemIds = setOf(item.id),
                    onItemSelected = {},
                    onDownloadItem = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("More actions").assertExists().performClick()
        composeRule.onNodeWithText("Download file").assertExists()
        composeRule.onNodeWithText("Mark as read").assertExists()
    }
}
