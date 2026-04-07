package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrimmoryDtosTest {
    @Test
    fun mapsLoginResponseToSession() {
        val session = Json.decodeFromString<JsonObject>(
            """
            {
              "accessToken": "access-token",
              "refreshToken": "refresh-token",
              "isDefaultPassword": "false"
            }
            """.trimIndent(),
        ).toLoginSession()

        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token", session.refreshToken)
        assertEquals(false, session.isDefaultPassword)
    }

    @Test
    fun mapsSnakeCaseLoginResponseToSession() {
        val session = Json.decodeFromString<JsonObject>(
            """
            {
              "access_token": "access-token",
              "refresh_token": "refresh-token"
            }
            """.trimIndent(),
        ).toLoginSession()

        assertEquals("access-token", session.accessToken)
        assertEquals("refresh-token", session.refreshToken)
    }

    @Test
    fun mapsLibraryToGrimmoryDomainModel() {
        val library = GrimmoryLibraryDto(
            id = 42,
            name = "Books",
        ).toDomain(serverId = "server-1")

        requireNotNull(library)
        assertEquals("42", library.id)
        assertEquals("server-1", library.serverId)
        assertEquals(SourceType.GRIMMORY, library.sourceType)
        assertEquals("Books", library.title)
    }

    @Test
    fun mapsEpubBookToReadableItem() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                id = 99,
                fileName = "a-book.epub",
                bookType = "EPUB",
                extension = "epub",
            ),
            title = "Fallback title",
            addedOn = "2026-04-01T12:30:00Z",
            metadata = GrimmoryBookMetadataDto(
                title = "Domain title",
                description = "A useful description",
                pageCount = 320,
                authors = listOf("Ada Author"),
            ),
        ).toDomain(
            serverId = "server-1",
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals("12", item.id)
        assertEquals("4", item.libraryId)
        assertEquals(SourceType.GRIMMORY, item.sourceType)
        assertEquals(FormatType.EPUB, item.format)
        assertEquals(ReadableItemKind.BOOK, item.kind)
        assertEquals("Domain title", item.title)
        assertEquals("Ada Author", item.bookMetadata?.author)
        assertEquals(320, item.pageCount)
        assertEquals("https://grimmory.example/api/v1/media/book/12/thumbnail", item.coverUrl)
        assertEquals(1775046600000L, item.addedAtEpochMillis)
    }

    @Test
    fun resolvesRelativeThumbnailUrlsAgainstGrimmoryBaseUrl() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            metadata = GrimmoryBookMetadataDto(
                thumbnailUrl = "/api/v1/media/book/12/thumbnail?size=large",
            ),
        ).toDomain(
            serverId = "server-1",
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(
            "https://grimmory.example/api/v1/media/book/12/thumbnail?size=large",
            item.coverUrl,
        )
    }

    @Test
    fun skipsComicArchiveFormatsBecauseKavitaOwnsCbrAndCbz() {
        val item = GrimmoryBookDto(
            id = 7,
            libraryId = 2,
            primaryFile = GrimmoryBookFileDto(
                bookType = "CBX",
                extension = "cbr",
            ),
            metadata = GrimmoryBookMetadataDto(
                title = "Issue title",
                seriesName = "Series",
                seriesNumber = 3f,
            ),
        ).toDomain(
            serverId = "server-1",
            baseUrl = "https://grimmory.example",
        )

        assertNull(item)
    }

    @Test
    fun skipsUnsupportedBookFormats() {
        val item = GrimmoryBookDto(
            id = 7,
            libraryId = 2,
            primaryFile = GrimmoryBookFileDto(
                bookType = "MOBI",
                extension = "mobi",
            ),
        ).toDomain(
            serverId = "server-1",
            baseUrl = "https://grimmory.example",
        )

        assertNull(item)
    }

    @Test
    fun mapsEpubProgressWithBookFileIdToCurrentGrimmoryProgressPayload() {
        val request = GrimmoryEpubProgressUpdate(
            bookId = "12",
            bookFileId = "99",
            cfi = "epubcfi(/6/2)",
            href = "chapter.xhtml",
            percent = 1.2,
        ).toRequestDto()

        assertEquals(12L, request.bookId)
        assertNull(request.deprecatedEpubProgress)
        assertEquals(99L, request.fileProgress?.bookFileId)
        assertEquals("epubcfi(/6/2)", request.fileProgress?.positionData)
        assertEquals("chapter.xhtml", request.fileProgress?.positionHref)
        assertEquals(100f, request.fileProgress?.progressPercent)
    }

    @Test
    fun mapsEpubProgressWithoutBookFileIdToDeprecatedFallbackPayload() {
        val request = GrimmoryEpubProgressUpdate(
            bookId = "12",
            bookFileId = null,
            cfi = "epubcfi(/6/2)",
            percent = -1.0,
        ).toRequestDto()

        assertEquals(12L, request.bookId)
        assertNull(request.fileProgress)
        assertEquals("epubcfi(/6/2)", request.deprecatedEpubProgress?.cfi)
        assertEquals(0f, request.deprecatedEpubProgress?.percentage)
    }

    @Test
    fun mapsGrimmoryPercentScaleToLocalFraction() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            epubProgress = GrimmoryEpubProgressDto(
                percentage = 1f,
            ),
        ).toCatalogEntry(
            serverId = "server-1",
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(0.01, item.progress?.percent ?: 0.0, 0.0001)
    }
}
