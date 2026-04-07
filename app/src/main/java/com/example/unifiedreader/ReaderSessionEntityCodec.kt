package com.example.unifiedreader

import androidx.annotation.VisibleForTesting
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.comics.ComicsPageFitModeKey
import com.example.unifiedreader.feature.reader.comics.ComicsReadingDirectionKey
import com.example.unifiedreader.feature.reader.comics.ComicsSpreadModeKey
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.enumValueOrDefault
import com.example.unifiedreader.feature.reader.core.toReaderPreferencesJson
import com.example.unifiedreader.feature.reader.core.toReaderPreferencesMap

internal fun ReadingProgressEntity.toSnapshot(): ReaderProgressSnapshot = ReaderProgressSnapshot(
    itemId = itemId,
    locatorType = locatorType.enumValueOrDefault(LocatorType.PAGE),
    page = page,
    cfi = cfi,
    percent = percent,
    isFinished = isFinished,
    syncState = syncState.enumValueOrDefault(SyncState.CLEAN),
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun ReaderProgressSnapshot.toEntity(): ReadingProgressEntity = ReadingProgressEntity(
    itemId = itemId,
    locatorType = locatorType.name,
    page = page,
    cfi = cfi,
    percent = percent,
    isFinished = isFinished,
    syncState = syncState.name,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun ReaderSessionStateEntity.toSnapshot(): ReaderSessionSnapshot {
    val parsedPreferences = preferencesJson.toReaderPreferencesMap()
    val fallbackPreferences = buildMap {
        if (readingDirection.isNotBlank()) put(ComicsReadingDirectionKey, readingDirection)
        if (pageFitMode.isNotBlank()) put(ComicsPageFitModeKey, pageFitMode)
        if (spreadMode.isNotBlank()) put(ComicsSpreadModeKey, spreadMode)
    }
    return ReaderSessionSnapshot(
        itemId = itemId,
        pageCount = pageCount,
        preferences = fallbackPreferences + parsedPreferences,
    )
}

internal fun ReaderSessionSnapshot.toEntity(): ReaderSessionStateEntity = ReaderSessionStateEntity(
    itemId = itemId,
    page = null,
    pageCount = pageCount,
    readingDirection = preferences[ComicsReadingDirectionKey].orEmpty(),
    pageFitMode = preferences[ComicsPageFitModeKey].orEmpty(),
    spreadMode = preferences[ComicsSpreadModeKey].orEmpty(),
    preferencesJson = preferences.toReaderPreferencesJson(),
    updatedAtEpochMillis = System.currentTimeMillis(),
)

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object ReaderSessionEntityCodecTestAccess {
    fun toSnapshot(entity: ReaderSessionStateEntity): ReaderSessionSnapshot = entity.toSnapshot()

    fun toEntity(snapshot: ReaderSessionSnapshot): ReaderSessionStateEntity = snapshot.toEntity()
}
