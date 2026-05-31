package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibraryReadingStatus
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun `equal rating maps reuse cached derived state`() {
        val libraries = listOf(
            Library(
                id = SourceType.KAVITA.remoteLibraryId("k-comics"),
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val item = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
            seriesKey = "akira",
            title = "Akira 1",
        )
        val baseInput = LibraryDerivedStateInput(
            selectedSource = SourceType.KAVITA,
            libraries = libraries,
            items = listOf(item),
            selectedKavitaSeriesKey = item.libraryId + "::akira",
            progressByItemId = mapOf(
                item.id to ItemProgressSummary(
                    label = "Finished",
                    fraction = 1f,
                    isFinished = true,
                ),
            ),
            ratingByItemId = mapOf(item.id to 9),
        )

        val first = computer.compute(baseInput)
        val second = computer.compute(
            baseInput.copy(
                ratingByItemId = buildMap {
                    put(item.id, 9)
                },
            ),
        )

        assertSame(first, second)
    }

    @Test
    fun `ordered item scope flattens Kavita issues and preserves scope order`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val first = issue(
            id = "1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val second = issue(
            id = "2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 2",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = emptyList(),
                items = listOf(first, second),
                selectedLibrarySortMode = LibrarySortMode.TITLE,
                orderedItemScope = LibraryOrderedItemScope(
                    key = "reading-list",
                    itemIds = listOf(second.id, first.id),
                ),
            ),
        )

        assertEquals(listOf(second.id, first.id), derivedState.visibleItems.map(ReadableItem::id))
    }

    @Test
    fun `ordered item scope applies reading status filters without changing list order`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val first = issue(
            id = "1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val second = issue(
            id = "2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 2",
        )
        val finished = issue(
            id = "3",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 3",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = emptyList(),
                items = listOf(first, second, finished),
                selectedLibraryFilters = LibraryFilterState(
                    readingStatuses = setOf(LibraryReadingStatus.NOT_STARTED),
                ),
                progressByItemId = mapOf(
                    finished.id to ItemProgressSummary(
                        label = "Finished",
                        fraction = 1f,
                        isFinished = true,
                    ),
                ),
                orderedItemScope = LibraryOrderedItemScope(
                    key = "reading-list",
                    itemIds = listOf(second.id, finished.id, first.id),
                ),
            ),
        )

        assertEquals(listOf(second.id, first.id), derivedState.visibleItems.map(ReadableItem::id))
    }

    @Test
    fun `reading status filter treats partially read root series as in progress`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val issueOne = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val issueTwo = issue(
            id = "k-2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 2",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = listOf(
                    Library(
                        id = libraryId,
                        sourceType = SourceType.KAVITA,
                        title = "Kavita Comics",
                        contentType = LibraryContentType.COMICS,
                    ),
                ),
                items = listOf(issueOne, issueTwo),
                selectedLibraryFilters = LibraryFilterState(
                    readingStatuses = setOf(LibraryReadingStatus.IN_PROGRESS),
                ),
                progressByItemId = mapOf(
                    issueOne.id to ItemProgressSummary(
                        label = "50%",
                        fraction = 0.5f,
                        lastReadAtEpochMillis = 1_000L,
                    ),
                ),
            ),
        )

        assertEquals(listOf("Akira"), derivedState.visibleItems.map(ReadableItem::title))
    }

    @Test
    fun `download availability resolves series containers and raw shelf items`() {
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

        assertEquals(
            setOf("kavita:series:${issueOne.libraryId}::akira", issueTwo.id),
            derivedState.remoteDownloadActionAvailability.downloadableItemIds,
        )
        assertEquals(
            setOf("kavita:series:${issueOne.libraryId}::akira", issueOne.id),
            derivedState.remoteDownloadActionAvailability.deletableDownloadedItemIds,
        )
    }

    @Test
    fun `root browse does not synthesize aggregate series progress`() {
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
        val progressByItemId = mapOf(
            issueOne.id to ItemProgressSummary(
                label = "Continue",
                fraction = 0.4f,
                lastReadAtEpochMillis = 42L,
            ),
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = libraries,
                items = listOf(issueOne, issueTwo),
                selectedLibrarySortMode = LibrarySortMode.TITLE,
                progressByItemId = progressByItemId,
            ),
        )

        assertEquals(progressByItemId, derivedState.displayProgressByItemId)
        assertEquals(1, derivedState.visibleItems.size)
        assertNull(derivedState.displayProgressByItemId[derivedState.visibleItems.single().id])
    }

    @Test
    fun `root series containers expose read progress when every issue is finished`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val issueOne = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val issueTwo = issue(
            id = "k-2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 2",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = libraries,
                items = listOf(issueOne, issueTwo),
                selectedLibrarySortMode = LibrarySortMode.TITLE,
                progressByItemId = mapOf(
                    issueOne.id to ItemProgressSummary(
                        label = "Finished",
                        fraction = 1f,
                        lastReadAtEpochMillis = 10L,
                        isFinished = true,
                    ),
                    issueTwo.id to ItemProgressSummary(
                        label = "Finished",
                        fraction = 1f,
                        lastReadAtEpochMillis = 20L,
                        isFinished = true,
                    ),
                ),
            ),
        )

        val seriesProgress = derivedState.displayProgressByItemId["kavita:series:${libraryId}::akira"]
        assertTrue(seriesProgress?.isComplete == true)
        assertEquals(20L, seriesProgress?.lastReadAtEpochMillis)
    }

    @Test
    fun `stump root series containers expose read progress when every issue is finished`() {
        val libraryId = SourceType.STUMP.remoteLibraryId("s-comics")
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.STUMP,
                title = "Stump Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val issueOne = issue(
            id = "s-1",
            sourceType = SourceType.STUMP,
            libraryId = libraryId,
            seriesKey = "blame",
            title = "Blame 1",
        )
        val issueTwo = issue(
            id = "s-2",
            sourceType = SourceType.STUMP,
            libraryId = libraryId,
            seriesKey = "blame",
            title = "Blame 2",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.STUMP,
                libraries = libraries,
                items = listOf(issueOne, issueTwo),
                selectedLibrarySortMode = LibrarySortMode.TITLE,
                progressByItemId = mapOf(
                    issueOne.id to ItemProgressSummary(
                        label = "Finished",
                        fraction = 1f,
                        isFinished = true,
                    ),
                    issueTwo.id to ItemProgressSummary(
                        label = "Finished",
                        fraction = 1f,
                        isFinished = true,
                    ),
                ),
            ),
        )

        assertTrue(derivedState.displayProgressByItemId["stump:series:${libraryId}::blame"]?.isComplete == true)
    }

    @Test
    fun `root last read sort still follows issue activity for series containers`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val akiraIssue = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val blameIssue = issue(
            id = "k-2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "blame",
            title = "Blame 1",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = libraries,
                items = listOf(akiraIssue, blameIssue),
                selectedLibrarySortMode = LibrarySortMode.LAST_READ,
                progressByItemId = mapOf(
                    akiraIssue.id to ItemProgressSummary(
                        label = "Continue",
                        fraction = 0.1f,
                        lastReadAtEpochMillis = 10L,
                    ),
                    blameIssue.id to ItemProgressSummary(
                        label = "Continue",
                        fraction = 0.2f,
                        lastReadAtEpochMillis = 20L,
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("kavita:series:${libraryId}::blame", "kavita:series:${libraryId}::akira"),
            derivedState.visibleItems.map(ReadableItem::id),
        )
    }

    @Test
    fun `root date added sort puts newest series containers first`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val olderIssue = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        ).copy(addedAtEpochMillis = 100L)
        val newerIssue = issue(
            id = "k-2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "blame",
            title = "Blame 1",
        ).copy(addedAtEpochMillis = 300L)

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = libraries,
                items = listOf(olderIssue, newerIssue),
                selectedLibrarySortMode = LibrarySortMode.DATE_ADDED,
            ),
        )

        assertEquals(
            listOf("kavita:series:${libraryId}::blame", "kavita:series:${libraryId}::akira"),
            derivedState.visibleItems.map(ReadableItem::id),
        )
    }

    @Test
    fun `read rated items expose rating badge state`() {
        val item = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = SourceType.KAVITA.remoteLibraryId("k-comics"),
            seriesKey = "akira",
            title = "Akira 1",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = emptyList(),
                items = listOf(item),
                selectedKavitaSeriesKey = item.libraryId + "::akira",
                progressByItemId = mapOf(
                    item.id to ItemProgressSummary(
                        label = "Finished",
                        fraction = 1f,
                        isFinished = true,
                    ),
                ),
                ratingByItemId = mapOf(item.id to 9),
            ),
        )

        assertEquals("4.5", derivedState.displayRatingByItemId[item.id]?.label)
    }

    @Test
    fun `root series containers expose average rating from rated issues`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val libraries = listOf(
            Library(
                id = libraryId,
                sourceType = SourceType.KAVITA,
                title = "Kavita Comics",
                contentType = LibraryContentType.COMICS,
            ),
        )
        val issueOne = issue(
            id = "k-1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val issueTwo = issue(
            id = "k-2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 2",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = libraries,
                items = listOf(issueOne, issueTwo),
                ratingByItemId = mapOf(
                    issueOne.id to 8,
                    issueTwo.id to 10,
                ),
            ),
        )

        assertEquals(
            "Avg 4.5",
            derivedState.displayRatingByItemId["kavita:series:${libraryId}::akira"]?.label,
        )
    }

    @Test
    fun `collection scope shows matching Kavita series containers`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-comics")
        val first = issue(
            id = "1",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 1",
        )
        val second = issue(
            id = "2",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "akira",
            title = "Akira 2",
        )
        val other = issue(
            id = "3",
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            seriesKey = "blame",
            title = "Blame 1",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = emptyList(),
                items = listOf(first, second, other),
                selectedKavitaCollectionId = "kavita:collection:7",
                selectedKavitaCollectionSeriesKeys = setOf("$libraryId::akira"),
            ),
        )

        assertEquals(listOf("kavita:series:${libraryId}::akira"), derivedState.visibleItems.map(ReadableItem::id))
    }

    @Test
    fun `collection scope includes matching Kavita book items`() {
        val libraryId = SourceType.KAVITA.remoteLibraryId("k-books")
        val book = ReadableItem(
            id = SourceType.KAVITA.remoteItemId("book-1"),
            sourceType = SourceType.KAVITA,
            libraryId = libraryId,
            remoteId = "book-1",
            remoteFileType = "series-9",
            title = "Collected Novel",
            format = FormatType.EPUB,
        )
        val other = book.copy(
            id = SourceType.KAVITA.remoteItemId("book-2"),
            remoteId = "book-2",
            remoteFileType = "series-10",
            title = "Other Novel",
        )

        val derivedState = computer.compute(
            LibraryDerivedStateInput(
                selectedSource = SourceType.KAVITA,
                libraries = emptyList(),
                items = listOf(book, other),
                selectedKavitaCollectionId = "kavita:collection:9",
                selectedKavitaCollectionSeriesKeys = setOf("$libraryId::series-9"),
            ),
        )

        assertEquals(listOf(book.id), derivedState.visibleItems.map(ReadableItem::id))
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
