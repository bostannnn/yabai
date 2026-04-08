package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType

data class StumpStoredSession(
    override val displayName: String,
    override val baseUrl: String,
    override val username: String,
    val accessToken: String,
    val refreshToken: String?,
) : StoredSourceSession {
    override val sourceType: SourceType = SourceType.STUMP
}

interface StumpCredentialStore : SourceSessionStore<StumpStoredSession>
