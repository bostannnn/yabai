package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem

interface DownloadCompletionSideEffect {
    fun supports(
        item: ReadableItem,
    ): Boolean

    suspend fun onDownloadCompleted(
        item: ReadableItem,
    )
}

internal fun QueuedDownloadItem.toReadableItem(): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType,
    libraryId = libraryId,
    remoteId = remoteId,
    isLocalImport = false,
    remoteFileId = remoteFileId,
    remoteFileType = remoteFileType,
    title = title,
    format = FormatType.valueOf(formatName),
    pageCount = pageCount,
)
