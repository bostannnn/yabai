package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao

class AppStartupInitializer(
    private val context: Context,
    private val importedComicArchiveDao: ImportedComicArchiveDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readerSessionStateDao: ReaderSessionStateDao,
) {
    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            LocalDocumentBootstrapper(
                context = context,
                importedComicArchiveDao = importedComicArchiveDao,
            ).bootstrapIfNeeded()
            ReaderStateBootstrapper(
                context = context,
                readingProgressDao = readingProgressDao,
                readerSessionStateDao = readerSessionStateDao,
            ).bootstrapIfNeeded()
            initialized = true
        }
    }
}
