package com.example.unifiedreader.feature.library.shelves

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.library.LibrarySeriesContinuationResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryShelvesStateComputerTest {
    private val computer = LibraryShelvesStateComputer(
        seriesContinuationResolver = LibrarySeriesContinuationResolver(),
    )

    @Test
    fun `continue reading picks latest unfinished item for selected source`() {
        val older = issue(id = "older", issueNumber = "1")
        val newer = issue(id = "newer", issueNumber = "2")
        val otherSource = issue(id = "other", issueNumber = "3", sourceType = SourceType.STUMP)

        val state = computer.compute(
            LibraryShelvesInput(
                selectedSource = SourceType.KAVITA,
                items = listOf(older, newer, otherSource),
                progressByItemId = mapOf(
                    older.id to progress("Resume on page 2", fraction = 0.2f, lastReadAt = 100L),
                    newer.id to progress("Resume on page 5", fraction = 0.5f, lastReadAt = 200L),
                    otherSource.id to progress("Resume on page 8", fraction = 0.8f, lastReadAt = 300L),
                ),
            ),
        )

        assertEquals(newer.id, state.continueReading?.item?.id)
    }

    @Test
    fun `next in series returns one entry per series ordered by latest activity`() {
        val alphaOne = issue(id = "alpha-1", series = "Alpha", issueNumber = "1", seriesKey = "alpha")
        val alphaTwo = issue(id = "alpha-2", series = "Alpha", issueNumber = "2", seriesKey = "alpha")
        val betaOne = issue(id = "beta-1", series = "Beta", issueNumber = "1", seriesKey = "beta")
        val betaTwo = issue(id = "beta-2", series = "Beta", issueNumber = "2", seriesKey = "beta")

        val state = computer.compute(
            LibraryShelvesInput(
                selectedSource = SourceType.KAVITA,
                items = listOf(betaTwo, alphaTwo, betaOne, alphaOne),
                progressByItemId = mapOf(
                    alphaOne.id to progress("Finished", fraction = 1f, lastReadAt = 100L, isFinished = true),
                    betaOne.id to progress("Resume on page 10", fraction = 0.4f, lastReadAt = 200L),
                ),
            ),
        )

        assertEquals(listOf(betaTwo.id, alphaTwo.id), state.nextInSeries.map { item -> item.item.id })
        assertEquals(betaOne.id, state.nextInSeries.first().currentItem.id)
    }

    @Test
    fun `next in series skips an already started successor`() {
        val issueOne = issue(id = "1", issueNumber = "1", seriesKey = "gamma")
        val issueTwo = issue(id = "2", issueNumber = "2", seriesKey = "gamma")
        val issueThree = issue(id = "3", issueNumber = "3", seriesKey = "gamma")

        val state = computer.compute(
            LibraryShelvesInput(
                selectedSource = SourceType.KAVITA,
                items = listOf(issueThree, issueTwo, issueOne),
                progressByItemId = mapOf(
                    issueOne.id to progress("Finished", fraction = 1f, lastReadAt = 100L, isFinished = true),
                    issueTwo.id to progress("Resume on page 8", fraction = 0.3f, lastReadAt = 200L),
                ),
            ),
        )

        assertEquals(listOf(issueThree.id), state.nextInSeries.map { item -> item.item.id })
    }

    @Test
    fun `recently finished keeps latest completed items only`() {
        val finishedRecent = issue(id = "finish-recent", issueNumber = "7")
        val finishedOlder = issue(id = "finish-older", issueNumber = "3")
        val unfinished = issue(id = "unfinished", issueNumber = "9")

        val state = computer.compute(
            LibraryShelvesInput(
                selectedSource = SourceType.KAVITA,
                items = listOf(finishedOlder, unfinished, finishedRecent),
                progressByItemId = mapOf(
                    finishedOlder.id to progress("Finished", fraction = 1f, lastReadAt = 100L, isFinished = true),
                    unfinished.id to progress("Resume on page 6", fraction = 0.3f, lastReadAt = 300L),
                    finishedRecent.id to progress("Finished", fraction = 1f, lastReadAt = 400L, isFinished = true),
                ),
            ),
        )

        assertEquals(listOf(finishedRecent.id, finishedOlder.id), state.recentlyFinished.map { item -> item.item.id })
    }

    @Test
    fun `recently finished ignores completed items without activity timestamp`() {
        val finishedWithActivity = issue(id = "finish-active", issueNumber = "7")
        val finishedWithoutActivity = issue(id = "finish-inactive", issueNumber = "3")

        val state = computer.compute(
            LibraryShelvesInput(
                selectedSource = SourceType.KAVITA,
                items = listOf(finishedWithoutActivity, finishedWithActivity),
                progressByItemId = mapOf(
                    finishedWithoutActivity.id to progress(
                        "Finished",
                        fraction = 1f,
                        lastReadAt = 0L,
                        isFinished = true,
                    ),
                    finishedWithActivity.id to progress(
                        "Finished",
                        fraction = 1f,
                        lastReadAt = 400L,
                        isFinished = true,
                    ),
                ),
            ),
        )

        assertEquals(listOf(finishedWithActivity.id), state.recentlyFinished.map { item -> item.item.id })
    }

    @Test
    fun `next in series is empty when series is fully finished`() {
        val issueOne = issue(id = "1", issueNumber = "1", seriesKey = "omega")
        val issueTwo = issue(id = "2", issueNumber = "2", seriesKey = "omega")

        val state = computer.compute(
            LibraryShelvesInput(
                selectedSource = SourceType.KAVITA,
                items = listOf(issueOne, issueTwo),
                progressByItemId = mapOf(
                    issueOne.id to progress("Finished", fraction = 1f, lastReadAt = 100L, isFinished = true),
                    issueTwo.id to progress("Finished", fraction = 1f, lastReadAt = 120L, isFinished = true),
                ),
            ),
        )

        assertNull(state.continueReading)
        assertEquals(emptyList<NextInSeriesShelfItem>(), state.nextInSeries)
    }
}

private fun issue(
    id: String,
    issueNumber: String,
    sourceType: SourceType = SourceType.KAVITA,
    series: String = "Akira",
    seriesKey: String? = "akira",
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:$id",
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library",
    remoteId = id,
    remoteFileType = seriesKey,
    title = "$series $issueNumber",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = series,
        issueTitle = "Issue $issueNumber",
        issueNumber = issueNumber,
    ),
)

private fun progress(
    label: String,
    fraction: Float,
    lastReadAt: Long,
    isFinished: Boolean = false,
): ItemProgressSummary = ItemProgressSummary(
    label = label,
    fraction = fraction,
    lastReadAtEpochMillis = lastReadAt,
    isFinished = isFinished,
)
