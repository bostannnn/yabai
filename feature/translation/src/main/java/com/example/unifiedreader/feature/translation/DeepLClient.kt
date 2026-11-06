package com.example.unifiedreader.feature.translation

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Why DeepL didn't translate; the card says it in a line, and the tablet translates instead. */
enum class DeepLFailure {
    /** No connection, or DeepL didn't answer. */
    Unreachable,

    /** DeepL refused the key (403). */
    KeyRefused,

    /** The month's characters are used up (456). */
    LimitReached,

    /** DeepL doesn't translate one of the two languages. */
    LanguageMissing,

    /** Anything else DeepL said no to. */
    Other,
}

class DeepLException(val failure: DeepLFailure, message: String? = null) : Exception(message ?: failure.name)

/** This month's characters on the key's plan. */
data class DeepLUsage(val used: Long, val limit: Long)

/**
 * DeepL's languages by the engine's tags. Source and target differ for a few: DeepL wants a
 * variety for English, Portuguese and Chinese when it writes them.
 */
object DeepLLanguages {
    private val Supported = setOf(
        "ar", "bg", "cs", "da", "de", "el", "en", "es", "et", "fi", "fr", "hu", "id", "it", "ja",
        "ko", "lt", "lv", "nl", "no", "pl", "pt", "ro", "ru", "sk", "sl", "sv", "tr", "uk", "zh",
    )

    fun supports(tag: String): Boolean = tag in Supported

    fun source(tag: String): String = when (tag) {
        "no" -> "NB"
        else -> tag.uppercase()
    }

    fun target(tag: String): String = when (tag) {
        "en" -> "EN-US"
        "pt" -> "PT-BR"
        "zh" -> "ZH-HANS"
        "no" -> "NB"
        else -> tag.uppercase()
    }
}

/**
 * DeepL's API with your own key. A free key ends in ":fx" and has its own host. [translate]
 * sends every text in one request and gets them back in the same order; [context] is the
 * sentence around a word, which DeepL uses to pick the meaning and doesn't translate or bill.
 */
class DeepLClient(private val httpClient: HttpClient) {
    suspend fun usage(key: String): DeepLUsage {
        val response = call { httpClient.get("${host(key)}/v2/usage") { auth(key) } }
        val body = Lenient.decodeFromString(UsageBody.serializer(), response.bodyAsText())
        return DeepLUsage(used = body.characterCount, limit = body.characterLimit)
    }

    suspend fun translate(
        key: String,
        texts: List<String>,
        from: String,
        to: String,
        context: String? = null,
    ): List<String> {
        if (!DeepLLanguages.supports(from) || !DeepLLanguages.supports(to)) {
            throw DeepLException(DeepLFailure.LanguageMissing)
        }
        val request = TranslateBody(
            text = texts,
            sourceLang = DeepLLanguages.source(from),
            targetLang = DeepLLanguages.target(to),
            context = context,
        )
        val response = call {
            httpClient.post("${host(key)}/v2/translate") {
                auth(key)
                contentType(ContentType.Application.Json)
                setBody(Lenient.encodeToString(TranslateBody.serializer(), request))
            }
        }
        val body = Lenient.decodeFromString(TranslationsBody.serializer(), response.bodyAsText())
        if (body.translations.size != texts.size) throw DeepLException(DeepLFailure.Other, "DeepL answered ${body.translations.size} of ${texts.size}")
        return body.translations.map { it.text }
    }

    private suspend fun call(block: suspend () -> HttpResponse): HttpResponse {
        val response = try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw DeepLException(DeepLFailure.Unreachable, error.message)
        }
        if (response.status.isSuccess()) return response
        throw DeepLException(
            when (response.status.value) {
                401, 403 -> DeepLFailure.KeyRefused
                456 -> DeepLFailure.LimitReached
                429, 500, 502, 503, 504, 529 -> DeepLFailure.Unreachable
                else -> DeepLFailure.Other
            },
            "DeepL answered ${response.status.value}",
        )
    }

    private fun io.ktor.client.request.HttpRequestBuilder.auth(key: String) {
        header("Authorization", "DeepL-Auth-Key $key")
    }

    @Serializable
    private data class UsageBody(
        @kotlinx.serialization.SerialName("character_count") val characterCount: Long = 0,
        @kotlinx.serialization.SerialName("character_limit") val characterLimit: Long = 0,
    )

    @Serializable
    private data class TranslateBody(
        val text: List<String>,
        @kotlinx.serialization.SerialName("source_lang") val sourceLang: String,
        @kotlinx.serialization.SerialName("target_lang") val targetLang: String,
        val context: String? = null,
    )

    @Serializable
    private data class TranslationsBody(val translations: List<TranslatedText> = emptyList())

    @Serializable
    private data class TranslatedText(val text: String)

    companion object {
        /** A free plan's key ends in ":fx". */
        fun isFreeKey(key: String): Boolean = key.trim().endsWith(":fx")

        internal fun host(key: String): String =
            if (isFreeKey(key)) "https://api-free.deepl.com" else "https://api.deepl.com"

        private val Lenient = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
