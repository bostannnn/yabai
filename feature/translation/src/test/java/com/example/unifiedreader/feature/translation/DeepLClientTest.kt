package com.example.unifiedreader.feature.translation

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeepLClientTest {
    private val requests = mutableListOf<Pair<HttpRequestData, String>>()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): DeepLClient =
        DeepLClient(
            HttpClient(
                MockEngine { request ->
                    requests += request to String(request.body.toByteArray())
                    handler(request)
                },
            ),
        )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun `a free key goes to the free host with the word's sentence as context`() = runTest {
        val deepL = client {
            json("""{"translations":[{"detected_source_language":"EN","text":"неохотный"},{"text":"Ему не хотелось уходить."}]}""")
        }
        val sentence = "He was reluctant to leave."

        val result = deepL.translate("abc:fx", listOf("reluctant", sentence), "en", "ru", context = sentence)

        assertEquals(listOf("неохотный", "Ему не хотелось уходить."), result)
        val (request, body) = requests.single()
        assertEquals("https://api-free.deepl.com/v2/translate", request.url.toString())
        assertEquals("DeepL-Auth-Key abc:fx", request.headers["Authorization"])
        assertTrue(body, body.contains("\"source_lang\":\"EN\""))
        assertTrue(body, body.contains("\"target_lang\":\"RU\""))
        assertTrue(body, body.contains("\"context\":\"He was reluctant to leave.\""))
    }

    @Test
    fun `a pro key goes to the pro host, and English is written as American English`() = runTest {
        val deepL = client { json("""{"translations":[{"text":"reluctant"}]}""") }

        deepL.translate("abc", listOf("неохотный"), "ru", "en")

        val (request, body) = requests.single()
        assertEquals("https://api.deepl.com/v2/translate", request.url.toString())
        assertTrue(body, body.contains("\"target_lang\":\"EN-US\""))
        assertTrue(body, !body.contains("context"))
    }

    @Test
    fun `DeepL's answers become reasons`() = runTest {
        assertFailure(DeepLFailure.KeyRefused) { client { json("{}", HttpStatusCode.Forbidden) }.usage("k:fx") }
        assertFailure(DeepLFailure.LimitReached) {
            client { json("{}", HttpStatusCode(456, "Quota")) }.translate("k", listOf("a"), "en", "ru")
        }
        assertFailure(DeepLFailure.Unreachable) { client { throw IOException("offline") }.usage("k") }
        assertFailure(DeepLFailure.Unreachable) { client { json("{}", HttpStatusCode.ServiceUnavailable) }.usage("k") }
    }

    @Test
    fun `a language DeepL lacks fails before asking`() = runTest {
        val deepL = client { json("""{"translations":[{"text":"x"}]}""") }

        assertFailure(DeepLFailure.LanguageMissing) { deepL.translate("k", listOf("שלום"), "he", "ru") }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `usage reads the month's characters`() = runTest {
        val usage = client { json("""{"character_count":41200,"character_limit":500000}""") }.usage("k:fx")

        assertEquals(DeepLUsage(used = 41_200, limit = 500_000), usage)
        assertEquals("https://api-free.deepl.com/v2/usage", requests.single().first.url.toString())
    }

    @Test
    fun `languages map to DeepL's codes`() {
        assertEquals("NB", DeepLLanguages.source("no"))
        assertEquals("PT-BR", DeepLLanguages.target("pt"))
        assertEquals("ZH-HANS", DeepLLanguages.target("zh"))
        assertEquals("ZH", DeepLLanguages.source("zh"))
        assertTrue(!DeepLLanguages.supports("he"))
    }

    private suspend fun assertFailure(expected: DeepLFailure, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (error: DeepLException) {
            assertEquals(expected, error.failure)
        }
    }
}
