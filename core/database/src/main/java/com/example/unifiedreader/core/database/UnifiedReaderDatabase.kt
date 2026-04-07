package com.example.unifiedreader.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity
import com.example.unifiedreader.core.database.entity.LibraryEntity
import com.example.unifiedreader.core.database.entity.ReaderSessionStateEntity
import com.example.unifiedreader.core.database.entity.ReadingProgressEntity
import com.example.unifiedreader.core.database.entity.ServerEntity

@Database(
    entities = [
        ServerEntity::class,
        LibraryEntity::class,
        ReadingProgressEntity::class,
        ImportedComicArchiveEntity::class,
        ReaderSessionStateEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class UnifiedReaderDatabase : RoomDatabase() {
    abstract fun importedComicArchiveDao(): ImportedComicArchiveDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun readerSessionStateDao(): ReaderSessionStateDao
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
        )
        .build()
}
