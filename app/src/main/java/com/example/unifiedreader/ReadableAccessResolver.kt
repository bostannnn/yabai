package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import javax.inject.Inject

sealed interface ReadableAccessDecision {
    data object LocalFile : ReadableAccessDecision
    data object DownloadedRemoteFile : ReadableAccessDecision
    data object RemoteStream : ReadableAccessDecision
    data class BlockedRemoteUnavailable(
        val sourceType: SourceType,
    ) : ReadableAccessDecision
}

class ReadableAccessResolver @Inject constructor(
    private val remoteItemLocalAvailability: RemoteItemLocalAvailability,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
) {
    fun resolve(
        item: ReadableItem,
    ): ReadableAccessDecision {
        if (item.sourceType == SourceType.LOCAL) {
            return ReadableAccessDecision.LocalFile
        }
        if (remoteItemLocalAvailability.hasStoredDocument(item)) {
            return ReadableAccessDecision.DownloadedRemoteFile
        }
        if (remoteSourceConnectivityPolicy.canOpenRemoteItem(item.sourceType)) {
            return ReadableAccessDecision.RemoteStream
        }
        return ReadableAccessDecision.BlockedRemoteUnavailable(item.sourceType)
    }
}
