package com.example.unifiedreader.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCoverPrefetchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * The prefetcher reads keys when the visible covers change, not when a key alone changes (key
     * changes come from `RemoteCoverController`'s reset version, which is not observable state), so a
     * new key for a cover already signaled is fetched again on the next pass, here a catalog update
     * that adds an item.
     */
    @Test
    fun changed_prefetch_key_is_fetched_again_on_the_next_visible_pass() {
        val seriesItem = seriesItem("series-1")
        var coverPrefetchVersion = 1
        var items by mutableStateOf(listOf(seriesItem))
        val signaledItemIds = mutableListOf<String>()

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    selectedSource = SourceType.STUMP,
                    browseContextKey = "stump",
                    items = items,
                    viewMode = LibraryViewMode.Grid,
                    coverImageUrlProvider = { _, _ -> null },
                    coverPrefetchKeyProvider = { item, _ -> "remote-cover:stump:${item.id}:coverv=$coverPrefetchVersion" },
                    onCoverVisible = { item, _ -> signaledItemIds += item.id },
                    onItemSelected = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { signaledItemIds.size == 1 }
        assertEquals(listOf(seriesItem.id), signaledItemIds)

        composeRule.runOnUiThread {
            coverPrefetchVersion = 2
            items = listOf(seriesItem, seriesItem("series-2"))
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { signaledItemIds.size == 3 }
        assertEquals(
            listOf(seriesItem.id, seriesItem.id, "stump:series:series-2"),
            listOf(signaledItemIds[0]) + signaledItemIds.drop(1).sorted(),
        )
    }

    private fun seriesItem(remoteId: String) = ReadableItem(
        id = "stump:series:$remoteId",
        sourceType = SourceType.STUMP,
        libraryId = "stump:library:comics",
        remoteId = remoteId,
        remoteFileId = remoteId,
        title = "Series $remoteId",
        format = FormatType.CBZ,
        coverUrl = null,
    )
}
