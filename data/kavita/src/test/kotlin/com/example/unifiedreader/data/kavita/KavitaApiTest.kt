package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.FormatType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaApiTest {
    @Test
    fun `series detail uses bounded supplemental chapter info for missing-title production format mapping`() = runBlocking {
        var chapterInfoRequests = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/api/Series/series-detail" -> {
                        assertEquals("12", request.url.parameters["seriesId"])
                        respondJson(
                            """
                            {
                              "chapters": [
                                {
                                  "id": 21,
                                  "pages": 22
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }
                    "/api/Reader/chapter-info" -> {
                        chapterInfoRequests += 1
                        assertEquals("21", request.url.parameters["chapterId"])
                        assertEquals("true", request.url.parameters["includeDimensions"])
                        respondJson(
                            """
                            {
                              "pageDimensions": [
                                {
                                  "fileName": "Invincible 001.cbr"
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }
                    else -> error("Unexpected request path: ${request.url.encodedPath}")
                }
            },
        ) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
        val api = KtorKavitaApi(
            httpClient = httpClient,
            baseUrl = "http://kavita.example",
        )

        val entries = api.getSeriesDetail(
            accessToken = "token",
            series = KavitaSeriesEntry(
                id = "12",
                libraryId = "5",
                title = "Invincible",
                format = FormatType.CBZ,
                addedAtEpochMillis = null,
                coverUrl = null,
            ),
        )

        assertEquals(1, chapterInfoRequests)
        assertEquals(FormatType.CBR, entries.single().item.format)
    }

    @Test
    fun `series detail caps supplemental chapter info requests for unresolved titles`() = runBlocking {
        var chapterInfoRequests = 0
        val chapterJson = (1..60).joinToString(separator = ",") { chapterId ->
            """
            {
              "id": $chapterId,
              "pages": 22
            }
            """.trimIndent()
        }
        val httpClient = HttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/api/Series/series-detail" -> {
                        respondJson(
                            """
                            {
                              "chapters": [
                                $chapterJson
                              ]
                            }
                            """.trimIndent(),
                        )
                    }
                    "/api/Reader/chapter-info" -> {
                        chapterInfoRequests += 1
                        assertEquals("true", request.url.parameters["includeDimensions"])
                        respondJson(
                            """
                            {
                              "pageDimensions": [
                                {
                                  "fileName": "Invincible ${request.url.parameters["chapterId"]}.cbr"
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }
                    else -> error("Unexpected request path: ${request.url.encodedPath}")
                }
            },
        ) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
        val api = KtorKavitaApi(
            httpClient = httpClient,
            baseUrl = "http://kavita.example",
        )

        val entries = api.getSeriesDetail(
            accessToken = "token",
            series = KavitaSeriesEntry(
                id = "12",
                libraryId = "5",
                title = "Invincible",
                format = FormatType.CBZ,
                addedAtEpochMillis = null,
                coverUrl = null,
            ),
        )

        assertEquals(50, chapterInfoRequests)
        assertEquals(60, entries.size)
        assertEquals(FormatType.CBR, entries.first { entry -> entry.item.remoteId == "50" }.item.format)
        assertEquals(FormatType.CBZ, entries.first { entry -> entry.item.remoteId == "51" }.item.format)
    }

    @Test
    fun `series detail uses chapter files for titled production format mapping without supplemental request`() = runBlocking {
        var chapterInfoRequests = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/api/Series/series-detail" -> {
                        respondJson(
                            """
                            {
                              "chapters": [
                                {
                                  "id": 21,
                                  "title": "Issue 1",
                                  "pages": 22,
                                  "files": [
                                    {
                                      "filePath": "/comics/Invincible 001.cbr",
                                      "format": 1
                                    }
                                  ]
                                }
                              ]
                            }
                            """.trimIndent(),
                        )
                    }
                    "/api/Reader/chapter-info" -> {
                        chapterInfoRequests += 1
                        respondJson("{}")
                    }
                    else -> error("Unexpected request path: ${request.url.encodedPath}")
                }
            },
        ) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
        val api = KtorKavitaApi(
            httpClient = httpClient,
            baseUrl = "http://kavita.example",
        )

        val entries = api.getSeriesDetail(
            accessToken = "token",
            series = KavitaSeriesEntry(
                id = "12",
                libraryId = "5",
                title = "Invincible",
                format = FormatType.CBZ,
                addedAtEpochMillis = null,
                coverUrl = null,
            ),
        )

        assertEquals(0, chapterInfoRequests)
        assertEquals(FormatType.CBR, entries.single().item.format)
    }

    @Test
    fun `chapter info no-content response maps to empty dto`() = runBlocking {
        val httpClient = HttpClient(
            MockEngine { request ->
                assertEquals("/api/Reader/chapter-info", request.url.encodedPath)
                assertEquals("42", request.url.parameters["chapterId"])
                assertEquals("false", request.url.parameters["includeDimensions"])
                respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                )
            },
        ) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
        }
        val api = KtorKavitaApi(
            httpClient = httpClient,
            baseUrl = "http://kavita.example",
        )

        val chapterInfo = api.getChapterInfo(
            accessToken = "token",
            chapterId = "42",
        )

        assertEquals(KavitaChapterInfoDto(), chapterInfo)
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
