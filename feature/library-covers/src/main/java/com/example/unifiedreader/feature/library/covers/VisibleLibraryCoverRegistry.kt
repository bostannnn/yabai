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

    fun promoteResolvedUriImmediately(
        itemId: String,
        variant: LibraryCoverVariant,
        uri: String,
    ) {
        entryFor(itemId, variant).promoteResolvedUri(uri)
    }

    fun promoteResolvedUriWhenViewportIdle(
        itemId: String,
        variant: LibraryCoverVariant,
        uri: String,
    ) {
        val entry = entryFor(itemId, variant)
        if (viewportScrolling && entry.presentedUri != uri) {
            entry.pendingResolvedUri = uri
            return
        }
        entry.promoteResolvedUri(uri)
    }

    fun onViewportScrollingChanged(
        isScrolling: Boolean,
    ) {
        if (viewportScrolling == isScrolling) return
        viewportScrolling = isScrolling
        if (isScrolling) return
        entries.values.forEach { entry ->
            val pendingResolvedUri = entry.pendingResolvedUri ?: return@forEach
            entry.promoteResolvedUri(pendingResolvedUri)
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

    fun promoteResolvedUri(
        uri: String,
    ) {
        if (resolvedUri.value != uri) {
            resolvedUri.value = uri
        }
        displayedUri = uri
        pendingResolvedUri = null
    }
}
