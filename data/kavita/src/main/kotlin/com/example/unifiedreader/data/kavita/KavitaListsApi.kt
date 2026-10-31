package com.example.unifiedreader.data.kavita

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/** Kavita's lists that the app writes to: the signed-in user's Want to read. */
interface KavitaListsApi {
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
internal data class KavitaWantToReadRequestDto(
    val seriesIds: List<Int>,
)
