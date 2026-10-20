package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        ).toDomain()

        requireNotNull(library)
        assertEquals(SourceType.GRIMMORY.remoteLibraryId("42"), library.id)
        assertEquals("42", library.remoteId)
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
                publisher = "Tor",
                categories = listOf("Literary Fiction"),
                tags = listOf("Speculative"),
                language = "English",
            ),
        ).toDomain(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(SourceType.GRIMMORY.remoteItemId("12"), item.id)
        assertEquals("12", item.remoteId)
        assertEquals(SourceType.GRIMMORY.remoteLibraryId("4"), item.libraryId)
        assertEquals(SourceType.GRIMMORY, item.sourceType)
        assertEquals(FormatType.EPUB, item.format)
        assertEquals(ReadableItemKind.BOOK, item.kind)
        assertEquals("Domain title", item.title)
        assertEquals("Ada Author", item.bookMetadata?.author)
        assertEquals(listOf("Ada Author"), item.bookMetadata?.authors)
        assertEquals(listOf("Tor"), item.bookMetadata?.publishers)
        assertEquals(listOf("Literary Fiction"), item.bookMetadata?.genres)
        assertEquals(listOf("Speculative"), item.bookMetadata?.tags)
        assertEquals(listOf("English"), item.bookMetadata?.languages)
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
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(
            "https://grimmory.example/api/v1/media/book/12/thumbnail?size=large",
            item.coverUrl,
        )
    }

    @Test
    fun mapsComicArchiveFormatsToComicItems() {
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
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(FormatType.CBR, item.format)
        assertEquals(ReadableItemKind.COMIC, item.kind)
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
    fun mapsFinishedProgressUpdateToFinishedRequestPayload() {
        val request = GrimmoryProgressUpdate(
            bookId = "12",
            bookFileId = "99",
            bookType = "EPUB",
            positionData = "100",
            percent = 1.0,
            isFinished = true,
            updatedAtEpochMillis = 1_775_131_200_000L,
        ).toRequestDto()

        assertEquals("2026-04-02T12:00:00Z", request.dateFinished?.jsonPrimitive?.content)
        assertEquals(100f, request.fileProgress?.progressPercent)
    }

    @Test
    fun mapsUnreadResetToExplicitFinishedClearPayload() {
        val request = GrimmoryProgressUpdate(
            bookId = "12",
            bookFileId = "99",
            bookType = "EPUB",
            positionData = "0",
            percent = 0.0,
            clearsFinishedState = true,
        ).toRequestDto()

        assertEquals(JsonNull, request.dateFinished)
    }

    @Test
    fun serializesUnreadResetWithExplicitNullFinishedField() {
        val body = testJson.encodeToString(
            GrimmoryReadProgressRequestDto.serializer(),
            GrimmoryProgressUpdate(
                bookId = "12",
                bookFileId = "99",
                bookType = "EPUB",
                positionData = "0",
                percent = 0.0,
                clearsFinishedState = true,
            ).toRequestDto(),
        )

        assertTrue(body.contains("\"dateFinished\":null"))
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
            lastReadTime = "2026-04-01T12:30:00Z",
            epubProgress = GrimmoryEpubProgressDto(
                percentage = 1f,
            ),
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(0.01, item.progress?.percent ?: 0.0, 0.0001)
        assertEquals(1775046600000L, item.progress?.updatedAtEpochMillis)
    }

    @Test
    fun mapsFinishedBookStatusToFinishedProgress() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            dateFinished = "2026-04-02T12:00:00Z",
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(1.0, item.progress?.percent ?: 0.0, 0.0001)
        assertTrue(item.progress?.isFinished ?: false)
        assertEquals(1775131200000L, item.progress?.updatedAtEpochMillis)
    }

    @Test
    fun treatsZeroCfiAndZeroPercentAsUnread() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            lastReadTime = "2026-04-17T19:25:51Z",
            epubProgress = GrimmoryEpubProgressDto(
                cfi = "0",
                percentage = 0f,
            ),
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertNull(item.progress)
        assertEquals(true, item.isExplicitUnreadReset)
    }

    @Test
    fun unreadBookMarksExplicitReset() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            lastReadTime = null,
            epubProgress = null,
            pdfProgress = null,
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertNull(item.progress)
        assertEquals(true, item.isExplicitUnreadReset)
    }

    @Test
    fun unreadBookDoesNotMarkExplicitResetWhenProgressStateWasStripped() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            lastReadTime = null,
            epubProgress = null,
            pdfProgress = null,
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
            progressStateIncluded = false,
        )

        requireNotNull(item)
        assertNull(item.progress)
        assertEquals(false, item.isExplicitUnreadReset)
    }

    @Test
    fun keepsMeaningfulCfiProgress() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            lastReadTime = "2026-04-17T19:25:51Z",
            epubProgress = GrimmoryEpubProgressDto(
                cfi = "epubcfi(/6/14!/4/2/10)",
                href = "OPS/ch2.xhtml",
                percentage = 50f,
            ),
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(0.5, item.progress?.percent ?: 0.0, 0.0001)
        assertEquals(false, item.isExplicitUnreadReset)
    }

    @Test
    fun webReaderProgressBecomesAnEncodedChapterLocatorCarryingTheCfi() {
        // Grimmory's web reader saves its contents entry's href, decoded and with its anchor.
        val progress = epubProgressPull(
            GrimmoryEpubProgressDto(
                cfi = "epubcfi(/6/12!/4/2[ch3]/120/1:0)",
                href = "OEBPS/Text/Глава 3.xhtml#ch3",
                percentage = 39f,
            ),
        )

        assertEquals(LocatorType.CFI, progress.locatorType)
        val locator = Json.parseToJsonElement(checkNotNull(progress.cfi)).jsonObject
        assertEquals(
            "OEBPS/Text/%D0%93%D0%BB%D0%B0%D0%B2%D0%B0%203.xhtml#ch3",
            locator["href"]?.jsonPrimitive?.content,
        )
        val locations = checkNotNull(locator["locations"]).jsonObject
        assertEquals(
            "epubcfi(/6/12!/4/2[ch3]/120/1:0)",
            locations["fragments"]?.jsonArray?.single()?.jsonPrimitive?.content,
        )
        assertEquals(0.39, checkNotNull(locations["totalProgression"]).jsonPrimitive.double, 0.0001)
        assertNull(locations["progression"])
    }

    @Test
    fun chapterProgressWithoutCfiBecomesAChapterLocatorAtThatProgress() {
        // What Kobo devices and this app save: the chapter file and how far into it.
        val progress = epubProgressPull(
            GrimmoryEpubProgressDto(
                href = "OEBPS/text/part06.xhtml",
                contentSourceProgressPercent = 40f,
                percentage = 46.9f,
            ),
        )

        assertEquals(LocatorType.CFI, progress.locatorType)
        val locator = Json.parseToJsonElement(checkNotNull(progress.cfi)).jsonObject
        assertEquals("OEBPS/text/part06.xhtml", locator["href"]?.jsonPrimitive?.content)
        val locations = checkNotNull(locator["locations"]).jsonObject
        assertNull(locations["fragments"])
        assertEquals(0.4, checkNotNull(locations["progression"]).jsonPrimitive.double, 0.0001)
        assertEquals(0.469, checkNotNull(locations["totalProgression"]).jsonPrimitive.double, 0.0001)
    }

    @Test
    fun readerLocatorTheAppUploadedAsTheCfiIsRestoredAsItIs() {
        val uploaded = """{"href":"OEBPS\/text\/part06.xhtml","type":"application\/xhtml+xml",""" +
            """"locations":{"progression":0.4,"position":99,"totalProgression":0.4688}}"""
        val progress = epubProgressPull(
            GrimmoryEpubProgressDto(
                cfi = uploaded,
                href = "OEBPS/text/part06.xhtml",
                percentage = 46.9f,
            ),
        )

        assertEquals(LocatorType.CFI, progress.locatorType)
        assertEquals(uploaded, progress.cfi)
    }

    @Test
    fun encodedHrefIsNotEncodedTwice() {
        assertEquals("OEBPS/text/%D0%93%201.xhtml", "OEBPS/text/%D0%93%201.xhtml".toEncodedEpubHref())
        assertEquals("text/a%25b.xhtml#n1", "text/a%b.xhtml#n1".toEncodedEpubHref())
    }

    @Test
    fun epubProgressWithoutCfiSendsTheChapterAndItsProgress() {
        val body = testJson.encodeToString(
            GrimmoryReadProgressRequestDto.serializer(),
            GrimmoryProgressUpdate(
                bookId = "12",
                bookFileId = "99",
                bookType = "EPUB",
                positionData = null,
                positionHref = "OEBPS/text/part06.xhtml",
                percent = 0.469,
                contentSourceProgressPercent = 40.0,
            ).toRequestDto(),
        )
        val fileProgress = checkNotNull(Json.parseToJsonElement(body).jsonObject["fileProgress"]).jsonObject

        assertNull(fileProgress["positionData"]?.takeUnless { it == JsonNull })
        assertEquals("OEBPS/text/part06.xhtml", fileProgress["positionHref"]?.jsonPrimitive?.content)
        assertEquals(40.0, checkNotNull(fileProgress["contentSourceProgressPercent"]).jsonPrimitive.double, 0.0001)
    }

    private fun epubProgressPull(
        progress: GrimmoryEpubProgressDto,
    ) = checkNotNull(
        GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "EPUB",
                extension = "epub",
            ),
            lastReadTime = "2026-10-20T10:00:00Z",
            epubProgress = progress,
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )?.progress,
    )

    @Test
    fun pdfProgressPullConvertsOneBasedRemotePageToZeroBasedLocalPage() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "PDF",
                extension = "pdf",
            ),
            lastReadTime = "2026-04-17T19:25:51Z",
            pdfProgress = GrimmoryPdfProgressDto(
                page = 5,
                percentage = 50f,
            ),
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(FormatType.PDF, item.item.format)
        assertEquals(4, item.progress?.page)
        assertEquals(0.5, item.progress?.percent ?: 0.0, 0.0001)
    }

    @Test
    fun cbxProgressPullConvertsOneBasedRemotePageToZeroBasedLocalPage() {
        val item = GrimmoryBookDto(
            id = 12,
            libraryId = 4,
            primaryFile = GrimmoryBookFileDto(
                bookType = "CBX",
                extension = "cbz",
            ),
            lastReadTime = "2026-04-17T19:25:51Z",
            cbxProgress = GrimmoryCbxProgressDto(
                page = 5,
                percentage = 25f,
            ),
        ).toCatalogEntry(
            baseUrl = "https://grimmory.example",
        )

        requireNotNull(item)
        assertEquals(FormatType.CBZ, item.item.format)
        assertEquals(4, item.progress?.page)
        assertEquals(0.25, item.progress?.percent ?: 0.0, 0.0001)
        assertEquals(false, item.isExplicitUnreadReset)
    }

    @Test
    fun mapsComicProgressWithoutBookFileIdToDeprecatedCbxPayload() {
        val request = GrimmoryProgressUpdate(
            bookId = "12",
            bookFileId = null,
            bookType = "CBX",
            positionData = "5",
            percent = 0.25,
        ).toRequestDto()

        assertNull(request.fileProgress)
        assertEquals(5, request.deprecatedCbxProgress?.page)
        assertEquals(25f, request.deprecatedCbxProgress?.percentage)
    }
}

private val testJson = Json {
    explicitNulls = false
}
