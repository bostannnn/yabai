package com.example.unifiedreader

import androidx.paging.PagingData
import com.example.unifiedreader.core.model.LibraryAlphabetSection
import com.example.unifiedreader.core.model.LibraryFilterState
import com.example.unifiedreader.core.model.LibrarySortMode
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LibraryGridPagingCacheTest {
    @Test
    fun `returning to the same view reuses its pager`() = runTest {
        val pager = RecordingLibraryGridPager()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = LibraryGridPagingCache(scope, pager)

        val beforeReader = cache.pagingData(SourceType.KAVITA, LibrarySortMode.TITLE, LibraryFilterState())
        val afterReader = cache.pagingData(SourceType.KAVITA, LibrarySortMode.TITLE, LibraryFilterState())

        assertSame(beforeReader, afterReader)
        assertEquals(1, pager.requests)
        scope.cancel()
    }

    @Test
    fun `another view gets its own pager and stops the previous one`() = runTest {
        val pager = RecordingLibraryGridPager()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val cache = LibraryGridPagingCache(scope, pager)

        val byTitle = cache.pagingData(SourceType.KAVITA, LibrarySortMode.TITLE, LibraryFilterState())
        backgroundScope.launch { byTitle.collect {} }
        runCurrent()
        assertEquals(1, pager.runningPagers)

        val byLastRead = cache.pagingData(SourceType.KAVITA, LibrarySortMode.LAST_READ, LibraryFilterState())
        backgroundScope.launch { byLastRead.collect {} }
        runCurrent()

        assertEquals(2, pager.requests)
        assertEquals(1, pager.runningPagers)
        scope.cancel()
    }
}

private class RecordingLibraryGridPager : LibraryGridPager {
    var requests = 0
        private set
    var runningPagers = 0
        private set

    override fun gridRootsPagingData(
        source: SourceType,
        sortMode: LibrarySortMode,
        filters: LibraryFilterState,
    ): Flow<PagingData<ReadableItem>> {
        requests += 1
        return flow {
            runningPagers += 1
            try {
                emit(PagingData.empty())
                awaitCancellation()
            } finally {
                runningPagers -= 1
            }
        }
    }

    override fun alphabetSections(
        source: SourceType,
        sortMode: LibrarySortMode,
    ): Flow<List<LibraryAlphabetSection>> = flowOf(emptyList())

    override suspend fun orderedGridRootIds(
        source: SourceType,
        sortMode: LibrarySortMode,
        limit: Int,
    ): List<String> = emptyList()

    override suspend fun rebuildGridRootsIfEmpty() = Unit
}
