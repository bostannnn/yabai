package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.downloads.RemoteItemDownloadDataSource
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveAppCatalogSnapshotsUseCaseTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `progress changes do not rebuild catalog snapshots`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val item = sampleItem(id = "epub-1", format = FormatType.EPUB)
        val downloadDataSource = CountingRemoteItemDownloadDataSource()
        val readerSessionStore = FakeReaderSessionStore()
        val useCase = ObserveAppCatalogSnapshotsUseCase(
            settingsPreferencesStore = FakeSettingsPreferencesStore(),
            libraryRepository = FakeLibraryRepository(),
            readableItemRepository = FakeReadableItemRepository(items = listOf(item)),
            remoteCatalogReadiness = FakeRemoteCatalogReadiness(),
            localDocumentRepository = FakeLocalDocumentRepository(),
            readerSessionStore = readerSessionStore,
            libraryStateAssembler = AppLibraryStateAssembler(
                localDocumentRepository = FakeLocalDocumentRepository(),
                remoteDownloadDataSources = listOf(downloadDataSource),
            ),
            appDispatchers = AppDispatchers(
                io = dispatcher,
                default = dispatcher,
            ),
        )
        useCase.observe().first()
        assertEquals(0, downloadDataSource.storedDocumentItemIdsCalls)
        val additionalEmissions = mutableListOf<AppCatalogSnapshot>()
        val collectionJob = backgroundScope.launch {
            useCase.observe()
                .drop(1)
                .collect(additionalEmissions::add)
        }
        advanceUntilIdle()

        readerSessionStore.seedProgress(
            com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot(
                itemId = item.id,
                locatorType = LocatorType.PAGE,
                page = 4,
                syncState = SyncState.CLEAN,
            ),
        )
        advanceUntilIdle()

        assertEquals(0, additionalEmissions.size)
        assertEquals(0, downloadDataSource.storedDocumentItemIdsCalls)

        collectionJob.cancel()
    }
}

private class CountingRemoteItemDownloadDataSource : RemoteItemDownloadDataSource {
    override val sourceType = com.example.unifiedreader.core.model.SourceType.GRIMMORY
    var storedDocumentItemIdsCalls: Int = 0
        private set

    override fun storedDocumentUri(item: ReadableItem): Uri? = null

    override suspend fun downloadToLocalStorage(item: ReadableItem): Uri? = null

    override suspend fun storedDocumentItemIds(items: List<ReadableItem>): Set<String> {
        storedDocumentItemIdsCalls += 1
        return emptySet()
    }

    override suspend fun deleteStoredDocument(item: ReadableItem): Boolean = false
}
