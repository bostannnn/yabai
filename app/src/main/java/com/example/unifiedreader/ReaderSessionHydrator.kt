package com.example.unifiedreader

interface ReaderSessionHydrator {
    suspend fun hydrate()
}

interface StartupReaderSessionCacheSeeder {
    suspend fun seedForStartupItemIds(itemIds: List<String>)
}
