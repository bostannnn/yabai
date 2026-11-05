package com.example.unifiedreader

import com.example.unifiedreader.core.model.runSuspendCatching
import com.example.unifiedreader.data.kavita.KavitaApi
import com.example.unifiedreader.data.kavita.KavitaSeriesEntry
import com.example.unifiedreader.data.kavita.KavitaUnauthorizedException
import com.example.unifiedreader.data.kavita.withMetadata
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Adds each series' own genres, tags and people (KavitaSeriesMetadata) to the series list, which has
 * none. Both syncs run it before the fingerprints: an edit on the server changes the series' fingerprint,
 * so a fast sync reads that series again. A series whose metadata fails to load keeps what it has.
 */
internal suspend fun withKavitaSeriesMetadata(
    api: KavitaApi,
    tokens: KavitaRunTokens,
    series: List<KavitaSeriesEntry>,
): List<KavitaSeriesEntry> = coroutineScope {
    val limiter = Semaphore(KAVITA_SERIES_METADATA_REQUEST_CONCURRENCY)
    series.map { entry ->
        async {
            limiter.withPermit {
                val metadata = runSuspendCatching {
                    tokens.run { accessToken, _ ->
                        api.getSeriesMetadata(accessToken = accessToken, seriesId = entry.id)
                    }
                }.getOrElse { error ->
                    if (error is CancellationException || error is KavitaUnauthorizedException) throw error
                    null
                }
                metadata?.let(entry::withMetadata) ?: entry
            }
        }
    }.awaitAll()
}

private const val KAVITA_SERIES_METADATA_REQUEST_CONCURRENCY = 6
