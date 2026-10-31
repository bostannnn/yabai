package com.example.unifiedreader.data.grimmory

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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GrimmoryMagicShelvesApiTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    // The app's own client settings: nulls and defaults are left out unless a field says otherwise.
    private fun api(respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorGrimmoryMagicShelvesApi(
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

    private val filterJson = """{"type":"group","join":"and","rules":[{"field":"tags","operator":"includes_any","value":["Russia"]}]}"""

    @Test
    fun `magic shelves keep their rules as a string, and one without an id is skipped`() = runBlocking {
        val shelves = api {
            respondJson(
                """
                [
                  {"id": 4, "name": "Russian classics", "icon": "book", "iconType": "LUCIDE",
                   "filterJson": ${JsonPrimitive(filterJson)}, "isPublic": true},
                  {"id": 9, "name": "Short reads", "filterJson": "{}", "isPublic": false},
                  {"name": "No id", "filterJson": "{}"}
                ]
                """.trimIndent(),
            )
        }.getMagicShelves("token")

        assertEquals(
            listOf(
                GrimmoryMagicShelfDto(
                    id = 4,
                    name = "Russian classics",
                    icon = "book",
                    iconType = "LUCIDE",
                    filterJson = filterJson,
                    isPublic = true,
                ),
                GrimmoryMagicShelfDto(id = 9, name = "Short reads", filterJson = "{}", isPublic = false),
            ),
            shelves,
        )
        val (request, _) = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("http://grimmory.example/api/magic-shelves", request.url.toString())
        assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a magic shelf's books come as ids, all at once`() = runBlocking {
        val ids = api { respondJson("[12, 40, 7]") }.getMagicShelfBookIds("token", shelfId = 4)

        assertEquals(listOf(12L, 40L, 7L), ids)
        assertEquals("http://grimmory.example/api/v1/app/books/ids?magicShelfId=4", requests.single().first.url.toString())
    }

    @Test
    fun `a new shelf is sent without an id but with isPublic false and its rules as a string`() = runBlocking {
        val saved = api {
            respondJson("""{"id": 11, "name": "Russian classics", "filterJson": ${JsonPrimitive(filterJson)}, "isPublic": false}""")
        }.saveMagicShelf("token", GrimmoryMagicShelfDto(name = "Russian classics", filterJson = filterJson))

        val (request, body) = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://grimmory.example/api/magic-shelves", request.url.toString())
        val sent = Json.parseToJsonElement(body).jsonObject
        assertEquals(
            Json.parseToJsonElement("""{"name":"Russian classics","filterJson":${JsonPrimitive(filterJson)},"isPublic":false}""").jsonObject,
            sent,
        )
        assertTrue("filterJson goes as a string", (sent.getValue("filterJson") as JsonPrimitive).isString)
        assertEquals(11L, saved.id)
    }

    @Test
    fun `an update sends the whole shelf back, its id, icon and isPublic included`() = runBlocking {
        val shelf = GrimmoryMagicShelfDto(
            id = 4,
            name = "Russian classics",
            icon = "book",
            iconType = "LUCIDE",
            filterJson = filterJson,
            isPublic = true,
        )
        api { respondJson("""{"id": 4, "name": "Russian classics", "filterJson": "{}", "isPublic": true}""") }
            .saveMagicShelf("token", shelf)

        assertEquals(
            Json.parseToJsonElement(
                """{"id":4,"name":"Russian classics","icon":"book","iconType":"LUCIDE","filterJson":${JsonPrimitive(filterJson)},"isPublic":true}""",
            ),
            Json.parseToJsonElement(requests.single().second),
        )
    }

    @Test
    fun `a refused save carries the server's message`() = runBlocking {
        try {
            api {
                respondJson(
                    """{"status": 400, "message": "A shelf with the same name already exists for this user."}""",
                    status = HttpStatusCode.BadRequest,
                )
            }.saveMagicShelf("token", GrimmoryMagicShelfDto(name = "Russian classics", filterJson = filterJson))
            fail("Expected the server's refusal.")
        } catch (exception: IllegalStateException) {
            assertEquals("A shelf with the same name already exists for this user.", exception.message)
        }
    }

    @Test
    fun `someone else's shelf is refused with the server's message`() = runBlocking {
        try {
            api {
                respondJson("""{"message": "An unexpected error occurred."}""", status = HttpStatusCode.InternalServerError)
            }.saveMagicShelf("token", GrimmoryMagicShelfDto(id = 9, name = "Theirs", filterJson = filterJson))
            fail("Expected the server's refusal.")
        } catch (exception: IllegalStateException) {
            assertEquals("An unexpected error occurred.", exception.message)
        }
    }

    @Test
    fun `deleting names the shelf in the path`() = runBlocking {
        api { respond(content = "", status = HttpStatusCode.NoContent) }.deleteMagicShelf("token", shelfId = 4)

        val (request, _) = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("http://grimmory.example/api/magic-shelves/4", request.url.toString())
    }

    @Test(expected = GrimmoryUnauthorizedException::class)
    fun `an expired token says so, so the caller can refresh it`(): Unit = runBlocking {
        api { respondJson("{}", status = HttpStatusCode.Unauthorized) }.getMagicShelves("token")
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
