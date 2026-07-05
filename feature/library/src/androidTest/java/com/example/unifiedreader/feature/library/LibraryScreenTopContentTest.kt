package com.example.unifiedreader.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTopContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyEmptyTopContentContractDoesNotLeaveDelayedGridShift() {
        composeRule.mainClock.autoAdvance = false
        val item = libraryItem(remoteId = "1", title = "Akira 1")
        var isLoading by mutableStateOf(true)
        var items by mutableStateOf(emptyList<ReadableItem>())

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.KAVITA,
                    browseContextKey = "top-content-ready-empty",
                    items = items,
                    viewMode = LibraryViewMode.Grid,
                    isLoadingSource = isLoading,
                    topContent = if (isLoading) {
                        {
                            Box(
                                modifier = Modifier
                                    .height(120.dp)
                                    .testTag(TopContentTag),
                            )
                        }
                    } else {
                        null
                    },
                    onItemSelected = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(TopContentTag).assertCountEquals(1)

        composeRule.runOnIdle {
            isLoading = false
            items = listOf(item)
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(TopContentTag).assertCountEquals(0)
        val beforeTop = composeRule.onNodeWithText("Akira 1")
            .getUnclippedBoundsInRoot()
            .top

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(TopContentTag).assertCountEquals(0)
        val afterTop = composeRule.onNodeWithText("Akira 1")
            .getUnclippedBoundsInRoot()
            .top
        assertEquals(beforeTop, afterTop)
    }
}

private const val TopContentTag = "library-screen-top-content"

private fun libraryItem(
    remoteId: String,
    title: String,
): ReadableItem = ReadableItem(
    id = "kavita:item:$remoteId",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:comics",
    remoteId = remoteId,
    title = title,
    format = FormatType.CBZ,
)
