package com.example.unifiedreader.data.grimmory

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * A Grimmory shelf (`/api/v1/shelves`): a plain collection of Grimmory books with no order of its
 * own. [sortField] is the web app's sort for it ("title", "addedOn", ...), when one was saved.
 * [ownerUserId] is who made it; someone else's shelf only shows when it is public.
 */
data class GrimmoryShelf(
    val id: String,
    val name: String,
    val icon: String? = null,
    val iconType: String? = null,
    val sortField: String? = null,
    val ownerUserId: String? = null,
    val isPublic: Boolean = false,
    val bookCount: Int = 0,
)

/**
 * Grimmory's plain shelves: list them and their books, create, rename and delete them, and put
 * books on and off them. Magic shelves are not here: their books come only over OPDS.
 */
interface GrimmoryShelvesApi {
    suspend fun getShelves(accessToken: String): List<GrimmoryShelf>

    /** The Grimmory book ids on [shelfId]. */
    suspend fun getShelfBookIds(accessToken: String, shelfId: String): List<String>

    /** The signed-in user's id, to tell their own public shelves from other people's. */
    suspend fun getCurrentUserId(accessToken: String): String?

    suspend fun createShelf(accessToken: String, name: String): GrimmoryShelf

    /** Renames [shelf], keeping its icon and whether it is public (the server replaces all three). */
    suspend fun renameShelf(accessToken: String, shelf: GrimmoryShelf, name: String): GrimmoryShelf

    suspend fun deleteShelf(accessToken: String, shelfId: String)

    /** Puts [bookIds] on [assignShelfIds] and takes them off [unassignShelfIds], in one call. */
    suspend fun setBookShelves(
        accessToken: String,
        bookIds: Collection<String>,
        assignShelfIds: Collection<String> = emptyList(),
        unassignShelfIds: Collection<String> = emptyList(),
    )
}

class KtorGrimmoryShelvesApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : GrimmoryShelvesApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun getShelves(accessToken: String): List<GrimmoryShelf> = authenticatedRequest {
        val response = httpClient.get("$baseUrl/api/v1/shelves") { bearerAuth(accessToken) }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryShelfDto>>().mapNotNull(GrimmoryShelfDto::toShelf)
    }

    override suspend fun getShelfBookIds(accessToken: String, shelfId: String): List<String> = authenticatedRequest {
        val response = httpClient.get("$baseUrl/api/v1/shelves/$shelfId/books") { bearerAuth(accessToken) }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryIdDto>>().mapNotNull { book -> book.id?.toString() }
    }

    override suspend fun getCurrentUserId(accessToken: String): String? = authenticatedRequest {
        val response = httpClient.get("$baseUrl/api/v1/users/me") { bearerAuth(accessToken) }
        response.throwIfGrimmoryError()
        response.body<GrimmoryIdDto>().id?.toString()
    }

    override suspend fun createShelf(accessToken: String, name: String): GrimmoryShelf = authenticatedRequest {
        val response = httpClient.post("$baseUrl/api/v1/shelves") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(GrimmoryShelfRequestDto(name = name))
        }
        response.throwIfGrimmoryError()
        response.body<GrimmoryShelfDto>().toShelf() ?: error("Grimmory did not return the new shelf.")
    }

    override suspend fun renameShelf(
        accessToken: String,
        shelf: GrimmoryShelf,
        name: String,
    ): GrimmoryShelf = authenticatedRequest {
        val response = httpClient.put("$baseUrl/api/v1/shelves/${shelf.id}") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                GrimmoryShelfRequestDto(
                    name = name,
                    icon = shelf.icon,
                    iconType = shelf.iconType,
                    publicShelf = shelf.isPublic,
                ),
            )
        }
        response.throwIfGrimmoryError()
        response.body<GrimmoryShelfDto>().toShelf() ?: shelf.copy(name = name)
    }

    override suspend fun deleteShelf(accessToken: String, shelfId: String) = authenticatedRequest {
        val response = httpClient.delete("$baseUrl/api/v1/shelves/$shelfId") { bearerAuth(accessToken) }
        response.throwIfGrimmoryError()
    }

    override suspend fun setBookShelves(
        accessToken: String,
        bookIds: Collection<String>,
        assignShelfIds: Collection<String>,
        unassignShelfIds: Collection<String>,
    ) = authenticatedRequest {
        val response = httpClient.post("$baseUrl/api/v1/books/shelves") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                GrimmoryShelvesAssignmentRequestDto(
                    bookIds = bookIds.mapNotNull(String::toLongOrNull),
                    // The server reads both sets; an absent one fails, an empty one is fine.
                    shelvesToAssign = assignShelfIds.mapNotNull(String::toLongOrNull),
                    shelvesToUnassign = unassignShelfIds.mapNotNull(String::toLongOrNull),
                ),
            )
        }
        response.throwIfGrimmoryError()
    }
}

@Serializable
internal data class GrimmoryShelfDto(
    val id: Long? = null,
    val name: String? = null,
    val icon: String? = null,
    val iconType: String? = null,
    val sort: GrimmoryShelfSortDto? = null,
    val userId: Long? = null,
    val publicShelf: Boolean? = null,
    val bookCount: Int? = null,
)

@Serializable
internal data class GrimmoryShelfSortDto(
    val field: String? = null,
    val direction: String? = null,
)

@Serializable
internal data class GrimmoryShelfRequestDto(
    val name: String,
    val icon: String? = null,
    val iconType: String? = null,
    val publicShelf: Boolean = false,
)

@Serializable
internal data class GrimmoryShelvesAssignmentRequestDto(
    val bookIds: List<Long>,
    val shelvesToAssign: List<Long>,
    val shelvesToUnassign: List<Long>,
)

/** Any Grimmory object read only for its id (a book on a shelf, the signed-in user). */
@Serializable
internal data class GrimmoryIdDto(
    val id: Long? = null,
)

internal fun GrimmoryShelfDto.toShelf(): GrimmoryShelf? {
    val shelfId = id?.toString() ?: return null
    return GrimmoryShelf(
        id = shelfId,
        name = name?.takeIf(String::isNotBlank) ?: "Shelf $shelfId",
        icon = icon,
        iconType = iconType,
        sortField = sort?.field?.takeIf(String::isNotBlank),
        ownerUserId = userId?.toString(),
        isPublic = publicShelf == true,
        bookCount = bookCount ?: 0,
    )
}
