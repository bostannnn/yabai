package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.KavitaLibrarySection
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KavitaScopeLandingModelTest {
    @Test
    fun `the count names the kind of list, and the search shows how many match`() {
        assertEquals("56 collections", kavitaScopeLandingCountLabel(KavitaLibrarySection.Collections, 56, 56, searchActive = false))
        assertEquals("1 reading list", kavitaScopeLandingCountLabel(KavitaLibrarySection.ReadingLists, 1, 1, searchActive = false))
        assertEquals("3 of 14 reading lists", kavitaScopeLandingCountLabel(KavitaLibrarySection.ReadingLists, 14, 3, searchActive = true))
    }

    @Test
    fun `a collection counts series, a reading list items`() {
        assertEquals("12 series", kavitaScopeLandingItemMeta(KavitaLibrarySection.Collections, 12))
        assertEquals("8 items", kavitaScopeLandingItemMeta(KavitaLibrarySection.ReadingLists, 8))
        assertEquals("1 item", kavitaScopeLandingItemMeta(KavitaLibrarySection.ReadingLists, 1))
    }

    @Test
    fun `not connected wins over everything, then search, then an unreachable server`() {
        assertEquals(
            KavitaScopeLandingEmptyReason.NotConnected,
            kavitaScopeLandingEmptyReason(searchActive = true, notConnected = true, unreachable = true),
        )
        assertEquals(
            KavitaScopeLandingEmptyReason.NoSearchMatches,
            kavitaScopeLandingEmptyReason(searchActive = true, notConnected = false, unreachable = true),
        )
        assertEquals(
            KavitaScopeLandingEmptyReason.Unreachable,
            kavitaScopeLandingEmptyReason(searchActive = false, notConnected = false, unreachable = true),
        )
        assertEquals(
            KavitaScopeLandingEmptyReason.NoneMade,
            kavitaScopeLandingEmptyReason(searchActive = false, notConnected = false, unreachable = false),
        )
    }

    @Test
    fun `the empty states say not connected apart from none made`() {
        assertEquals(
            KavitaScopeLandingEmptyText(
                title = "Kavita isn't connected",
                body = "Add your Kavita server, and its collections show up here after the first sync.",
            ),
            kavitaScopeLandingEmptyText(KavitaLibrarySection.Collections, KavitaScopeLandingEmptyReason.NotConnected),
        )
        assertEquals(
            KavitaScopeLandingEmptyText(
                title = "No reading lists yet",
                body = "Reading lists you make in Kavita show up here after the next sync.",
            ),
            kavitaScopeLandingEmptyText(KavitaLibrarySection.ReadingLists, KavitaScopeLandingEmptyReason.NoneMade),
        )
    }

    @Test
    fun `a reading list's top counts what is read and offers the next one with its place`() {
        val items = listOf(item("1"), item("2"), item("3"), item("4"), item("5"))
        val progress = mapOf(
            "1" to finished(),
            "2" to finished(),
            "3" to ItemProgressSummary(label = "Resume", fraction = 0.22f, lastReadAtEpochMillis = 9),
        )

        val top = libraryReadingListTop("<p>One a month, chosen by turns.</p>", items, progress)

        assertEquals("One a month, chosen by turns.", top.description)
        assertEquals("5 items · 2 read", top.countLabel)
        assertEquals(LibrarySeriesNext(items[2], inProgress = true), top.next)
        assertEquals("Up next in this list · 3 of 5", top.nextOverline)
    }

    @Test
    fun `a finished reading list has no card, and an unread one no read count`() {
        val items = listOf(item("1"), item("2"))

        val finishedTop = libraryReadingListTop(null, items, items.associate { it.id to finished() })
        val unreadTop = libraryReadingListTop("  ", items, emptyMap())

        assertNull(finishedTop.next)
        assertEquals("2 items · 2 read", finishedTop.countLabel)
        assertEquals("2 items", unreadTop.countLabel)
        assertNull(unreadTop.description)
        assertEquals("Up next in this list · 1 of 2", unreadTop.nextOverline)
    }

    @Test
    fun `a collection's top has no card`() {
        val top = libraryCollectionTop("Space operas, old and new.", seriesCount = 12)

        assertEquals("12 series", top.countLabel)
        assertNull(top.next)
    }

    private fun finished() = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true, lastReadAtEpochMillis = 1)

    private fun item(id: String) = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "library",
        title = "Item $id",
        format = FormatType.EPUB,
    )
}
