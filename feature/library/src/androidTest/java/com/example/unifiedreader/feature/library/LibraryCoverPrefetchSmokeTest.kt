package com.example.unifiedreader.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    @Test
    fun null_cover_remote_item_retries_prefetch_when_prefetch_key_changes() {
        val seriesItem = ReadableItem(
            id = "stump:series:series-1",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:comics",
            remoteId = "series-1",
            remoteFileId = "series-1",
            title = "Series 1",
            format = FormatType.CBZ,
            coverUrl = null,
        )
        var coverPrefetchKey by mutableStateOf("remote-cover:stump:stump:series:series-1:coverv=1")
        var coverVisibleCalls by mutableIntStateOf(0)

        composeRule.setContent {
            MaterialTheme {
                GridCoverCard(
                    item = seriesItem,
                    progress = null,
                    isDownloaded = false,
                    isDownloading = false,
                    coverImageUrlProvider = { null },
                    coverPrefetchKeyProvider = { coverPrefetchKey },
                    onCoverVisible = { coverVisibleCalls += 1 },
                    onClick = {},
                    onDeleteRequested = null,
                    onDownloadRequested = null,
                    onDeleteDownloadedRequested = null,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            coverVisibleCalls == 1
        }

        composeRule.runOnUiThread {
            coverPrefetchKey = "remote-cover:stump:stump:series:series-1:coverv=2"
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            coverVisibleCalls == 2
        }
        assertEquals(2, coverVisibleCalls)
    }
}
