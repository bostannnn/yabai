package com.example.unifiedreader

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.kavitaCatalogSeriesKey
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The summary of a Kavita book's series, for a chapter without one of its own and for the series
 * page: Kavita keeps most descriptions on the series. Each series is asked once per run of the app.
 */
@Singleton
class KavitaBookDescriptionSource @Inject constructor(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : RemoteBookDescriptionSource {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)
    private val summariesBySeriesId = ConcurrentHashMap<String, FetchedDescription>()

    override suspend fun fetch(item: ReadableItem): FetchedDescription? {
        val seriesId = item.kavitaCatalogSeriesKey()?.substringAfterLast("::")?.takeIf(String::isNotBlank) ?: return null
        summariesBySeriesId[seriesId]?.let { return it }
        val session = kavitaCredentialStore.sessionFlow.value ?: return null
        return try {
            val text = runWithStoredSessionAddressFailover(
                sessionStore = kavitaCredentialStore,
                session = session,
                createApi = kavitaApiFactory::create,
            ) { api, activeSession ->
                sessionRequestRunner.run(api = api, session = activeSession) { accessToken, _ ->
                    api.getSeriesSummary(accessToken = accessToken, seriesId = seriesId)
                }
            }
            FetchedDescription(text).also { fetched -> summariesBySeriesId[seriesId] = fetched }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
