package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType

data class KavitaStoredSession(
    override val displayName: String,
    override val baseUrl: String,
    override val username: String,
    override val accessToken: String,
    override val refreshToken: String?,
    val apiKey: String?,
) : AccessTokenStoredSession {
    override val sourceType: SourceType = SourceType.KAVITA
}

interface KavitaCredentialStore : SourceSessionStore<KavitaStoredSession>
