package com.example.unifiedreader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedRemoteComicEntryCacheCoordinatorTest {
    @Test
    fun `stale in-flight entry load is discarded after invalidation`() = runTest {
        val archiveEntryCache = DownloadedRemoteComicArchiveEntryCache()
        val coordinator = DownloadedRemoteComicEntryCacheCoordinator(archiveEntryCache)
        val firstLoadStarted = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()
        var loadCount = 0

        val result = backgroundScope.async {
            coordinator.entryNamesFor(itemId = "comic-1") {
                loadCount += 1
                when (loadCount) {
                    1 -> {
                        firstLoadStarted.complete(Unit)
                        releaseFirstLoad.await()
                        listOf("stale-page")
                    }

                    else -> listOf("fresh-page")
                }
            }
        }

        firstLoadStarted.await()
        coordinator.invalidate("comic-1")
        releaseFirstLoad.complete(Unit)

        assertEquals(listOf("fresh-page"), result.await())
        assertEquals(listOf("fresh-page"), archiveEntryCache.get("comic-1"))
        assertEquals(2, loadCount)
    }
}
