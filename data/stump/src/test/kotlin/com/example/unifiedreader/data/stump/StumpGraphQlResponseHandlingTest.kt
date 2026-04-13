package com.example.unifiedreader.data.stump

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StumpGraphQlResponseHandlingTest {
    @Test
    fun `strict response handling rejects graphql errors`() {
        val response = StumpGraphQlResponseDto(
            data = buildJsonObject {
                put("seriesById", JsonNull)
            },
            errors = listOf(
                StumpGraphQlErrorDto(
                    message = "thumbnail resolver failed",
                    path = listOf(JsonPrimitive("seriesById"), JsonPrimitive("media"), JsonPrimitive(0), JsonPrimitive("thumbnail")),
                ),
            ),
        )

        val error = runCatching {
            response.requireGraphQlData()
        }.exceptionOrNull()

        require(error is StumpGraphQlException)
        assertEquals("seriesById.media.0.thumbnail: thumbnail resolver failed", error.message)
    }

    @Test
    fun `partial response handling preserves data and reports graphql errors`() {
        val data = buildJsonObject {
            put("seriesById", JsonNull)
        }
        val response = StumpGraphQlResponseDto(
            data = data,
            errors = listOf(
                StumpGraphQlErrorDto(
                    message = "read history resolver failed",
                    path = listOf(JsonPrimitive("seriesById"), JsonPrimitive("media"), JsonPrimitive(1), JsonPrimitive("readHistory")),
                ),
            ),
        )

        val result = response.requireGraphQlData(allowPartialData = true)

        assertSame(data, result.data)
        assertEquals(1, result.errors.size)
        assertEquals("seriesById.media.1.readHistory: read history resolver failed", result.errors.single().displayMessage)
    }

    @Test
    fun `partial response handling still treats auth errors as unauthorized`() {
        val response = StumpGraphQlResponseDto(
            data = buildJsonObject {
                put("seriesById", JsonNull)
            },
            errors = listOf(
                StumpGraphQlErrorDto(message = "not authenticated"),
            ),
        )

        val error = runCatching {
            response.requireGraphQlData(allowPartialData = true)
        }.exceptionOrNull()

        require(error is StumpUnauthorizedException)
    }

    @Test
    fun `partial response handling treats auth extension codes as unauthorized`() {
        val response = StumpGraphQlResponseDto(
            data = buildJsonObject {
                put("seriesById", JsonNull)
            },
            errors = listOf(
                StumpGraphQlErrorDto(
                    message = "resolver failed",
                    extensions = buildJsonObject {
                        put("code", "UNAUTHENTICATED")
                    },
                ),
            ),
        )

        val error = runCatching {
            response.requireGraphQlData(allowPartialData = true)
        }.exceptionOrNull()

        require(error is StumpUnauthorizedException)
    }
}
