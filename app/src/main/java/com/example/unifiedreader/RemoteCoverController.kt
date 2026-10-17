package com.example.unifiedreader

import android.util.Log
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.VisibleLibraryCoverRegistry
import java.net.URI
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@ViewModelScoped
class RemoteCoverController @Inject constructor(
    kavitaCoverRepository: KavitaCoverDataSource,
    stumpCoverRepository: StumpCoverDataSource,
    grimmoryCoverRepository: GrimmoryCoverDataSource,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val stumpCredentialStore: StumpCredentialStore,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy = defaultOnlineCoverConnectivityPolicy(),
    private val visibleLibraryCoverRegistry: VisibleLibraryCoverRegistry,
    private val appDispatchers: AppDispatchers = AppDispatchers(),
) {
    private val coverDataSources = listOf<RemoteCoverDataSource>(
        kavitaCoverRepository,
        stumpCoverRepository,
        grimmoryCoverRepository,
    ).associateBy(RemoteCoverDataSource::sourceType)
    private val sourceTypeByItemId = mutableMapOf<String, SourceType>()
    private val pendingCoverKeys = mutableSetOf<RemoteCoverRequestKey>()
    private val activeRequestNonceByKey = mutableMapOf<RemoteCoverRequestKey, Long>()
    private val coverRequestJobsByKey = mutableMapOf<RemoteCoverRequestKey, Job>()
    private val sourceResetVersionBySource = mutableMapOf<SourceType, Long>()
    private val sourcesBeingReset = mutableSetOf<SourceType>()
    private val warmedSources = mutableSetOf<SourceType>()
    private val sourceWarmupJobs = mutableMapOf<SourceType, Job>()
    private var lastErrorMessageBySource = mutableMapOf<SourceType, String>()
    private var nextRequestNonce: Long = 0L
    private val backgroundScope = CoroutineScope(SupervisorJob() + appDispatchers.io)
    private val coverRequestSemaphore = Semaphore(REMOTE_COVER_CACHE_CONCURRENCY)

    fun coverImageUrlFor(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): String? {
        // A local import's cover is the file LocalCoverCache wrote into imported-cover-cache;
        // there is no remote source to fetch from or cache for, so hand it back as is.
        if (item.sourceType == SourceType.LOCAL) {
            return item.coverUrl?.takeIf(String::isNotBlank)
        }
        val coverVariant = variant.toCoverAssetVariant()

        val coverIdentity = item.coverIdentityFileSegment()
        sourceTypeByItemId[item.id] = item.sourceType
        val canFetchRemoteCover = remoteSourceConnectivityPolicy.canFetchCover(item.sourceType)
        if (canFetchRemoteCover) {
            scheduleCacheWarmup(item.sourceType)
        }
        val presentedUri = if (canFetchRemoteCover) {
            visibleLibraryCoverRegistry.presentedUriFor(item.id, coverVariant, coverIdentity)
        } else {
            visibleLibraryCoverRegistry.resolvedUriFor(item.id, coverVariant, coverIdentity)
        }
        presentedUri?.let { return it }
        if (item.sourceType !in sourcesBeingReset) {
            cachedCoverUri(item, variant)?.let { cachedUri ->
                visibleLibraryCoverRegistry.promoteResolvedUriImmediately(item.id, coverVariant, cachedUri, coverIdentity)
                return visibleLibraryCoverRegistry.presentedUriFor(item.id, coverVariant, coverIdentity)
            }
        }
        if (coverDataSources[item.sourceType]?.usesRemoteDisplayFallback == false) {
            return null
        }
        if (!canFetchRemoteCover) {
            return null
        }
        return remoteFallbackCoverUrlFor(item)?.also { fallbackUri ->
            visibleLibraryCoverRegistry.rememberDisplayedFallbackUri(
                itemId = item.id,
                variant = coverVariant,
                uri = fallbackUri,
                coverIdentity = coverIdentity,
            )
        }
    }

    fun coverPrefetchKeyFor(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): String? {
        if (item.sourceType == SourceType.LOCAL) return null
        val coverIdentity = item.coverIdentityFileSegment() ?: NO_COVER_IDENTITY_PREFETCH_SEGMENT
        return "remote-cover:${item.sourceType.name.lowercase()}:${item.id}:${variant.name.lowercase()}:cover=$coverIdentity:coverv=${sourceResetVersion(item.sourceType)}"
    }

    fun ensureCoverCached(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        val coverVariant = variant.toCoverAssetVariant()
        val coverIdentity = item.coverIdentityFileSegment()
        sourceTypeByItemId[item.id] = item.sourceType
        val canFetchRemoteCover = remoteSourceConnectivityPolicy.canFetchCover(item.sourceType)
        if (canFetchRemoteCover) {
            scheduleCacheWarmup(item.sourceType)
        }
        val dataSource = coverDataSources[item.sourceType] ?: return
        if (item.sourceType in sourcesBeingReset) return
        val requestKey = RemoteCoverRequestKey(
            itemId = item.id,
            variant = variant,
            coverIdentity = coverIdentity,
        )
        val cachedCover = dataSource.cachedCover(item, variant)
        cachedCover?.uri?.toString()
            ?.takeIf(String::isNotBlank)
            ?.let { cachedUri ->
                visibleLibraryCoverRegistry.promoteResolvedUriWhenViewportIdle(item.id, coverVariant, cachedUri, coverIdentity)
                lastErrorMessageBySource.remove(item.sourceType)
                if (cachedCover.isPersistent && cachedCover.matchesRequestedVariant) {
                    return
                }
            }
        if (!canFetchRemoteCover) return
        if (requestKey in pendingCoverKeys) return
        pendingCoverKeys += requestKey
        val requestNonce = nextRequestNonce++
        val requestSourceResetVersion = sourceResetVersion(item.sourceType)
        activeRequestNonceByKey[requestKey] = requestNonce
        val job = scope.launch {
            try {
                val cachedUri = withContext(appDispatchers.io) {
                    coverRequestSemaphore.withPermit {
                        val refreshedCachedCover = dataSource.cachedCover(item, variant)
                        if (refreshedCachedCover?.isPersistent == true &&
                            refreshedCachedCover.matchesRequestedVariant
                        ) {
                            refreshedCachedCover.uri.toString().takeIf(String::isNotBlank)
                        } else {
                            dataSource.getCoverUri(item, variant)?.toString()
                        }
                    }
                }
                if (!cachedUri.isNullOrBlank() && isActiveRequest(item, variant, requestNonce, requestSourceResetVersion)) {
                    val currentUri = visibleLibraryCoverRegistry.presentedUriFor(item.id, coverVariant, coverIdentity)
                    if (currentUri != cachedUri) {
                        Log.d(LOG_TAG, "Cached ${item.sourceType} ${variant.name.lowercase()} cover for ${item.id}: $cachedUri")
                        visibleLibraryCoverRegistry.promoteResolvedUriWhenViewportIdle(item.id, coverVariant, cachedUri, coverIdentity)
                    }
                    lastErrorMessageBySource.remove(item.sourceType)
                } else if (cachedUri.isNullOrBlank()) {
                    Log.w(LOG_TAG, "${item.sourceType} ${variant.name.lowercase()} cover cache returned no URI for ${item.id}.")
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (isActiveRequest(item, variant, requestNonce, requestSourceResetVersion)) {
                    Log.w(LOG_TAG, "Unable to cache ${item.sourceType} ${variant.name.lowercase()} cover for ${item.id}.", throwable)
                    val message = "Unable to load ${item.sourceType.name.lowercase().replaceFirstChar(Char::titlecase)} covers: ${throwable.userFacingMessage()}"
                    if (lastErrorMessageBySource[item.sourceType] != message) {
                        lastErrorMessageBySource[item.sourceType] = message
                        onError(message)
                    }
                }
            } finally {
                if (activeRequestNonceByKey[requestKey] == requestNonce) {
                    activeRequestNonceByKey.remove(requestKey)
                    pendingCoverKeys -= requestKey
                }
            }
        }
        coverRequestJobsByKey[requestKey] = job
        job.invokeOnCompletion {
            if (coverRequestJobsByKey[requestKey] === job) {
                coverRequestJobsByKey.remove(requestKey)
            }
        }
    }

    fun prepareSource(
        sourceType: SourceType,
    ) {
        if (!remoteSourceConnectivityPolicy.canFetchCover(sourceType)) return
        scheduleCacheWarmup(sourceType)
    }

    fun onLibraryViewportScrollingChanged(
        isScrolling: Boolean,
    ) {
        visibleLibraryCoverRegistry.onViewportScrollingChanged(isScrolling)
    }

    suspend fun resetSource(
        sourceType: SourceType,
    ) {
        val dataSource = coverDataSources[sourceType] ?: return
        sourcesBeingReset += sourceType
        warmedSources -= sourceType
        sourceWarmupJobs.remove(sourceType)?.cancel()
        bumpSourceResetVersion(sourceType)
        cancelRequestsForSource(sourceType)
        clearCachedUrisForSource(sourceType)
        try {
            dataSource.clearCachedCovers()
        } finally {
            clearSource(sourceType)
            bumpSourceResetVersion(sourceType)
            sourcesBeingReset -= sourceType
        }
    }

    fun refreshSourceAfterCatalogSync(
        sourceType: SourceType,
    ) {
        bumpSourceResetVersion(sourceType)
        clearSource(sourceType)
    }

    fun removeMissingItems(
        activeItemIds: Set<String>,
    ) {
        val missingItemIds = sourceTypeByItemId.keys
            .toList()
            .filterNot(activeItemIds::contains)
        missingItemIds.forEach(sourceTypeByItemId::remove)
        missingItemIds.forEach(visibleLibraryCoverRegistry::remove)
        missingItemIds.forEach { itemId ->
            activeRequestNonceByKey.keys.removeAll { key -> key.itemId == itemId }
            coverRequestJobsByKey.entries
                .filter { (key, _) -> key.itemId == itemId }
                .forEach { (key, job) ->
                    job.cancel()
                    coverRequestJobsByKey.remove(key)
                }
        }
        pendingCoverKeys.removeAll { key -> key.itemId !in activeItemIds }
    }

    fun clearSource(
        sourceType: SourceType,
    ) {
        val itemIdsForSource = sourceTypeByItemId.entries
            .filter { (_, itemSourceType) -> itemSourceType == sourceType }
            .mapTo(mutableSetOf()) { (itemId, _) -> itemId }
        coverRequestJobsByKey.entries
            .filter { (key, _) -> key.itemId in itemIdsForSource }
            .forEach { (key, job) ->
                job.cancel()
                coverRequestJobsByKey.remove(key)
            }
        itemIdsForSource.forEach(visibleLibraryCoverRegistry::remove)
        itemIdsForSource.forEach(sourceTypeByItemId::remove)
        activeRequestNonceByKey.keys.removeAll { key -> key.itemId in itemIdsForSource }
        pendingCoverKeys.removeAll { key -> key.itemId in itemIdsForSource }
        lastErrorMessageBySource.remove(sourceType)
    }

    private fun scheduleCacheWarmup(
        sourceType: SourceType,
    ) {
        if (sourceType == SourceType.LOCAL) return
        if (sourceType in warmedSources) return
        if (sourceWarmupJobs[sourceType]?.isActive == true) return
        val dataSource = coverDataSources[sourceType] ?: return
        sourceWarmupJobs[sourceType] = backgroundScope.launch {
            runCatching { dataSource.warmCacheIndexes() }
                .onSuccess {
                    warmedSources += sourceType
                }
                .onFailure { throwable ->
                    Log.w(LOG_TAG, "Unable to warm ${sourceType.name.lowercase()} cover cache indexes.", throwable)
                }
        }.also { job ->
            job.invokeOnCompletion {
                if (sourceWarmupJobs[sourceType] === job) {
                    sourceWarmupJobs.remove(sourceType)
                }
            }
        }
    }

    private fun isActiveRequest(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
        requestNonce: Long,
        requestSourceResetVersion: Long,
    ): Boolean = activeRequestNonceByKey[RemoteCoverRequestKey(item.id, variant, item.coverIdentityFileSegment())] == requestNonce &&
        sourceResetVersion(item.sourceType) == requestSourceResetVersion &&
        sourceTypeByItemId[item.id] == item.sourceType

    private fun sourceResetVersion(
        sourceType: SourceType,
    ): Long = sourceResetVersionBySource[sourceType] ?: 0L

    private fun bumpSourceResetVersion(
        sourceType: SourceType,
    ) {
        sourceResetVersionBySource[sourceType] = sourceResetVersion(sourceType) + 1L
    }

    private fun cancelRequestsForSource(
        sourceType: SourceType,
    ) {
        sourceTypeByItemId.entries
            .asSequence()
            .filter { (_, itemSourceType) -> itemSourceType == sourceType }
            .map { (itemId, _) -> itemId }
            .toList()
            .forEach { itemId ->
                coverRequestJobsByKey.entries
                    .filter { (key, _) -> key.itemId == itemId }
                    .forEach { (key, job) ->
                        job.cancel()
                        coverRequestJobsByKey.remove(key)
                    }
            }
    }

    private fun clearCachedUrisForSource(
        sourceType: SourceType,
    ) {
        sourceTypeByItemId.entries
            .asSequence()
            .filter { (_, itemSourceType) -> itemSourceType == sourceType }
            .map { (itemId, _) -> itemId }
            .toList()
            .forEach(visibleLibraryCoverRegistry::remove)
    }

    private fun cachedCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): String? = coverDataSources[item.sourceType]
        ?.cachedCover(item, variant)
        ?.uri
        ?.toString()
        ?.takeIf(String::isNotBlank)

    private fun versionRemoteCoverUrl(
        sourceType: SourceType,
        url: String?,
    ): String? {
        if (url.isNullOrBlank()) return url
        val resetVersion = sourceResetVersion(sourceType)
        if (resetVersion <= 0L) return url
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return url
            }
            val versionFragment = "coverv=$resetVersion"
            URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                uri.path,
                uri.query,
                listOfNotNull(uri.fragment?.takeIf(String::isNotBlank), versionFragment).joinToString("&"),
            ).toString()
        }.getOrDefault(url)
    }

    private fun remoteFallbackCoverUrlFor(
        item: ReadableItem,
    ): String? = when (item.sourceType) {
        SourceType.KAVITA -> versionRemoteCoverUrl(
            sourceType = item.sourceType,
            url = rewriteRemoteAssetUrlForActiveServer(
                url = item.coverUrl,
                session = kavitaCredentialStore.activeSessionFor(item),
            ),
        )
        SourceType.STUMP -> versionRemoteCoverUrl(
            sourceType = item.sourceType,
            url = rewriteRemoteAssetUrlForActiveServer(
                url = item.coverUrl,
                session = stumpCredentialStore.activeSessionFor(item),
            ),
        )
        SourceType.GRIMMORY -> versionRemoteCoverUrl(
            sourceType = item.sourceType,
            url = rewriteRemoteAssetUrlForActiveServer(
                url = item.coverUrl,
                session = grimmoryCredentialStore.activeSessionFor(item),
            ),
        )
        SourceType.LOCAL -> null
    }
}

private data class RemoteCoverRequestKey(
    val itemId: String,
    val variant: LibraryCoverImageVariant,
    val coverIdentity: String?,
)

private const val LOG_TAG = "UnifiedReaderRemoteCover"
private const val REMOTE_COVER_CACHE_CONCURRENCY = 4
private const val NO_COVER_IDENTITY_PREFETCH_SEGMENT = "no-cover-identity"

private fun defaultOnlineCoverConnectivityPolicy(): RemoteSourceConnectivityPolicy =
    RemoteSourceConnectivityPolicy().apply {
        markReachable(SourceType.KAVITA)
        markReachable(SourceType.STUMP)
        markReachable(SourceType.GRIMMORY)
    }
