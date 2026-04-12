package com.example.unifiedreader

import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import java.net.URI
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@ViewModelScoped
class RemoteCoverController @Inject constructor(
    kavitaCoverRepository: KavitaCoverDataSource,
    stumpCoverRepository: StumpCoverDataSource,
    grimmoryCoverRepository: GrimmoryCoverDataSource,
    private val kavitaCredentialStore: KavitaCredentialStore,
    private val stumpCredentialStore: StumpCredentialStore,
    private val grimmoryCredentialStore: GrimmoryCredentialStore,
) {
    private val coverDataSources = listOf<RemoteCoverDataSource>(
        kavitaCoverRepository,
        stumpCoverRepository,
        grimmoryCoverRepository,
    ).associateBy(RemoteCoverDataSource::sourceType)
    private val coverUrisByItemId = mutableStateMapOf<String, String>()
    private val sourceTypeByItemId = mutableMapOf<String, SourceType>()
    private val pendingCoverIds = mutableSetOf<String>()
    private val activeRequestNonceByItemId = mutableMapOf<String, Long>()
    private val coverRequestJobsByItemId = mutableMapOf<String, Job>()
    private val sourceResetVersionBySource = mutableMapOf<SourceType, Long>()
    private val sourcesBeingReset = mutableSetOf<SourceType>()
    private var lastErrorMessageBySource = mutableMapOf<SourceType, String>()
    private var nextRequestNonce: Long = 0L

    fun coverImageUrlFor(
        item: ReadableItem,
    ): String? {
        sourceTypeByItemId[item.id] = item.sourceType
        coverUrisByItemId[item.id]?.let { return it }
        if (item.sourceType !in sourcesBeingReset) {
            coverDataSources[item.sourceType]
                ?.cachedCover(item)
                ?.let { cachedCover ->
                    val cachedUri = cachedCover.uri.toString()
                    if (cachedUri.isNotBlank()) {
                        coverUrisByItemId[item.id] = cachedUri
                        return cachedUri
                    }
                }
        }
        return when (item.sourceType) {
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
            SourceType.LOCAL -> item.coverUrl
        }
    }

    fun coverPrefetchKeyFor(
        item: ReadableItem,
    ): String? {
        if (item.sourceType == SourceType.LOCAL) return null
        return remoteFallbackCoverUrlFor(item)
            ?: "remote-cover:${item.sourceType.name.lowercase()}:${item.id}:coverv=${sourceResetVersion(item.sourceType)}"
    }

    fun ensureCoverCached(
        item: ReadableItem,
        scope: CoroutineScope,
        onError: (String) -> Unit,
    ) {
        sourceTypeByItemId[item.id] = item.sourceType
        val dataSource = coverDataSources[item.sourceType] ?: return
        if (item.sourceType in sourcesBeingReset) return
        if (item.id in pendingCoverIds) return
        val cachedCover = dataSource.cachedCover(item)
        if (cachedCover?.isPersistent == true) {
            val cachedUri = cachedCover.uri.toString()
            if (cachedUri.isNotBlank()) {
                coverUrisByItemId[item.id] = cachedUri
            }
            return
        }
        pendingCoverIds += item.id
        val requestNonce = nextRequestNonce++
        val requestSourceResetVersion = sourceResetVersion(item.sourceType)
        activeRequestNonceByItemId[item.id] = requestNonce
        val job = scope.launch {
            runCatching {
                dataSource.getCoverUri(item)?.toString()
            }.onSuccess { cachedUri ->
                if (!cachedUri.isNullOrBlank() && isActiveRequest(item, requestNonce, requestSourceResetVersion)) {
                    Log.d(LOG_TAG, "Cached ${item.sourceType} cover for ${item.id}: $cachedUri")
                    coverUrisByItemId[item.id] = cachedUri
                    lastErrorMessageBySource.remove(item.sourceType)
                } else if (cachedUri.isNullOrBlank()) {
                    Log.w(LOG_TAG, "${item.sourceType} cover cache returned no URI for ${item.id}.")
                }
            }.onFailure { throwable ->
                if (!isActiveRequest(item, requestNonce, requestSourceResetVersion)) {
                    return@onFailure
                }
                Log.w(LOG_TAG, "Unable to cache ${item.sourceType} cover for ${item.id}.", throwable)
                val message = "Unable to load ${item.sourceType.name.lowercase().replaceFirstChar(Char::titlecase)} covers: ${throwable.userFacingMessage()}"
                if (lastErrorMessageBySource[item.sourceType] != message) {
                    lastErrorMessageBySource[item.sourceType] = message
                    onError(message)
                }
            }.also {
                if (activeRequestNonceByItemId[item.id] == requestNonce) {
                    activeRequestNonceByItemId.remove(item.id)
                    pendingCoverIds -= item.id
                }
            }
        }
        coverRequestJobsByItemId[item.id] = job
        job.invokeOnCompletion {
            if (coverRequestJobsByItemId[item.id] === job) {
                coverRequestJobsByItemId.remove(item.id)
            }
        }
    }

    suspend fun resetSource(
        sourceType: SourceType,
    ) {
        val dataSource = coverDataSources[sourceType] ?: return
        sourcesBeingReset += sourceType
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

    fun removeMissingItems(
        activeItemIds: Set<String>,
    ) {
        val missingItemIds = sourceTypeByItemId.keys
            .toList()
            .filterNot(activeItemIds::contains)
        missingItemIds.forEach(sourceTypeByItemId::remove)
        missingItemIds.forEach(coverUrisByItemId::remove)
        missingItemIds.forEach(activeRequestNonceByItemId::remove)
        missingItemIds.forEach { itemId ->
            coverRequestJobsByItemId.remove(itemId)?.cancel()
        }
        pendingCoverIds.retainAll(activeItemIds)
    }

    fun clearSource(
        sourceType: SourceType,
    ) {
        val itemIdsForSource = sourceTypeByItemId.entries
            .filter { (_, itemSourceType) -> itemSourceType == sourceType }
            .mapTo(mutableSetOf()) { (itemId, _) -> itemId }
        itemIdsForSource.forEach { itemId ->
            coverRequestJobsByItemId.remove(itemId)?.cancel()
        }
        itemIdsForSource.forEach(coverUrisByItemId::remove)
        itemIdsForSource.forEach(sourceTypeByItemId::remove)
        itemIdsForSource.forEach(activeRequestNonceByItemId::remove)
        pendingCoverIds.removeAll(itemIdsForSource)
        lastErrorMessageBySource.remove(sourceType)
    }

    private fun isActiveRequest(
        item: ReadableItem,
        requestNonce: Long,
        requestSourceResetVersion: Long,
    ): Boolean = activeRequestNonceByItemId[item.id] == requestNonce &&
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
                coverRequestJobsByItemId.remove(itemId)?.cancel()
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
            .forEach(coverUrisByItemId::remove)
    }

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

private const val LOG_TAG = "UnifiedReaderRemoteCover"
