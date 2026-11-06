package com.example.unifiedreader.data.kavita

import com.example.unifiedreader.core.network.LenientJson
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * A new sign-in for one that was turned down or is getting old: the refresh token first, then the
 * API key, which Kavita takes in place of the password. Before Kavita 0.9.1 a refresh token lasts a
 * day and an access token ten, so once the access token has run out only the key still works.
 */
suspend fun KavitaApi.renewSession(
    accessToken: String,
    refreshToken: String?,
    apiKey: String?,
): KavitaSession {
    val refreshFailure = refreshToken?.takeIf(String::isNotBlank)?.let { savedRefreshToken ->
        try {
            val refreshed = refreshSession(
                refreshToken = savedRefreshToken,
                accessToken = accessToken,
                apiKey = apiKey,
            )
            return refreshed.copy(
                refreshToken = refreshed.refreshToken ?: savedRefreshToken,
                apiKey = refreshed.apiKey ?: apiKey,
            )
        } catch (rejected: KavitaUnauthorizedException) {
            rejected
        }
    } ?: KavitaUnauthorizedException(KAVITA_REFRESH_TOKEN_MISSING_MESSAGE)
    val savedApiKey = apiKey?.takeIf(String::isNotBlank) ?: throw refreshFailure
    return try {
        loginWithApiKey(savedApiKey)
    } catch (_: KavitaUnauthorizedException) {
        throw refreshFailure
    }
}

/**
 * Whether an access token is past half its life, so it is worth renewing before it runs out: Kavita
 * before 0.9.1 renews one only while it has more than an hour left. False for a token whose expiry
 * can't be read; that one is renewed when the server turns it down.
 */
fun kavitaAccessTokenWantsRenewal(
    accessToken: String,
    nowEpochMillis: Long,
): Boolean {
    val lifetime = kavitaAccessTokenLifetime(accessToken) ?: return false
    val issuedAt = lifetime.issuedAtEpochSeconds?.takeIf { issuedAt -> issuedAt < lifetime.expiresAtEpochSeconds }
    val renewAtEpochSeconds = if (issuedAt != null) {
        issuedAt + (lifetime.expiresAtEpochSeconds - issuedAt) / 2
    } else {
        lifetime.expiresAtEpochSeconds - SECONDS_PER_DAY
    }
    return nowEpochMillis / 1_000L >= renewAtEpochSeconds
}

private class KavitaTokenLifetime(
    val accessToken: String,
    val issuedAtEpochSeconds: Long?,
    val expiresAtEpochSeconds: Long,
)

// Every request asks about the same token until it is renewed, so the last one read is kept.
private val lastTokenLifetime = AtomicReference<KavitaTokenLifetime?>(null)

private fun kavitaAccessTokenLifetime(
    accessToken: String,
): KavitaTokenLifetime? {
    lastTokenLifetime.get()?.takeIf { lifetime -> lifetime.accessToken == accessToken }?.let { lifetime ->
        return lifetime
    }
    return readKavitaTokenLifetime(accessToken)?.also(lastTokenLifetime::set)
}

// The access token is a JWT: its middle part holds `exp` and `iat`, in seconds.
private fun readKavitaTokenLifetime(
    accessToken: String,
): KavitaTokenLifetime? {
    val payload = accessToken.split('.').getOrNull(1) ?: return null
    return try {
        val claims = LenientJson.parseToJsonElement(
            String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8),
        ).jsonObject
        val expiresAt = claims["exp"]?.jsonPrimitive?.longOrNull ?: return null
        KavitaTokenLifetime(
            accessToken = accessToken,
            issuedAtEpochSeconds = (claims["iat"] ?: claims["nbf"])?.jsonPrimitive?.longOrNull,
            expiresAtEpochSeconds = expiresAt,
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}

private const val SECONDS_PER_DAY = 24 * 60 * 60L
