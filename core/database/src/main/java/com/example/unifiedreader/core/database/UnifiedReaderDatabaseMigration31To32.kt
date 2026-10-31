package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lists kept on a server (`server_lists`, Grimmory shelves first) and their books
 * (`server_list_items`), as last read, for offline. Both start empty; the first refresh fills them.
 */
internal val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `server_lists` (
                `id` TEXT NOT NULL,
                `sourceType` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `remoteId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `icon` TEXT,
                `iconType` TEXT,
                `isPublic` INTEGER NOT NULL,
                `isOwned` INTEGER NOT NULL,
                `remoteSortField` TEXT,
                `remoteItemCount` INTEGER NOT NULL,
                `orderMode` TEXT NOT NULL,
                `hideFinished` INTEGER NOT NULL,
                `syncedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `server_list_items` (
                `listId` TEXT NOT NULL,
                `itemId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                PRIMARY KEY(`listId`, `itemId`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_server_list_items_itemId` ON `server_list_items` (`itemId`)",
        )
    }
}
