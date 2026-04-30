package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GrimmoryCatalogMetadataEnrichmentTest {
    @Test
    fun `journal metadata enrichment limits detail request attempts`() = runTest {
        val entries = (0 until 300).map { index ->
            GrimmoryBookCatalogEntry(
                item = readableItem(remoteId = "book-$index"),
                progress = null,
                personalRating = 8,
            )
        }
        val api = RecordingGrimmoryApi()

        val enrichedEntries = enrichGrimmoryJournalCatalogMetadata(
            api = api,
            accessToken = "token",
            entries = entries,
        )

        assertEquals(entries, enrichedEntries)
        assertEquals((0 until 256).map { index -> "book-$index" }, api.detailBookRequests)
    }
}

private class RecordingGrimmoryApi : GrimmoryApi {
    val detailBookRequests = mutableListOf<String>()

    override suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession = error("Unused")

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = error("Unused")

    override suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem> = error("Unused")

    override suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? {
        detailBookRequests += bookId
        return null
    }

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = error("Unused")

    override fun bookCoverUrl(
        bookId: String,
    ): String = error("Unused")
}

private fun readableItem(
    remoteId: String,
): ReadableItem = ReadableItem(
    id = "grimmory:item:$remoteId",
    sourceType = SourceType.GRIMMORY,
    libraryId = "grimmory:library:1",
    remoteId = remoteId,
    title = "Book $remoteId",
    format = FormatType.EPUB,
    kind = ReadableItemKind.BOOK,
)
