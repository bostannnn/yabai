package com.example.unifiedreader

/** A request's [value] and the tokens to use next; [refreshedSession] is set when the request had to renew them. */
internal data class TokenRefreshResult<T, Session>(
    val value: T,
    val accessToken: String,
    val refreshToken: String?,
    val refreshedSession: Session?,
)

/**
 * Runs [block] with [initialAccessToken]; when the server says the token has expired ([isUnauthorized]), renews it
 * with the refresh token and runs [block] once more. Without a refresh token the expiry is rethrown.
 */
// Each server has its own expiry exception, so [isUnauthorized] picks it; every other exception is rethrown.
@Suppress("LongParameterList", "TooGenericExceptionCaught")
internal suspend fun <T, Session> runWithTokenRefresh(
    initialAccessToken: String,
    initialRefreshToken: String?,
    isUnauthorized: (Throwable) -> Boolean,
    refreshSession: suspend (refreshToken: String) -> Session,
    tokensOf: (Session) -> Pair<String, String?>,
    block: suspend (accessToken: String) -> T,
): TokenRefreshResult<T, Session> = try {
    TokenRefreshResult(
        value = block(initialAccessToken),
        accessToken = initialAccessToken,
        refreshToken = initialRefreshToken,
        refreshedSession = null,
    )
} catch (exception: Exception) {
    if (!isUnauthorized(exception)) throw exception
    val refreshToken = initialRefreshToken?.takeIf(String::isNotBlank) ?: throw exception
    val refreshedSession = refreshSession(refreshToken)
    val (accessToken, nextRefreshToken) = tokensOf(refreshedSession)
    TokenRefreshResult(
        value = block(accessToken),
        accessToken = accessToken,
        refreshToken = nextRefreshToken,
        refreshedSession = refreshedSession,
    )
}

/** How a server renews an expired access token. */
internal class TokenRenewal<Session>(
    val isUnauthorized: (Throwable) -> Boolean,
    val refreshSession: suspend (refreshToken: String) -> Session,
    val tokensOf: (Session) -> Pair<String, String?>,
)

internal suspend fun <T, Session> TokenRenewal<Session>.run(
    accessToken: String,
    refreshToken: String?,
    block: suspend (accessToken: String) -> T,
): TokenRefreshResult<T, Session> = runWithTokenRefresh(
    initialAccessToken = accessToken,
    initialRefreshToken = refreshToken,
    isUnauthorized = isUnauthorized,
    refreshSession = refreshSession,
    tokensOf = tokensOf,
    block = block,
)

/**
 * The tokens of a run of several requests: each request starts with the tokens the last one ended with, and
 * [result] carries them, and the session a renewal made, to be saved.
 */
internal class RefreshingTokens<Session>(
    private var accessToken: String,
    private var refreshToken: String?,
    private val renewal: TokenRenewal<Session>,
) {
    private var latestSession: Session? = null

    suspend fun <T> run(block: suspend (accessToken: String) -> T): T {
        val result = renewal.run(accessToken, refreshToken, block)
        accessToken = result.accessToken
        refreshToken = result.refreshToken
        latestSession = result.refreshedSession ?: latestSession
        return result.value
    }

    fun <T> result(value: T): TokenRefreshResult<T, Session> = TokenRefreshResult(
        value = value,
        accessToken = accessToken,
        refreshToken = refreshToken,
        refreshedSession = latestSession,
    )
}
