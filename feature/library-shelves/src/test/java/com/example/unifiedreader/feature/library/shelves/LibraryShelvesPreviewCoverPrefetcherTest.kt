package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryShelvesPreviewCoverPrefetcherTest {
    @Test
    fun `preview prefetch items are capped per section and deduped`() {
        val repeated = remoteItem(id = "repeat")
        val continueReading = listOf(
            repeated,
            remoteItem(id = "continue-2"),
            remoteItem(id = "continue-3"),
            remoteItem(id = "continue-4"),
            remoteItem(id = "continue-5"),
        ).mapIndexed { index, item ->
            ContinueReadingShelfItem(
                item = item,
                progress = progress(index.toFloat()),
            )
        }
        val nextInSeries = listOf(
            remoteItem(id = "next-1"),
            repeated,
            remoteItem(id = "next-3"),
            remoteItem(id = "next-4"),
            remoteItem(id = "next-5"),
        ).mapIndexed { index, item ->
            NextInSeriesShelfItem(
                item = item,
                currentItem = remoteItem(id = "current-$index"),
                lastActivityAtEpochMillis = index.toLong(),
            )
        }
        val recentlyFinished = listOf(
            remoteItem(id = "finished-1"),
            remoteItem(id = "finished-2"),
            remoteItem(id = "finished-3"),
            remoteItem(id = "finished-4"),
            remoteItem(id = "finished-5"),
        ).mapIndexed { index, item ->
            RecentlyFinishedShelfItem(
                item = item,
                progress = progress(index.toFloat()),
            )
        }

        val targets = shelfPreviewCoverPrefetchItems(
            state = LibraryShelvesState(
                continueReading = continueReading,
                nextInSeries = nextInSeries,
                recentlyFinished = recentlyFinished,
            ),
            coverImageUrlProvider = { item, _ -> item.coverUrl },
        )

        assertEquals(
            listOf(
                "repeat",
                "continue-2",
                "continue-3",
                "continue-4",
                "next-1",
                "next-3",
                "next-4",
                "finished-1",
                "finished-2",
                "finished-3",
                "finished-4",
            ),
            targets.map(ReadableItem::remoteId),
        )
    }

    @Test
    fun `preview prefetch items skip local but keep remote items before cached url exists`() {
        val localItem = ReadableItem(
            id = "local-item",
            sourceType = SourceType.LOCAL,
            libraryId = "local-library",
            title = "Local",
            format = FormatType.CBZ,
            coverUrl = "https://example.com/local.jpg",
        )
        val uncachedRemote = remoteItem(id = "blank", coverUrl = "")

        val targets = shelfPreviewCoverPrefetchItems(
            state = LibraryShelvesState(
                continueReading = listOf(
                    ContinueReadingShelfItem(localItem, progress(0.2f)),
                    ContinueReadingShelfItem(uncachedRemote, progress(0.3f)),
                ),
            ),
            coverImageUrlProvider = { item, _ -> item.coverUrl },
        )

        assertEquals(listOf("blank"), targets.map(ReadableItem::remoteId))
    }
}

private fun remoteItem(
    id: String,
    coverUrl: String = "https://example.com/$id.jpg",
): ReadableItem = ReadableItem(
    id = "kavita:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library",
    remoteId = id,
    title = id,
    format = FormatType.CBZ,
    coverUrl = coverUrl,
)

private fun progress(
    fraction: Float,
) = com.example.unifiedreader.feature.library.ItemProgressSummary(
    label = "Progress",
    fraction = fraction,
    lastReadAtEpochMillis = 10L,
)
