package com.example.unifiedreader.data.kavita

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaApiTest {
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
}
