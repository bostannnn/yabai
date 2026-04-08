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
    val description: String?,
    val addedAtEpochMillis: Long?,
    val bookAuthor: String?,
    val comicSeries: String?,
    val comicIssueTitle: String?,
    val comicVolume: String?,
    val comicIssueNumber: String?,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val itemId: String,
    val locatorType: String,
    val page: Int?,
    val cfi: String?,
    val percent: Double?,
    val isFinished: Boolean,
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
