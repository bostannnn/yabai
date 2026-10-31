package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lists that keep their next books downloaded and smart lists (two `user_lists` columns), and the
 * lists read to the end, which the journal keeps (`finished_lists`, empty at first).
 */
internal val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `user_lists` ADD COLUMN `keepNextDownloaded` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `user_lists` ADD COLUMN `smartFilter` TEXT")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `finished_lists` (
                `id` TEXT NOT NULL,
                `listId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `startedAtEpochMillis` INTEGER NOT NULL,
                `finishedAtEpochMillis` INTEGER NOT NULL,
                `bookCount` INTEGER NOT NULL,
                `seriesCount` INTEGER NOT NULL,
                `readMillis` INTEGER NOT NULL,
                `itemIds` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}
