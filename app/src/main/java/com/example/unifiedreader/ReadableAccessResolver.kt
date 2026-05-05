package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.isBookReaderFormat
import com.example.unifiedreader.core.model.isComicReaderFormat
import javax.inject.Inject

sealed interface ReadableAccessDecision {
    data object LocalFile : ReadableAccessDecision
    data object DownloadedRemoteFile : ReadableAccessDecision
    data object PrepareRemoteFile : ReadableAccessDecision
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
        if (!remoteSourceConnectivityPolicy.canAttemptReaderContent(item.sourceType)) {
            return ReadableAccessDecision.BlockedRemoteUnavailable(item.sourceType)
        }
        if (item.canOpenRemoteStream()) {
            return ReadableAccessDecision.RemoteStream
        }
        if (remoteItemLocalAvailability.canStoreDocument(item)) {
            return ReadableAccessDecision.PrepareRemoteFile
        }
        return ReadableAccessDecision.RemoteStream
    }
}

private fun ReadableItem.canOpenRemoteStream(): Boolean = when (sourceType) {
    SourceType.GRIMMORY -> format.isBookReaderFormat()
    SourceType.KAVITA,
    SourceType.STUMP -> format.isComicReaderFormat()
    SourceType.LOCAL -> true
}
