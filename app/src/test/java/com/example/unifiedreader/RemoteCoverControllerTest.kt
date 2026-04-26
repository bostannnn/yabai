package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.covers.VisibleLibraryCoverRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoteCoverControllerTest {
    @Test
    fun `coverImageUrlFor returns cached file immediately when warm cache exists`() {
        val cachedUri = Uri.parse("file:///tmp/persistent-kavita-cover.png")
        val kavitaDataSource = PersistentCachedKavitaCoverDataSource(
            cachedUri = cachedUri,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = kavitaDataSource,
            stumpCoverRepository = EmptyStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val kavitaItem = remoteItem(
            id = "kavita:item:issue-cached",
            sourceType = SourceType.KAVITA,
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=99&apiKey=token-1",
        )

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(kavitaItem, LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `coverImageUrlFor returns cached file immediately while viewport is scrolling`() {
        val cachedUri = Uri.parse("file:///tmp/persistent-kavita-cover.png")
        val kavitaDataSource = PersistentCachedKavitaCoverDataSource(
            cachedUri = cachedUri,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = kavitaDataSource,
            stumpCoverRepository = EmptyStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val kavitaItem = remoteItem(
            id = "kavita:item:issue-scrolling",
            sourceType = SourceType.KAVITA,
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=100&apiKey=token-1",
        )

        controller.onLibraryViewportScrollingChanged(true)

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(kavitaItem, LibraryCoverImageVariant.Grid))

        controller.onLibraryViewportScrollingChanged(false)

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(kavitaItem, LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `coverImageUrlFor suppresses remembered remote fallback while offline`() {
        val connectivityPolicy = RemoteSourceConnectivityPolicy().apply {
            markReachable(SourceType.STUMP)
        }
        val registry = VisibleLibraryCoverRegistry()
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = EmptyStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            remoteSourceConnectivityPolicy = connectivityPolicy,
            visibleLibraryCoverRegistry = registry,
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:fallback",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/fallback/thumbnail",
        )

        assertEquals(stumpItem.coverUrl, controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))

        connectivityPolicy.markUnavailable(SourceType.STUMP)

        assertNull(controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `ensureCoverCached skips background fetch when exact persistent variant already cached`() = runTest {
        val cachedUri = Uri.parse("file:///tmp/persistent-stump-cover.png")
        val stumpDataSource = PersistentCachedStumpCoverDataSource(
            cachedUri = cachedUri,
            matchesRequestedVariant = true,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-cached",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-cached/thumbnail",
        )

        controller.ensureCoverCached(stumpItem, LibraryCoverImageVariant.Grid, this) {}
        advanceUntilIdle()

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
        assertEquals(0, stumpDataSource.getCoverUriCalls)
    }

    @Test
    fun `ensureCoverCached defers background cover completion while viewport is scrolling`() = runTest {
        val cachedUri = Uri.parse("file:///tmp/persistent-stump-cover.png")
        val stumpDataSource = DelayedCoverDataSource(sourceType = SourceType.STUMP)
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-scrolling-completion",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-scrolling-completion/thumbnail",
        )

        assertEquals(stumpItem.coverUrl, controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
        controller.onLibraryViewportScrollingChanged(true)

        controller.ensureCoverCached(stumpItem, LibraryCoverImageVariant.Grid, this) {}
        advanceUntilIdle()
        stumpDataSource.awaitRequest()
        stumpDataSource.complete(cachedUri)
        advanceUntilIdle()

        assertEquals(stumpItem.coverUrl, controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))

        controller.onLibraryViewportScrollingChanged(false)

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `ensureCoverCached backfills thumbnail when only persistent fallback cover exists`() = runTest {
        val fallbackUri = Uri.parse("file:///tmp/persistent-stump-cover.png")
        val thumbnailUri = Uri.parse("file:///tmp/persistent-stump-cover-grid.jpg")
        val stumpDataSource = PersistentCachedStumpCoverDataSource(
            cachedUri = fallbackUri,
            matchesRequestedVariant = false,
            refreshedUri = thumbnailUri,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-backfill",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-backfill/thumbnail",
        )

        assertEquals(fallbackUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))

        controller.ensureCoverCached(stumpItem, LibraryCoverImageVariant.Grid, this) {}
        advanceUntilIdle()

        assertEquals(thumbnailUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
        assertEquals(1, stumpDataSource.getCoverUriCalls)
    }

    @Test
    fun `clearSource ignores stale cover fetch completions`() = runTest {
        val stumpDataSource = DelayedCoverDataSource(sourceType = SourceType.STUMP)
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-1",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-1/thumbnail",
        )

        controller.ensureCoverCached(stumpItem, LibraryCoverImageVariant.Grid, this) {}
        advanceUntilIdle()
        stumpDataSource.awaitRequest()

        controller.clearSource(SourceType.STUMP)
        stumpDataSource.complete(Uri.parse("file:///tmp/stale-stump-cover.png"))
        advanceUntilIdle()

        assertEquals(stumpItem.coverUrl, controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `ensureCoverCached migrates legacy file covers into persistent state`() = runTest {
        val legacyUri = Uri.parse("file:///tmp/legacy-stump-cover.png")
        val persistentUri = Uri.parse("file:///tmp/persistent-stump-cover.png")
        val stumpDataSource = MigratingCoverDataSource(
            sourceType = SourceType.STUMP,
            legacyUri = legacyUri,
            persistentUri = persistentUri,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-2",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-2/thumbnail",
        )

        assertEquals(legacyUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))

        controller.ensureCoverCached(stumpItem, LibraryCoverImageVariant.Grid, this) {}
        advanceUntilIdle()

        assertEquals(persistentUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))
    }

    @Test
    fun `resetSource blocks synchronous cache rehydration until delete completes`() = runTest {
        val cachedUri = Uri.parse("file:///tmp/stale-stump-cover.png")
        val stumpDataSource = ResettableCachedCoverDataSource(
            sourceType = SourceType.STUMP,
            cachedUri = cachedUri,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-3",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-3/thumbnail",
        )

        controller.ensureCoverCached(stumpItem, LibraryCoverImageVariant.Grid, this) {}
        advanceUntilIdle()
        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid))

        val resetJob = async {
            controller.resetSource(SourceType.STUMP)
        }
        stumpDataSource.awaitClearStarted()

        assertEquals(
            "https://stump.example/api/v1/media/issue-3/thumbnail#coverv=1",
            controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid),
        )

        stumpDataSource.finishClear()
        resetJob.await()
        advanceUntilIdle()

        assertEquals(
            "https://stump.example/api/v1/media/issue-3/thumbnail#coverv=2",
            controller.coverImageUrlFor(stumpItem, LibraryCoverImageVariant.Grid),
        )
    }

    @Test
    fun `coverPrefetchKey changes across reset for remote items without cover urls`() = runTest {
        val cachedUri = Uri.parse("file:///tmp/stale-stump-series-cover.png")
        val stumpDataSource = ResettableCachedCoverDataSource(
            sourceType = SourceType.STUMP,
            cachedUri = cachedUri,
        )
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = stumpDataSource,
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val stumpSeriesItem = ReadableItem(
            id = "stump:series:series-1",
            sourceType = SourceType.STUMP,
            libraryId = "stump:library:comics",
            remoteId = "series-1",
            remoteFileId = "series-1",
            title = "Series 1",
            format = FormatType.CBZ,
            coverUrl = null,
        )

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(stumpSeriesItem, LibraryCoverImageVariant.Grid))
        assertEquals(
            "remote-cover:stump:stump:series:series-1:grid:coverv=0",
            controller.coverPrefetchKeyFor(stumpSeriesItem, LibraryCoverImageVariant.Grid),
        )

        val resetJob = async {
            controller.resetSource(SourceType.STUMP)
        }
        stumpDataSource.awaitClearStarted()

        assertEquals(
            "remote-cover:stump:stump:series:series-1:grid:coverv=1",
            controller.coverPrefetchKeyFor(stumpSeriesItem, LibraryCoverImageVariant.Grid),
        )

        stumpDataSource.finishClear()
        resetJob.await()
        advanceUntilIdle()

        assertEquals(
            "remote-cover:stump:stump:series:series-1:grid:coverv=2",
            controller.coverPrefetchKeyFor(stumpSeriesItem, LibraryCoverImageVariant.Grid),
        )
    }

    @Test
    fun `kavita cover waits for cached file instead of using remote fallback`() {
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = EmptyStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val kavitaItem = remoteItem(
            id = "kavita:item:issue-1",
            sourceType = SourceType.KAVITA,
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=1&apiKey=token-1",
        )

        assertNull(controller.coverImageUrlFor(kavitaItem, LibraryCoverImageVariant.Grid))
        assertEquals(
            "remote-cover:kavita:kavita:item:issue-1:grid:coverv=0",
            controller.coverPrefetchKeyFor(kavitaItem, LibraryCoverImageVariant.Grid),
        )
    }

    @Test
    fun `grimmory cover waits for cached file instead of using remote fallback`() {
        val controller = RemoteCoverController(
            kavitaCoverRepository = EmptyKavitaCoverDataSource(),
            stumpCoverRepository = EmptyStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = immediateAppDispatchers(),
        )
        val grimmoryItem = remoteItem(
            id = "grimmory:item:book-1",
            sourceType = SourceType.GRIMMORY,
            coverUrl = "https://grimmory.example/api/v1/media/book/1/thumbnail",
        )

        assertNull(controller.coverImageUrlFor(grimmoryItem, LibraryCoverImageVariant.Grid))
        assertEquals(
            "remote-cover:grimmory:grimmory:item:book-1:grid:coverv=0",
            controller.coverPrefetchKeyFor(grimmoryItem, LibraryCoverImageVariant.Grid),
        )
    }

    @Test
    fun `prepareSource warms cache so cached kavita cover becomes visible without prefetch`() = runTest {
        val cachedUri = Uri.parse("file:///tmp/warmed-kavita-cover.png")
        val kavitaDataSource = WarmableKavitaCoverDataSource(cachedUri = cachedUri)
        val controller = RemoteCoverController(
            kavitaCoverRepository = kavitaDataSource,
            stumpCoverRepository = EmptyStumpCoverDataSource(),
            grimmoryCoverRepository = EmptyGrimmoryCoverDataSource(),
            kavitaCredentialStore = FakeKavitaCredentialStore(),
            stumpCredentialStore = FakeStumpCredentialStore(),
            grimmoryCredentialStore = FakeGrimmoryCredentialStore(),
            visibleLibraryCoverRegistry = VisibleLibraryCoverRegistry(),
            appDispatchers = AppDispatchers(
                io = StandardTestDispatcher(testScheduler),
                default = StandardTestDispatcher(testScheduler),
            ),
        )
        val kavitaItem = remoteItem(
            id = "kavita:item:warm-cache",
            sourceType = SourceType.KAVITA,
            coverUrl = "https://kavita.example/api/Image/chapter-cover?chapterId=7&apiKey=token-1",
        )

        assertNull(controller.coverImageUrlFor(kavitaItem, LibraryCoverImageVariant.Grid))

        controller.prepareSource(SourceType.KAVITA)
        advanceUntilIdle()

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(kavitaItem, LibraryCoverImageVariant.Grid))
        assertEquals(1, kavitaDataSource.warmCacheIndexesCalls)
    }
}

private fun immediateAppDispatchers(): AppDispatchers = AppDispatchers(
    io = Dispatchers.Unconfined,
    default = Dispatchers.Unconfined,
)

private class EmptyKavitaCoverDataSource : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

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

private class DelayedCoverDataSource(
    override val sourceType: SourceType,
) : StumpCoverDataSource {
    private val requestStarted = CompletableDeferred<Unit>()
    private val response = CompletableDeferred<Uri?>()

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? {
        requestStarted.complete(Unit)
        return response.await()
    }

    override suspend fun clearCachedCovers() = Unit

    suspend fun awaitRequest() {
        requestStarted.await()
    }

    fun complete(
        uri: Uri?,
    ) {
        response.complete(uri)
    }
}

private class EmptyStumpCoverDataSource : StumpCoverDataSource {
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

private class EmptyGrimmoryCoverDataSource : GrimmoryCoverDataSource {
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

private class MigratingCoverDataSource(
    override val sourceType: SourceType,
    legacyUri: Uri,
    private val persistentUri: Uri,
) : StumpCoverDataSource {
    private var cachedCover = CachedRemoteCover(
        uri = legacyUri,
        isPersistent = false,
    )

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = cachedCover

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri {
        cachedCover = CachedRemoteCover(
            uri = persistentUri,
            isPersistent = true,
        )
        return persistentUri
    }

    override suspend fun clearCachedCovers() = Unit
}

private class ResettableCachedCoverDataSource(
    override val sourceType: SourceType,
    cachedUri: Uri,
) : StumpCoverDataSource {
    private val clearStarted = CompletableDeferred<Unit>()
    private val clearFinished = CompletableDeferred<Unit>()
    private var cachedCover: CachedRemoteCover? = CachedRemoteCover(
        uri = cachedUri,
        isPersistent = true,
    )

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = cachedCover

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri? = cachedCover?.uri

    override suspend fun clearCachedCovers() {
        clearStarted.complete(Unit)
        clearFinished.await()
        cachedCover = null
    }

    suspend fun awaitClearStarted() {
        clearStarted.await()
    }

    fun finishClear() {
        clearFinished.complete(Unit)
    }
}

private abstract class PersistentCachedCoverDataSource(
    cachedUri: Uri,
    matchesRequestedVariant: Boolean,
    private val refreshedUri: Uri = cachedUri,
) : RemoteCoverDataSource {
    private var cachedCover = CachedRemoteCover(
        uri = cachedUri,
        isPersistent = true,
        matchesRequestedVariant = matchesRequestedVariant,
    )
    var getCoverUriCalls: Int = 0
        private set

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover = cachedCover

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri {
        getCoverUriCalls += 1
        cachedCover = CachedRemoteCover(
            uri = refreshedUri,
            isPersistent = true,
            matchesRequestedVariant = true,
        )
        return cachedCover.uri
    }

    override suspend fun clearCachedCovers() = Unit
}

private class PersistentCachedKavitaCoverDataSource(
    cachedUri: Uri,
    matchesRequestedVariant: Boolean = true,
    refreshedUri: Uri = cachedUri,
) : PersistentCachedCoverDataSource(cachedUri, matchesRequestedVariant, refreshedUri), KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA
}

private class WarmableKavitaCoverDataSource(
    private val cachedUri: Uri,
) : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    var warmCacheIndexesCalls: Int = 0
        private set

    private var isWarm = false

    override fun cachedCover(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): CachedRemoteCover? = if (isWarm) {
        CachedRemoteCover(
            uri = cachedUri,
            isPersistent = true,
        )
    } else {
        null
    }

    override suspend fun getCoverUri(
        item: ReadableItem,
        variant: LibraryCoverImageVariant,
    ): Uri = cachedUri

    override suspend fun warmCacheIndexes() {
        warmCacheIndexesCalls += 1
        isWarm = true
    }

    override suspend fun clearCachedCovers() = Unit
}

private class PersistentCachedStumpCoverDataSource(
    cachedUri: Uri,
    matchesRequestedVariant: Boolean = true,
    refreshedUri: Uri = cachedUri,
) : PersistentCachedCoverDataSource(cachedUri, matchesRequestedVariant, refreshedUri), StumpCoverDataSource {
    override val sourceType: SourceType = SourceType.STUMP
}

private fun remoteItem(
    id: String,
    sourceType: SourceType,
    coverUrl: String,
): ReadableItem = ReadableItem(
    id = id,
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:test",
    remoteId = id.substringAfterLast(':'),
    title = id,
    format = FormatType.CBZ,
    coverUrl = coverUrl,
)
