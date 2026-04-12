package com.example.unifiedreader

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RemoteCoverControllerTest {
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
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-1",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-1/thumbnail",
        )

        controller.ensureCoverCached(stumpItem, this) {}
        advanceUntilIdle()
        stumpDataSource.awaitRequest()

        controller.clearSource(SourceType.STUMP)
        stumpDataSource.complete(Uri.parse("file:///tmp/stale-stump-cover.png"))
        advanceUntilIdle()

        assertEquals(stumpItem.coverUrl, controller.coverImageUrlFor(stumpItem))
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
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-2",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-2/thumbnail",
        )

        assertEquals(legacyUri.toString(), controller.coverImageUrlFor(stumpItem))

        controller.ensureCoverCached(stumpItem, this) {}
        advanceUntilIdle()

        assertEquals(persistentUri.toString(), controller.coverImageUrlFor(stumpItem))
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
        )
        val stumpItem = remoteItem(
            id = "stump:item:issue-3",
            sourceType = SourceType.STUMP,
            coverUrl = "https://stump.example/api/v1/media/issue-3/thumbnail",
        )

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(stumpItem))

        val resetJob = async {
            controller.resetSource(SourceType.STUMP)
        }
        stumpDataSource.awaitClearStarted()

        assertEquals(
            "https://stump.example/api/v1/media/issue-3/thumbnail#coverv=1",
            controller.coverImageUrlFor(stumpItem),
        )

        stumpDataSource.finishClear()
        resetJob.await()
        advanceUntilIdle()

        assertEquals(
            "https://stump.example/api/v1/media/issue-3/thumbnail#coverv=2",
            controller.coverImageUrlFor(stumpItem),
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

        assertEquals(cachedUri.toString(), controller.coverImageUrlFor(stumpSeriesItem))
        assertEquals(
            "remote-cover:stump:stump:series:series-1:coverv=0",
            controller.coverPrefetchKeyFor(stumpSeriesItem),
        )

        val resetJob = async {
            controller.resetSource(SourceType.STUMP)
        }
        stumpDataSource.awaitClearStarted()

        assertEquals(
            "remote-cover:stump:stump:series:series-1:coverv=1",
            controller.coverPrefetchKeyFor(stumpSeriesItem),
        )

        stumpDataSource.finishClear()
        resetJob.await()
        advanceUntilIdle()

        assertEquals(
            "remote-cover:stump:stump:series:series-1:coverv=2",
            controller.coverPrefetchKeyFor(stumpSeriesItem),
        )
    }
}

private class EmptyKavitaCoverDataSource : KavitaCoverDataSource {
    override val sourceType: SourceType = SourceType.KAVITA

    override fun cachedCover(
        item: ReadableItem,
    ): CachedRemoteCover? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
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
    ): CachedRemoteCover? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
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

private class EmptyGrimmoryCoverDataSource : GrimmoryCoverDataSource {
    override val sourceType: SourceType = SourceType.GRIMMORY

    override fun cachedCover(
        item: ReadableItem,
    ): CachedRemoteCover? = null

    override suspend fun getCoverUri(
        item: ReadableItem,
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
    ): CachedRemoteCover? = cachedCover

    override suspend fun getCoverUri(
        item: ReadableItem,
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
    ): CachedRemoteCover? = cachedCover

    override suspend fun getCoverUri(
        item: ReadableItem,
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
