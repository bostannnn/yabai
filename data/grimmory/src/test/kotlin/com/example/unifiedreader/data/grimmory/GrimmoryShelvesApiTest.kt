package com.example.unifiedreader.data.grimmory

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class GrimmoryShelvesApiTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun api(respond: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        KtorGrimmoryShelvesApi(
            httpClient = HttpClient(
                MockEngine { request ->
                    requests += request to String(request.body.toByteArray())
                    respond(request)
                },
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true; explicitNulls = false })
                }
            },
            baseUrl = "http://grimmory.example/",
        )

    @Test
    fun `shelves keep their owner, sort and count and skip one without an id`() = runBlocking {
        val shelves = api {
            respondJson(
                """
                [
                  {"id": 3, "name": "Book club", "icon": "book", "iconType": "LUCIDE", "sort": {"field": "title", "direction": "ASCENDING"},
                   "userId": 1, "publicShelf": false, "bookCount": 5},
                  {"id": 9, "name": " ", "userId": 2, "publicShelf": true},
                  {"name": "No id"}
                ]
                """.trimIndent(),
            )
        }.getShelves("token")

        assertEquals(
            listOf(
                GrimmoryShelf(
                    id = "3",
                    name = "Book club",
                    icon = "book",
                    iconType = "LUCIDE",
                    sortField = "title",
                    ownerUserId = "1",
                    isPublic = false,
                    bookCount = 5,
                ),
                GrimmoryShelf(id = "9", name = "Shelf 9", ownerUserId = "2", isPublic = true),
            ),
            shelves,
        )
        assertEquals("http://grimmory.example/api/v1/shelves", requests.single().first.url.toString())
        assertEquals("Bearer token", requests.single().first.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a shelf's books are read for their ids only`() = runBlocking {
        val ids = api {
            respondJson("""[{"id": 12, "title": "A", "metadata": {"title": "A"}}, {"id": 40}, {"title": "no id"}]""")
        }.getShelfBookIds("token", shelfId = "3")

        assertEquals(listOf("12", "40"), ids)
        assertEquals("http://grimmory.example/api/v1/shelves/3/books", requests.single().first.url.toString())
    }

    @Test
    fun `moving books sends both shelf sets even when one is empty`() = runBlocking {
        api { respondJson("[]") }.setBookShelves(
            accessToken = "token",
            bookIds = listOf("12", "40"),
            assignShelfIds = listOf("3"),
        )

        val (request, body) = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://grimmory.example/api/v1/books/shelves", request.url.toString())
        assertEquals(
            Json.parseToJsonElement("""{"bookIds":[12,40],"shelvesToAssign":[3],"shelvesToUnassign":[]}"""),
            Json.parseToJsonElement(body),
        )
    }

    @Test
    fun `renaming keeps the shelf's icon and whether it is public`() = runBlocking {
        val shelf = GrimmoryShelf(id = "3", name = "Book club", icon = "book", iconType = "LUCIDE", isPublic = true)
        val renamed = api { respondJson("""{"id": 3, "name": "Reading club", "icon": "book", "iconType": "LUCIDE", "publicShelf": true}""") }
            .renameShelf("token", shelf, "Reading club")

        val (request, body) = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("http://grimmory.example/api/v1/shelves/3", request.url.toString())
        val sent: JsonObject = Json.parseToJsonElement(body).jsonObject
        assertEquals(
            Json.parseToJsonElement("""{"name":"Reading club","icon":"book","iconType":"LUCIDE","publicShelf":true}""").jsonObject,
            sent,
        )
        assertEquals("Reading club", renamed.name)
    }

    @Test
    fun `a refused change carries the server's message`() = runBlocking {
        try {
            api {
                respondJson("""{"message": "Shelf already exists: Book club"}""", status = HttpStatusCode.BadRequest)
            }.createShelf("token", "Book club")
            fail("Expected the server's refusal.")
        } catch (exception: IllegalStateException) {
            assertEquals("Shelf already exists: Book club", exception.message)
        }
    }

    @Test(expected = GrimmoryUnauthorizedException::class)
    fun `an expired token says so, so the caller can refresh it`(): Unit = runBlocking {
        api { respondJson("{}", status = HttpStatusCode.Unauthorized) }.deleteShelf("token", "3")
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
