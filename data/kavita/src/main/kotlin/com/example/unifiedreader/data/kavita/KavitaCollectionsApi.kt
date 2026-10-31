package com.example.unifiedreader.data.kavita

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A Kavita collection as the Lists screen keeps it. [summary], [promoted] and [coverImageLocked] are
 * kept to be sent back on a change, which replaces them all. [ownerUserName] is whose it is;
 * [isFromKavitaPlus] when Kavita+ syncs it from elsewhere (AniList, MAL...), so it is read-only here.
 */
data class KavitaCollection(
    val id: String,
    val title: String,
    val summary: String? = null,
    val promoted: Boolean = false,
    val coverImageLocked: Boolean = false,
    val itemCount: Int = 0,
    val ownerUserName: String? = null,
    val isFromKavitaPlus: Boolean = false,
)

/** A series in a Kavita collection, by its id and its library's (the app's own id for it takes both). */
data class KavitaCollectionSeries(
    val seriesId: String,
    val libraryId: String,
)

/** Kavita's collections (groups of series): list them and their series, rename, take series off, delete. */
interface KavitaCollectionsApi {
    /** Every collection the user can see, other people's promoted ones included. */
    suspend fun getCollections(accessToken: String): List<KavitaCollection>

    /** Every series in the collection, at once. */
    suspend fun getCollectionSeries(accessToken: String, collectionId: String): List<KavitaCollectionSeries>

    /** Renames [collection], sending back its summary, cover lock and promotion as read (Kavita replaces them). */
    suspend fun renameCollection(accessToken: String, collection: KavitaCollection, title: String)

    /** Takes [seriesIds] out of [collection]. */
    suspend fun removeSeriesFromCollection(accessToken: String, collection: KavitaCollection, seriesIds: Collection<String>)

    suspend fun deleteCollection(accessToken: String, collectionId: String)
}

class KtorKavitaCollectionsApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : KavitaCollectionsApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun getCollections(accessToken: String): List<KavitaCollection> = authenticatedRequest {
        val response = httpClient.get("$baseUrl/api/Collection") {
            bearerAuth(accessToken)
            url { parameters.append("ownedOnly", "false") }
        }
        response.throwIfKavitaError()
        response.body<List<KavitaUserCollectionDto>>().mapNotNull(KavitaUserCollectionDto::toCollection)
    }

    override suspend fun getCollectionSeries(accessToken: String, collectionId: String): List<KavitaCollectionSeries> =
        authenticatedRequest {
            // No page size: Kavita then sends every series.
            val response = httpClient.get("$baseUrl/api/Series/series-by-collection") {
                bearerAuth(accessToken)
                url { parameters.append("collectionId", collectionId) }
            }
            response.throwIfKavitaError()
            response.body<List<KavitaSeriesDto>>().mapNotNull { series ->
                KavitaCollectionSeries(
                    seriesId = series.id?.toString() ?: return@mapNotNull null,
                    libraryId = series.libraryId?.toString() ?: return@mapNotNull null,
                )
            }
        }

    override suspend fun renameCollection(accessToken: String, collection: KavitaCollection, title: String) {
        authenticatedRequest {
            // Kavita answers with JSON or plain text by version; any success will do.
            val response = httpClient.post("$baseUrl/api/Collection/update") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(collection.toUpdateDto(title))
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun removeSeriesFromCollection(
        accessToken: String,
        collection: KavitaCollection,
        seriesIds: Collection<String>,
    ) {
        val ids = seriesIds.mapNotNull(String::toIntOrNull).distinct()
        if (ids.isEmpty()) return
        authenticatedRequest {
            val response = httpClient.post("$baseUrl/api/Collection/update-series") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(KavitaCollectionSeriesUpdateDto(tag = collection.toUpdateDto(collection.title), seriesIdsToRemove = ids))
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun deleteCollection(accessToken: String, collectionId: String) {
        authenticatedRequest {
            val response = httpClient.delete("$baseUrl/api/Collection") {
                bearerAuth(accessToken)
                url { parameters.append("tagId", collectionId) }
            }
            response.throwIfKavitaError()
        }
    }
}

/** Kavita's `AppUserCollectionDto`, the parts the app reads. [source] 0 is Kavita's own; the rest are Kavita+. */
@Serializable
internal data class KavitaUserCollectionDto(
    val id: Int? = null,
    val title: String? = null,
    val summary: String? = null,
    val promoted: Boolean? = null,
    val coverImageLocked: Boolean? = null,
    val itemCount: Int? = null,
    val owner: String? = null,
    // A number; read as any value so a server that names it doesn't hide every collection.
    val source: JsonPrimitive? = null,
)

// No defaults: a default isn't sent, and Kavita would wipe what's missing.
@Serializable
internal data class KavitaCollectionUpdateDto(
    val id: Int,
    val title: String,
    val summary: String,
    val coverImageLocked: Boolean,
    val promoted: Boolean,
)

@Serializable
internal data class KavitaCollectionSeriesUpdateDto(
    val tag: KavitaCollectionUpdateDto,
    val seriesIdsToRemove: List<Int>,
)

internal fun KavitaUserCollectionDto.toCollection(): KavitaCollection? {
    val collectionId = id?.toString() ?: return null
    val kavitaSource = source?.takeUnless { it is JsonNull }
        ?.let { value -> value.intOrNull?.let { it == 0 } ?: value.content.equals("Kavita", ignoreCase = true) }
        ?: true
    return KavitaCollection(
        id = collectionId,
        title = title?.takeIf(String::isNotBlank) ?: "Collection $collectionId",
        summary = summary,
        promoted = promoted == true,
        coverImageLocked = coverImageLocked == true,
        itemCount = itemCount?.coerceAtLeast(0) ?: 0,
        ownerUserName = owner?.takeIf(String::isNotBlank),
        isFromKavitaPlus = !kavitaSource,
    )
}

private fun KavitaCollection.toUpdateDto(title: String) = KavitaCollectionUpdateDto(
    id = id.toInt(),
    title = title,
    summary = summary.orEmpty(),
    coverImageLocked = coverImageLocked,
    promoted = promoted,
)
