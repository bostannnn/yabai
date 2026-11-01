package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lists that stay short with many server lists: each list's pin, hidden flag and last opening
 * (`list_preferences`), the Lists screen's choices (`list_settings`), and a Kavita reading list's
 * first and last year on `server_lists`, which the next refresh fills.
 */
internal val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `server_lists` ADD COLUMN `startYear` INTEGER DEFAULT NULL")
        database.execSQL("ALTER TABLE `server_lists` ADD COLUMN `endYear` INTEGER DEFAULT NULL")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `list_preferences` (
                `listId` TEXT NOT NULL,
                `pinnedAtEpochMillis` INTEGER,
                `hidden` INTEGER NOT NULL,
                `openedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`listId`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `list_settings` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent(),
        )
    }
}
