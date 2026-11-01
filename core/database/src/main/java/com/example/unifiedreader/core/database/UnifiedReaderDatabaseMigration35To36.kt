package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A book keeps its moods (`bookMoodsJson`; Grimmory's, from Hardcover), for Browse's Mood row and
 * smart lists by mood. Books synced before have none until the next Grimmory sync, which rewrites
 * every book.
 */
internal val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `readable_items` ADD COLUMN `bookMoodsJson` TEXT DEFAULT NULL")
    }
}
