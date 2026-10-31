package com.example.unifiedreader.data.kavita

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class KavitaListsApiTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun api(respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("") }) =
        KtorKavitaListsApi(
            httpClient = HttpClient(
                MockEngine { request ->
                    requests += request to String(request.body.toByteArray())
                    respond(request)
                },
            ) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
            },
            baseUrl = "http://kavita.example/",
        )

    @Test
    fun `want to read takes series ids as numbers, each once`() = runBlocking {
        api().addToWantToRead("token", listOf("7", "7", "9"))
        api().removeFromWantToRead("token", listOf("9"))

        assertEquals("http://kavita.example/api/want-to-read/add-series", requests[0].first.url.toString())
        assertEquals(Json.parseToJsonElement("""{"seriesIds":[7,9]}"""), Json.parseToJsonElement(requests[0].second))
        assertEquals("http://kavita.example/api/want-to-read/remove-series", requests[1].first.url.toString())
        assertEquals("Bearer token", requests[1].first.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `nothing is sent without a series`() = runBlocking {
        api().addToWantToRead("token", listOf("not-a-number"))

        assertEquals(0, requests.size)
    }

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
