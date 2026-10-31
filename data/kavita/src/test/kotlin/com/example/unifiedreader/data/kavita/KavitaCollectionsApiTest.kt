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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class KavitaCollectionsApiTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    // Reached the way the app reaches it: through the lists API.
    private fun api(respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondText("") }): KavitaListsApi =
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

    private val collection = KavitaCollection(
        id = "5",
        title = "Space operas",
        summary = null,
        promoted = true,
        coverImageLocked = true,
        itemCount = 3,
        ownerUserName = "admin",
    )

    @Test
    fun `collections say whose they are and which come from Kavita+`() = runBlocking {
        val collections = api {
            respondJson(
                """
                [
                  {"id":5,"title":"Space operas","summary":"Big ships","promoted":true,"coverImageLocked":false,"itemCount":3,
                   "owner":"admin","source":0,"coverImage":"x","ageRating":0},
                  {"id":6,"title":"Seasonal","promoted":false,"itemCount":40,"owner":"bob","source":2},
                  {"id":7,"title":" "},
                  {"title":"no id"}
                ]
                """.trimIndent(),
            )
        }.getCollections("token")

        assertEquals(
            listOf(
                KavitaCollection("5", "Space operas", "Big ships", promoted = true, coverImageLocked = false, itemCount = 3, ownerUserName = "admin"),
                KavitaCollection("6", "Seasonal", itemCount = 40, ownerUserName = "bob", isFromKavitaPlus = true),
                KavitaCollection("7", "Collection 7"),
            ),
            collections,
        )
        val (request, _) = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("http://kavita.example/api/Collection?ownedOnly=false", request.url.toString())
        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a collection's series come with their library, every one at once`() = runBlocking {
        val series = api {
            respondJson(
                """[{"id":12,"name":"Expanse","libraryId":1,"genres":[{"id":1,"title":"SF"}]},{"id":40,"libraryId":3},""" +
                    """{"id":41},{"name":"no id","libraryId":1}]""",
            )
        }.getCollectionSeries("token", "5")

        assertEquals(listOf(KavitaCollectionSeries("12", "1"), KavitaCollectionSeries("40", "3")), series)
        val request = requests.single().first
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("http://kavita.example/api/Series/series-by-collection?collectionId=5", request.url.toString())
    }

    @Test
    fun `renaming sends back the summary, cover lock and promotion as read`() = runBlocking {
        // Kavita 0.8 answers with plain text.
        api { respondText("Collection updated successfully") }.renameCollection("token", collection, "Space opera")
        // Kavita 0.9 answers with JSON.
        api { respondJson("""{"success":true}""") }
            .renameCollection("token", collection.copy(summary = "Big ships", promoted = false, coverImageLocked = false), "Space opera")

        assertEquals(HttpMethod.Post, requests[0].first.method)
        assertEquals("http://kavita.example/api/Collection/update", requests[0].first.url.toString())
        assertEquals(
            Json.parseToJsonElement("""{"id":5,"title":"Space opera","summary":"","coverImageLocked":true,"promoted":true}"""),
            Json.parseToJsonElement(requests[0].second),
        )
        assertEquals(
            Json.parseToJsonElement("""{"id":5,"title":"Space opera","summary":"Big ships","coverImageLocked":false,"promoted":false}"""),
            Json.parseToJsonElement(requests[1].second),
        )
    }

    @Test
    fun `a refused rename carries Kavita's text`() = runBlocking {
        try {
            api { respondText("A collection with this name already exists", status = HttpStatusCode.BadRequest) }
                .renameCollection("token", collection, "Taken")
            fail("Expected Kavita's refusal.")
        } catch (exception: IllegalStateException) {
            assertEquals("Kavita request failed (400 Bad Request): A collection with this name already exists", exception.message)
        }
    }

    @Test
    fun `taking series out sends the collection with the series ids`() = runBlocking {
        api().removeSeriesFromCollection("token", collection.copy(summary = "Big ships"), listOf("7", "9", "7", "x"))

        val (request, body) = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://kavita.example/api/Collection/update-series", request.url.toString())
        assertEquals(
            Json.parseToJsonElement(
                """{"tag":{"id":5,"title":"Space operas","summary":"Big ships","coverImageLocked":true,"promoted":true},"seriesIdsToRemove":[7,9]}""",
            ),
            Json.parseToJsonElement(body),
        )
    }

    @Test
    fun `nothing is sent without a series to take out`() = runBlocking {
        api().removeSeriesFromCollection("token", collection, listOf("not-a-number"))

        assertEquals(0, requests.size)
    }

    @Test
    fun `deleting names the collection as its tag`() = runBlocking {
        api().deleteCollection("token", "5")

        val (request, _) = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("http://kavita.example/api/Collection?tagId=5", request.url.toString())
    }

    @Test(expected = KavitaUnauthorizedException::class)
    fun `an expired token says so, so the caller can refresh it`(): Unit = runBlocking {
        api { respondText("", status = HttpStatusCode.Unauthorized) }.deleteCollection("token", "5")
    }

    private fun MockRequestHandleScope.respondJson(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun MockRequestHandleScope.respondText(content: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
    )
}
