package com.example.unifiedreader.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.DownloadQueueDao
import com.example.unifiedreader.core.database.dao.JournalDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.dao.RemoteCatalogDao
import com.example.unifiedreader.core.database.entity.DownloadQueueEntryEntity
import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity
import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
import com.example.unifiedreader.core.database.entity.JournalReadingSessionEntity
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.LibraryGridRootEntity
import com.example.unifiedreader.core.database.entity.LibraryRootFacetEntity
import com.example.unifiedreader.core.database.entity.LibraryRootMemberEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import com.example.unifiedreader.core.database.entity.RemoteCatalogSyncGroupEntity
import com.example.unifiedreader.core.database.entity.RemoteLibraryCacheEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity

@Database(
    entities = [
        LibraryEntity::class,
        RemoteLibraryCacheEntity::class,
        ReadableItemEntity::class,
        ReadingProgressEntity::class,
        ImportedComicArchiveEntity::class,
        ReaderSessionStateEntity::class,
        DownloadQueueEntryEntity::class,
        JournalReadingEventEntity::class,
        JournalReadingSessionEntity::class,
        ItemJournalEntryEntity::class,
        RemoteCatalogSyncGroupEntity::class,
        LibraryGridRootEntity::class,
        LibraryRootFacetEntity::class,
        LibraryRootMemberEntity::class,
    ],
    version = 27,
    exportSchema = false,
)
abstract class UnifiedReaderDatabase : RoomDatabase() {
    abstract fun importedComicArchiveDao(): ImportedComicArchiveDao
    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun journalDao(): JournalDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerSessionStateDao(): ReaderSessionStateDao
    abstract fun remoteCatalogDao(): RemoteCatalogDao
}

object UnifiedReaderDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reader_session_state (
                    itemId TEXT NOT NULL PRIMARY KEY,
                    page INTEGER,
                    pageCount INTEGER,
                    readingDirection TEXT NOT NULL,
                    pageFitMode TEXT NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS imported_comic_archives (
                    itemId TEXT NOT NULL PRIMARY KEY,
                    documentUri TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    format TEXT NOT NULL,
                    pageCount INTEGER NOT NULL,
                    pageEntryNamesJson TEXT NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE reader_session_state
                ADD COLUMN spreadMode TEXT NOT NULL DEFAULT 'Auto'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE reader_session_state
                ADD COLUMN preferencesJson TEXT NOT NULL DEFAULT '{}'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN title TEXT NOT NULL DEFAULT ''
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN author TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN series TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN issueTitle TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN volume TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN issueNumber TEXT
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE imported_comic_archives
                ADD COLUMN coverFileName TEXT
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE reading_progress
                ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE libraries
                ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'KAVITA'
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS readable_items (
                    id TEXT NOT NULL PRIMARY KEY,
                    serverId TEXT NOT NULL,
                    sourceType TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    isLocalImport INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    format TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    pageCount INTEGER,
                    coverUrl TEXT,
                    description TEXT,
                    addedAtEpochMillis INTEGER,
                    bookAuthor TEXT,
                    comicSeries TEXT,
                    comicIssueTitle TEXT,
                    comicVolume TEXT,
                    comicIssueNumber TEXT,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_readable_items_serverId ON readable_items (serverId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_readable_items_libraryId ON readable_items (libraryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_readable_items_sourceType ON readable_items (sourceType)")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN remoteFileId TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN remoteFileType TEXT
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS libraries_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceType TEXT NOT NULL,
                    title TEXT NOT NULL,
                    remoteId TEXT
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO libraries_new (id, sourceType, title, remoteId)
                SELECT
                    lower(
                        COALESCE(
                            (
                                SELECT servers.sourceType
                                FROM servers
                                WHERE servers.id = libraries.serverId
                            ),
                            libraries.sourceType
                        )
                    ) || ':library:' || libraries.id,
                    COALESCE(
                        (
                            SELECT servers.sourceType
                            FROM servers
                            WHERE servers.id = libraries.serverId
                        ),
                        libraries.sourceType
                    ),
                    libraries.title,
                    libraries.id
                FROM libraries
                """.trimIndent(),
            )
            database.execSQL("DROP TABLE libraries")
            database.execSQL("ALTER TABLE libraries_new RENAME TO libraries")

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS readable_items_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    sourceType TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    remoteId TEXT,
                    isLocalImport INTEGER NOT NULL,
                    remoteFileId TEXT,
                    remoteFileType TEXT,
                    title TEXT NOT NULL,
                    format TEXT NOT NULL,
                    kind TEXT NOT NULL,
                    pageCount INTEGER,
                    coverUrl TEXT,
                    description TEXT,
                    addedAtEpochMillis INTEGER,
                    bookAuthor TEXT,
                    comicSeries TEXT,
                    comicIssueTitle TEXT,
                    comicVolume TEXT,
                    comicIssueNumber TEXT,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO readable_items_new (
                    id,
                    sourceType,
                    libraryId,
                    remoteId,
                    isLocalImport,
                    remoteFileId,
                    remoteFileType,
                    title,
                    format,
                    kind,
                    pageCount,
                    coverUrl,
                    description,
                    addedAtEpochMillis,
                    bookAuthor,
                    comicSeries,
                    comicIssueTitle,
                    comicVolume,
                    comicIssueNumber,
                    updatedAtEpochMillis
                )
                SELECT
                    lower(
                        COALESCE(
                            (
                                SELECT servers.sourceType
                                FROM servers
                                WHERE servers.id = readable_items.serverId
                            ),
                            readable_items.sourceType
                        )
                    ) || ':item:' || readable_items.id,
                    COALESCE(
                        (
                            SELECT servers.sourceType
                            FROM servers
                            WHERE servers.id = readable_items.serverId
                        ),
                        readable_items.sourceType
                    ),
                    lower(
                        COALESCE(
                            (
                                SELECT servers.sourceType
                                FROM servers
                                WHERE servers.id = readable_items.serverId
                            ),
                            readable_items.sourceType
                        )
                    ) || ':library:' || readable_items.libraryId,
                    readable_items.id,
                    isLocalImport,
                    remoteFileId,
                    remoteFileType,
                    readable_items.title,
                    readable_items.format,
                    readable_items.kind,
                    readable_items.pageCount,
                    readable_items.coverUrl,
                    readable_items.description,
                    readable_items.addedAtEpochMillis,
                    readable_items.bookAuthor,
                    readable_items.comicSeries,
                    readable_items.comicIssueTitle,
                    readable_items.comicVolume,
                    readable_items.comicIssueNumber,
                    readable_items.updatedAtEpochMillis
                FROM readable_items
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE reading_progress
                SET itemId = (
                    SELECT lower(
                        COALESCE(
                            (
                                SELECT servers.sourceType
                                FROM servers
                                WHERE servers.id = readable_items.serverId
                            ),
                            readable_items.sourceType
                        )
                    ) || ':item:' || readable_items.id
                    FROM readable_items
                    WHERE readable_items.id = reading_progress.itemId
                )
                WHERE EXISTS (
                    SELECT 1
                    FROM readable_items
                    WHERE readable_items.id = reading_progress.itemId
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE reader_session_state
                SET itemId = (
                    SELECT lower(
                        COALESCE(
                            (
                                SELECT servers.sourceType
                                FROM servers
                                WHERE servers.id = readable_items.serverId
                            ),
                            readable_items.sourceType
                        )
                    ) || ':item:' || readable_items.id
                    FROM readable_items
                    WHERE readable_items.id = reader_session_state.itemId
                )
                WHERE EXISTS (
                    SELECT 1
                    FROM readable_items
                    WHERE readable_items.id = reader_session_state.itemId
                )
                """.trimIndent(),
            )
            database.execSQL("DROP TABLE readable_items")
            database.execSQL("ALTER TABLE readable_items_new RENAME TO readable_items")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_readable_items_libraryId ON readable_items (libraryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_readable_items_sourceType ON readable_items (sourceType)")

            database.execSQL("DROP TABLE IF EXISTS servers")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_queue_entries (
                    id TEXT NOT NULL PRIMARY KEY,
                    selectedItemId TEXT NOT NULL,
                    selectedItemTitle TEXT NOT NULL,
                    sourceType TEXT NOT NULL,
                    actionItemsEncoded TEXT NOT NULL,
                    trackedItemIdsEncoded TEXT NOT NULL,
                    completedItemIdsEncoded TEXT NOT NULL,
                    status TEXT NOT NULL,
                    currentItemId TEXT,
                    currentItemTitle TEXT,
                    errorMessage TEXT,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    completedAtEpochMillis INTEGER
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_download_queue_entries_status ON download_queue_entries (status)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_download_queue_entries_createdAtEpochMillis ON download_queue_entries (createdAtEpochMillis)",
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE libraries
                ADD COLUMN contentType TEXT NOT NULL DEFAULT 'UNKNOWN'
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE libraries
                SET contentType = 'COMICS'
                WHERE id IN (
                    SELECT DISTINCT libraryId
                    FROM readable_items
                    WHERE kind = 'COMIC' OR format IN ('CBZ', 'CBR')
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE libraries
                SET contentType = 'BOOKS'
                WHERE id IN (
                    SELECT DISTINCT libraryId
                    FROM readable_items
                    WHERE kind = 'BOOK' OR format IN ('EPUB', 'PDF')
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE libraries
                SET contentType = 'COMICS'
                WHERE sourceType = 'STUMP' AND contentType = 'UNKNOWN'
                """.trimIndent(),
            )
            database.execSQL(
                """
                UPDATE libraries
                SET contentType = 'BOOKS'
                WHERE sourceType = 'GRIMMORY' AND contentType = 'UNKNOWN'
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS remote_library_cache_entries (
                    sourceType TEXT NOT NULL,
                    baseUrl TEXT NOT NULL,
                    username TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    remoteId TEXT,
                    contentType TEXT NOT NULL,
                    PRIMARY KEY(sourceType, baseUrl, username, libraryId)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_remote_library_cache_entries_sourceType_baseUrl_username
                ON remote_library_cache_entries (sourceType, baseUrl, username)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE reading_progress
                ADD COLUMN isExplicitUnreadReset INTEGER NOT NULL DEFAULT 0
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN bookAuthorsJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN bookSeriesName TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN bookPublishersJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN bookGenresJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN bookTagsJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN bookLanguagesJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN comicPublishersJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN comicGenresJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN comicTagsJson TEXT
                """.trimIndent(),
            )
            database.execSQL(
                """
                ALTER TABLE readable_items
                ADD COLUMN comicLanguagesJson TEXT
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS journal_reading_events (
                    id TEXT NOT NULL PRIMARY KEY,
                    itemId TEXT NOT NULL,
                    sourceType TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    itemTitle TEXT NOT NULL,
                    itemKind TEXT NOT NULL,
                    format TEXT NOT NULL,
                    eventType TEXT NOT NULL,
                    fromPercent REAL,
                    toPercent REAL,
                    fromPage INTEGER,
                    toPage INTEGER,
                    pageCount INTEGER,
                    occurredAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_journal_reading_events_itemId ON journal_reading_events (itemId)",
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_journal_reading_events_occurredAtEpochMillis
                ON journal_reading_events (occurredAtEpochMillis)
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS item_journal_entries (
                    itemId TEXT NOT NULL PRIMARY KEY,
                    sourceType TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    itemTitle TEXT NOT NULL,
                    itemKind TEXT NOT NULL,
                    format TEXT NOT NULL,
                    rating INTEGER,
                    reviewText TEXT NOT NULL,
                    reviewedAtEpochMillis INTEGER,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_item_journal_entries_sourceType ON item_journal_entries (sourceType)",
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_item_journal_entries_updatedAtEpochMillis
                ON item_journal_entries (updatedAtEpochMillis)
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reading_goals (
                    id TEXT NOT NULL PRIMARY KEY,
                    periodType TEXT NOT NULL,
                    periodStartEpochMillis INTEGER NOT NULL,
                    periodEndEpochMillis INTEGER NOT NULL,
                    metric TEXT NOT NULL,
                    target INTEGER NOT NULL,
                    createdAtEpochMillis INTEGER NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_reading_goals_periodType_periodStartEpochMillis_metric
                ON reading_goals (periodType, periodStartEpochMillis, metric)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE journal_reading_events ADD COLUMN coverUrl TEXT")
            database.execSQL("ALTER TABLE item_journal_entries ADD COLUMN coverUrl TEXT")
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS reading_goals")
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS remote_catalog_sync_groups (
                    sourceType TEXT NOT NULL,
                    groupId TEXT NOT NULL,
                    libraryId TEXT,
                    fingerprint TEXT NOT NULL,
                    updatedAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(sourceType, groupId)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_remote_catalog_sync_groups_sourceType
                ON remote_catalog_sync_groups (sourceType)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE readable_items ADD COLUMN seriesCoverUrl TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE journal_reading_events ADD COLUMN remoteEventId TEXT")
            database.execSQL("ALTER TABLE journal_reading_events ADD COLUMN remoteUpdatedAtEpochMillis INTEGER")
            database.execSQL("ALTER TABLE journal_reading_events ADD COLUMN syncState TEXT NOT NULL DEFAULT 'CLEAN'")
            database.execSQL("ALTER TABLE item_journal_entries ADD COLUMN remoteEntryId TEXT")
            database.execSQL("ALTER TABLE item_journal_entries ADD COLUMN remoteReviewNoteId TEXT")
            database.execSQL("ALTER TABLE item_journal_entries ADD COLUMN remoteUpdatedAtEpochMillis INTEGER")
            database.execSQL("ALTER TABLE item_journal_entries ADD COLUMN syncState TEXT NOT NULL DEFAULT 'CLEAN'")
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_journal_reading_events_sourceType_syncState
                ON journal_reading_events (sourceType, syncState)
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_item_journal_entries_sourceType_syncState
                ON item_journal_entries (sourceType, syncState)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS journal_reading_sessions (
                    id TEXT NOT NULL PRIMARY KEY,
                    itemId TEXT NOT NULL,
                    sourceType TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    itemTitle TEXT NOT NULL,
                    coverUrl TEXT,
                    itemKind TEXT NOT NULL,
                    format TEXT NOT NULL,
                    mediaType TEXT NOT NULL,
                    startedAtEpochMillis INTEGER NOT NULL,
                    endedAtEpochMillis INTEGER NOT NULL,
                    durationMillis INTEGER NOT NULL,
                    fromPercent REAL,
                    toPercent REAL,
                    fromPage INTEGER,
                    toPage INTEGER,
                    pageCount INTEGER,
                    remoteSessionId TEXT,
                    remoteUpdatedAtEpochMillis INTEGER,
                    syncState TEXT NOT NULL DEFAULT 'CLEAN'
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_reading_sessions_itemId ON journal_reading_sessions (itemId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_reading_sessions_sourceType ON journal_reading_sessions (sourceType)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_reading_sessions_startedAtEpochMillis ON journal_reading_sessions (startedAtEpochMillis)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_reading_sessions_endedAtEpochMillis ON journal_reading_sessions (endedAtEpochMillis)")
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_journal_reading_sessions_sourceType_syncState
                ON journal_reading_sessions (sourceType, syncState)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE readable_items ADD COLUMN comicWritersJson TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE readable_items ADD COLUMN comicPencillersJson TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Empty derived table; populated from readable_items on the next catalog write or the
            // startup rebuild-if-empty. Column shape mirrors LibraryGridRootEntity so Room's schema
            // validation passes on open.
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_grid_roots` (
                    `id` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `remoteId` TEXT,
                    `isLocalImport` INTEGER NOT NULL,
                    `remoteFileId` TEXT,
                    `remoteFileType` TEXT,
                    `title` TEXT NOT NULL,
                    `format` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `pageCount` INTEGER,
                    `coverUrl` TEXT,
                    `seriesCoverUrl` TEXT,
                    `description` TEXT,
                    `addedAtEpochMillis` INTEGER,
                    `issueCount` INTEGER,
                    `bookAuthor` TEXT,
                    `bookAuthorsJson` TEXT,
                    `bookSeriesName` TEXT,
                    `bookPublishersJson` TEXT,
                    `bookGenresJson` TEXT,
                    `bookTagsJson` TEXT,
                    `bookLanguagesJson` TEXT,
                    `comicSeries` TEXT,
                    `comicIssueTitle` TEXT,
                    `comicVolume` TEXT,
                    `comicIssueNumber` TEXT,
                    `comicWritersJson` TEXT,
                    `comicPencillersJson` TEXT,
                    `comicPublishersJson` TEXT,
                    `comicGenresJson` TEXT,
                    `comicTagsJson` TEXT,
                    `comicLanguagesJson` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_library_grid_roots_sourceType`
                ON `library_grid_roots` (`sourceType`)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Empty normalized facet table; populated from the grid roots on the next catalog write
            // or the startup rebuild-if-empty. Column shape mirrors LibraryRootFacetEntity.
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_root_facets` (
                    `rootId` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    `dimension` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`rootId`, `dimension`, `value`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_library_root_facets_sourceType_dimension`
                ON `library_root_facets` (`sourceType`, `dimension`)
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_library_root_facets_libraryId`
                ON `library_root_facets` (`libraryId`)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Empty root->member-items map; populated from the grid roots on the next catalog write
            // or the startup rebuild-if-empty. Column shape mirrors LibraryRootMemberEntity.
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_root_members` (
                    `rootId` TEXT NOT NULL,
                    `itemId` TEXT NOT NULL,
                    `sourceType` TEXT NOT NULL,
                    `libraryId` TEXT NOT NULL,
                    PRIMARY KEY(`rootId`, `itemId`)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_library_root_members_itemId`
                ON `library_root_members` (`itemId`)
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_library_root_members_libraryId`
                ON `library_root_members` (`libraryId`)
                """.trimIndent(),
            )
        }
    }
}

object UnifiedReaderDatabaseFactory {
    fun create(
        context: Context,
    ): UnifiedReaderDatabase = Room.databaseBuilder(
        context,
        UnifiedReaderDatabase::class.java,
        "unified-reader.db",
    )
        .addMigrations(
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2,
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3,
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4,
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5,
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6,
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7,
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8,
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9,
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10,
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11,
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12,
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13,
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14,
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15,
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16,
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17,
            UnifiedReaderDatabaseMigrations.MIGRATION_17_18,
            UnifiedReaderDatabaseMigrations.MIGRATION_18_19,
            UnifiedReaderDatabaseMigrations.MIGRATION_19_20,
            UnifiedReaderDatabaseMigrations.MIGRATION_20_21,
            UnifiedReaderDatabaseMigrations.MIGRATION_21_22,
            UnifiedReaderDatabaseMigrations.MIGRATION_22_23,
            UnifiedReaderDatabaseMigrations.MIGRATION_23_24,
            UnifiedReaderDatabaseMigrations.MIGRATION_24_25,
            UnifiedReaderDatabaseMigrations.MIGRATION_25_26,
            UnifiedReaderDatabaseMigrations.MIGRATION_26_27,
        )
        .build()
}
