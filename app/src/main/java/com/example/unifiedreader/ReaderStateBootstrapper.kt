package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.comics.ComicPageFitMode
import com.example.unifiedreader.feature.reader.comics.ComicReadingDirection
import com.example.unifiedreader.feature.reader.comics.ComicSpreadMode
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesSnapshot
import com.example.unifiedreader.feature.reader.comics.buildComicReaderPreferencesMap
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.enumValueOrNull

class ReaderStateBootstrapper(
    context: Context,
    private val readingProgressDao: ReadingProgressDao,
    private val readerSessionStateDao: ReaderSessionStateDao,
) {
    private val legacyProgressPreferences = context.getSharedPreferences(
        LEGACY_PROGRESS_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val legacyReaderPreferences = context.getSharedPreferences(
        LEGACY_READER_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val bootstrapPreferences = context.getSharedPreferences(
        BOOTSTRAP_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    suspend fun bootstrapIfNeeded() {
        runRoomBootstrap(
            bootstrapPreferences = bootstrapPreferences,
            bootstrappedKey = READER_STATE_BOOTSTRAPPED_KEY,
            hasExistingRoomData = {
                readingProgressDao.hasAny() || readerSessionStateDao.hasAny()
            },
            migrateLegacyData = {
                val itemIds = buildSet {
                    legacyProgressPreferences.all.keys
                        .mapNotNull(::itemIdFromLegacyProgressKey)
                        .forEach(::add)
                    legacyReaderPreferences.all.keys
                        .mapNotNull(::itemIdFromLegacyPreferenceKey)
                        .forEach(::add)
                }
                itemIds.forEach { itemId ->
                    legacyProgressPreferences.getInt(legacyPageKey(itemId), NO_PAGE_SAVED)
                        .takeIf { it >= 0 }
                        ?.let { page ->
                            readingProgressDao.insertOrReplace(
                                ReadingProgressEntity(
                                    itemId = itemId,
                                    locatorType = LocatorType.PAGE.name,
                                    page = page,
                                    cfi = null,
                                    percent = null,
                                    isFinished = false,
                                    syncState = SyncState.CLEAN.name,
                                    updatedAtEpochMillis = 0L,
                                ),
                            )
                        }

                    val direction = legacyReaderPreferences
                        .getString(legacyReadingDirectionKey(itemId), null)
                        ?.enumValueOrNull<ComicReadingDirection>()
                        ?: ComicReadingDirection.LTR
                    val fitMode = legacyReaderPreferences
                        .getString(legacyPageFitModeKey(itemId), null)
                        ?.enumValueOrNull<ComicPageFitMode>()
                        ?: ComicPageFitMode.FitPage

                    if (direction != ComicReadingDirection.LTR || fitMode != ComicPageFitMode.FitPage) {
                        readerSessionStateDao.insertOrReplace(
                            ReaderSessionSnapshot(
                                itemId = itemId,
                                preferences = buildComicReaderPreferencesMap(
                                    ComicReaderPreferencesSnapshot(
                                        readingDirection = direction,
                                        pageFitMode = fitMode,
                                        spreadMode = ComicSpreadMode.Auto,
                                        splitWidePages = false,
                                        resumeSegment = null,
                                    ),
                                ),
                            ).toEntity(),
                        )
                    }
                }
            },
            clearLegacyState = ::clearLegacyState,
        )
    }

    private fun clearLegacyState() {
        legacyProgressPreferences.edit().clear().apply()
        legacyReaderPreferences.edit().clear().apply()
    }

    private fun itemIdFromLegacyProgressKey(key: String): String? = key
        .removePrefix("$LEGACY_ITEM_PREFIX.")
        .removeSuffix(".page")
        .takeIf { key.startsWith("$LEGACY_ITEM_PREFIX.") && key.endsWith(".page") && it.isNotEmpty() }

    private fun itemIdFromLegacyPreferenceKey(key: String): String? {
        val suffix = when {
            key.endsWith(".direction") -> ".direction"
            key.endsWith(".fit_mode") -> ".fit_mode"
            else -> return null
        }
        return key
            .removePrefix("$LEGACY_ITEM_PREFIX.")
            .removeSuffix(suffix)
            .takeIf { key.startsWith("$LEGACY_ITEM_PREFIX.") && it.isNotEmpty() }
    }

    private companion object {
        const val LEGACY_PROGRESS_PREFERENCES_NAME = "unified_reader_local_progress"
        const val LEGACY_READER_PREFERENCES_NAME = "unified_reader_reader_preferences"
        const val BOOTSTRAP_PREFERENCES_NAME = "unified_reader_bootstrap_state"
        const val READER_STATE_BOOTSTRAPPED_KEY = "reader_state_room_bootstrap_v1"
        const val LEGACY_ITEM_PREFIX = "reader.cbz"
        const val NO_PAGE_SAVED = -1
    }
}

private fun legacyPageKey(itemId: String): String = "reader.cbz.$itemId.page"

private fun legacyReadingDirectionKey(itemId: String): String = "reader.cbz.$itemId.direction"

private fun legacyPageFitModeKey(itemId: String): String = "reader.cbz.$itemId.fit_mode"
