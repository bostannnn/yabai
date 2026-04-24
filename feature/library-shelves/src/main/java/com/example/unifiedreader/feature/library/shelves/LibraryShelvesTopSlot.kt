package com.example.unifiedreader.feature.library.shelves

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import kotlinx.coroutines.delay

@Composable
fun LibraryShelvesTopSlot(
    browseContextKey: String,
    shouldReserveSlot: Boolean,
    state: LibraryShelvesState,
    itemActionContext: LibraryItemActionContext = LibraryItemActionContext(),
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var retainedState by remember(browseContextKey) { mutableStateOf<LibraryShelvesState?>(null) }
    var slotVisible by remember(browseContextKey) { mutableStateOf(shouldReserveSlot) }

    LaunchedEffect(browseContextKey, state) {
        if (state.hasContent) {
            retainedState = state.ready()
        }
    }

    LaunchedEffect(browseContextKey, shouldReserveSlot, state) {
        if (!shouldReserveSlot) {
            retainedState = null
            slotVisible = false
            return@LaunchedEffect
        }
        slotVisible = true
        if (state.isLoading || state.hasContent) {
            return@LaunchedEffect
        }
        delay(LibraryShelvesSlotCollapseDelayMillis)
        if (shouldReserveSlot && !state.isLoading && !state.hasContent) {
            retainedState = null
            slotVisible = false
        }
    }

    val displayedState = when {
        state.hasContent -> state.ready()
        retainedState?.hasContent == true -> retainedState
        else -> null
    }
    val showPlaceholder = slotVisible && displayedState == null

    if (!showPlaceholder && displayedState == null) return

    if (displayedState != null) {
        LibraryShelvesZone(
            browseContextKey = browseContextKey,
            state = displayedState,
            itemActionContext = itemActionContext,
            coverImageUrlProvider = coverImageUrlProvider,
            coverPrefetchKeyProvider = coverPrefetchKeyProvider,
            onCoverVisible = onCoverVisible,
            onItemSelected = onItemSelected,
            onDeleteImportedItem = onDeleteImportedItem,
            modifier = modifier,
        )
    } else {
        LibraryShelvesSlotPlaceholder(modifier = modifier)
    }
}

private const val LibraryShelvesSlotCollapseDelayMillis = 220L
