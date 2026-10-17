package com.example.unifiedreader.feature.library.shelves

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.LibraryGridMetrics
import com.example.unifiedreader.feature.library.LibraryItemActionContext

@Composable
fun LibraryShelvesTopSlot(
    browseContextKey: String,
    shouldReserveSlot: Boolean,
    state: LibraryShelvesState,
    gridMetrics: LibraryGridMetrics = LibraryGridMetrics(),
    itemActionContext: LibraryItemActionContext = LibraryItemActionContext(),
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverPrefetchKeyProvider: (ReadableItem, LibraryCoverImageVariant) -> String? = coverImageUrlProvider,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    onItemSelected: (ReadableItem) -> Unit,
    onBrowseShelf: (LibraryShelfBrowserKind) -> Unit = {},
    onDeleteImportedItem: (ReadableItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!shouldReserveSlot) return

    when {
        state.isLoading -> {
            LibraryShelvesSlotPlaceholder(
                gridMetrics = gridMetrics,
                modifier = modifier,
            )
        }

        state.hasContent -> {
            LibraryShelvesZone(
                browseContextKey = browseContextKey,
                state = state.ready(),
                gridMetrics = gridMetrics,
                itemActionContext = itemActionContext,
                coverImageUrlProvider = coverImageUrlProvider,
                coverPrefetchKeyProvider = coverPrefetchKeyProvider,
                onCoverVisible = onCoverVisible,
                onItemSelected = onItemSelected,
                onBrowseShelf = onBrowseShelf,
                onDeleteImportedItem = onDeleteImportedItem,
                modifier = modifier,
            )
        }
    }
}
