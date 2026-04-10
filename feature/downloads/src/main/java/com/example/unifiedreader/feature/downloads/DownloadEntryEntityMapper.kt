package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.database.entity.DownloadQueueEntryEntity
import com.example.unifiedreader.core.model.SourceType

internal fun DownloadQueueEntryEntity.toDomain(): DownloadEntry = DownloadEntry(
    id = id,
    selectedItemId = selectedItemId,
    selectedItemTitle = selectedItemTitle,
    sourceType = SourceType.valueOf(sourceType),
    actionItems = actionItemsEncoded.decodeQueuedDownloadItems(),
    trackedItemIds = trackedItemIdsEncoded.decodeStringList().toSet(),
    completedItemIds = completedItemIdsEncoded.decodeStringList().toSet(),
    status = DownloadEntryStatus.valueOf(status),
    currentItemId = currentItemId,
    currentItemTitle = currentItemTitle,
    errorMessage = errorMessage,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
)

internal fun DownloadEntry.toEntity(): DownloadQueueEntryEntity = DownloadQueueEntryEntity(
    id = id,
    selectedItemId = selectedItemId,
    selectedItemTitle = selectedItemTitle,
    sourceType = sourceType.name,
    actionItemsEncoded = actionItems.encodeQueuedDownloadItems(),
    trackedItemIdsEncoded = trackedItemIds.toList().encodeStringList(),
    completedItemIdsEncoded = completedItemIds.toList().encodeStringList(),
    status = status.name,
    currentItemId = currentItemId,
    currentItemTitle = currentItemTitle,
    errorMessage = errorMessage,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    completedAtEpochMillis = completedAtEpochMillis,
)

internal fun List<String>.encodeStringList(): String = joinToString(separator = ",") { value ->
    value.encodeStorageToken()
}

internal fun String.decodeStringList(): List<String> {
    if (isBlank()) return emptyList()
    return split(',')
        .filter(String::isNotBlank)
        .map(String::decodeStorageToken)
}

private fun List<QueuedDownloadItem>.encodeQueuedDownloadItems(): String = joinToString(separator = "\n") { item ->
    listOf(
        item.id,
        item.sourceType.name,
        item.libraryId,
        item.remoteId.orEmpty(),
        item.remoteFileId.orEmpty(),
        item.remoteFileType.orEmpty(),
        item.title,
        item.formatName,
        item.pageCount?.toString().orEmpty(),
    ).joinToString(separator = "|") { field ->
        field.encodeStorageToken()
    }
}

private fun String.decodeQueuedDownloadItems(): List<QueuedDownloadItem> {
    if (isBlank()) return emptyList()
    return lineSequence()
        .filter(String::isNotBlank)
        .map { line ->
            val fields = line.split('|')
            check(fields.size == 9) { "Unexpected download queue item encoding." }
            QueuedDownloadItem(
                id = fields[0].decodeStorageToken(),
                sourceType = SourceType.valueOf(fields[1].decodeStorageToken()),
                libraryId = fields[2].decodeStorageToken(),
                remoteId = fields[3].decodeStorageToken().ifBlank { null },
                remoteFileId = fields[4].decodeStorageToken().ifBlank { null },
                remoteFileType = fields[5].decodeStorageToken().ifBlank { null },
                title = fields[6].decodeStorageToken(),
                formatName = fields[7].decodeStorageToken(),
                pageCount = fields[8].decodeStorageToken().ifBlank { null }?.toInt(),
            )
        }
        .toList()
}

private fun String.encodeStorageToken(): String = java.util.Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(toByteArray(Charsets.UTF_8))

private fun String.decodeStorageToken(): String = String(
    java.util.Base64.getUrlDecoder().decode(this),
    Charsets.UTF_8,
)
