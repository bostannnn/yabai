package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class RemoteCoverPreloadProgress(
    val processedCount: Int,
    val totalCount: Int,
    val currentTitle: String?,
    val failedCount: Int,
) {
    val fraction: Float
        get() = if (totalCount <= 0) 1f else processedCount.toFloat() / totalCount.toFloat()

    val label: String
        get() = buildString {
            append(processedCount)
            append(" / ")
            append(totalCount)
            append(" covers")
            if (failedCount > 0) {
                append(", ")
                append(failedCount)
                append(" failed")
            }
            currentTitle?.takeIf(String::isNotBlank)?.let { title ->
                append(" - ")
                append(title)
            }
        }
}

data class RemoteCoverPreloadResult(
    val totalCount: Int,
    val succeededCount: Int,
    val failures: List<RemoteCoverPreloadFailure> = emptyList(),
) {
    val failedCount: Int
        get() = failures.size

    val message: String
        get() = if (failedCount == 0) {
            "Preloaded $succeededCount covers."
        } else {
            "Preloaded $succeededCount of $totalCount covers; $failedCount failed."
        }
}

data class RemoteCoverPreloadFailure(
    val item: ReadableItem,
    val variant: LibraryCoverImageVariant,
    val reason: String,
)

class RemoteCoverPreloader @Inject constructor(
    kavitaCoverRepository: KavitaCoverDataSource,
    stumpCoverRepository: StumpCoverDataSource,
    grimmoryCoverRepository: GrimmoryCoverDataSource,
    private val remoteSourceConnectivityPolicy: RemoteSourceConnectivityPolicy,
    private val appDispatchers: AppDispatchers = AppDispatchers(),
) {
    private val coverDataSources = listOf<RemoteCoverDataSource>(
        kavitaCoverRepository,
        stumpCoverRepository,
        grimmoryCoverRepository,
    ).associateBy(RemoteCoverDataSource::sourceType)

    suspend fun preload(
        sourceType: SourceType,
        items: List<ReadableItem>,
        variants: List<LibraryCoverImageVariant> = defaultCoverPreloadVariants,
        onProgress: (RemoteCoverPreloadProgress) -> Unit,
    ): RemoteCoverPreloadResult = preloadTargets(
        sourceType = sourceType,
        targets = buildTargets(
            sourceType = sourceType,
            items = items,
            variants = variants,
        ),
        onProgress = onProgress,
    )

    suspend fun preloadTargets(
        sourceType: SourceType,
        targets: List<RemoteCoverPreloadTarget>,
        onProgress: (RemoteCoverPreloadProgress) -> Unit,
    ): RemoteCoverPreloadResult {
        val dataSource = coverDataSources[sourceType]
        if (dataSource == null || targets.isEmpty() || !remoteSourceConnectivityPolicy.canFetchCover(sourceType)) {
            onProgress(RemoteCoverPreloadProgress(0, targets.size, null, 0))
            return RemoteCoverPreloadResult(
                totalCount = targets.size,
                succeededCount = 0,
            )
        }
        withContext(appDispatchers.io) {
            dataSource.warmCacheIndexes()
        }
        val semaphore = Semaphore(REMOTE_COVER_PRELOAD_CONCURRENCY)
        val progressLock = Any()
        var processedCount = 0
        var succeededCount = 0
        val failures = mutableListOf<RemoteCoverPreloadFailure>()
        onProgress(RemoteCoverPreloadProgress(0, targets.size, null, 0))
        coroutineScope {
            targets.map { target ->
                async {
                    val failure = semaphore.withPermit {
                        withContext(appDispatchers.io) {
                            preloadTarget(dataSource, target)
                        }
                    }
                    synchronized(progressLock) {
                        processedCount += 1
                        if (failure == null) {
                            succeededCount += 1
                        } else {
                            failures += failure
                        }
                        onProgress(
                            RemoteCoverPreloadProgress(
                                processedCount = processedCount,
                                totalCount = targets.size,
                                currentTitle = target.item.title,
                                failedCount = failures.size,
                            ),
                        )
                    }
                }
            }.awaitAll()
        }
        return RemoteCoverPreloadResult(
            totalCount = targets.size,
            succeededCount = succeededCount,
            failures = failures.toList(),
        )
    }

    private suspend fun preloadTarget(
        dataSource: RemoteCoverDataSource,
        target: RemoteCoverPreloadTarget,
    ): RemoteCoverPreloadFailure? {
        val cachedCover = dataSource.cachedCover(target.item, target.variant)
        if (cachedCover?.isPersistent == true && cachedCover.matchesRequestedVariant) {
            return null
        }
        return try {
            if (dataSource.getCoverUri(target.item, target.variant) != null) {
                null
            } else {
                target.failure("No cover URI was returned.")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (throwable: Throwable) {
            target.failure(throwable.userFacingMessage())
        }
    }

    private fun buildTargets(
        sourceType: SourceType,
        items: List<ReadableItem>,
        variants: List<LibraryCoverImageVariant>,
    ): List<RemoteCoverPreloadTarget> = items
        .asSequence()
        .filter { item -> item.sourceType == sourceType }
        .filter { item -> sourceType != SourceType.LOCAL }
        .flatMap { item ->
            variants.asSequence()
                .distinctBy { variant -> variant.toCoverAssetVariant() }
                .map { variant -> RemoteCoverPreloadTarget(item, variant) }
        }
        .toList()
}

data class RemoteCoverPreloadTarget(
    val item: ReadableItem,
    val variant: LibraryCoverImageVariant,
)

private fun RemoteCoverPreloadTarget.failure(
    reason: String,
): RemoteCoverPreloadFailure = RemoteCoverPreloadFailure(
    item = item,
    variant = variant,
    reason = reason,
)

private val defaultCoverPreloadVariants = listOf(
    LibraryCoverImageVariant.ShelfPreview,
    LibraryCoverImageVariant.Grid,
    LibraryCoverImageVariant.GridLarge,
    LibraryCoverImageVariant.GridExtraLarge,
    LibraryCoverImageVariant.Detailed,
)

private const val REMOTE_COVER_PRELOAD_CONCURRENCY = 4
