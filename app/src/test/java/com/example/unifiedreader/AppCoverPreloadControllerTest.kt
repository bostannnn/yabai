package com.example.unifiedreader

import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCoverPreloadControllerTest {
    @Test
    fun `cover preload failures are grouped by issue with comic identity`() {
        val issue = kavitaIssue(
            title = "Golden Age",
            issueNumber = "1",
            issueTitle = "Golden Age",
        )

        val failures = listOf(
            RemoteCoverPreloadFailure(
                item = issue,
                variant = LibraryCoverImageVariant.Grid,
                reason = """{"title":"Not Found","status":404}""",
            ),
            RemoteCoverPreloadFailure(
                item = issue,
                variant = LibraryCoverImageVariant.GridLarge,
                reason = """{"title":"Not Found","status":404}""",
            ),
        ).toFailureUiItems()

        assertEquals(1, failures.size)
        assertEquals("Best of Archie Americana (2017) - Issue 1 - Golden Age", failures.single().title)
        assertEquals("2 variants (Grid, GridLarge)", failures.single().variant)
        assertEquals("404 Not Found", failures.single().reason)
    }

    @Test
    fun `pending cover preload retries are grouped by issue`() {
        val issue = kavitaIssue(
            title = "Silver Age",
            issueNumber = "2",
            issueTitle = "Silver Age",
        )

        val pending = listOf(
            RemoteCoverPreloadTarget(issue, LibraryCoverImageVariant.ShelfPreview),
            RemoteCoverPreloadTarget(issue, LibraryCoverImageVariant.Grid),
            RemoteCoverPreloadTarget(issue, LibraryCoverImageVariant.GridLarge),
            RemoteCoverPreloadTarget(issue, LibraryCoverImageVariant.GridExtraLarge),
        ).toPendingFailureUiItems()

        assertEquals(1, pending.size)
        assertEquals("Best of Archie Americana (2017) - Issue 2 - Silver Age", pending.single().title)
        assertEquals("4 variants (ShelfPreview, Grid, GridLarge, ...)", pending.single().variant)
        assertEquals("Retry pending.", pending.single().reason)
    }

    private fun kavitaIssue(
        title: String,
        issueNumber: String,
        issueTitle: String,
    ): ReadableItem = ReadableItem(
        id = "kavita:item:$issueNumber",
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:4",
        remoteId = issueNumber,
        remoteFileType = "2738",
        title = title,
        format = FormatType.CBZ,
        comicMetadata = ComicMetadata(
            series = "Best of Archie Americana (2017)",
            issueTitle = issueTitle,
            issueNumber = issueNumber,
        ),
    )
}
