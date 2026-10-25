package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The year a book came out, from its catalog entry, and the `item_details` a book page asks its
 * server for (a Grimmory description, a Kavita series summary). Rows synced before keep no year
 * until their source syncs again; Kavita re-reads every series once for it (its catalog
 * fingerprint version moved).
 */
internal val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `readable_items` ADD COLUMN `publishedYear` INTEGER DEFAULT NULL")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `item_details` (
                `itemId` TEXT NOT NULL PRIMARY KEY,
                `description` TEXT,
                `updatedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
