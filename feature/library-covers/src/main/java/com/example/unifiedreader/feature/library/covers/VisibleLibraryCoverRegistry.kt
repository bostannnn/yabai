package com.example.unifiedreader.feature.library.covers

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisibleLibraryCoverRegistry @Inject constructor() {
    private val entries = mutableMapOf<VisibleLibraryCoverKey, VisibleLibraryCoverEntry>()
    private var viewportScrolling = false

    fun presentedUriFor(
        itemId: String,
        variant: LibraryCoverVariant,
    ): String? {
        val entry = entryFor(itemId, variant)
        return entry.resolvedUri.value ?: entry.displayedUri
    }

    fun resolvedUriFor(
        itemId: String,
        variant: LibraryCoverVariant,
    ): String? = entryFor(itemId, variant).resolvedUri.value

    fun rememberDisplayedFallbackUri(
        itemId: String,
        variant: LibraryCoverVariant,
        uri: String,
    ) {
        val entry = entryFor(itemId, variant)
        if (entry.resolvedUri.value != null) return
        entry.displayedUri = uri
    }

    fun promoteResolvedUri(
        itemId: String,
        variant: LibraryCoverVariant,
        uri: String,
    ) {
        val entry = entryFor(itemId, variant)
        if (viewportScrolling && entry.presentedUri != uri) {
            entry.pendingResolvedUri = uri
            return
        }
        if (entry.resolvedUri.value != uri) {
            entry.resolvedUri.value = uri
        }
        entry.displayedUri = uri
        entry.pendingResolvedUri = null
    }

    fun onViewportScrollingChanged(
        isScrolling: Boolean,
    ) {
        if (viewportScrolling == isScrolling) return
        viewportScrolling = isScrolling
        if (isScrolling) return
        entries.values.forEach { entry ->
            val pendingResolvedUri = entry.pendingResolvedUri ?: return@forEach
            if (entry.resolvedUri.value != pendingResolvedUri) {
                entry.resolvedUri.value = pendingResolvedUri
            }
            entry.displayedUri = pendingResolvedUri
            entry.pendingResolvedUri = null
        }
    }

    fun remove(
        itemId: String,
    ) {
        entries.keys
            .filter { key -> key.itemId == itemId }
            .forEach(entries::remove)
    }

    private fun entryFor(
        itemId: String,
        variant: LibraryCoverVariant,
    ): VisibleLibraryCoverEntry = entries.getOrPut(VisibleLibraryCoverKey(itemId, variant)) {
        VisibleLibraryCoverEntry(
            resolvedUri = mutableStateOf(null),
        )
    }
}

private data class VisibleLibraryCoverKey(
    val itemId: String,
    val variant: LibraryCoverVariant,
)

private data class VisibleLibraryCoverEntry(
    val resolvedUri: MutableState<String?>,
    var displayedUri: String? = null,
    var pendingResolvedUri: String? = null,
) {
    val presentedUri: String?
        get() = resolvedUri.value ?: displayedUri
}
