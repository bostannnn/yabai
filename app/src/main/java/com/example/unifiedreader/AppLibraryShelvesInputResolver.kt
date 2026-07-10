package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.shelves.LibraryShelvesInput
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject

/**
 * Supplies the library shelves computer's input from the database instead of the fully-hydrated
 * in-memory catalog, so shelves can compute at cold start without waiting for all items to load.
 * Returns null to fall back to the in-memory input (non-paged sources, and tests).
 */
fun interface LibraryShelvesInputResolver {
    suspend fun resolve(state: AppUiState): LibraryShelvesInput?
}

/**
 * Kavita shelves input, sourced entirely from the DB: the bounded shelf item set
 * ([LibraryShelvesInputSource.shelvesInputItems] — progressed items + their series siblings) plus the
 * reading-progress summaries for just those items (built from the reader session store, which is
 * available before the catalog hydrates). Other sources return null and keep the in-memory path.
 */
class AppLibraryShelvesInputResolver @Inject constructor(
    private val shelvesInputSource: LibraryShelvesInputSource,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val readerSessionStore: ReaderSessionStore,
) : LibraryShelvesInputResolver {
    override suspend fun resolve(state: AppUiState): LibraryShelvesInput? {
        if (state.selectedSource != SourceType.KAVITA) return null
        val items = shelvesInputSource.shelvesInputItems(SourceType.KAVITA)
        return LibraryShelvesInput(
            selectedSource = SourceType.KAVITA,
            items = items,
            progressByItemId = libraryStateAssembler.buildProgressSummaries(
                items = items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
            ),
        )
    }
}
