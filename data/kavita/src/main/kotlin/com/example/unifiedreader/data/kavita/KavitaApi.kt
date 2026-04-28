package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.network.writeBodyTo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import java.io.File

interface KavitaApi {
    suspend fun login(
        username: String,
        password: String,
    ): KavitaSession

    suspend fun refreshSession(
        refreshToken: String,
        accessToken: String? = null,
        apiKey: String? = null,
    ): KavitaSession

    suspend fun getLibraries(
        accessToken: String,
    ): List<Library>

    suspend fun getCurrentUserId(
        accessToken: String,
    ): Int? = error("Fetching the current Kavita user is not implemented.")

    suspend fun getSeries(
        accessToken: String,
        apiKey: String? = null,
    ): List<KavitaSeriesEntry>

    suspend fun getRecentlyUpdatedSeries(
        accessToken: String,
    ): List<KavitaSeriesUpdateEntry> = emptyList()

    suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry>

    suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto

    suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean = false,
    ): KavitaChapterInfoDto

    suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    )

    suspend fun updateChapterRating(
        accessToken: String,
        seriesId: Int,
        chapterId: Int,
        rating: Float,
    ) {
        error("Updating Kavita chapter ratings is not implemented.")
    }

    suspend fun updateChapterReview(
        accessToken: String,
        seriesId: Int,
        chapterId: Int,
        body: String?,
    ): KavitaUserReviewDto = error("Updating Kavita chapter reviews is not implemented.")

    suspend fun deleteChapterReview(
        accessToken: String,
        chapterId: Int,
    ) {
        error("Deleting Kavita chapter reviews is not implemented.")
    }

    suspend fun getAllReviews(
        accessToken: String,
        userId: Int? = null,
    ): List<KavitaUserReviewDto> = error("Fetching Kavita reviews is not implemented.")

    suspend fun getReadingHistory(
        accessToken: String,
        libraryId: Int,
    ): List<KavitaReadingHistoryItemDto> = error("Fetching Kavita reading history is not implemented.")

    suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray

    suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray

    suspend fun downloadCover(
        accessToken: String,
        coverUrl: String,
        apiKey: String?,
    ): ByteArray

    suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
        destination: File,
    )
}

data class KavitaSession(
    val accessToken: String,
    val refreshToken: String?,
    val apiKey: String?,
)

data class KavitaSeriesEntry(
    val id: String,
    val libraryId: String,
    val title: String,
    val format: com.example.unifiedreader.core.model.FormatType,
    val pages: Int? = null,
    val addedAtEpochMillis: Long?,
    val lastChapterAddedAtEpochMillis: Long? = null,
    val coverUrl: String?,
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
)

data class KavitaSeriesUpdateEntry(
    val seriesId: String,
    val libraryId: String?,
    val createdAtEpochMillis: Long?,
    val chapterId: String?,
    val volumeId: String?,
    val count: Int?,
)

data class KavitaChapterCatalogEntry(
    val item: com.example.unifiedreader.core.model.ReadableItem,
    val progress: com.example.unifiedreader.core.model.ReadingProgress?,
    val isExplicitUnreadReset: Boolean = false,
)

data class KavitaProgressUpdate(
    val libraryId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val chapterId: Int,
    val pageNum: Int,
    val updatedAtEpochMillis: Long,
)

class KavitaUnauthorizedException : RuntimeException("Kavita access token expired.")

class KtorKavitaApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : KavitaApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private var activeApiKey: String? = null

    override suspend fun login(
        username: String,
        password: String,
    ): KavitaSession {
        val response = httpClient.post("$normalizedBaseUrl/api/Account/login") {
            contentType(ContentType.Application.Json)
            setBody(
                KavitaLoginRequestDto(
                    username = username,
                    password = password,
                ),
            )
        }
        response.throwIfKavitaError()
        return response.body<KavitaUserDto>().toSession().also { session ->
            activeApiKey = session.apiKey
        }
    }

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession {
        val response = httpClient.post("$normalizedBaseUrl/api/Account/refresh-token") {
            contentType(ContentType.Application.Json)
            setBody(
                KavitaTokenRequestDto(
                    token = accessToken,
                    refreshToken = refreshToken,
                ),
            )
        }
        response.throwIfKavitaError()
        return response.body<KavitaTokenRequestDto>().toSession(
            apiKey = apiKey ?: activeApiKey,
        ).also { session ->
            activeApiKey = session.apiKey
        }
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Library/libraries") {
            bearerAuth(accessToken)
        }
        response.throwIfKavitaError()
        response.body<List<KavitaLibraryDto>>()
            .mapNotNull(KavitaLibraryDto::toDomain)
    }

    override suspend fun getCurrentUserId(
        accessToken: String,
    ): Int? = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Account") {
            bearerAuth(accessToken)
        }
        response.throwIfKavitaError()
        response.body<KavitaUserDto>().id
    }

    override suspend fun getSeries(
        accessToken: String,
        apiKey: String?,
    ): List<KavitaSeriesEntry> = authenticatedRequest {
        val resolvedApiKey = apiKey?.takeIf(String::isNotBlank) ?: activeApiKey
        if (!resolvedApiKey.isNullOrBlank()) {
            activeApiKey = resolvedApiKey
        }
        val response = httpClient.post("$normalizedBaseUrl/api/Series/all-v2") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            url {
                parameters.append("PageNumber", "0")
                parameters.append("PageSize", "10000")
                parameters.append("context", "1")
            }
            setBody(KavitaFilterV2Dto())
        }
        response.throwIfKavitaError()
        response.body<List<KavitaSeriesDto>>()
            .mapNotNull { series ->
                series.toEntry(
                    baseUrl = normalizedBaseUrl,
                    apiKey = resolvedApiKey,
                )
            }
    }

    override suspend fun getRecentlyUpdatedSeries(
        accessToken: String,
    ): List<KavitaSeriesUpdateEntry> = authenticatedRequest {
        val response = httpClient.post("$normalizedBaseUrl/api/Series/recently-updated-series") {
            bearerAuth(accessToken)
            url {
                parameters.append("PageNumber", "0")
                parameters.append("PageSize", "10000")
            }
        }
        response.throwIfKavitaError()
        response.body<List<KavitaGroupedSeriesDto>>()
            .mapNotNull(KavitaGroupedSeriesDto::toUpdateEntry)
    }

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Series/series-detail") {
            bearerAuth(accessToken)
            url {
                parameters.append("seriesId", series.id)
            }
        }
        response.throwIfKavitaError()
        val detail = response.body<KavitaSeriesDetailDto>()
        val issueTitleOverridesByChapterId = detail.resolveSupplementalIssueTitles { chapterId ->
            getChapterInfo(
                accessToken = accessToken,
                chapterId = chapterId,
                includeDimensions = true,
            )
        }
        detail.toCatalogEntries(
            series = series,
            issueTitleOverridesByChapterId = issueTitleOverridesByChapterId,
        )
    }

    override suspend fun getProgress(
        accessToken: String,
        chapterId: String,
    ): KavitaProgressDto = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Reader/get-progress") {
            bearerAuth(accessToken)
            url {
                parameters.append("chapterId", chapterId)
            }
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Reader/chapter-info") {
            bearerAuth(accessToken)
            url {
                parameters.append("chapterId", chapterId)
                parameters.append("includeDimensions", includeDimensions.toString())
            }
        }
        response.throwIfKavitaError()
        if (response.status == HttpStatusCode.NoContent) {
            return@authenticatedRequest KavitaChapterInfoDto()
        }
        response.body()
    }

    override suspend fun updateProgress(
        accessToken: String,
        progress: KavitaProgressUpdate,
    ) {
        authenticatedRequest {
            val response = httpClient.post("$normalizedBaseUrl/api/Reader/progress") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    KavitaProgressDto(
                        libraryId = progress.libraryId,
                        seriesId = progress.seriesId,
                        volumeId = progress.volumeId,
                        chapterId = progress.chapterId,
                        pageNum = progress.pageNum,
                        lastModifiedUtc = java.time.Instant.ofEpochMilli(progress.updatedAtEpochMillis).toString(),
                    ),
                )
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun updateChapterRating(
        accessToken: String,
        seriesId: Int,
        chapterId: Int,
        rating: Float,
    ) {
        authenticatedRequest {
            val response = httpClient.post("$normalizedBaseUrl/api/Rating/chapter") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    KavitaUpdateRatingDto(
                        seriesId = seriesId,
                        chapterId = chapterId,
                        userRating = rating.coerceIn(0f, 5f),
                    ),
                )
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun updateChapterReview(
        accessToken: String,
        seriesId: Int,
        chapterId: Int,
        body: String?,
    ): KavitaUserReviewDto = authenticatedRequest {
        val response = httpClient.post("$normalizedBaseUrl/api/Review/chapter") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                KavitaUpdateUserReviewDto(
                    seriesId = seriesId,
                    chapterId = chapterId,
                    body = body,
                ),
            )
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun deleteChapterReview(
        accessToken: String,
        chapterId: Int,
    ) {
        authenticatedRequest {
            val response = httpClient.delete("$normalizedBaseUrl/api/Review/chapter") {
                bearerAuth(accessToken)
                url {
                    parameters.append("chapterId", chapterId.toString())
                }
            }
            response.throwIfKavitaError()
        }
    }

    override suspend fun getAllReviews(
        accessToken: String,
        userId: Int?,
    ): List<KavitaUserReviewDto> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Review/all") {
            bearerAuth(accessToken)
            userId?.let { id ->
                url {
                    parameters.append("userId", id.toString())
                }
            }
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun getReadingHistory(
        accessToken: String,
        libraryId: Int,
    ): List<KavitaReadingHistoryItemDto> = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Stats/reading-history") {
            bearerAuth(accessToken)
            url {
                parameters.append("Libraries", libraryId.toString())
                parameters.append("PageNumber", "0")
                parameters.append("PageSize", "10000")
            }
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Reader/image") {
            bearerAuth(accessToken)
            apiKey?.takeIf(String::isNotBlank)?.let { key ->
                header("x-api-key", key)
            }
            url {
                parameters.append("chapterId", chapterId)
                parameters.append("page", pageIndex.toString())
                apiKey?.takeIf(String::isNotBlank)?.let { key ->
                    parameters.append("apiKey", key)
                }
            }
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray = authenticatedRequest {
        val response = httpClient.get("$normalizedBaseUrl/api/Reader/thumbnail") {
            bearerAuth(accessToken)
            apiKey?.takeIf(String::isNotBlank)?.let { key ->
                header("x-api-key", key)
            }
            url {
                parameters.append("chapterId", chapterId)
                parameters.append("pageNum", pageNumber.toString())
                apiKey?.takeIf(String::isNotBlank)?.let { key ->
                    parameters.append("apiKey", key)
                }
            }
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun downloadCover(
        accessToken: String,
        coverUrl: String,
        apiKey: String?,
    ): ByteArray = authenticatedRequest {
        val sameOriginUrl = coverUrl.resolveAgainst(normalizedBaseUrl)
            .takeIf { candidate -> candidate.isSameOriginAs(normalizedBaseUrl) }
            ?: error("Refusing to send Kavita credentials to a different host.")
        val response = httpClient.get(sameOriginUrl) {
            bearerAuth(accessToken)
            apiKey?.takeIf(String::isNotBlank)?.let { key ->
                header("x-api-key", key)
            }
        }
        response.throwIfKavitaError()
        response.body()
    }

    override suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
        destination: File,
    ) {
        authenticatedRequest {
            httpClient.prepareGet {
                url.takeFrom("$normalizedBaseUrl/api/Download/chapter")
                bearerAuth(accessToken)
                apiKey?.takeIf(String::isNotBlank)?.let { key ->
                    header("x-api-key", key)
                }
                url {
                    parameters.append("chapterId", chapterId)
                    apiKey?.takeIf(String::isNotBlank)?.let { key ->
                        parameters.append("apiKey", key)
                    }
                }
            }.execute { response ->
                response.throwIfKavitaError()
                response.writeBodyTo(destination)
            }
        }
    }
}

private suspend fun <T> authenticatedRequest(
    block: suspend () -> T,
): T = try {
    block()
} catch (exception: ClientRequestException) {
    if (exception.response.status == HttpStatusCode.Unauthorized) {
        throw KavitaUnauthorizedException()
    }
    throw exception
}

private suspend fun HttpResponse.throwIfKavitaError() {
    if (status == HttpStatusCode.Unauthorized) {
        throw KavitaUnauthorizedException()
    }
    if (!status.isSuccess()) {
        error(bodyAsText().ifBlank { status.description })
    }
}

private fun String.resolveAgainst(
    baseUrl: String,
): String = runCatching {
    val baseUri = java.net.URI.create(baseUrl.trimEnd('/') + "/")
    val candidateUri = java.net.URI.create(this)
    if (candidateUri.isAbsolute) {
        candidateUri.toString()
    } else {
        baseUri.resolve(candidateUri).toString()
    }
}.getOrElse {
    error("Unable to resolve the Kavita asset URL.")
}

private fun String.isSameOriginAs(
    baseUrl: String,
): Boolean = runCatching {
    val candidate = java.net.URI.create(this)
    val base = java.net.URI.create(baseUrl)
    candidate.scheme?.equals(base.scheme, ignoreCase = true) == true &&
        candidate.host?.equals(base.host, ignoreCase = true) == true &&
        candidate.effectivePort() == base.effectivePort()
}.getOrDefault(false)

private fun java.net.URI.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    scheme.equals("http", ignoreCase = true) -> 80
    else -> -1
}
