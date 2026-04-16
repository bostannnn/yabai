package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.SettingsPreferencesStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class ObserveAppCatalogSnapshotsUseCase @Inject constructor(
    private val settingsPreferencesStore: SettingsPreferencesStore,
    private val libraryRepository: LibraryRepository,
    private val readableItemRepository: ReadableItemRepository,
    private val remoteCatalogReadiness: RemoteCatalogReadiness,
    private val localDocumentRepository: LocalDocumentDataSource,
    private val readerSessionStore: ReaderSessionStore,
    private val libraryStateAssembler: AppLibraryStateAssembler,
    private val appDispatchers: AppDispatchers = AppDispatchers(),
) {
    internal fun observe(): Flow<AppCatalogSnapshot> = combine(
        settingsPreferencesStore.preferencesFlow,
        combine(
            libraryRepository.observeLibraries(),
            readableItemRepository.observeItems(),
            remoteCatalogReadiness.isHydratedFlow,
        ) { libraries, catalogItems, isRemoteCatalogHydrated ->
            RemoteCatalogObservation(
                libraries = libraries,
                catalogItems = catalogItems,
                isRemoteCatalogHydrated = isRemoteCatalogHydrated,
            )
        },
        combine(
            localDocumentRepository.isHydratedFlow,
            localDocumentRepository.importedItemsFlow,
        ) { isLocalCatalogHydrated, importedItems ->
            LocalCatalogObservation(
                isLocalCatalogHydrated = isLocalCatalogHydrated,
                importedItems = importedItems,
            )
        },
    ) { preferences, remoteObservation, localObservation ->
        withContext(appDispatchers.default) {
            libraryStateAssembler.buildSnapshot(
                libraries = remoteObservation.libraries,
                catalogItems = remoteObservation.catalogItems,
                importedItems = localObservation.importedItems,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
                isRemoteCatalogHydrated = remoteObservation.isRemoteCatalogHydrated,
                isLocalCatalogHydrated = localObservation.isLocalCatalogHydrated,
                themeMode = preferences.themeMode,
                includeRemoteItemLocalState = false,
            )
        }
    }

    internal suspend fun currentSnapshot(): AppCatalogSnapshot = withContext(appDispatchers.default) {
        libraryStateAssembler.buildSnapshot(
            libraries = libraryRepository.getLibraries(),
            catalogItems = readableItemRepository.getItems(),
            importedItems = localDocumentRepository.getImportedItems(),
            progressByItemId = readerSessionStore.progressByItemIdFlow.value,
            sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
            isRemoteCatalogHydrated = remoteCatalogReadiness.isHydratedFlow.value,
            isLocalCatalogHydrated = localDocumentRepository.isHydratedFlow.value,
            themeMode = settingsPreferencesStore.preferencesFlow.value.themeMode,
            includeRemoteItemLocalState = false,
        )
    }
}

private data class RemoteCatalogObservation(
    val libraries: List<com.example.unifiedreader.core.model.Library>,
    val catalogItems: List<com.example.unifiedreader.core.model.ReadableItem>,
    val isRemoteCatalogHydrated: Boolean,
)

private data class LocalCatalogObservation(
    val isLocalCatalogHydrated: Boolean,
    val importedItems: List<com.example.unifiedreader.core.model.ReadableItem>,
)
