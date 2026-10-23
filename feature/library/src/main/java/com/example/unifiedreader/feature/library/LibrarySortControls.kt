package com.example.unifiedreader.feature.library

import androidx.compose.runtime.Composable
import com.example.unifiedreader.core.model.LibrarySortMode

internal const val LibraryGridSizeOptionTagPrefix = "library-grid-size-option-"
private const val LibraryBrowseSortSectionId = "sort"

@Composable
internal fun LibraryBrowseOptionsSelector(
    sortMode: LibrarySortMode,
    gridCellSizeDp: Int,
    fadeReadItems: Boolean,
    showSortOptions: Boolean,
    availableSortModes: List<LibrarySortMode> = LibrarySortMode.entries,
    lockedSortLabel: String? = null,
    onSortModeChanged: (LibrarySortMode) -> Unit,
    onGridCellSizeDpChanged: (Int) -> Unit,
    onFadeReadItemsChanged: (Boolean) -> Unit,
) {
    val sanitizedGridCellSizeDp = sanitizeLibraryGridCellSizeDp(gridCellSizeDp)
    LibraryBrowseOptionsMenu(
        label = if (lockedSortLabel != null) {
            "Order"
        } else if (showSortOptions) {
            sortMode.label
        } else {
            "View"
        },
        selected = sanitizedGridCellSizeDp != defaultLibraryGridCellSizeDp ||
            fadeReadItems ||
            (showSortOptions && sortMode != defaultLibrarySortMode) ||
            lockedSortLabel != null,
        optionSections = if (showSortOptions && lockedSortLabel == null) {
            listOf(
                LibraryBrowseOptionSection(
                    id = LibraryBrowseSortSectionId,
                    title = "Sort by",
                    options = availableSortModes.map { mode ->
                        LibraryBrowseOption(
                            id = mode.name,
                            label = mode.label,
                            selected = mode == sortMode,
                        )
                    },
                ),
            )
        } else {
            emptyList()
        },
        lockedOption = lockedSortLabel?.let { label ->
            LibraryBrowseLockedOption(
                sectionTitle = "Order",
                label = label,
            )
        },
        gridCellSizeDp = sanitizedGridCellSizeDp,
        fadeReadItems = fadeReadItems,
        onOptionSelected = { sectionId, optionId ->
            if (sectionId == LibraryBrowseSortSectionId) {
                availableSortModes
                    .firstOrNull { mode -> mode.name == optionId }
                    ?.let(onSortModeChanged)
            }
        },
        onGridCellSizeDpChanged = onGridCellSizeDpChanged,
        onFadeReadItemsChanged = onFadeReadItemsChanged,
    )
}
