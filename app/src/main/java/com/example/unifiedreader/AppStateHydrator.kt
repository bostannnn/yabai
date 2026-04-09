package com.example.unifiedreader

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class AppStateHydrator @Inject constructor(
    private val localDocumentHydrator: LocalDocumentHydrator,
    private val readerSessionHydrator: ReaderSessionHydrator,
) {
    suspend fun hydrate() = withContext(Dispatchers.IO) {
        coroutineScope {
            val localDocuments = async { localDocumentHydrator.hydrate() }
            val readerSessions = async { readerSessionHydrator.hydrate() }
            localDocuments.await()
            readerSessions.await()
        }
    }
}
