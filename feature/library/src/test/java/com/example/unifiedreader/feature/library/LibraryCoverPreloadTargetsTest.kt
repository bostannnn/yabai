package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCoverPreloadTargetsTest {
    @Test
    fun `kavita targets include series containers and child issues`() {
        val issue1 = kavitaIssue("1")
        val issue2 = kavitaIssue("2")

        val targets = buildLibraryCoverPreloadTargets(
            libraries = listOf(Library("kavita:library:1", SourceType.KAVITA, "Comics")),
            items = listOf(issue1, issue2),
            sourceType = SourceType.KAVITA,
        )

        assertEquals(
            listOf("kavita:series:kavita:library:1::series-1", issue1.id, issue2.id),
            targets.map(ReadableItem::id),
        )
    }

    @Test
    fun `grimmory targets include synced books`() {
        val book = ReadableItem(
            id = "grimmory:item:1",
            sourceType = SourceType.GRIMMORY,
            libraryId = "grimmory:library:1",
            remoteId = "1",
            title = "Book",
            format = FormatType.EPUB,
        )

        val targets = buildLibraryCoverPreloadTargets(
            libraries = listOf(Library("grimmory:library:1", SourceType.GRIMMORY, "Books")),
            items = listOf(book),
            sourceType = SourceType.GRIMMORY,
        )

        assertEquals(listOf(book.id), targets.map(ReadableItem::id))
    }
}

private fun kavitaIssue(
    id: String,
): ReadableItem = ReadableItem(
    id = "kavita:item:$id",
    sourceType = SourceType.KAVITA,
    libraryId = "kavita:library:1",
    remoteId = id,
    remoteFileType = "series-1",
    title = "Issue $id",
    format = FormatType.CBZ,
    comicMetadata = ComicMetadata(
        series = "Series",
        issueTitle = "Issue $id",
        issueNumber = id,
    ),
)

