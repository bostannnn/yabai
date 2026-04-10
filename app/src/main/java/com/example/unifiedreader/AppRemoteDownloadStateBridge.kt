package com.example.unifiedreader

import com.example.unifiedreader.feature.downloads.RemoteDownloadState

internal fun AppUiState.toRemoteDownloadState(): RemoteDownloadState = RemoteDownloadState(
    downloadingItemIds = downloadingRemoteItemIds,
)

internal fun AppUiState.withRemoteDownloadState(
    remoteDownloadState: RemoteDownloadState,
): AppUiState = copy(
    downloadingRemoteItemIds = remoteDownloadState.downloadingItemIds,
)
