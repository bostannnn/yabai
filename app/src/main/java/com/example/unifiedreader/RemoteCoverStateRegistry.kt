package com.example.unifiedreader

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

internal class RemoteCoverStateRegistry {
    private val coverUriStateByKey = mutableMapOf<RemoteCoverStateKey, MutableState<String?>>()

    fun coverUriFor(
        itemId: String,
        variant: LibraryCoverImageVariant,
    ): String? = coverUriStateFor(itemId, variant).value

    fun update(
        itemId: String,
        variant: LibraryCoverImageVariant,
        uri: String,
    ) {
        val state = coverUriStateFor(itemId, variant)
        if (state.value != uri) {
            state.value = uri
        }
    }

    fun remove(
        itemId: String,
    ) {
        coverUriStateByKey.keys
            .filter { key -> key.itemId == itemId }
            .forEach(coverUriStateByKey::remove)
    }

    private fun coverUriStateFor(
        itemId: String,
        variant: LibraryCoverImageVariant,
    ): MutableState<String?> = coverUriStateByKey.getOrPut(
        RemoteCoverStateKey(
            itemId = itemId,
            variant = variant,
        ),
    ) {
        mutableStateOf(null)
    }
}

private data class RemoteCoverStateKey(
    val itemId: String,
    val variant: LibraryCoverImageVariant,
)
