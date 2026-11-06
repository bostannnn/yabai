package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class AppStateHydrator @Inject constructor(
    private val appStartupTasks: AppStartupTasks,
    private val remoteCatalogHydrator: RemoteCatalogHydrator,
    private val localDocumentHydrator: LocalDocumentHydrator,
    private val readerSessionHydrator: ReaderSessionHydrator,
    private val appDispatchers: AppDispatchers = AppDispatchers(),
) {
    suspend fun hydrate() = withContext(appDispatchers.io) {
        appStartupTasks.initialize()
        coroutineScope {
            val remoteCatalog = async { remoteCatalogHydrator.hydrate() }
            val localDocuments = async { localDocumentHydrator.hydrate() }
            val readerSessions = async { readerSessionHydrator.hydrate() }
            remoteCatalog.await()
            localDocuments.await()
            readerSessions.await()
        }
    }
}
