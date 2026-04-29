package com.example.unifiedreader

import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaChapterCatalogEntry
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry

internal data class KavitaCatalogFetchPlan(
    val entries: List<KavitaChapterCatalogEntry>,
)

internal suspend fun KavitaCatalogSyncService.fetchKavitaCatalog(
    api: KavitaApi,
    accessToken: String,
    libraries: List<Library>,
    series: List<KavitaSeriesEntry>,
    onProgress: (KavitaSyncProgress) -> Unit,
): KavitaCatalogFetchPlan {
    return KavitaCatalogFetchPlan(
        entries = fetchSeriesEntries(
            api = api,
            accessToken = accessToken,
            series = series,
            onProgress = onProgress,
        ),
    )
}
