package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.model.Library
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KavitaSessionRenewalTest {
    @Test
    fun `renewal uses the refresh token and keeps what the server left out`() = runBlocking {
        val api = RenewalApi(
            refreshed = KavitaSession(accessToken = "fresh-access", refreshToken = null, apiKey = null),
        )

        val renewed = api.renewSession(
            accessToken = "old-access",
            refreshToken = "old-refresh",
            apiKey = "api-key",
        )

        assertEquals(KavitaSession(accessToken = "fresh-access", refreshToken = "old-refresh", apiKey = "api-key"), renewed)
        assertEquals(listOf("old-refresh"), api.refreshTokens)
        assertTrue(api.apiKeyLogins.isEmpty())
    }

    @Test
    fun `renewal signs in with the api key when the refresh token is turned down`() = runBlocking {
        val api = RenewalApi(
            keySession = KavitaSession(accessToken = "key-access", refreshToken = "key-refresh", apiKey = "api-key"),
        )

        val renewed = api.renewSession(
            accessToken = "expired-access",
            refreshToken = "stale-refresh",
            apiKey = "api-key",
        )

        assertEquals("key-access", renewed.accessToken)
        assertEquals(listOf("stale-refresh"), api.refreshTokens)
        assertEquals(listOf("api-key"), api.apiKeyLogins)
    }

    @Test
    fun `renewal without a refresh token goes straight to the api key`() = runBlocking {
        val api = RenewalApi(
            keySession = KavitaSession(accessToken = "key-access", refreshToken = "key-refresh", apiKey = "api-key"),
        )

        val renewed = api.renewSession(
            accessToken = "expired-access",
            refreshToken = null,
            apiKey = "api-key",
        )

        assertEquals("key-access", renewed.accessToken)
        assertTrue(api.refreshTokens.isEmpty())
    }

    @Test
    fun `renewal asks to sign in again when the key is turned down too`() = runBlocking {
        val api = RenewalApi()

        try {
            api.renewSession(
                accessToken = "expired-access",
                refreshToken = "stale-refresh",
                apiKey = "revoked-key",
            )
            fail("Expected the renewal to be turned down.")
        } catch (exception: KavitaUnauthorizedException) {
            assertEquals(KAVITA_REFRESH_TOKEN_REJECTED_MESSAGE, exception.message)
        }
        assertEquals(listOf("revoked-key"), api.apiKeyLogins)
    }

    @Test
    fun `renewal without a key reports the refresh token`() = runBlocking {
        try {
            RenewalApi().renewSession(
                accessToken = "expired-access",
                refreshToken = null,
                apiKey = null,
            )
            fail("Expected the renewal to be turned down.")
        } catch (exception: KavitaUnauthorizedException) {
            assertEquals(KAVITA_REFRESH_TOKEN_MISSING_MESSAGE, exception.message)
        }
    }

    @Test
    fun `token wants renewal once past half its life`() {
        val issuedAt = 1_000_000L
        val token = jwt("""{"name":"reader","iat":$issuedAt,"nbf":$issuedAt,"exp":${issuedAt + 3 * DAY}}""")

        assertFalse(kavitaAccessTokenWantsRenewal(token, (issuedAt + DAY) * 1_000L))
        assertTrue(kavitaAccessTokenWantsRenewal(token, (issuedAt + 2 * DAY) * 1_000L))
        assertTrue(kavitaAccessTokenWantsRenewal(token, (issuedAt + 4 * DAY) * 1_000L))
    }

    @Test
    fun `token without an issue time wants renewal in its last day`() {
        val expiresAt = 5_000_000L
        val token = jwt("""{"exp":$expiresAt}""")

        assertFalse(kavitaAccessTokenWantsRenewal(token, (expiresAt - 2 * DAY) * 1_000L))
        assertTrue(kavitaAccessTokenWantsRenewal(token, (expiresAt - DAY / 2) * 1_000L))
    }

    @Test
    fun `token that can't be read never wants early renewal`() {
        assertFalse(kavitaAccessTokenWantsRenewal("not-a-jwt", Long.MAX_VALUE))
        assertFalse(kavitaAccessTokenWantsRenewal("a.%%%.c", Long.MAX_VALUE))
        assertFalse(kavitaAccessTokenWantsRenewal(jwt("""{"name":"reader"}"""), Long.MAX_VALUE))
    }

    private fun jwt(
        claims: String,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(
            """{"alg":"HS512","typ":"JWT"}""",
            claims,
        ).joinToString(".") { part -> encoder.encodeToString(part.toByteArray()) } + ".signature"
    }
}

private const val DAY = 24 * 60 * 60L

private class RenewalApi(
    private val refreshed: KavitaSession? = null,
    private val keySession: KavitaSession? = null,
) : KavitaApi {
    val refreshTokens = mutableListOf<String>()
    val apiKeyLogins = mutableListOf<String>()

    override suspend fun refreshSession(
        refreshToken: String,
        accessToken: String?,
        apiKey: String?,
    ): KavitaSession {
        refreshTokens += refreshToken
        return refreshed ?: throw KavitaUnauthorizedException(KAVITA_REFRESH_TOKEN_REJECTED_MESSAGE)
    }

    override suspend fun loginWithApiKey(
        apiKey: String,
    ): KavitaSession {
        apiKeyLogins += apiKey
        return keySession ?: throw KavitaUnauthorizedException(KAVITA_API_KEY_REJECTED_MESSAGE)
    }

    override suspend fun login(username: String, password: String): KavitaSession = error("Not needed.")

    override suspend fun getLibraries(accessToken: String): List<Library> = error("Not needed.")

    override suspend fun getSeries(accessToken: String, apiKey: String?): List<KavitaSeriesEntry> = error("Not needed.")

    override suspend fun getSeriesDetail(
        accessToken: String,
        series: KavitaSeriesEntry,
    ): List<KavitaChapterCatalogEntry> = error("Not needed.")

    override suspend fun getProgress(accessToken: String, chapterId: String): KavitaProgressDto = error("Not needed.")

    override suspend fun getChapterInfo(
        accessToken: String,
        chapterId: String,
        includeDimensions: Boolean,
    ): KavitaChapterInfoDto = error("Not needed.")

    override suspend fun updateProgress(accessToken: String, progress: KavitaProgressUpdate) = error("Not needed.")

    override suspend fun downloadPageImage(
        accessToken: String,
        chapterId: String,
        pageIndex: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed.")

    override suspend fun downloadPageThumbnail(
        accessToken: String,
        chapterId: String,
        pageNumber: Int,
        apiKey: String?,
    ): ByteArray = error("Not needed.")

    override suspend fun downloadCover(accessToken: String, coverUrl: String, apiKey: String?): ByteArray =
        error("Not needed.")

    override suspend fun downloadChapter(
        accessToken: String,
        chapterId: String,
        apiKey: String?,
        destination: File,
    ) = error("Not needed.")
}
