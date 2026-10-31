package com.example.unifiedreader.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * A server's list keeps its rules (a Grimmory magic shelf's, as the server wrote them; null for
 * every other list), so its page can show them and its editor change them.
 */
internal val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `server_lists` ADD COLUMN `rules` TEXT")
    }
}
