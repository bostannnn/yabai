package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoteCoverPreloaderTest {
    @Test
    fun `preload skips exact cached variants and fetches missing variants`() = runTest {
        val kavitaDataSource = RecordingKavitaCoverDataSource(
            cachedVariants = setOf(LibraryCoverImageVariant.Grid),
        )
        val preloader = RemoteCoverPreloader(
            kavitaCoverRepository = kavitaDataSource,
            stumpCoverRepository = EmptyPreloadStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyPreloadGrimmoryCoverDataSource(),
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy().apply {
                markReachable(SourceType.KAVITA)
            },
            appDispatchers = AppDispatchers(
                io = Dispatchers.Unconfined,
                default = Dispatchers.Unconfined,
            ),
        )
        val item = preloadItem(SourceType.KAVITA)

        val result = preloader.preload(
            sourceType = SourceType.KAVITA,
            items = listOf(item),
            variants = listOf(
                LibraryCoverImageVariant.Grid,
                LibraryCoverImageVariant.GridLarge,
            ),
            onProgress = {},
        )

        assertEquals(2, result.totalCount)
        assertEquals(2, result.succeededCount)
        assertEquals(0, result.failedCount)
        assertEquals(listOf(LibraryCoverImageVariant.GridLarge), kavitaDataSource.requestedVariants)
    }

    @Test
    fun `preload does not fetch when source is offline`() = runTest {
        val kavitaDataSource = RecordingKavitaCoverDataSource()
        val preloader = RemoteCoverPreloader(
            kavitaCoverRepository = kavitaDataSource,
            stumpCoverRepository = EmptyPreloadStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyPreloadGrimmoryCoverDataSource(),
            remoteSourceConnectivityPolicy = RemoteSourceConnectivityPolicy(),
            appDispatchers = AppDispatchers(
                io = Dispatchers.Unconfined,
                default = Dispatchers.Unconfined,
            ),
        )

        val result = preloader.preload(
            sourceType = SourceType.KAVITA,
            items = listOf(preloadItem(SourceType.KAVITA)),
            variants = listOf(LibraryCoverImageVariant.Grid),
            onProgress = {},
        )

        assertEquals(1, result.totalCount)
        assertEquals(0, result.succeededCount)
        assertEquals(0, kavitaDataSource.requestedVariants.size)
    }
}

private class RecordingKavitaCoverDataSource(
    private val cachedVariants: Set<LibraryCoverImageVariant> = emptySet(),
) : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA
    val requestedVariants = mutableListOf<LibraryCoverImageVariant>()

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = if (variant in cachedVariants) {
        CachedRemoteCover(
            uri = Uri.parse("file:///tmp/${variant.name}.jpg"),
            isPersistent = true,
            matchesRequestedVariant = true,
        )
    } else {
        null
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri {
        requestedVariants += variant
        return Uri.parse("file:///tmp/${variant.name}-downloaded.jpg")
    }

    override suspend fun clearCachedCovers() = Unit
}

private class EmptyPreloadStumpCoverDataSource : StumpCoverDataSource {
    override val sourceType: SourceType = SourceType.STUMP

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? = null

    override suspend fun clearCachedCovers() = Unit
}

private class EmptyPreloadGrimmoryCoverDataSource : GrimmoryCoverDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? = null

    override suspend fun clearCachedCovers() = Unit
}

private fun preloadItem(
    sourceType: SourceType,
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:1",
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:1",
    remoteId = "1",
    title = "Item",
    format = FormatType.CBZ,
)

