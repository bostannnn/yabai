package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.dao.ReadingProgressDao
import com.example.unifiedreader.core.database.dao.ReaderSessionStateDao
import com.example.unifiedreader.feature.downloads.RemoteDownloadQueueController
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface AppStartupTasks {
    suspend fun initialize()
}

class AppStartupInitializer(
    private val context: Context,
    private val importedComicArchiveDao: ImportedComicArchiveDao,
    private val readingProgressDao: ReadingProgressDao,
    private val readerSessionStateDao: ReaderSessionStateDao,
    private val remoteDownloadQueueController: RemoteDownloadQueueController,
) : AppStartupTasks {
    @Volatile
    private var initialized = false
    private val initializationMutex = Mutex()

    override suspend fun initialize() {
        if (initialized) return
        initializationMutex.withLock {
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
            remoteDownloadQueueController.ensureScheduled()
            initialized = true
        }
    }
}
