package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The user's own lists (`user_lists`) and their books (`user_list_entries`). Both start empty; the
 * built-in Want to read is created the first time the lists are read.
 */
internal val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_lists` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `kind` TEXT NOT NULL,
                `orderMode` TEXT NOT NULL,
                `hideFinished` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `lastOpenedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_list_entries` (
                `listId` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `addedAtEpochMillis` INTEGER NOT NULL,
                `sourceType` TEXT NOT NULL,
                `libraryId` TEXT NOT NULL,
                `itemTitle` TEXT NOT NULL,
                `itemPrimaryText` TEXT,
                `itemSecondaryText` TEXT,
                `coverUrl` TEXT,
                `itemKind` TEXT NOT NULL,
                `format` TEXT NOT NULL,
                PRIMARY KEY(`listId`, `itemId`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_user_list_entries_itemId` ON `user_list_entries` (`itemId`)",
        )
    }
}
