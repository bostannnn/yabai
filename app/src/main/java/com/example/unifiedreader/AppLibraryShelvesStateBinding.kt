package com.example.unifiedreader

import com.example.unifiedreader.feature.library.shelves.LibraryShelvesInput
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesState

internal fun AppUiState.toLibraryShelvesInput(): LibraryShelvesInput = LibraryShelvesInput(
    selectedSource = selectedSource,
    items = items,
    progressByItemId = progressByItemId,
)

internal fun AppUiState.withLibraryShelvesState(
    shelvesState: LibraryShelvesState,
): AppUiState = copy(
    libraryShelvesState = shelvesState,
)
