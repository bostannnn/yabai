package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySeriesContinuationResolverTest {
    private val resolver = LibrarySeriesContinuationResolver()

    @Test
    fun `next unread after current issue uses canonical issue order`() {
        val issueOne = issue(id = "1", issueNumber = "1")
        val issueTwo = issue(id = "2", issueNumber = "2")
        val issueTen = issue(id = "10", issueNumber = "10")

        val nextIssue = resolver.nextUnreadAfter(
            currentItem = issueOne,
            items = listOf(issueTen, issueTwo, issueOne),
            progressByItemId = emptyMap(),
        )

        assertEquals(issueTwo, nextIssue)
    }

    @Test
    fun `next unread skips completed issues`() {
        val issueOne = issue(id = "1", issueNumber = "1")
        val issueTwo = issue(id = "2", issueNumber = "2")
        val issueThree = issue(id = "3", issueNumber = "3")

        val nextIssue = resolver.nextUnreadAfter(
            currentItem = issueOne,
            items = listOf(issueOne, issueTwo, issueThree),
            progressByItemId = mapOf(
                issueTwo.id to ItemProgressSummary(
                    label = "Finished",
                    fraction = 1f,
                    isFinished = true,
                ),
            ),
        )

        assertEquals(issueThree, nextIssue)
    }

    @Test
    fun `next unstarted skips in progress issues`() {
        val issueOne = issue(id = "1", issueNumber = "1")
        val issueTwo = issue(id = "2", issueNumber = "2")
        val issueThree = issue(id = "3", issueNumber = "3")

        val nextIssue = resolver.nextUnstartedAfter(
            currentItem = issueOne,
            items = listOf(issueOne, issueTwo, issueThree),
            progressByItemId = mapOf(
                issueTwo.id to ItemProgressSummary(
                    label = "Resume on page 6",
                    fraction = 0.4f,
                ),
            ),
        )

        assertEquals(issueThree, nextIssue)
    }

    @Test
    fun `next unstarted bulk lookup uses canonical issue order per series`() {
        val alphaOne = issue(id = "alpha-1", issueNumber = "1", seriesKey = "alpha")
        val alphaTwo = issue(id = "alpha-2", issueNumber = "2", seriesKey = "alpha")
        val alphaThree = issue(id = "alpha-3", issueNumber = "3", seriesKey = "alpha")
        val betaOne = issue(id = "beta-1", issueNumber = "1", seriesKey = "beta")
        val betaTwo = issue(id = "beta-2", issueNumber = "2", seriesKey = "beta")

        val nextByCurrentItemId = resolver.nextUnstartedAfterByCurrentItem(
            items = listOf(alphaThree, betaTwo, alphaTwo, betaOne, alphaOne),
            progressByItemId = mapOf(
                alphaTwo.id to ItemProgressSummary(
                    label = "Resume on page 6",
                    fraction = 0.4f,
                ),
            ),
        )

        assertEquals(alphaThree, nextByCurrentItemId[alphaOne.id])
        assertEquals(alphaThree, nextByCurrentItemId[alphaTwo.id])
        assertEquals(betaTwo, nextByCurrentItemId[betaOne.id])
    }

    @Test
    fun `next unread returns null when series is finished`() {
        val issueOne = issue(id = "1", issueNumber = "1")
        val issueTwo = issue(id = "2", issueNumber = "2")

        val nextIssue = resolver.nextUnreadAfter(
            currentItem = issueOne,
            items = listOf(issueOne, issueTwo),
            progressByItemId = mapOf(
                issueTwo.id to ItemProgressSummary(
                    label = "Finished",
                    fraction = 1f,
                    isFinished = true,
                ),
            ),
        )

        assertNull(nextIssue)
    }

    @Test
    fun `local comic imports can continue by metadata series`() {
        val issueOne = issue(
            id = "local-1",
            sourceType = SourceType.LOCAL,
            libraryId = "local",
            seriesKey = null,
            series = "Akira",
            issueNumber = "1",
        )
        val issueTwo = issue(
            id = "local-2",
            sourceType = SourceType.LOCAL,
            libraryId = "local",
            seriesKey = null,
            series = "Akira",
            issueNumber = "2",
        )

        val nextIssue = resolver.nextUnreadAfter(
            currentItem = issueOne,
            items = listOf(issueTwo, issueOne),
            progressByItemId = emptyMap(),
        )

        assertEquals(issueTwo, nextIssue)
    }
}

private fun issue(
    id: String,
    issueNumber: String,
    sourceType: SourceType = SourceType.KAVITA,
    libraryId: String = "library",
    seriesKey: String? = "series-1",
    series: String = "Akira",
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:$id",
    sourceType = sourceType,
    libraryId = libraryId,
    remoteId = id,
    remoteFileType = seriesKey,
    title = "$series $issueNumber",
    format = FormatType.CBZ,
    isLocalImport = sourceType == SourceType.LOCAL,
    comicMetadata = ComicMetadata(
        series = series,
        issueTitle = "Issue $issueNumber",
        issueNumber = issueNumber,
    ),
)
