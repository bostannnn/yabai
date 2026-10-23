package com.example.unifiedreader.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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

    /** A new key for a cover already signaled is fetched again on the next pass, here a catalog update that adds an item. */
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

    /**
     * A cover reset or a catalog sync bumps the cover version and drops the requests in flight
     * without changing the items; [LocalLibraryCoverGeneration] makes the grid ask for the covers on
     * screen again.
     */
    @Test
    fun cover_generation_change_fetches_visible_covers_again_without_an_item_change() {
        val seriesItem = seriesItem("series-1")
        var coverGeneration by mutableStateOf(0L)
        // The version in the key lives outside Compose state, as `RemoteCoverController` keeps it.
        var coverVersion = 0
        val signaledItemIds = mutableListOf<String>()
        // Built once, so only the generation changes when the content recomposes.
        val items = listOf(seriesItem)
        val coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { _, _ -> null }
        val coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = { item, _ ->
            "remote-cover:stump:${item.id}:coverv=$coverVersion"
        }
        val onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit = { item, _ -> signaledItemIds += item.id }
        val onItemSelected: (ReadableItem) -> Unit = {}

        composeRule.setContent {
            CompositionLocalProvider(LocalLibraryCoverGeneration provides coverGeneration) {
                MaterialTheme {
                    LibraryScreen(
                        selectedSource = SourceType.STUMP,
                        browseContextKey = "stump",
                        items = items,
                        viewMode = LibraryViewMode.Grid,
                        coverImageUrlProvider = coverImageUrlProvider,
                        coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                        onCoverVisible = onCoverVisible,
                        onItemSelected = onItemSelected,
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { signaledItemIds.size == 1 }

        composeRule.runOnUiThread {
            coverVersion = 1
            coverGeneration = 1L
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { signaledItemIds.size == 2 }
        assertEquals(listOf(seriesItem.id, seriesItem.id), signaledItemIds)
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

