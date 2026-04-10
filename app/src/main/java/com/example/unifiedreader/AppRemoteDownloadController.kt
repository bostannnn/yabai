package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.downloads.RemoteDownloadStateCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppRemoteDownloadController @Inject constructor(
    private val libraryActionsCoordinator: AppLibraryActionsCoordinator,
    private val remoteDownloadStateCoordinator: RemoteDownloadStateCoordinator,
) {
    fun onDownloadRemoteItem(
        stateProvider: () -> AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        item: ReadableItem,
    ) {
        val currentState = stateProvider()
        val start = remoteDownloadStateCoordinator.startDownload(
            currentState = currentState.toRemoteDownloadState(),
            item = item,
            items = currentState.items,
        ) ?: return
        updateState { state ->
            state.withRemoteDownloadState(start.state).copy(
                libraryMessage = start.libraryMessage,
                libraryMessageSeverity = LibraryMessageSeverity.Info,
            )
        }
        scope.launch {
            try {
                val result = libraryActionsCoordinator.downloadRemoteItem(item, stateProvider().items)
                updateState { state ->
                    state.copy(
                        downloadedRemoteItemIds = result.downloadedRemoteItemIds,
                        locallyAvailableRemoteItemIds = result.locallyAvailableRemoteItemIds,
                        libraryMessage = result.libraryMessage,
                        libraryMessageSeverity = result.libraryMessageSeverity,
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                updateState { state ->
                    state.copy(
                        libraryMessage = "Unable to download ${item.libraryDisplayLabel()}: ${throwable.userFacingMessage()}",
                        libraryMessageSeverity = LibraryMessageSeverity.Error,
                    )
                }
            } finally {
                updateState { state ->
                    state.withRemoteDownloadState(
                        remoteDownloadStateCoordinator.finishDownload(
                            currentState = state.toRemoteDownloadState(),
                            trackedItemIds = start.trackedItemIds,
                        ),
                    )
                }
            }
        }
    }

    fun onDeleteRemoteLocalFile(
        stateProvider: () -> AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        item: ReadableItem,
    ) {
        scope.launch {
            val result = libraryActionsCoordinator.deleteRemoteLocalFile(item, stateProvider().items)
            updateState { state ->
                state.copy(
                    downloadedRemoteItemIds = result.downloadedRemoteItemIds,
                    locallyAvailableRemoteItemIds = result.locallyAvailableRemoteItemIds,
                    libraryMessage = result.libraryMessage,
                    libraryMessageSeverity = result.libraryMessageSeverity,
                )
            }
        }
    }
}
