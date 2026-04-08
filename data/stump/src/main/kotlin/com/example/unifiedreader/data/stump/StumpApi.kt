package com.example.unifiedreader.data.stump

import com.example.unifiedreader.core.model.Library
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.net.URI
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

interface StumpApi {
    suspend fun login(
        username: String,
        password: String,
    ): StumpSession

    suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession

    suspend fun getLibraries(
        accessToken: String,
    ): List<Library>

    suspend fun getSeries(
        accessToken: String,
        libraryId: String,
    ): List<StumpSeriesEntry>

    suspend fun getSeriesDetail(
        accessToken: String,
        series: StumpSeriesEntry,
    ): List<StumpMediaCatalogEntry>

    suspend fun getProgress(
        accessToken: String,
        mediaId: String,
        itemId: String,
        pageCount: Int?,
    ): com.example.unifiedreader.core.model.ReadingProgress?

    suspend fun updatePagedProgress(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    )

    suspend fun markMediaComplete(
        accessToken: String,
        mediaId: String,
        isComplete: Boolean,
        pageIndex: Int?,
    )

    suspend fun downloadPageImage(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ): ByteArray

    suspend fun downloadThumbnail(
        accessToken: String,
        mediaId: String,
    ): ByteArray

    suspend fun downloadSeriesThumbnail(
        accessToken: String,
        seriesId: String,
    ): ByteArray

    suspend fun downloadUrl(
        accessToken: String,
        url: String,
    ): ByteArray
}

data class StumpSession(
    val accessToken: String,
    val refreshToken: String?,
)

data class StumpSeriesEntry(
    val id: String,
    val libraryId: String,
    val title: String,
    val coverUrl: String?,
)

data class StumpMediaCatalogEntry(
    val item: com.example.unifiedreader.core.model.ReadableItem,
    val progress: com.example.unifiedreader.core.model.ReadingProgress?,
    val isExplicitUnreadReset: Boolean = false,
)

class StumpUnauthorizedException : RuntimeException("Stump access token expired.")

class KtorStumpApi(
    private val httpClient: HttpClient,
    baseUrl: String,
) : StumpApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/').removeTrailingStumpApiPath()
    private val restBaseUrl = "$normalizedBaseUrl/api/v2"
    private val graphQlUrl = "$normalizedBaseUrl/api/graphql"
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun login(
        username: String,
        password: String,
    ): StumpSession {
        val response = httpClient.post("$restBaseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            url {
                parameters.append("create_session", "false")
                parameters.append("generate_token", "true")
            }
            setBody(
                StumpLoginRequestDto(
                    username = username,
                    password = password,
                ),
            )
        }
        response.throwIfStumpError()
        return response.body<StumpLoginResponseDto>().toSession()
    }

    override suspend fun refreshSession(
        refreshToken: String,
    ): StumpSession {
        val response = httpClient.post("$restBaseUrl/auth/refresh-token") {
            header(HttpHeaders.Authorization, "Bearer $refreshToken")
        }
        response.throwIfStumpError()
        return response.body<StumpRefreshTokenResponseDto>().toSession()
    }

    override suspend fun getLibraries(
        accessToken: String,
    ): List<Library> {
        val data = graphQlRequest(
            accessToken = accessToken,
            query = """
                query StumpLibraries {
                  libraries(pagination: {none: {unpaginated: true}}) {
                    nodes {
                      id
                      name
                    }
                  }
                }
            """.trimIndent(),
        )
        return json.decodeFromJsonElement(StumpLibrariesQueryData.serializer(), data)
            .libraries
            ?.nodes
            .orEmpty()
            .mapNotNull(StumpLibraryNodeDto::toDomain)
    }

    override suspend fun getSeries(
        accessToken: String,
        libraryId: String,
    ): List<StumpSeriesEntry> {
        val data = graphQlRequest(
            accessToken = accessToken,
            query = """
                query StumpSeries(${'$'}libraryId: String!) {
                  series(
                    filter: {libraryId: {eq: ${'$'}libraryId}}
                    pagination: {none: {unpaginated: true}}
                  ) {
                    nodes {
                      id
                      resolvedName
                      thumbnail {
                        url
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("libraryId", libraryId)
            },
        )
        return json.decodeFromJsonElement(StumpSeriesQueryData.serializer(), data)
            .series
            ?.nodes
            .orEmpty()
            .mapNotNull { node ->
                node.toEntry(
                    baseUrl = normalizedBaseUrl,
                    libraryId = libraryId,
                )
            }
    }

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: StumpSeriesEntry,
    ): List<StumpMediaCatalogEntry> {
        val data = graphQlRequest(
            accessToken = accessToken,
            query = """
                query StumpSeriesDetail(${'$'}id: ID!) {
                  seriesById(id: ${'$'}id) {
                    id
                    resolvedName
                    media {
                      id
                      resolvedName
                      extension
                      pages
                      thumbnail {
                        url
                      }
                      metadata {
                        summary
                        title
                        volume
                        number
                        pageCount
                      }
                      readProgress {
                        page
                        percentageCompleted
                        updatedAt
                      }
                      readHistory {
                        completedAt
                      }
                    }
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("id", series.id)
            },
        )
        return json.decodeFromJsonElement(StumpSeriesDetailQueryData.serializer(), data)
            .seriesById
            ?.toCatalogEntries(
                baseUrl = normalizedBaseUrl,
                series = series,
            )
            .orEmpty()
    }

    override suspend fun getProgress(
        accessToken: String,
        mediaId: String,
        itemId: String,
        pageCount: Int?,
    ): com.example.unifiedreader.core.model.ReadingProgress? {
        val data = graphQlRequest(
            accessToken = accessToken,
            query = """
                query StumpMediaProgress(${'$'}id: ID!) {
                  mediaById(id: ${'$'}id) {
                    id
                    pages
                    readProgress {
                      page
                      percentageCompleted
                      updatedAt
                    }
                    readHistory {
                      completedAt
                    }
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("id", mediaId)
            },
        )
        return json.decodeFromJsonElement(StumpMediaProgressQueryData.serializer(), data)
            .mediaById
            ?.toReadingProgress(
                itemId = itemId,
                pageCount = pageCount,
            )
    }

    override suspend fun updatePagedProgress(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ) {
        graphQlRequest(
            accessToken = accessToken,
            query = """
                mutation StumpUpdatePagedProgress(${'$'}id: ID!, ${'$'}input: MediaProgressInput!) {
                  updateMediaProgress(id: ${'$'}id, input: ${'$'}input) {
                    __typename
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("id", mediaId)
                put(
                    "input",
                    buildJsonObject {
                        put(
                            "paged",
                            buildJsonObject {
                                put("page", pageIndex + 1)
                            },
                        )
                    },
                )
            },
        )
    }

    override suspend fun markMediaComplete(
        accessToken: String,
        mediaId: String,
        isComplete: Boolean,
        pageIndex: Int?,
    ) {
        graphQlRequest(
            accessToken = accessToken,
            query = """
                mutation StumpMarkMediaComplete(${'$'}id: ID!, ${'$'}isComplete: Boolean!, ${'$'}page: Int) {
                  markMediaAsComplete(id: ${'$'}id, isComplete: ${'$'}isComplete, page: ${'$'}page) {
                    completedAt
                  }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("id", mediaId)
                put("isComplete", isComplete)
                pageIndex?.let { put("page", it + 1) }
            },
        )
    }

    override suspend fun downloadPageImage(
        accessToken: String,
        mediaId: String,
        pageIndex: Int,
    ): ByteArray {
        val response = httpClient.get("$restBaseUrl/media/$mediaId/page/${pageIndex + 1}") {
            bearerAuth(accessToken)
        }
        response.throwIfStumpError()
        return response.body()
    }

    override suspend fun downloadThumbnail(
        accessToken: String,
        mediaId: String,
    ): ByteArray {
        val response = httpClient.get("$restBaseUrl/media/$mediaId/thumbnail") {
            bearerAuth(accessToken)
        }
        response.throwIfStumpError()
        return response.body()
    }

    override suspend fun downloadSeriesThumbnail(
        accessToken: String,
        seriesId: String,
    ): ByteArray {
        val response = httpClient.get("$restBaseUrl/series/$seriesId/thumbnail") {
            bearerAuth(accessToken)
        }
        response.throwIfStumpError()
        return response.body()
    }

    override suspend fun downloadUrl(
        accessToken: String,
        url: String,
    ): ByteArray {
        val sameOriginUrl = url.resolveAgainst(normalizedBaseUrl)
            .takeIf { candidate -> candidate.isSameOriginAs(normalizedBaseUrl) }
            ?: error("Refusing to send Stump credentials to a different host.")
        val response = httpClient.get(sameOriginUrl) {
            bearerAuth(accessToken)
        }
        response.throwIfStumpError()
        return response.body()
    }

    private suspend fun graphQlRequest(
        accessToken: String,
        query: String,
        variables: JsonObject = buildJsonObject {},
    ): JsonObject {
        val response = httpClient.post(graphQlUrl) {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                StumpGraphQlRequestDto(
                    query = query,
                    variables = variables,
                ),
            )
        }
        response.throwIfStumpError()
        val payload = response.body<StumpGraphQlResponseDto>()
        payload.errors.orEmpty()
            .mapNotNull(StumpGraphQlErrorDto::message)
            .firstOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { message ->
                if (message.isUnauthorizedGraphQlMessage()) {
                    throw StumpUnauthorizedException()
                }
                error(message)
            }
        return payload.data ?: error("Stump GraphQL response did not include data.")
    }
}

private suspend fun HttpResponse.throwIfStumpError() {
    if (status == HttpStatusCode.Unauthorized) {
        throw StumpUnauthorizedException()
    }
    if (status.isSuccess()) return
    val responseBody = bodyAsText()
    if (responseBody.contains("unauthorized", ignoreCase = true)) {
        throw StumpUnauthorizedException()
    }
    error(responseBody.ifBlank { status.description })
}

private fun String.isUnauthorizedGraphQlMessage(): Boolean = contains(
    other = "unauthorized",
    ignoreCase = true,
) || contains(
    other = "not authenticated",
    ignoreCase = true,
) || contains(
    other = "invalid token",
    ignoreCase = true,
)

private fun String.removeTrailingStumpApiPath(): String = replace(
    Regex("/api(?:/v\\d+)?$"),
    "",
)

private fun String.resolveAgainst(
    baseUrl: String,
): String = runCatching {
    val baseUri = URI.create(baseUrl.trimEnd('/') + "/")
    val candidateUri = URI.create(this)
    if (candidateUri.isAbsolute) {
        candidateUri.toString()
    } else {
        baseUri.resolve(candidateUri).toString()
    }
}.getOrElse {
    error("Unable to resolve the Stump asset URL.")
}

private fun String.isSameOriginAs(
    baseUrl: String,
): Boolean = runCatching {
    val candidate = URI.create(this)
    val base = URI.create(baseUrl)
    candidate.scheme?.equals(base.scheme, ignoreCase = true) == true &&
        candidate.host?.equals(base.host, ignoreCase = true) == true &&
        candidate.effectivePort() == base.effectivePort()
}.getOrDefault(false)

private fun URI.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("https", ignoreCase = true) -> 443
    scheme.equals("http", ignoreCase = true) -> 80
    else -> -1
}
