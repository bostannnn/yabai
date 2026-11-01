package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.FormatType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaSeriesMetadataTest {
    @Test
    fun `series metadata reads the series' own genres, tags and people`() = runBlocking {
        val httpClient = HttpClient(
            MockEngine { request ->
                assertEquals("/api/Series/metadata", request.url.encodedPath)
                assertEquals("12", request.url.parameters["seriesId"])
                respond(
                    content = """
                        {"id":3,"seriesId":12,"summary":"A long war.",
                         "genres":[{"id":1,"title":"Science Fiction"},{"id":2,"title":" Fantasy "}],
                         "tags":[{"id":4,"title":"Space Opera"},{"id":5,"title":"space opera"}],
                         "writers":[{"id":7,"name":"Brian K. Vaughan"}],
                         "pencillers":[{"id":8,"name":"Fiona Staples"}],
                         "publishers":[{"id":9,"name":"Image"}],
                         "language":"en","genresLocked":true}
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val api = KtorKavitaApi(httpClient = httpClient, baseUrl = "http://kavita.example")

        val metadata = api.getSeriesMetadata(accessToken = "token", seriesId = "12")

        assertEquals(
            KavitaSeriesMetadata(
                summary = "A long war.",
                genres = listOf("Science Fiction", "Fantasy"),
                tags = listOf("Space Opera"),
                writers = listOf("Brian K. Vaughan"),
                pencillers = listOf("Fiona Staples"),
                publishers = listOf("Image"),
                languages = listOf("en"),
            ),
            metadata,
        )
        assertEquals("A long war.", api.getSeriesSummary(accessToken = "token", seriesId = "12"))
    }

    @Test
    fun `a book carries its own genres and tags and the series' ones`() {
        val series = seriesEntry().withMetadata(
            KavitaSeriesMetadata(
                genres = listOf("Fantasy", "Mystery"),
                tags = listOf("Found Family"),
                writers = listOf("Series Writer"),
            ),
        )

        val entry = KavitaSeriesDetailDto(
            chapters = listOf(
                KavitaChapterDto(
                    id = 21,
                    pages = 300,
                    genres = listOf(KavitaGenreTagDto(title = "fantasy"), KavitaGenreTagDto(title = "Romance")),
                    writers = listOf(KavitaPersonDto(name = "Book Writer")),
                ),
            ),
        ).toCatalogEntries(series = series).single()

        val metadata = entry.item.comicMetadata!!
        assertEquals(listOf("fantasy", "Romance", "Mystery"), metadata.genres)
        assertEquals(listOf("Found Family"), metadata.tags)
        // People stay the book's own when it has them; the series' fill in only when it has none.
        assertEquals(listOf("Book Writer"), metadata.writers)
    }

    @Test
    fun `metadata adds to the series without repeating what it has`() {
        val series = seriesEntry().copy(genres = listOf("Fantasy")).withMetadata(
            KavitaSeriesMetadata(genres = listOf("FANTASY", "Horror"), languages = listOf("en")),
        )

        assertEquals(listOf("Fantasy", "Horror"), series.genres)
        assertEquals(listOf("en"), series.languages)
    }

    private fun seriesEntry() = KavitaSeriesEntry(
        id = "12",
        libraryId = "5",
        title = "Saga",
        format = FormatType.EPUB,
        addedAtEpochMillis = null,
        coverUrl = null,
    )
}
