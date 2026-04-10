package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.model.FormatType

class LocalDocumentBootstrapper(
    context: Context,
    private val importedComicArchiveDao: ImportedComicArchiveDao,
) {
    private val legacyPreferences = context.getSharedPreferences(
        LEGACY_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val bootstrapPreferences = context.getSharedPreferences(
        BOOTSTRAP_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    suspend fun bootstrapIfNeeded() {
        runRoomBootstrap(
            bootstrapPreferences = bootstrapPreferences,
            bootstrappedKey = IMPORTED_ARCHIVES_BOOTSTRAPPED_KEY,
            hasExistingRoomData = {
                importedComicArchiveDao.hasAny()
            },
            migrateLegacyData = {
                val legacyRecords = loadLegacyRecords()
                if (legacyRecords.isNotEmpty()) {
                    importedComicArchiveDao.insertOrReplace(legacyRecords.map { it.toEntity() })
                }
            },
            clearLegacyState = ::clearLegacyRecords,
        )
    }

    private fun loadLegacyRecords(): List<LocalDocumentRecord> {
        val rawRecords = legacyPreferences.getStringSet(LEGACY_IMPORTED_RECORDS_KEY, emptySet()).orEmpty()
        return rawRecords.mapNotNull(::decodeLegacyRecord)
            .filter { it.pageCount > 0 && it.pageEntryNames.isNotEmpty() }
    }

    private fun decodeLegacyRecord(raw: String): LocalDocumentRecord? {
        val parts = raw.split(LEGACY_RECORD_SEPARATOR)
        return when (parts.size) {
            5 -> LocalDocumentRecord(
                id = parts[0].decodeLegacyRecordField(),
                uri = parts[1].decodeLegacyRecordField(),
                displayName = parts[2].decodeLegacyRecordField(),
                title = parts[2].decodeLegacyRecordField().comicArchiveTitle(),
                format = FormatType.CBZ,
                pageCount = parts[3].decodeLegacyRecordField().toIntOrNull() ?: 0,
                pageEntryNames = parts[4]
                    .decodeLegacyRecordField()
                    .takeIf { it.isNotEmpty() }
                    ?.split(LEGACY_PAGE_ENTRY_SEPARATOR)
                    ?.map(String::decodeLegacyRecordField)
                    .orEmpty(),
                coverFileName = null,
            )

            6 -> LocalDocumentRecord(
                id = parts[0].decodeLegacyRecordField(),
                uri = parts[1].decodeLegacyRecordField(),
                displayName = parts[2].decodeLegacyRecordField(),
                format = parts[3].decodeLegacyRecordField().toFormatTypeOrNull() ?: return null,
                title = (parts[3].decodeLegacyRecordField().toFormatTypeOrNull() ?: return null)
                    .localImportTitleFallback(parts[2].decodeLegacyRecordField()),
                pageCount = parts[4].decodeLegacyRecordField().toIntOrNull() ?: 0,
                pageEntryNames = parts[5]
                    .decodeLegacyRecordField()
                    .takeIf { it.isNotEmpty() }
                    ?.split(LEGACY_PAGE_ENTRY_SEPARATOR)
                    ?.map(String::decodeLegacyRecordField)
                    .orEmpty(),
                coverFileName = null,
            )

            else -> null
        }
    }

    private fun clearLegacyRecords() {
        legacyPreferences.edit().remove(LEGACY_IMPORTED_RECORDS_KEY).apply()
    }

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "unified_reader_imported_cbz"
        const val LEGACY_IMPORTED_RECORDS_KEY = "records"
        const val LEGACY_RECORD_SEPARATOR = "|"
        const val LEGACY_PAGE_ENTRY_SEPARATOR = ","

        const val BOOTSTRAP_PREFERENCES_NAME = "unified_reader_bootstrap_state"
        const val IMPORTED_ARCHIVES_BOOTSTRAPPED_KEY = "imported_archives_room_bootstrap_v1"
    }
}

private fun String.decodeLegacyRecordField(): String = Uri.decode(this)
