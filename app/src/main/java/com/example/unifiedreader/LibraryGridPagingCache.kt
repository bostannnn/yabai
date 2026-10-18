package com.example.unifiedreader

import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * Hands out the paged library grid for one source, sort and filter set at a time. Coming back to the
 * same view (from the reader, or whenever the library re-enters composition) reuses the pages already
 * loaded instead of starting an empty pager, and asking for another view stops the previous pager,
 * which a bare `cachedIn(viewModelScope)` per request kept running for the ViewModel's lifetime.
 */
internal class LibraryGridPagingCache(
    private val scope: CoroutineScope,
    private val pager: LibraryGridPager,
) {
    private var cachedView: CachedView? = null

    fun pagingData(
        source: SourceType,
        sortMode: LibrarySortMode,
        filters: LibraryFilterState,
    ): Flow<PagingData<ReadableItem>> {
        val key = ViewKey(source, sortMode, filters)
        cachedView?.takeIf { view -> view.key == key }?.let { view -> return view.pagingData }
        cachedView?.scope?.cancel()
        val viewScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        return pager.gridRootsPagingData(source, sortMode, filters)
            .cachedIn(viewScope)
            .also { pagingData -> cachedView = CachedView(key, viewScope, pagingData) }
    }

    private data class ViewKey(
        val source: SourceType,
        val sortMode: LibrarySortMode,
        val filters: LibraryFilterState,
    )

    private class CachedView(
        val key: ViewKey,
        val scope: CoroutineScope,
        val pagingData: Flow<PagingData<ReadableItem>>,
    )
}
