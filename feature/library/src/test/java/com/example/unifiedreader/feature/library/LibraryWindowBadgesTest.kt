package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [computeLibraryWindowBadges] must produce, for the grid roots in a window, the same badge state the
 * full [LibraryDerivedStateComputer] recompute produces — otherwise overlaying the window badges onto
 * the (late) recompute maps would flip a badge when the recompute lands. Fed the exact roots / members
 * the computer derives (via [LibrarySourceIndex]) so the only thing under test is the window function's
 * composition of the shared rollups.
 */
class LibraryWindowBadgesTest {
    private val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
    private val libraries = listOf(
        Library(
            id = libraryId,
            sourceType = SourceType.KAVITA,
            title = "Kavita Comics",
            contentType = LibraryContentType.COMICS,
        ),
    )

    // "akira": both issues finished + rated -> Finished container progress + average rating badge.
    private val akiraOne = issue("k-1", "akira", "Akira 1")
    private val akiraTwo = issue("k-2", "akira", "Akira 2")

    // "blame": issue 1 in progress, issue 2 unread -> no completed-series progress, no rating badge.
    private val blameOne = issue("k-3", "blame", "Blame 1")
    private val blameTwo = issue("k-4", "blame", "Blame 2")

    private val items = listOf(akiraOne, akiraTwo, blameOne, blameTwo)

    private val progressByItemId = mapOf(
        akiraOne.id to ItemProgressSummary(label = "Finished", fraction = 1f, lastReadAtEpochMillis = 10L, isFinished = true),
        akiraTwo.id to ItemProgressSummary(label = "Finished", fraction = 1f, lastReadAtEpochMillis = 20L, isFinished = true),
        blameOne.id to ItemProgressSummary(label = "50%", fraction = 0.5f, lastReadAtEpochMillis = 30L),
    )
    private val ratingByItemId = mapOf(akiraOne.id to 8, akiraTwo.id to 10)
    private val downloadedItemIds = setOf(akiraOne.id)

    private fun computerInput() = LibraryDerivedStateInput(
        selectedSource = SourceType.KAVITA,
        libraries = libraries,
        items = items,
        selectedLibrarySortMode = LibrarySortMode.LAST_READ,
        progressByItemId = progressByItemId,
        ratingByItemId = ratingByItemId,
        downloadedRemoteItemIds = downloadedItemIds,
    )

    private fun windowBadges(): LibraryWindowBadges {
        val index = LibrarySourceIndex.build(libraries = libraries, items = items)
        val rootItems = index.sourceItems(SourceType.KAVITA, selectedKavitaSeriesKey = null, selectedStumpSeriesKey = null)
        val issuesBySeriesKey = index.issueGroupsFor(SourceType.KAVITA)
        return computeLibraryWindowBadges(
            source = SourceType.KAVITA,
            rootItems = rootItems,
            issuesBySeriesKey = issuesBySeriesKey,
            memberItems = issuesBySeriesKey.values.flatten(),
            progressByItemId = progressByItemId,
            ratingByItemId = ratingByItemId,
            downloadedItemIds = downloadedItemIds,
            locallyAvailableItemIds = downloadedItemIds,
            downloadingItemIds = emptySet(),
        )
    }

    @Test
    fun `window badges match the recompute for every grid root`() {
        val derived = LibraryDerivedStateComputer().compute(computerInput())
        val window = windowBadges()
        val rootIds = derived.sourceItems.map(ReadableItem::id).toSet()
        assertTrue("expected at least one series container root", rootIds.isNotEmpty())

        assertEquals(
            derived.displayProgressByItemId.filterKeys { it in rootIds },
            window.displayProgressByItemId.filterKeys { it in rootIds },
        )
        assertEquals(
            derived.displayRatingByItemId.filterKeys { it in rootIds },
            window.displayRatingByItemId.filterKeys { it in rootIds },
        )
        // Download availability for the grid roots (the computer additionally evaluates raw issue ids,
        // which the grid never renders, so compare the root-scoped entries).
        assertEquals(
            derived.remoteDownloadActionAvailability.downloadableItemIds.filter { it in rootIds }.toSet(),
            window.downloadActionAvailability.downloadableItemIds,
        )
        assertEquals(
            derived.remoteDownloadActionAvailability.deletableDownloadedItemIds.filter { it in rootIds }.toSet(),
            window.downloadActionAvailability.deletableDownloadedItemIds,
        )
    }

    @Test
    fun `finished series container shows finished progress and average rating, partial one shows neither`() {
        val window = windowBadges()
        val akira = "kavita:series:$libraryId::akira"
        val blame = "kavita:series:$libraryId::blame"

        assertTrue(window.displayProgressByItemId[akira]?.isComplete == true)
        assertEquals(20L, window.displayProgressByItemId[akira]?.lastReadAtEpochMillis)
        assertEquals("Avg 4.5", window.displayRatingByItemId[akira]?.label)

        assertNull(window.displayProgressByItemId[blame])
        assertNull(window.displayRatingByItemId[blame])
    }

    private fun issue(
        id: String,
        seriesKey: String,
        title: String,
    ): ReadableItem = ReadableItem(
        id = "kavita:item:$id",
        sourceType = SourceType.KAVITA,
        libraryId = libraryId,
        remoteId = id,
        remoteFileType = seriesKey,
        title = title,
        format = FormatType.CBZ,
        comicMetadata = ComicMetadata(
            series = seriesKey.replaceFirstChar(Char::titlecase),
            issueTitle = title,
        ),
    )
}
