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

/** A Kavita reading list as the Lists screen keeps it: whose it is and how many issues it holds. */
data class KavitaReadingList(
    val id: String,
    val title: String,
    val itemCount: Int,
    val promoted: Boolean,
    val ownerUserName: String?,
)

/** An issue in a Kavita reading list: the list's own id for it, its place, and the chapter. */
data class KavitaReadingListEntry(
    val id: String,
    val order: Int,
    val chapterId: String,
)

/** Kavita's lists that the app reads and writes: the user's reading lists and Want to read. */
interface KavitaListsApi {
    /** Every reading list the user can see, promoted ones included. */
    suspend fun getReadingLists(accessToken: String): List<KavitaReadingList>

    /** A reading list's issues in its order. */
    suspend fun getReadingListEntries(accessToken: String, readingListId: String): List<KavitaReadingListEntry>

    suspend fun createReadingList(accessToken: String, title: String): KavitaReadingList

    /** Renames the list, sending back everything else Kavita keeps on it (the update replaces it all). */
    suspend fun renameReadingList(accessToken: String, readingListId: String, title: String)

    suspend fun deleteReadingList(accessToken: String, readingListId: String)

    /** Adds an issue at the end of the list (`update-by-chapter`). */
    suspend fun addChapterToReadingList(accessToken: String, readingListId: String, chapterId: String)

    suspend fun removeReadingListEntry(accessToken: String, readingListId: String, entryId: String)

    /** Moves an issue from [fromPosition] to [toPosition] (0 first). */
    suspend fun moveReadingListEntry(accessToken: String, readingListId: String, entryId: String, fromPosition: Int, toPosition: Int)

    /** Puts the series on Kavita's Want to read (`api/want-to-read/add-series`). */
    suspend fun addToWantToRead(accessToken: String, seriesIds: Collection<String>)

    /** Takes the series off Kavita's Want to read (`api/want-to-read/remove-series`). */
    suspend fun removeFromWantToRead(accessToken: String, seriesIds: Collection<String>)
}

class KtorKavitaListsApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : KavitaListsApi {
    private val baseUrl = baseUrl.trimEnd('/')

    override suspend fun getReadingLists(accessToken: String): List<KavitaReadingList> = authenticatedRequest {
        val response = httpClient.post("$baseUrl/api/ReadingList/lists") {
            bearerAuth(accessToken)
            url {
                parameters.append("PageNumber", "0")
                parameters.append("PageSize", "10000")
                parameters.append("includePromoted", "true")
                parameters.append("sortByLastModified", "false")
            }
        }
        response.throwIfKavitaError()
        response.body<List<KavitaReadingListInfoDto>>().mapNotNull(KavitaReadingListInfoDto::toReadingList)
    }

    override suspend fun getReadingListEntries(accessToken: String, readingListId: String): List<KavitaReadingListEntry> =
        authenticatedRequest {
            val response = httpClient.get("$baseUrl/api/ReadingList/items") {
                bearerAuth(accessToken)
                url { parameters.append("readingListId", readingListId) }
            }
            response.throwIfKavitaError()
            response.body<List<KavitaReadingListEntryDto>>()
                .mapNotNull { entry ->
                    KavitaReadingListEntry(
                        id = entry.id?.toString() ?: return@mapNotNull null,
                        order = entry.order ?: 0,
                        chapterId = entry.chapterId?.toString() ?: return@mapNotNull null,
                    )
                }
                .sortedBy(KavitaReadingListEntry::order)
        }

    override suspend fun createReadingList(accessToken: String, title: String): KavitaReadingList = authenticatedRequest {
        val response = httpClient.post("$baseUrl/api/ReadingList/create") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(KavitaCreateReadingListDto(title = title))
        }
        response.throwIfKavitaError()
        response.body<KavitaReadingListInfoDto>().toReadingList() ?: error("Kavita did not return the new reading list.")
    }

    override suspend fun renameReadingList(accessToken: String, readingListId: String, title: String) {
        authenticatedRequest {
            val current = httpClient.get("$baseUrl/api/ReadingList") {
                bearerAuth(accessToken)
                url { parameters.append("readingListId", readingListId) }
            }
            current.throwIfKavitaError()
            val list = current.body<KavitaReadingListInfoDto>()
            val response = httpClient.post("$baseUrl/api/ReadingList/update") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    KavitaUpdateReadingListDto(
                        readingListId = readingListId.toInt(),
                        title = title,
                        summary = list.summary.orEmpty(),
                        promoted = list.promoted == true,
                        coverImageLocked = list.coverImageLocked == true,
                        startingMonth = list.startingMonth ?: 0,
                        startingYear = list.startingYear ?: 0,
                        endingMonth = list.endingMonth ?: 0,
                        endingYear = list.endingYear ?: 0,
                        tags = list.tags.orEmpty().mapNotNull(KavitaReadingListTagDto::title),
                    ),
                )
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun deleteReadingList(accessToken: String, readingListId: String) {
        authenticatedRequest {
            val response = httpClient.delete("$baseUrl/api/ReadingList") {
                bearerAuth(accessToken)
                url { parameters.append("readingListId", readingListId) }
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun addChapterToReadingList(accessToken: String, readingListId: String, chapterId: String) {
        authenticatedRequest {
            val response = httpClient.post("$baseUrl/api/ReadingList/update-by-chapter") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                // Kavita adds by the chapter; the series id it also takes isn't used for that.
                setBody(KavitaReadingListChapterDto(readingListId = readingListId.toInt(), chapterId = chapterId.toInt(), seriesId = 0))
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun removeReadingListEntry(accessToken: String, readingListId: String, entryId: String) {
        authenticatedRequest {
            val response = httpClient.post("$baseUrl/api/ReadingList/delete-item") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    KavitaReadingListPositionDto(
                        readingListId = readingListId.toInt(),
                        readingListItemId = entryId.toInt(),
                        fromPosition = 0,
                        toPosition = 0,
                    ),
                )
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun moveReadingListEntry(
        accessToken: String,
        readingListId: String,
        entryId: String,
        fromPosition: Int,
        toPosition: Int,
    ) {
        authenticatedRequest {
            val response = httpClient.post("$baseUrl/api/ReadingList/update-position") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    KavitaReadingListPositionDto(
                        readingListId = readingListId.toInt(),
                        readingListItemId = entryId.toInt(),
                        fromPosition = fromPosition,
                        toPosition = toPosition,
                    ),
                )
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun addToWantToRead(accessToken: String, seriesIds: Collection<String>) =
        postWantToRead("add-series", accessToken, seriesIds)

    override suspend fun removeFromWantToRead(accessToken: String, seriesIds: Collection<String>) =
        postWantToRead("remove-series", accessToken, seriesIds)

    private suspend fun postWantToRead(path: String, accessToken: String, seriesIds: Collection<String>) {
        val ids = seriesIds.mapNotNull(String::toIntOrNull).distinct()
        if (ids.isEmpty()) return
        authenticatedRequest {
            val response = httpClient.post("$baseUrl/api/want-to-read/$path") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(KavitaWantToReadRequestDto(seriesIds = ids))
            }
            response.throwIfKavitaError()
        }
    }
}

@Serializable
internal data class KavitaReadingListInfoDto(
    val id: Int? = null,
    val title: String? = null,
    val summary: String? = null,
    val promoted: Boolean? = null,
    val coverImageLocked: Boolean? = null,
    val itemCount: Int? = null,
    val startingMonth: Int? = null,
    val startingYear: Int? = null,
    val endingMonth: Int? = null,
    val endingYear: Int? = null,
    val ownerUserName: String? = null,
    val tags: List<KavitaReadingListTagDto>? = null,
)

@Serializable
internal data class KavitaReadingListTagDto(
    val title: String? = null,
)

@Serializable
internal data class KavitaReadingListEntryDto(
    val id: Int? = null,
    val order: Int? = null,
    val chapterId: Int? = null,
)

@Serializable
internal data class KavitaCreateReadingListDto(
    val title: String,
)

@Serializable
internal data class KavitaUpdateReadingListDto(
    val readingListId: Int,
    val title: String,
    val summary: String,
    val promoted: Boolean,
    val coverImageLocked: Boolean,
    val startingMonth: Int,
    val startingYear: Int,
    val endingMonth: Int,
    val endingYear: Int,
    val tags: List<String>,
)

@Serializable
internal data class KavitaReadingListChapterDto(
    val readingListId: Int,
    val chapterId: Int,
    val seriesId: Int,
)

// No defaults: a default isn't sent, and Kavita requires the positions even when they are 0.
@Serializable
internal data class KavitaReadingListPositionDto(
    val readingListId: Int,
    val readingListItemId: Int,
    val fromPosition: Int,
    val toPosition: Int,
)

internal fun KavitaReadingListInfoDto.toReadingList(): KavitaReadingList? {
    val listId = id?.toString() ?: return null
    return KavitaReadingList(
        id = listId,
        title = title?.takeIf(String::isNotBlank) ?: "Reading list $listId",
        itemCount = itemCount?.coerceAtLeast(0) ?: 0,
        promoted = promoted == true,
        ownerUserName = ownerUserName?.takeIf(String::isNotBlank),
    )
}

@Serializable
internal data class KavitaWantToReadRequestDto(
    val seriesIds: List<Int>,
)
