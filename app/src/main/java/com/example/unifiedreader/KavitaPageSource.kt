package com.example.unifiedreader

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.comics.ComicPageLoadResult
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec

class KavitaPageSource(
    private val kavitaApiFactory: KavitaApiFactory,
    private val kavitaCredentialStore: KavitaCredentialStore,
) : ComicPageSource {
    private val sessionRequestRunner = KavitaStoredSessionRequestRunner(kavitaCredentialStore)

    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> {
        if (item.sourceType != SourceType.KAVITA || item.format != FormatType.CBZ) return emptyList()
        val pageCount = item.pageCount?.coerceAtLeast(0) ?: return emptyList()
        return List(pageCount) { index ->
            ComicPageSpec(
                id = index.toString(),
                contentDescription = "${item.title} page ${index + 1}",
            )
        }
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult {
        if (item.sourceType != SourceType.KAVITA) {
            return ComicPageLoadResult.Failure("Unsupported source.")
        }
        val session = kavitaCredentialStore.activeSessionFor(item)
            ?: return ComicPageLoadResult.Failure("Connect to Kavita before reading remote comics.")
        val chapterId = item.remoteId
            ?: return ComicPageLoadResult.Failure("Unable to resolve Kavita chapter id.")
        val pageIndex = page.id.toIntOrNull()
            ?: return ComicPageLoadResult.Failure("Unable to resolve Kavita page index.")
        val api = kavitaApiFactory.create(session.baseUrl)
        return runCatching {
            sessionRequestRunner.run(
                api = api,
                session = session,
            ) { accessToken, apiKey ->
                api.downloadPageImage(
                    accessToken = accessToken,
                    chapterId = chapterId,
                    pageIndex = pageIndex,
                    apiKey = apiKey,
                )
            }
        }.mapCatching { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Unable to decode this page.")
            ComicPageLoadResult.Success(bitmap.asImageBitmap())
        }.getOrElse { throwable ->
            ComicPageLoadResult.Failure(throwable.message ?: "Unable to load this page.")
        }
    }
}

class CompositeComicPageSource(
    private val localComicPageSource: LocalComicPageSource,
    private val kavitaPageSource: KavitaPageSource,
) : ComicPageSource {
    override fun getPages(
        item: ReadableItem,
    ): List<ComicPageSpec> = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.getPages(item)
        SourceType.KAVITA -> kavitaPageSource.getPages(item)
        SourceType.GRIMMORY -> emptyList()
    }

    override suspend fun loadPageImage(
        item: ReadableItem,
        page: ComicPageSpec,
    ): ComicPageLoadResult = when (item.sourceType) {
        SourceType.LOCAL -> localComicPageSource.loadPageImage(item, page)
        SourceType.KAVITA -> kavitaPageSource.loadPageImage(item, page)
        SourceType.GRIMMORY -> ComicPageLoadResult.Failure("Unsupported source.")
    }

    override fun prefetchAround(
        item: ReadableItem,
        settledPageIndex: Int,
        pageCount: Int,
    ) {
        if (item.sourceType == SourceType.LOCAL) {
            localComicPageSource.prefetchAround(item, settledPageIndex, pageCount)
        }
    }
}
