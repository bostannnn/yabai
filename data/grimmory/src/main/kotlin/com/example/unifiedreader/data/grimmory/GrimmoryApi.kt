package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.network.writeBodyTo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.post
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
)

data class GrimmoryBookCatalogEntry(
    val item: ReadableItem,
    val progress: com.example.unifiedreader.core.model.ReadingProgress?,
    val isExplicitUnreadReset: Boolean = false,
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
                    progressStateIncluded = false,
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
