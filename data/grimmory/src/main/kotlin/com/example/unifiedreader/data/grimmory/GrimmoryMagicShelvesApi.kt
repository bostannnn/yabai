package com.example.unifiedreader.data.grimmory

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * A Grimmory magic shelf (`/api/magic-shelves`): a saved rule set whose books the server works out.
 * [filterJson] is the rules as a JSON document held in a string (see [toGrimmoryFilterJson]).
 * [iconType] is "LUCIDE" or "CUSTOM_SVG" when an icon was picked. The server says nothing of who owns
 * a shelf: its list holds the user's own shelves first, then everyone's public ones.
 *
 * The same object is sent to save a shelf: without [id] it makes a new one, with it it replaces that
 * shelf whole, so an update sends back the [name], [icon], [iconType] and [isPublic] it read.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GrimmoryMagicShelfDto(
    val id: Long? = null,
    val name: String,
    val icon: String? = null,
    val iconType: String? = null,
    val filterJson: String,
    // Always written, false included: the server stores what it is sent, and a missing value is no answer.
    @EncodeDefault val isPublic: Boolean = false,
)

/** Grimmory's magic shelves: list them and their books, save (create or replace) and delete them. */
interface GrimmoryMagicShelvesApi {
    /** The user's own magic shelves, then everyone's public ones. */
    suspend fun getMagicShelves(accessToken: String): List<GrimmoryMagicShelfDto>

    /** The ids of every Grimmory book [shelfId]'s rules match now, all at once. */
    suspend fun getMagicShelfBookIds(accessToken: String, shelfId: Long): List<Long>

    /**
     * Makes [shelf] when it has no id, or replaces the shelf with its id. Returns what the server
     * saved. A refusal (a name already taken, someone else's shelf) throws [IllegalStateException]
     * with the server's message.
     */
    suspend fun saveMagicShelf(accessToken: String, shelf: GrimmoryMagicShelfDto): GrimmoryMagicShelfDto

    suspend fun deleteMagicShelf(accessToken: String, shelfId: Long)
}

class KtorGrimmoryMagicShelvesApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : GrimmoryMagicShelvesApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun getMagicShelves(accessToken: String): List<GrimmoryMagicShelfDto> = authenticatedRequest {
        val response = httpClient.get("$baseUrl/api/magic-shelves") { bearerAuth(accessToken) }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryMagicShelfResponseDto>>().mapNotNull(GrimmoryMagicShelfResponseDto::toMagicShelf)
    }

    override suspend fun getMagicShelfBookIds(accessToken: String, shelfId: Long): List<Long> = authenticatedRequest {
        val response = httpClient.get("$baseUrl/api/v1/app/books/ids") {
            bearerAuth(accessToken)
            url { parameters.append("magicShelfId", shelfId.toString()) }
        }
        response.throwIfGrimmoryError()
        response.body<List<Long?>>().filterNotNull()
    }

    override suspend fun saveMagicShelf(
        accessToken: String,
        shelf: GrimmoryMagicShelfDto,
    ): GrimmoryMagicShelfDto = authenticatedRequest {
        val response = httpClient.post("$baseUrl/api/magic-shelves") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(shelf)
        }
        response.throwIfGrimmoryError()
        response.body<GrimmoryMagicShelfResponseDto>().toMagicShelf() ?: error("Grimmory did not return the magic shelf.")
    }

    override suspend fun deleteMagicShelf(accessToken: String, shelfId: Long) = authenticatedRequest {
        val response = httpClient.delete("$baseUrl/api/magic-shelves/$shelfId") { bearerAuth(accessToken) }
        response.throwIfGrimmoryError()
    }
}

/** A magic shelf as the server sends it, read leniently: one odd shelf shouldn't hide the others. */
@Serializable
internal data class GrimmoryMagicShelfResponseDto(
    val id: Long? = null,
    val name: String? = null,
    val icon: String? = null,
    val iconType: String? = null,
    // A string holding JSON; read as any JSON in case a server sends the rules as an object.
    val filterJson: JsonElement? = null,
    val isPublic: Boolean? = null,
)

internal fun GrimmoryMagicShelfResponseDto.toMagicShelf(): GrimmoryMagicShelfDto? {
    val shelfId = id ?: return null
    val rules = when (val json = filterJson) {
        null, JsonNull -> ""
        is JsonPrimitive -> json.content
        else -> json.toString()
    }
    return GrimmoryMagicShelfDto(
        id = shelfId,
        name = name?.takeIf(String::isNotBlank) ?: "Magic shelf $shelfId",
        icon = icon,
        iconType = iconType,
        filterJson = rules,
        isPublic = isPublic == true,
    )
}
