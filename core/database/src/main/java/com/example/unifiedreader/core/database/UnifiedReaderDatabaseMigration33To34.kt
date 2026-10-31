package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Where you put each tag in Browse (`library_tag_choices`, empty at first: every tag starts in
 * About), and the places a Kavita comic is set in (`comicLocationsJson`). Rows synced before have no
 * Locations until Kavita re-reads every series once (its catalog fingerprint version moved).
 */
internal val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `readable_items` ADD COLUMN `comicLocationsJson` TEXT DEFAULT NULL")
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `library_tag_choices` (
                `sourceType` TEXT NOT NULL,
                `field` TEXT NOT NULL,
                `tagKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `region` TEXT,
                `chosenAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`sourceType`, `field`, `tagKey`)
            )
            """.trimIndent(),
        )
    }
}
