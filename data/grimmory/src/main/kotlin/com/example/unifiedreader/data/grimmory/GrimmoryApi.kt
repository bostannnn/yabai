package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.network.writeBodyTo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface GrimmoryApi {
    suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession

    suspend fun refreshSession(
        refreshToken: String,
    ): GrimmorySession = error("Refreshing Grimmory sessions is not implemented.")

    suspend fun getLibraries(
        accessToken: String,
    ): List<Library>

    suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem>

    suspend fun getBookEntries(
        accessToken: String,
        libraryId: String,
    ): List<GrimmoryBookCatalogEntry> = getBooks(
        accessToken = accessToken,
        libraryId = libraryId,
    ).map { item ->
        GrimmoryBookCatalogEntry(
            item = item,
            progress = null,
        )
    }

    suspend fun getAllBookEntries(
        accessToken: String,
    ): List<GrimmoryBookCatalogEntry> = error("Fetching Grimmory books with progress is not implemented.")

    suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? = error("Fetching a Grimmory book with progress is not implemented.")

    fun bookContentUrl(
        bookId: String,
        bookType: String? = null,
    ): String

    fun bookCoverUrl(
        bookId: String,
    ): String

    suspend fun updateEpubProgress(
        accessToken: String,
        progress: GrimmoryEpubProgressUpdate,
    ) {
        updateProgress(
            accessToken = accessToken,
            progress = progress.toGenericProgressUpdate(),
        )
    }

    suspend fun updateProgress(
        accessToken: String,
        progress: GrimmoryProgressUpdate,
    ): Unit = error("Updating Grimmory progress is not implemented.")

    suspend fun updatePersonalRating(
        accessToken: String,
        bookId: String,
        rating: Int,
    ): GrimmoryPersonalRatingResponseDto = error("Updating Grimmory rating is not implemented.")

    suspend fun resetPersonalRating(
        accessToken: String,
        bookId: String,
    ): GrimmoryPersonalRatingResponseDto? = error("Resetting Grimmory rating is not implemented.")

    suspend fun getReadingSessionsForBook(
        accessToken: String,
        bookId: String,
        pageSize: Int = 100,
    ): List<GrimmoryReadingSessionDto> = error("Fetching Grimmory reading sessions is not implemented.")

    suspend fun getBookNotesV2(
        accessToken: String,
        bookId: String,
    ): List<GrimmoryBookNoteV2Dto> = error("Fetching Grimmory book notes is not implemented.")

    suspend fun getNotebookEntriesExport(
        accessToken: String,
        types: List<String> = emptyList(),
        search: String? = null,
        sort: String = "desc",
    ): List<GrimmoryNotebookEntryDto> = error("Fetching Grimmory notebook entries is not implemented.")

    suspend fun createBookNoteV2(
        accessToken: String,
        bookId: String,
        cfi: String,
        noteContent: String,
        chapterTitle: String,
        selectedText: String? = null,
        color: String? = null,
    ): GrimmoryBookNoteV2Dto = error("Creating Grimmory book notes is not implemented.")

    suspend fun updateBookNoteV2(
        accessToken: String,
        noteId: String,
        noteContent: String,
        chapterTitle: String,
        color: String? = null,
    ): GrimmoryBookNoteV2Dto = error("Updating Grimmory book notes is not implemented.")

    suspend fun deleteBookNoteV2(
        accessToken: String,
        noteId: String,
    ): Unit = error("Deleting Grimmory book notes is not implemented.")

    suspend fun downloadBookContent(
        accessToken: String,
        bookId: String,
        destination: File,
        bookType: String? = null,
    ): Unit = error("Downloading Grimmory book content is not implemented.")

    suspend fun downloadBookCover(
        accessToken: String,
        bookId: String,
        coverUrl: String? = null,
    ): ByteArray = error("Downloading Grimmory book covers is not implemented.")
}

data class GrimmorySession(
    val accessToken: String,
    val refreshToken: String,
    val isDefaultPassword: Boolean = false,
)

data class GrimmoryEpubProgressUpdate(
    val bookId: String,
    val bookFileId: String?,
    val cfi: String,
    val href: String? = null,
    val percent: Double,
    val ttsPositionCfi: String? = null,
)

data class GrimmoryProgressUpdate(
    val bookId: String,
    val bookFileId: String?,
    val bookType: String?,
    val positionData: String,
    val positionHref: String? = null,
    val percent: Double,
    val ttsPositionCfi: String? = null,
    val isFinished: Boolean = false,
    val clearsFinishedState: Boolean = false,
    val updatedAtEpochMillis: Long? = null,
)

data class GrimmoryBookCatalogEntry(
    val item: ReadableItem,
    val progress: com.example.unifiedreader.core.model.ReadingProgress?,
    val isExplicitUnreadReset: Boolean = false,
    val personalRating: Int? = null,
    val readStatus: String? = null,
    val dateFinished: String? = null,
    val lastReadTime: String? = null,
)

class GrimmoryUnauthorizedException : RuntimeException("Grimmory access token expired.")

class KtorGrimmoryApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : GrimmoryApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun login(
        username: String,
        password: String,
    ): GrimmorySession {
        val response = httpClient.post("$normalizedBaseUrl/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(GrimmoryLoginRequestDto(username = username, password = password))
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(responseBody.toGrimmoryErrorMessage(response.status.description))
        }
        return Json.decodeFromString<JsonObject>(responseBody).toLoginSession()
    }

    override suspend fun refreshSession(
        refreshToken: String,
    ): GrimmorySession {
        val response = httpClient.post("$normalizedBaseUrl/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(GrimmoryRefreshTokenRequestDto(refreshToken = refreshToken))
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(responseBody.toGrimmoryErrorMessage(response.status.description))
        }
        return Json.decodeFromString<JsonObject>(responseBody).toLoginSession()
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v1/libraries") {
            bearerAuth(accessToken)
        }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryLibraryDto>>()
            .mapNotNull(GrimmoryLibraryDto::toDomain)
    }

    override suspend fun getBooks(
        accessToken: String,
        libraryId: String,
    ): List<ReadableItem> = getBookEntries(
        accessToken = accessToken,
        libraryId = libraryId,
    ).map(GrimmoryBookCatalogEntry::item)

    override suspend fun getBookEntries(
        accessToken: String,
        libraryId: String,
    ): List<GrimmoryBookCatalogEntry> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v1/libraries/$libraryId/book") {
            bearerAuth(accessToken)
        }
        response.throwIfGrimmoryError()
            response.body<List<GrimmoryBookDto>>()
                .mapNotNull { book ->
                    book.toCatalogEntry(
                        baseUrl = normalizedBaseUrl,
                    )
                }
    }

    override suspend fun getAllBookEntries(
        accessToken: String,
    ): List<GrimmoryBookCatalogEntry> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v1/books") {
            bearerAuth(accessToken)
            url {
                parameters.append("withDescription", "false")
                parameters.append("stripForListView", "true")
            }
        }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryBookDto>>()
            .mapNotNull { book ->
                book.toCatalogEntry(
                    baseUrl = normalizedBaseUrl,
                )
            }
    }

    override suspend fun getBookEntry(
        accessToken: String,
        bookId: String,
    ): GrimmoryBookCatalogEntry? = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v1/books/$bookId") {
            bearerAuth(accessToken)
            url {
                parameters.append("withDescription", "false")
            }
        }
        response.throwIfGrimmoryError()
        response.body<GrimmoryBookDto>().toCatalogEntry(
            baseUrl = normalizedBaseUrl,
        )
    }

    override fun bookContentUrl(
        bookId: String,
        bookType: String?,
    ): String = buildString {
        append(normalizedBaseUrl)
        append("/api/v1/books/")
        append(bookId)
        append("/content")
        if (!bookType.isNullOrBlank()) {
            append("?bookType=")
            append(bookType)
        }
    }

    override fun bookCoverUrl(
        bookId: String,
    ): String = "$normalizedBaseUrl/api/v1/media/book/$bookId/thumbnail"

    override suspend fun updateEpubProgress(
        accessToken: String,
        progress: GrimmoryEpubProgressUpdate,
    ) {
        updateProgress(
            accessToken = accessToken,
            progress = progress.toGenericProgressUpdate(),
        )
    }

    override suspend fun updateProgress(
        accessToken: String,
        progress: GrimmoryProgressUpdate,
    ) {
        authenticatedRequest {
            val response = httpClient.post("$normalizedBaseUrl/api/v1/books/progress") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(progress.toRequestDto())
            }
            if (!response.status.isSuccess()) {
                response.throwIfGrimmoryError()
            }
        }
    }

    override suspend fun updatePersonalRating(
        accessToken: String,
        bookId: String,
        rating: Int,
    ): GrimmoryPersonalRatingResponseDto = authenticatedRequest {
        val response = httpClient.put("$normalizedBaseUrl/api/v1/books/personal-rating") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                GrimmoryPersonalRatingRequestDto(
                    ids = listOf(bookId.toLong()),
                    rating = rating.coerceIn(1, 10),
                ),
            )
        }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryPersonalRatingResponseDto>>().firstOrNull()
            ?: GrimmoryPersonalRatingResponseDto(bookId = bookId.toLong(), personalRating = rating)
    }

    override suspend fun resetPersonalRating(
        accessToken: String,
        bookId: String,
    ): GrimmoryPersonalRatingResponseDto? = authenticatedRequest {
        val response = httpClient.post("$normalizedBaseUrl/api/v1/books/reset-personal-rating") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(listOf(bookId.toLong()))
        }
        response.throwIfGrimmoryError()
        response.body<List<GrimmoryPersonalRatingResponseDto>>().firstOrNull()
    }

    override suspend fun getReadingSessionsForBook(
        accessToken: String,
        bookId: String,
        pageSize: Int,
    ): List<GrimmoryReadingSessionDto> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v1/reading-sessions/book/$bookId") {
            bearerAuth(accessToken)
            url {
                parameters.append("page", "0")
                parameters.append("size", pageSize.coerceIn(1, 100).toString())
            }
        }
        response.throwIfGrimmoryError()
        response.body<GrimmoryReadingSessionPageDto>().content.orEmpty()
    }

    override suspend fun getBookNotesV2(
        accessToken: String,
        bookId: String,
    ): List<GrimmoryBookNoteV2Dto> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v2/book-notes/book/$bookId") {
            bearerAuth(accessToken)
        }
        response.throwIfGrimmoryError()
        response.body()
    }

    override suspend fun getNotebookEntriesExport(
        accessToken: String,
        types: List<String>,
        search: String?,
        sort: String,
    ): List<GrimmoryNotebookEntryDto> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/v1/notebook/export") {
            bearerAuth(accessToken)
            url {
                types.forEach { type -> parameters.append("types", type) }
                search?.let { value -> parameters.append("search", value) }
                parameters.append("sort", sort)
            }
        }
        response.throwIfGrimmoryError()
        response.body()
    }

    override suspend fun createBookNoteV2(
        accessToken: String,
        bookId: String,
        cfi: String,
        noteContent: String,
        chapterTitle: String,
        selectedText: String?,
        color: String?,
    ): GrimmoryBookNoteV2Dto = authenticatedRequest {
        val response = httpClient.post("$normalizedBaseUrl/api/v2/book-notes") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                GrimmoryCreateBookNoteV2RequestDto(
                    bookId = bookId.toLong(),
                    cfi = cfi,
                    selectedText = selectedText,
                    noteContent = noteContent,
                    color = color,
                    chapterTitle = chapterTitle,
                ),
            )
        }
        response.throwIfGrimmoryError()
        response.body()
    }

    override suspend fun updateBookNoteV2(
        accessToken: String,
        noteId: String,
        noteContent: String,
        chapterTitle: String,
        color: String?,
    ): GrimmoryBookNoteV2Dto = authenticatedRequest {
        val response = httpClient.put("$normalizedBaseUrl/api/v2/book-notes/$noteId") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                GrimmoryUpdateBookNoteV2RequestDto(
                    noteContent = noteContent,
                    color = color,
                    chapterTitle = chapterTitle,
                ),
            )
        }
        response.throwIfGrimmoryError()
        response.body()
    }

    override suspend fun deleteBookNoteV2(
        accessToken: String,
        noteId: String,
    ) {
        authenticatedRequest {
            val response = httpClient.delete("$normalizedBaseUrl/api/v2/book-notes/$noteId") {
                bearerAuth(accessToken)
            }
            response.throwIfGrimmoryError()
        }
    }

    override suspend fun downloadBookContent(
        accessToken: String,
        bookId: String,
        destination: File,
        bookType: String?,
    ) {
        authenticatedRequest {
            httpClient.prepareGet {
                url.takeFrom(bookContentUrl(bookId = bookId, bookType = bookType))
                bearerAuth(accessToken)
            }.execute { response ->
                response.throwIfGrimmoryError()
                response.writeBodyTo(destination)
            }
        }
    }

    override suspend fun downloadBookCover(
        accessToken: String,
        bookId: String,
        coverUrl: String?,
    ): ByteArray = authenticatedRequest {
        val response = httpClient.get(
            coverUrl.toGrimmoryCoverRequestUrl(bookId = bookId)
                .withGrimmoryMediaToken(accessToken),
        )
        response.throwIfGrimmoryError()
        response.body<ByteArray>()
    }

    private fun String?.toGrimmoryCoverRequestUrl(
        bookId: String,
    ): String {
        val requestedUrl = this?.takeIf(String::isNotBlank)
            ?: return bookCoverUrl(bookId = bookId)
        return runCatching {
            val parsedUrl = Url(requestedUrl)
            val serverUrl = Url(normalizedBaseUrl)
            if (
                parsedUrl.protocol == serverUrl.protocol &&
                parsedUrl.host == serverUrl.host &&
                parsedUrl.port == serverUrl.port
            ) {
                requestedUrl
            } else {
                bookCoverUrl(bookId = bookId)
            }
        }.getOrElse {
            bookCoverUrl(bookId = bookId)
        }
    }

    private fun String.withGrimmoryMediaToken(
        accessToken: String,
    ): String = URLBuilder(this).apply {
        parameters.append("token", accessToken)
    }.buildString()
}

private val grimmoryJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun String.toGrimmoryErrorMessage(
    statusDescription: String,
): String = runCatching {
    grimmoryJson.decodeFromString<GrimmoryApiErrorDto>(this).toUserMessage(statusDescription)
}.getOrElse {
    takeIf(String::isNotBlank) ?: statusDescription
}

private suspend fun <T> authenticatedRequest(
    block: suspend () -> T,
): T = try {
    block()
} catch (exception: ClientRequestException) {
    if (exception.response.status == HttpStatusCode.Unauthorized) {
        throw GrimmoryUnauthorizedException()
    }
    throw exception
}

private suspend fun HttpResponse.throwIfGrimmoryError() {
    if (status == HttpStatusCode.Unauthorized) {
        throw GrimmoryUnauthorizedException()
    }
    if (!status.isSuccess()) {
        error(bodyAsText().toGrimmoryErrorMessage(status.description))
    }
}
