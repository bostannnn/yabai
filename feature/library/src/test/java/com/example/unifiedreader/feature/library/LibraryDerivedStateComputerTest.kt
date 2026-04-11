package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryDerivedStateComputerTest {
    private val computer = LibraryDerivedStateComputer()

    @Test
    fun `switching back to same source reuses cached derived state`() {
        val libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
            Library(
                id = SourceType.STUMP.remoteLibraryId("s-comics"),
                sourceType = SourceType.STUMP,
                title = "Stump Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val items = listOf(
            issue(
                id = "k-1",
                sourceType = SourceType.KAVITA,
                libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
                seriesKey = "akira",
                title = "Akira 1",
            ),
            issue(
                id = "s-1",
                sourceType = SourceType.STUMP,
                libraryId = SourceType.STUMP.remoteLibraryId("s-comics"),
                seriesKey = "berserk",
                title = "Berserk 1",
            ),
        )
        val baseInput = LibraryDerivedStateInput(
            selectedSource = SourceType.KAVITA,
            libraries = libraries,
            items = items,
            selectedLibraryFilters = LibraryFilterState(),
            selectedLibrarySearchQuery = "",
            selectedLibrarySortMode = LibrarySortMode.LAST_READ,
        )

        val first = computer.compute(baseInput)
        computer.compute(baseInput.copy(selectedSource = SourceType.STUMP))
        val second = computer.compute(baseInput)

        assertSame(first, second)
    }

    @Test
    fun `download availability resolves series containers without rescanning all items`() {
        val libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val issueOne = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
            seriesKey = "akira",
            title = "Akira 1",
        )
        val issueTwo = issue(
            id = "k-2",
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
            seriesKey = "akira",
            title = "Akira 2",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = libraries,
                items = listOf(issueOne, issueTwo),
                selectedLibrarySortMode = LibrarySortMode.TITLE,
                downloadedRemoteItemIds = setOf(issueOne.id),
            ),
        )

        assertEquals(setOf("kavita:series:${issueOne.libraryId}::akira"), derivedState.remoteDownloadActionAvailability.downloadableItemIds)
        assertEquals(setOf("kavita:series:${issueOne.libraryId}::akira"), derivedState.remoteDownloadActionAvailability.deletableDownloadedItemIds)
    }
}

private fun issue(
    id: String,
    sourceType: SourceType,
    libraryId: String,
    seriesKey: String,
    title: String,
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:$id",
    sourceType = sourceType,
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
