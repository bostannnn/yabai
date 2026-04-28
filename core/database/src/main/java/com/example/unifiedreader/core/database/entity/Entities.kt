package com.example.unifiedreader.core.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "libraries")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val title: String,
    val remoteId: String?,
    val contentType: String,
)

@Entity(
    tableName = "remote_library_cache_entries",
    primaryKeys = ["sourceType", "baseUrl", "username", "libraryId"],
    indices = [
        Index(value = ["sourceType", "baseUrl", "username"]),
    ],
)
data class RemoteLibraryCacheEntity(
    val sourceType: String,
    val baseUrl: String,
    val username: String,
    val libraryId: String,
    val title: String,
    val remoteId: String?,
    val contentType: String,
)

@Entity(
    tableName = "remote_catalog_sync_groups",
    primaryKeys = ["sourceType", "groupId"],
    indices = [
        Index(value = ["sourceType"]),
    ],
)
data class RemoteCatalogSyncGroupEntity(
    val sourceType: String,
    val groupId: String,
    val libraryId: String?,
    val fingerprint: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "readable_items",
    indices = [
        Index(value = ["libraryId"]),
        Index(value = ["sourceType"]),
    ],
)
data class ReadableItemEntity(
    @PrimaryKey val id: String,
    val sourceType: String,
    val libraryId: String,
    val remoteId: String?,
    val isLocalImport: Boolean,
    val remoteFileId: String?,
    val remoteFileType: String?,
    val title: String,
    val format: String,
    val kind: String,
    val pageCount: Int?,
    val coverUrl: String?,
    @ColumnInfo(defaultValue = "NULL") val seriesCoverUrl: String? = null,
    val description: String?,
    val addedAtEpochMillis: Long?,
    val bookAuthor: String?,
    val bookAuthorsJson: String?,
    val bookSeriesName: String?,
    val bookPublishersJson: String?,
    val bookGenresJson: String?,
    val bookTagsJson: String?,
    val bookLanguagesJson: String?,
    val comicSeries: String?,
    val comicIssueTitle: String?,
    val comicVolume: String?,
    val comicIssueNumber: String?,
    val comicPublishersJson: String?,
    val comicGenresJson: String?,
    val comicTagsJson: String?,
    val comicLanguagesJson: String?,
    val updatedAtEpochMillis: Long,
)

data class ReadableItemLibraryEntity(
    val id: String,
    val sourceType: String,
    val libraryId: String,
    val remoteId: String?,
    val isLocalImport: Boolean,
    val remoteFileId: String?,
    val remoteFileType: String?,
    val title: String,
    val format: String,
    val kind: String,
    val pageCount: Int?,
    val coverUrl: String?,
    val seriesCoverUrl: String? = null,
    val description: String?,
    val addedAtEpochMillis: Long?,
    val bookAuthor: String?,
    val bookAuthorsJson: String?,
    val bookSeriesName: String?,
    val bookPublishersJson: String?,
    val bookGenresJson: String?,
    val bookTagsJson: String?,
    val bookLanguagesJson: String?,
    val comicSeries: String?,
    val comicIssueTitle: String?,
    val comicVolume: String?,
    val comicIssueNumber: String?,
    val comicPublishersJson: String?,
    val comicGenresJson: String?,
    val comicTagsJson: String?,
    val comicLanguagesJson: String?,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val itemId: String,
    val locatorType: String,
    val page: Int?,
    val cfi: String?,
    val percent: Double?,
    val isFinished: Boolean,
    @ColumnInfo(defaultValue = "0") val isExplicitUnreadReset: Boolean,
    val syncState: String,
    @ColumnInfo(defaultValue = "0") val updatedAtEpochMillis: Long,
)

@Entity(tableName = "imported_comic_archives")
data class ImportedComicArchiveEntity(
    @PrimaryKey val itemId: String,
    val documentUri: String,
    val displayName: String,
    val title: String,
    val coverFileName: String?,
    val format: String,
    val pageCount: Int,
    val pageEntryNamesJson: String,
    val author: String?,
    val series: String?,
    val issueTitle: String?,
    val volume: String?,
    val issueNumber: String?,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "reader_session_state")
data class ReaderSessionStateEntity(
    @PrimaryKey val itemId: String,
    val page: Int?,
    val pageCount: Int?,
    val readingDirection: String,
    val pageFitMode: String,
    val spreadMode: String,
    val preferencesJson: String,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "download_queue_entries",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAtEpochMillis"]),
    ],
)
data class DownloadQueueEntryEntity(
    @PrimaryKey val id: String,
    val selectedItemId: String,
    val selectedItemTitle: String,
    val sourceType: String,
    val actionItemsEncoded: String,
    val trackedItemIdsEncoded: String,
    val completedItemIdsEncoded: String,
    val status: String,
    val currentItemId: String?,
    val currentItemTitle: String?,
    val errorMessage: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)

@Entity(
    tableName = "journal_reading_events",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["occurredAtEpochMillis"]),
    ],
)
data class JournalReadingEventEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val sourceType: String,
    val libraryId: String,
    val itemTitle: String,
    val coverUrl: String?,
    val itemKind: String,
    val format: String,
    val eventType: String,
    val fromPercent: Double?,
    val toPercent: Double?,
    val fromPage: Int?,
    val toPage: Int?,
    val pageCount: Int?,
    val occurredAtEpochMillis: Long,
)

@Entity(
    tableName = "item_journal_entries",
    indices = [
        Index(value = ["sourceType"]),
        Index(value = ["updatedAtEpochMillis"]),
    ],
)
data class ItemJournalEntryEntity(
    @PrimaryKey val itemId: String,
    val sourceType: String,
    val libraryId: String,
    val itemTitle: String,
    val coverUrl: String?,
    val itemKind: String,
    val format: String,
    val rating: Int?,
    val reviewText: String,
    val reviewedAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long,
)
