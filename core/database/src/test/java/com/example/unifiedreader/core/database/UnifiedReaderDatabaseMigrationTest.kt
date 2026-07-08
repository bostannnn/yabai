package com.example.unifiedreader.core.database

import android.content.Context
import androidx.room.Room
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReadableItemEntity
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnifiedReaderDatabaseMigrationTest {
    @Test
    fun `migration 1 to 2 creates reader session state table`() {
        createTestDatabase(
            name = "migration-1-2-${System.nanoTime()}",
            version = 1,
        ) { database ->
            createVersion1Schema(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)

            assertTrue(tableExists(database, "reader_session_state"))
        }
    }

    @Test
    fun `migration 2 to 3 creates imported comic archives table`() {
        createTestDatabase(
            name = "migration-2-3-${System.nanoTime()}",
            version = 2,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)

            assertTrue(tableExists(database, "imported_comic_archives"))
        }
    }

    @Test
    fun `migration 3 to 4 adds spread mode to reader session state`() {
        createTestDatabase(
            name = "migration-3-4-${System.nanoTime()}",
            version = 3,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)

            assertEquals(
                "Auto",
                querySpreadModeDefault(database),
            )
        }
    }

    @Test
    fun `migration 22 to 23 creates journal reading sessions table`() {
        createTestDatabase(
            name = "migration-22-23-${System.nanoTime()}",
            version = 22,
        ) { database ->
            createVersion22Schema(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_22_23.migrate(database)

            assertTrue(tableExists(database, "journal_reading_sessions"))
            assertTrue(indexExists(database, "index_journal_reading_sessions_itemId"))
            assertTrue(indexExists(database, "index_journal_reading_sessions_sourceType_syncState"))
        }
    }

    @Test
    fun `migration 23 to 24 adds comic creator metadata columns`() {
        createTestDatabase(
            name = "migration-23-24-${System.nanoTime()}",
            version = 23,
        ) { database ->
            createVersion23Schema(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_23_24.migrate(database)

            assertTrue(columnExists(database, "readable_items", "comicWritersJson"))
            assertTrue(columnExists(database, "readable_items", "comicPencillersJson"))
        }
    }

    @Test
    fun `migration 24 to 25 creates library grid roots table`() {
        createTestDatabase(
            name = "migration-24-25-${System.nanoTime()}",
            version = 24,
        ) { database ->
            createVersion24Schema(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_24_25.migrate(database)

            assertTrue(tableExists(database, "library_grid_roots"))
            assertTrue(indexExists(database, "index_library_grid_roots_sourceType"))
        }
    }

    @Test
    fun `migration 25 to 26 creates library root facets table`() {
        createTestDatabase(
            name = "migration-25-26-${System.nanoTime()}",
            version = 25,
        ) { database ->
            createVersion25Schema(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_25_26.migrate(database)

            assertTrue(tableExists(database, "library_root_facets"))
            assertTrue(indexExists(database, "index_library_root_facets_sourceType_dimension"))
            assertTrue(indexExists(database, "index_library_root_facets_libraryId"))
        }
    }

    @Test
    fun `room migrates 4 to 5 and validates reader session state schema`() {
        val databaseName = "migration-4-5-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 4,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            database.execSQL(
                """
                INSERT INTO reader_session_state (
                    itemId,
                    page,
                    pageCount,
                    readingDirection,
                    pageFitMode,
                    updatedAtEpochMillis,
                    spreadMode
                ) VALUES (
                    'comic-1',
                    7,
                    24,
                    'RTL',
                    'FitWidth',
                    123456789,
                    'Double'
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val migratedRow = checkNotNull(
                migratedDatabase.readerSessionStateDao().getByItemId("comic-1"),
            )
            assertEquals(7, migratedRow.page)
            assertEquals(24, migratedRow.pageCount)
            assertEquals("RTL", migratedRow.readingDirection)
            assertEquals("FitWidth", migratedRow.pageFitMode)
            assertEquals("Double", migratedRow.spreadMode)
            assertEquals("{}", migratedRow.preferencesJson)
            assertEquals(123456789L, migratedRow.updatedAtEpochMillis)
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun `room migrates 5 to 6 and validates imported metadata schema`() {
        val databaseName = "migration-5-6-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 5,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            database.execSQL(
                """
                INSERT INTO imported_comic_archives (
                    itemId,
                    documentUri,
                    displayName,
                    format,
                    pageCount,
                    pageEntryNamesJson,
                    updatedAtEpochMillis
                ) VALUES (
                    'comic-1',
                    'content://imports/comic-1.cbz',
                    'Akira 001.cbz',
                    'CBZ',
                    24,
                    '[""page-001.jpg""]',
                    123456789
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val migratedRow = checkNotNull(
                migratedDatabase.importedComicArchiveDao().getByItemId("comic-1"),
            )
            assertEquals("Akira 001.cbz", migratedRow.displayName)
            assertEquals("", migratedRow.title)
            assertEquals(null, migratedRow.author)
            assertEquals(null, migratedRow.series)
            assertEquals(null, migratedRow.issueTitle)
            assertEquals(null, migratedRow.volume)
            assertEquals(null, migratedRow.issueNumber)
            assertEquals(null, migratedRow.coverFileName)
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun `room migrates 6 to 7 and validates imported cover schema`() {
        val databaseName = "migration-6-7-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 6,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            database.execSQL(
                """
                INSERT INTO imported_comic_archives (
                    itemId,
                    documentUri,
                    displayName,
                    title,
                    format,
                    pageCount,
                    pageEntryNamesJson,
                    author,
                    series,
                    issueTitle,
                    volume,
                    issueNumber,
                    updatedAtEpochMillis
                ) VALUES (
                    'book-1',
                    'content://imports/book-1.epub',
                    'Book 1.epub',
                    'Book 1',
                    'EPUB',
                    0,
                    '[]',
                    'Author',
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    123456789
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val migratedRow = checkNotNull(
                migratedDatabase.importedComicArchiveDao().getByItemId("book-1"),
            )
            assertEquals(null, migratedRow.coverFileName)
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun `room migrates 7 to 8 and validates reading progress timestamp schema`() {
        val databaseName = "migration-7-8-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 7,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            database.execSQL(
                """
                INSERT INTO reading_progress (
                    itemId,
                    locatorType,
                    page,
                    cfi,
                    percent,
                    isFinished,
                    syncState
                ) VALUES (
                    'book-1',
                    'PERCENT',
                    NULL,
                    NULL,
                    0.5,
                    0,
                    'CLEAN'
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val migratedRow = checkNotNull(
                migratedDatabase.readingProgressDao().getByItemId("book-1"),
            )
            assertEquals(0L, migratedRow.updatedAtEpochMillis)
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun `migration 16 to 17 creates journal tables`() {
        createTestDatabase(
            name = "migration-16-17-${System.nanoTime()}",
            version = 16,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)

            assertTrue(tableExists(database, "journal_reading_events"))
            assertTrue(tableExists(database, "item_journal_entries"))
            assertTrue(tableExists(database, "reading_goals"))
        }
    }

    @Test
    fun `migration 17 to 18 adds journal cover urls`() {
        createTestDatabase(
            name = "migration-17-18-${System.nanoTime()}",
            version = 17,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_17_18.migrate(database)

            assertTrue(columnExists(database, "journal_reading_events", "coverUrl"))
            assertTrue(columnExists(database, "item_journal_entries", "coverUrl"))
        }
    }

    @Test
    fun `migration 18 to 19 removes reading goals`() {
        createTestDatabase(
            name = "migration-18-19-${System.nanoTime()}",
            version = 18,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_17_18.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_18_19.migrate(database)

            assertFalse(tableExists(database, "reading_goals"))
        }
    }

    @Test
    fun `migration 19 to 20 creates remote catalog sync groups`() {
        createTestDatabase(
            name = "migration-19-20-${System.nanoTime()}",
            version = 19,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_17_18.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_18_19.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_19_20.migrate(database)

            assertTrue(tableExists(database, "remote_catalog_sync_groups"))
        }
    }

    @Test
    fun `migration 20 to 21 adds series cover url`() {
        createTestDatabase(
            name = "migration-20-21-${System.nanoTime()}",
            version = 20,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_17_18.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_18_19.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_19_20.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_20_21.migrate(database)

            assertTrue(columnExists(database, "readable_items", "seriesCoverUrl"))
        }
    }

    @Test
    fun `migration 21 to 22 adds journal sync metadata`() {
        createTestDatabase(
            name = "migration-21-22-${System.nanoTime()}",
            version = 21,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_17_18.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_18_19.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_19_20.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_20_21.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_21_22.migrate(database)

            assertTrue(columnExists(database, "journal_reading_events", "remoteEventId"))
            assertTrue(columnExists(database, "journal_reading_events", "syncState"))
            assertTrue(columnExists(database, "item_journal_entries", "remoteReviewNoteId"))
            assertTrue(columnExists(database, "item_journal_entries", "syncState"))
        }
    }

    @Test
    fun `room migrates 8 to 9 and validates remote catalog schema`() {
        val databaseName = "migration-8-9-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 8,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            database.execSQL(
                """
                INSERT INTO servers (
                    id,
                    displayName,
                    baseUrl,
                    sourceType
                ) VALUES (
                    'grimmory-1',
                    'Grimmory',
                    'https://grimmory.example',
                    'GRIMMORY'
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO libraries (
                    id,
                    serverId,
                    title
                ) VALUES (
                    'books',
                    'grimmory-1',
                    'Books'
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val libraries = migratedDatabase.remoteCatalogDao().getLibraries()
            assertEquals(1, libraries.size)
            assertEquals("GRIMMORY", libraries.single().sourceType)
            assertEquals("grimmory:library:books", libraries.single().id)
            assertEquals("books", libraries.single().remoteId)
            assertTrue(migratedDatabase.remoteCatalogDao().getItems().isEmpty())
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun `room migrates 14 to 15 and adds explicit unread reset flag`() {
        val databaseName = "migration-14-15-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 14,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
            database.execSQL(
                """
                INSERT INTO reading_progress (
                    itemId,
                    locatorType,
                    page,
                    cfi,
                    percent,
                    isFinished,
                    syncState,
                    updatedAtEpochMillis
                ) VALUES (
                    'book-1',
                    'PERCENT',
                    NULL,
                    NULL,
                    0.0,
                    0,
                    'DIRTY',
                    123
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val migratedRow = checkNotNull(
                migratedDatabase.readingProgressDao().getByItemId("book-1"),
            )
            assertEquals(false, migratedRow.isExplicitUnreadReset)
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }

    @Test
    fun `room migrates 9 to 10 and validates remote file metadata schema`() {
        val databaseName = "migration-9-10-${System.nanoTime()}"
        createTestDatabase(
            name = databaseName,
            version = 9,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            database.execSQL(
                """
                INSERT INTO servers (
                    id,
                    displayName,
                    baseUrl,
                    sourceType
                ) VALUES (
                    'grimmory-1',
                    'Grimmory',
                    'https://grimmory.example',
                    'GRIMMORY'
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO libraries (
                    id,
                    serverId,
                    title,
                    sourceType
                ) VALUES (
                    'books',
                    'grimmory-1',
                    'Books',
                    'KAVITA'
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO readable_items (
                    id,
                    serverId,
                    sourceType,
                    libraryId,
                    isLocalImport,
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
                ) VALUES (
                    'book-1',
                    'grimmory-1',
                    'GRIMMORY',
                    'books',
                    0,
                    'Book 1',
                    'EPUB',
                    'BOOK',
                    300,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    1775046600000
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO reading_progress (
                    itemId,
                    locatorType,
                    page,
                    cfi,
                    percent,
                    isFinished,
                    syncState,
                    updatedAtEpochMillis
                ) VALUES (
                    'book-1',
                    'PERCENT',
                    NULL,
                    NULL,
                    0.5,
                    0,
                    'CLEAN',
                    1775046600000
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO reader_session_state (
                    itemId,
                    page,
                    pageCount,
                    readingDirection,
                    pageFitMode,
                    updatedAtEpochMillis,
                    spreadMode,
                    preferencesJson
                ) VALUES (
                    'book-1',
                    NULL,
                    300,
                    'LTR',
                    'FitPage',
                    1775046600000,
                    'Auto',
                    '{}'
                )
                """.trimIndent(),
            )
        }.use { _ -> Unit }

        val migratedDatabase = openRoomDatabase(databaseName)
        try {
            val library = migratedDatabase.remoteCatalogDao().getLibraries().single()
            assertEquals("GRIMMORY", library.sourceType)
            assertEquals("grimmory:library:books", library.id)
            assertEquals("books", library.remoteId)
            val item = migratedDatabase.remoteCatalogDao().getItems().single()
            assertEquals("grimmory:item:book-1", item.id)
            assertEquals("grimmory:library:books", item.libraryId)
            assertEquals("book-1", item.remoteId)
            assertEquals(null, item.remoteFileId)
            assertEquals(null, item.remoteFileType)
            assertEquals(
                "grimmory:item:book-1",
                checkNotNull(
                    migratedDatabase.readingProgressDao().getByItemId("grimmory:item:book-1"),
                ).itemId,
            )
            assertEquals(
                "grimmory:item:book-1",
                checkNotNull(
                    migratedDatabase.readerSessionStateDao().getByItemId("grimmory:item:book-1"),
                ).itemId,
            )
        } finally {
            migratedDatabase.close()
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
        }
    }


    @Test
    fun `remote catalog dao replaces source catalog atomically`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.remoteCatalogDao()
            dao.replaceSourceCatalog(
                sourceType = "GRIMMORY",
                libraries = listOf(
                    LibraryEntity(
                        id = "grimmory:library:g-books",
                        sourceType = "GRIMMORY",
                        title = "Books",
                        remoteId = "g-books",
                        contentType = LibraryContentType.BOOKS.name,
                    ),
                ),
                items = listOf(
                    ReadableItemEntity(
                        id = "grimmory:item:book-1",
                        sourceType = "GRIMMORY",
                        libraryId = "grimmory:library:g-books",
                        remoteId = "book-1",
                        isLocalImport = false,
                        remoteFileId = "99",
                        remoteFileType = "EPUB",
                        title = "Book 1",
                        format = "EPUB",
                        kind = "BOOK",
                        pageCount = 300,
                        coverUrl = "https://grimmory.example/api/v1/media/book/1/thumbnail",
                        description = "Description",
                        addedAtEpochMillis = 1775046600000L,
                        bookAuthor = "Author",
                        bookAuthorsJson = null,
                        bookSeriesName = null,
                        bookPublishersJson = null,
                        bookGenresJson = null,
                        bookTagsJson = null,
                        bookLanguagesJson = null,
                        comicSeries = null,
                        comicIssueTitle = null,
                        comicVolume = null,
                        comicIssueNumber = null,
                        comicPublishersJson = null,
                        comicGenresJson = null,
                        comicTagsJson = null,
                        comicLanguagesJson = null,
                        updatedAtEpochMillis = 1775046600000L,
                    ),
                ),
            )
            dao.replaceSourceCatalog(
                sourceType = "GRIMMORY",
                libraries = listOf(
                    LibraryEntity(
                        id = "grimmory:library:g-new",
                        sourceType = "GRIMMORY",
                        title = "New",
                        remoteId = "g-new",
                        contentType = LibraryContentType.BOOKS.name,
                    ),
                ),
                items = emptyList(),
            )

            assertEquals(listOf("grimmory:library:g-new"), dao.getLibraries().map { it.id })
            assertTrue(dao.getItemsForSourceType(SourceType.GRIMMORY.name).isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun `migration 11 to 12 creates download queue table`() {
        createTestDatabase(
            name = "migration-11-12-${System.nanoTime()}",
            version = 11,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)

            assertTrue(tableExists(database, "download_queue_entries"))
        }
    }

    @Test
    fun `migration 12 to 13 adds library content types`() {
        createTestDatabase(
            name = "migration-12-13-${System.nanoTime()}",
            version = 12,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            database.execSQL(
                """
                INSERT INTO libraries (id, sourceType, title, remoteId)
                VALUES ('stump:library:manga', 'STUMP', 'Manga', 'manga')
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO libraries (id, sourceType, title, remoteId)
                VALUES ('grimmory:library:books', 'GRIMMORY', 'Books', 'books')
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO libraries (id, sourceType, title, remoteId)
                VALUES ('kavita:library:empty-comics', 'KAVITA', 'Empty Comics', 'empty-comics')
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO readable_items (
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
                ) VALUES (
                    'stump:item:berserk-1',
                    'STUMP',
                    'stump:library:manga',
                    'berserk-1',
                    0,
                    '1',
                    'CBZ',
                    'Berserk 1',
                    'CBZ',
                    'COMIC',
                    180,
                    NULL,
                    NULL,
                    0,
                    NULL,
                    'Berserk',
                    NULL,
                    NULL,
                    NULL,
                    0
                )
                """.trimIndent(),
            )
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)

            database.query(
                "SELECT id, contentType FROM libraries ORDER BY id",
            ).use { cursor ->
                val values = mutableMapOf<String, String>()
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val contentTypeIndex = cursor.getColumnIndexOrThrow("contentType")
                while (cursor.moveToNext()) {
                    values[cursor.getString(idIndex)] = cursor.getString(contentTypeIndex)
                }
                assertEquals(LibraryContentType.BOOKS.name, values["grimmory:library:books"])
                assertEquals(LibraryContentType.UNKNOWN.name, values["kavita:library:empty-comics"])
                assertEquals(LibraryContentType.COMICS.name, values["stump:library:manga"])
            }
        }
    }

    @Test
    fun `migration 13 to 14 creates remote library cache table`() {
        createTestDatabase(
            name = "migration-13-14-${System.nanoTime()}",
            version = 13,
        ) { database ->
            createVersion1Schema(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
            UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
        }.use { database ->
            UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)

            assertTrue(tableExists(database, "remote_library_cache_entries"))
        }
    }
}

private fun createVersion1Schema(database: SupportSQLiteDatabase) {
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS servers (
            id TEXT NOT NULL PRIMARY KEY,
            displayName TEXT NOT NULL,
            baseUrl TEXT NOT NULL,
            sourceType TEXT NOT NULL
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS libraries (
            id TEXT NOT NULL PRIMARY KEY,
            serverId TEXT NOT NULL,
            title TEXT NOT NULL
        )
        """.trimIndent(),
    )
    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS reading_progress (
            itemId TEXT NOT NULL PRIMARY KEY,
            locatorType TEXT NOT NULL,
            page INTEGER,
            cfi TEXT,
            percent REAL,
            isFinished INTEGER NOT NULL,
            syncState TEXT NOT NULL
        )
        """.trimIndent(),
    )
}

private fun createVersion22Schema(database: SupportSQLiteDatabase) {
    createVersion1Schema(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_1_2.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_2_3.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_3_4.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_4_5.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_5_6.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_6_7.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_7_8.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_8_9.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_9_10.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_10_11.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_11_12.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_12_13.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_13_14.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_14_15.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_15_16.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_16_17.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_17_18.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_18_19.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_19_20.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_20_21.migrate(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_21_22.migrate(database)
}

private fun createVersion23Schema(database: SupportSQLiteDatabase) {
    createVersion22Schema(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_22_23.migrate(database)
}

private fun createVersion24Schema(database: SupportSQLiteDatabase) {
    createVersion23Schema(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_23_24.migrate(database)
}

private fun createVersion25Schema(database: SupportSQLiteDatabase) {
    createVersion24Schema(database)
    UnifiedReaderDatabaseMigrations.MIGRATION_24_25.migrate(database)
}

private fun tableExists(
    database: SupportSQLiteDatabase,
    tableName: String,
): Boolean = database.query(
    "SELECT name FROM sqlite_master WHERE type = ? AND name = ?",
    arrayOf("table", tableName),
).use { cursor ->
    cursor.moveToFirst()
}

private fun indexExists(
    database: SupportSQLiteDatabase,
    indexName: String,
): Boolean = database.query(
    "SELECT name FROM sqlite_master WHERE type = ? AND name = ?",
    arrayOf("index", indexName),
).use { cursor ->
    cursor.moveToFirst()
}

private fun columnExists(
    database: SupportSQLiteDatabase,
    tableName: String,
    columnName: String,
): Boolean = database.query(
    "PRAGMA table_info($tableName)",
).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == columnName) return@use true
    }
    false
}

private fun querySpreadModeDefault(
    database: SupportSQLiteDatabase,
): String = database.query(
    "PRAGMA table_info(reader_session_state)",
).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
    while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == "spreadMode") {
            return cursor.getString(defaultIndex).trim('\'')
        }
    }
    error("spreadMode column not found")
}

private fun createTestDatabase(
    name: String,
    version: Int,
    onCreate: (SupportSQLiteDatabase) -> Unit,
): CloseableSupportDatabase {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.deleteDatabase(name)
    val helper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        onCreate(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                },
            )
            .build(),
    )
    return CloseableSupportDatabase(helper)
}

private fun openRoomDatabase(
    name: String,
): UnifiedReaderDatabase = Room.databaseBuilder(
    ApplicationProvider.getApplicationContext(),
    UnifiedReaderDatabase::class.java,
    name,
)
    .allowMainThreadQueries()
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
    )
    .build()

private class CloseableSupportDatabase(
    private val helper: SupportSQLiteOpenHelper,
) : AutoCloseable {
    private val database: SupportSQLiteDatabase
        get() = helper.writableDatabase

    fun use(
        block: (SupportSQLiteDatabase) -> Unit,
    ) {
        try {
            block(database)
        } finally {
            close()
        }
    }

    override fun close() {
        helper.close()
    }
}
