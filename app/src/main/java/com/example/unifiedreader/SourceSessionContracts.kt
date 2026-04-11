package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.StateFlow

interface StoredSourceSession {
    val sourceType: SourceType
    val displayName: String
    val baseUrl: String
    val backupBaseUrl: String?
    val activeBaseUrl: String?
    val username: String
}

interface SourceSessionStore<T : StoredSourceSession> {
    val sessionFlow: StateFlow<T?>

    suspend fun save(
        session: T,
    )

    suspend fun clear()
}

internal fun <T : StoredSourceSession> SourceSessionStore<T>.activeSessionFor(
    item: ReadableItem,
): T? = sessionFlow.value?.takeIf { session ->
    session.sourceType == item.sourceType
}

internal fun StoredSourceSession.matchesServerIdentity(
    baseUrl: String,
    backupBaseUrl: String?,
    username: String,
): Boolean {
    if (this.username != username) return false
    val requestedCandidates = buildList {
        add(baseUrl)
        backupBaseUrl?.takeIf(String::isNotBlank)?.let(::add)
    }.distinct()
    return configuredBaseUrlCandidates().any(requestedCandidates::contains)
}

internal fun StoredSourceSession.configuredBaseUrlCandidates(): List<String> = buildList {
    add(baseUrl)
    backupBaseUrl?.takeIf(String::isNotBlank)?.let(::add)
}.distinct()

internal fun StoredSourceSession.baseUrlCandidates(): List<String> = buildList {
    activeBaseUrl?.takeIf(String::isNotBlank)?.let(::add)
    add(baseUrl)
    backupBaseUrl?.takeIf(String::isNotBlank)?.let(::add)
}.distinct()

internal fun List<ReadableItem>.forSource(
    sourceType: SourceType,
): List<ReadableItem> = filter { item ->
    item.sourceType == sourceType
}
