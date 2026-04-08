package com.example.unifiedreader

import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveAppCatalogSnapshotsUseCase @Inject constructor(
    private val appPreferencesStore: AppPreferencesStore,
    private val libraryRepository: LibraryRepository,
    private val readableItemRepository: ReadableItemRepository,
    private val remoteCatalogReadiness: RemoteCatalogReadiness,
    private val localDocumentRepository: LocalDocumentDataSource,
    private val readerSessionStore: ReaderSessionStore,
    private val libraryStateAssembler: AppLibraryStateAssembler,
) {
    internal fun observe(): Flow<AppCatalogSnapshot> = combine(
        appPreferencesStore.preferencesFlow,
        combine(
            libraryRepository.observeLibraries(),
            readableItemRepository.observeItems(),
            combine(
                remoteCatalogReadiness.isHydratedFlow,
                localDocumentRepository.isHydratedFlow,
                localDocumentRepository.importedItemsFlow,
                readerSessionStore.progressByItemIdFlow,
                readerSessionStore.sessionByItemIdFlow,
            ) { isRemoteCatalogHydrated, isLocalCatalogHydrated, importedItems, progressByItemId, sessionByItemId ->
                AppCatalogObservation(
                    isRemoteCatalogHydrated = isRemoteCatalogHydrated,
                    isLocalCatalogHydrated = isLocalCatalogHydrated,
                    importedItems = importedItems,
                    progressByItemId = progressByItemId,
                    sessionByItemId = sessionByItemId,
                )
            },
        ) { libraries, catalogItems, observation ->
            libraryStateAssembler.buildSnapshot(
                libraries = libraries,
                catalogItems = catalogItems,
                importedItems = observation.importedItems,
                progressByItemId = observation.progressByItemId,
                sessionByItemId = observation.sessionByItemId,
                isRemoteCatalogHydrated = observation.isRemoteCatalogHydrated,
                isLocalCatalogHydrated = observation.isLocalCatalogHydrated,
                themeMode = appPreferencesStore.preferencesFlow.value.themeMode,
            )
        },
    ) { preferences, snapshot ->
        snapshot.copy(themeMode = preferences.themeMode)
    }
}

private data class AppCatalogObservation(
    val isRemoteCatalogHydrated: Boolean,
    val isLocalCatalogHydrated: Boolean,
    val importedItems: List<com.example.unifiedreader.core.model.ReadableItem>,
    val progressByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>,
    val sessionByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>,
)
