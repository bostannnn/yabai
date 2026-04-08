package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.StateFlow

interface StoredSourceSession {
    val sourceType: SourceType
    val displayName: String
    val baseUrl: String
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

internal fun List<ReadableItem>.forSource(
    sourceType: SourceType,
): List<ReadableItem> = filter { item ->
    item.sourceType == sourceType
}
