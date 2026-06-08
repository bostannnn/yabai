package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteLibraryId
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
import org.junit.Assert.fail
import org.junit.Test

class KavitaApiTest {
    @Test
    fun `login unauthorized reports login rejection`() = runBlocking {
        val httpClient = HttpClient(
            MockEngine {
                respondJson(
                    """{"message":"invalid-login"}""",
                    status = HttpStatusCode.Unauthorized,
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

        try {
            api.login(
                username = "reader",
                password = "wrong",
            )
            fail("Expected login rejection.")
        } catch (exception: KavitaUnauthorizedException) {
            assertEquals(KAVITA_LOGIN_REJECTED_MESSAGE, exception.message)
        }
    }

    @Test
    fun `refresh session unauthorized reports refresh token rejection`() = runBlocking {
        val httpClient = HttpClient(
            MockEngine {
                respondJson(
                    """{"message":"invalid-token"}""",
                    status = HttpStatusCode.Unauthorized,
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

        try {
            api.refreshSession(
                refreshToken = "stale-refresh",
                accessToken = "still-valid-access",
                apiKey = "api-key",
            )
            fail("Expected refresh token rejection.")
        } catch (exception: KavitaUnauthorizedException) {
            assertEquals(KAVITA_REFRESH_TOKEN_REJECTED_MESSAGE, exception.message)
        }
    }

    @Test
    fun `cover download problem response is summarized for users`() = runBlocking {
        val httpClient = HttpClient(
            MockEngine {
                respond(
                    content = """
                        {
                          "type": "https://tools.ietf.org/html/rfc9110#section-15.5.5",
                          "title": "Not Found",
                          "status": 404,
                          "traceId": "00-test"
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
        )
        val api = KtorKavitaApi(
            httpClient = httpClient,
            baseUrl = "http://kavita.example",
        )

        try {
            api.downloadCover(
                accessToken = "access",
                coverUrl = "http://kavita.example/api/Image/chapter-cover?chapterId=1",
                apiKey = "api-key",
            )
            fail("Expected cover download failure.")
        } catch (exception: IllegalStateException) {
            assertEquals("Kavita request failed (404 Not Found).", exception.message)
        }
    }

    @Test
    fun `collection series request uses collection id and maps series keys`() = runBlocking {
        var requests = 0
        val httpClient = HttpClient(
            MockEngine { request ->
                requests += 1
                assertEquals("/api/Series/series-by-collection", request.url.encodedPath)
                assertEquals("7", request.url.parameters["collectionId"])
                assertEquals(null, request.url.parameters["collectionTagId"])
                assertEquals("0", request.url.parameters["PageNumber"])
                assertEquals("10000", request.url.parameters["PageSize"])
                respondJson(
                    """
                    [
                      {
                        "id": 12,
                        "libraryId": 5,
                        "name": "KonoSuba"
                      }
                    ]
                    """.trimIndent(),
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

        val seriesRefs = api.getCollectionSeries(
            accessToken = "token",
            collectionRemoteId = "7",
        )

        assertEquals(1, requests)
        assertEquals("${SourceType.KAVITA.remoteLibraryId("5")}::12", seriesRefs.single().seriesKey)
    }

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

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
