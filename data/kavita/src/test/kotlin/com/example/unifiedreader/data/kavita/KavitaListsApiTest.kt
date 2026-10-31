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

    @Test
    fun `reading lists say whose they are, and their issues come in order`() = runBlocking {
        val lists = api {
            respondJson("""[{"id":4,"title":"Eclipse War","itemCount":22,"promoted":true,"ownerUserName":"admin","coverImage":"x"},{"title":"no id"}]""")
        }.getReadingLists("token")
        assertEquals(listOf(KavitaReadingList("4", "Eclipse War", 22, promoted = true, ownerUserName = "admin")), lists)

        val entries = api { respondJson("""[{"id":31,"order":1,"chapterId":700},{"id":30,"order":0,"chapterId":699}]""") }
            .getReadingListEntries("token", "4")
        assertEquals(listOf("699", "700"), entries.map(KavitaReadingListEntry::chapterId))
        assertEquals("http://kavita.example/api/ReadingList/items?readingListId=4", requests.last().first.url.toString())
    }

    @Test
    fun `renaming sends back everything else the list keeps`() = runBlocking {
        api { request ->
            if (request.url.encodedPath.endsWith("/update")) {
                respondJson("{}")
            } else {
                respondJson("""{"id":4,"title":"Old","summary":"Story arc","promoted":true,"coverImageLocked":true,"startingYear":2016,"tags":[{"id":1,"title":"Events"}]}""")
            }
        }.renameReadingList("token", "4", "Eclipse War")

        val update = requests.single { it.first.url.encodedPath.endsWith("/update") }
        assertEquals(
            Json.parseToJsonElement(
                """{"readingListId":4,"title":"Eclipse War","summary":"Story arc","promoted":true,"coverImageLocked":true,""" +
                    """"startingMonth":0,"startingYear":2016,"endingMonth":0,"endingYear":0,"tags":["Events"]}""",
            ),
            Json.parseToJsonElement(update.second),
        )
    }

    @Test
    fun `moving and removing name the list's own id for the issue`() = runBlocking {
        api().moveReadingListEntry("token", "4", entryId = "31", fromPosition = 5, toPosition = 0)
        api().removeReadingListEntry("token", "4", entryId = "30")

        assertEquals(
            Json.parseToJsonElement("""{"readingListId":4,"readingListItemId":31,"fromPosition":5,"toPosition":0}"""),
            Json.parseToJsonElement(requests[0].second),
        )
        assertEquals("http://kavita.example/api/ReadingList/delete-item", requests[1].first.url.toString())
    }

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
