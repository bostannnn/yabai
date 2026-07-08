package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySeriesRootsTest {
    @Test
    fun `kavita comic issues collapse into one series container root`() {
        val issueA1 = kavitaIssue(id = "kavita:item:1", libraryId = "libA", seriesId = "s1", title = "Series A #1")
        val issueA2 = kavitaIssue(id = "kavita:item:2", libraryId = "libA", seriesId = "s1", title = "Series A #2")
        val standaloneBook = kavitaBook(id = "kavita:item:3", libraryId = "libA", title = "A Standalone Book")

        val grouping = deriveKavitaSeriesGrouping(listOf(issueA1, issueA2, standaloneBook))

        // The two issues collapse into a single synthetic container; the book stays a direct root.
        val rootIds = grouping.rootItems.map(ReadableItem::id)
        assertEquals(setOf("kavita:series:libA::s1", "kavita:item:3"), rootIds.toSet())
        assertTrue("issues must not appear as roots", rootIds.none { it == "kavita:item:1" || it == "kavita:item:2" })

        val container = grouping.rootItems.first { it.id == "kavita:series:libA::s1" }
        assertEquals(2, container.issueCount)
        assertEquals("libA::s1", container.remoteId)
        assertEquals(listOf(issueA1, issueA2), grouping.issuesBySeriesKey.getValue("libA::s1"))
    }

    @Test
    fun `distinct kavita series produce distinct container roots`() {
        val s1 = kavitaIssue(id = "kavita:item:1", libraryId = "libA", seriesId = "s1", title = "One")
        val s2 = kavitaIssue(id = "kavita:item:2", libraryId = "libA", seriesId = "s2", title = "Two")

        val grouping = deriveKavitaSeriesGrouping(listOf(s1, s2))

        assertEquals(
            setOf("kavita:series:libA::s1", "kavita:series:libA::s2"),
            grouping.rootItems.map(ReadableItem::id).toSet(),
        )
        assertEquals(setOf("libA::s1", "libA::s2"), grouping.issuesBySeriesKey.keys)
    }

    @Test
    fun `stump comic issues collapse into one series container root`() {
        val issue1 = stumpIssue(id = "stump:item:1", libraryId = "libS", seriesId = "sx", title = "X #1")
        val issue2 = stumpIssue(id = "stump:item:2", libraryId = "libS", seriesId = "sx", title = "X #2")

        val grouping = deriveStumpSeriesGrouping(listOf(issue1, issue2))

        assertEquals(listOf("stump:series:libS::sx"), grouping.rootItems.map(ReadableItem::id))
        assertEquals(2, grouping.rootItems.single().issueCount)
        assertEquals(listOf(issue1, issue2), grouping.issuesBySeriesKey.getValue("libS::sx"))
    }

    @Test
    fun `empty input yields empty grouping`() {
        val grouping = deriveKavitaSeriesGrouping(emptyList())
        assertEquals(emptyList<ReadableItem>(), grouping.rootItems)
        assertEquals(emptyMap<String, List<ReadableItem>>(), grouping.issuesBySeriesKey)
    }
}

private fun kavitaIssue(
    id: String,
    libraryId: String,
    seriesId: String,
    title: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = libraryId,
    remoteFileType = seriesId,
    title = title,
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    comicMetadata = ComicMetadata(series = "Series $seriesId"),
)

private fun kavitaBook(
    id: String,
    libraryId: String,
    title: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.KAVITA,
    libraryId = libraryId,
    title = title,
    format = FormatType.EPUB,
    kind = ReadableItemKind.BOOK,
)

private fun stumpIssue(
    id: String,
    libraryId: String,
    seriesId: String,
    title: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = SourceType.STUMP,
    libraryId = libraryId,
    remoteFileType = seriesId,
    title = title,
    format = FormatType.CBZ,
    kind = ReadableItemKind.COMIC,
    comicMetadata = ComicMetadata(series = "Series $seriesId"),
)
