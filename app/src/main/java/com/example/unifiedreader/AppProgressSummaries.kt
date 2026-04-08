package com.example.unifiedreader

import androidx.annotation.VisibleForTesting
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot

internal fun buildProgressSummaries(
    items: List<ReadableItem>,
    progressByItemId: Map<String, ReaderProgressSnapshot>,
    sessionByItemId: Map<String, ReaderSessionSnapshot>,
    localPageCountProvider: (ReadableItem) -> Int,
): Map<String, ItemProgressSummary> = items.associateNotNull { item ->
    val progress = progressByItemId[item.id] ?: return@associateNotNull null
    val sessionPageCount = sessionByItemId[item.id]?.pageCount?.takeIf { it > 0 }
    val summary = when (progress.locatorType) {
        LocatorType.PAGE -> {
            val pageIndex = progress.page ?: return@associateNotNull null
            val pageCount = sessionPageCount ?: item.pageCount ?: when (item.format) {
                FormatType.CBZ,
                FormatType.CBR -> localPageCountProvider(item)
                FormatType.EPUB -> 0
                FormatType.PDF -> 0
            }
            buildItemProgressSummary(
                pageIndex = pageIndex,
                pageCount = pageCount,
                lastReadAtEpochMillis = progress.updatedAtEpochMillis,
            )
        }

        LocatorType.PERCENT -> buildPercentProgressSummary(
            percent = progress.percent,
            pageCount = sessionPageCount,
            lastReadAtEpochMillis = progress.updatedAtEpochMillis,
        )
        LocatorType.CFI -> buildSavedLocationProgressSummary(
            percent = progress.percent,
            page = progress.page,
            pageCount = sessionPageCount,
            lastReadAtEpochMillis = progress.updatedAtEpochMillis,
        )
    } ?: return@associateNotNull null
    item.id to summary
}

private fun buildItemProgressSummary(
    pageIndex: Int,
    pageCount: Int,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary? {
    if (pageCount <= 0) return null
    val clampedPageIndex = pageIndex.coerceIn(0, pageCount - 1)
    return ItemProgressSummary(
        label = "Resume on page ${clampedPageIndex + 1} of $pageCount",
        fraction = (clampedPageIndex + 1) / pageCount.toFloat(),
        currentPage = clampedPageIndex + 1,
        pageCount = pageCount,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
    )
}

private fun buildPercentProgressSummary(
    percent: Double?,
    pageCount: Int? = null,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary? {
    val normalizedPercent = percent?.coerceIn(0.0, 1.0) ?: return null
    val percentLabel = (normalizedPercent * 100).toInt()
    return ItemProgressSummary(
        label = "Resume at $percentLabel%",
        fraction = normalizedPercent.toFloat(),
        pageCount = pageCount,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
    )
}

private fun buildSavedLocationProgressSummary(
    percent: Double?,
    page: Int? = null,
    pageCount: Int? = null,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary = ItemProgressSummary(
    label = "Resume from saved location",
    fraction = (percent ?: 0.0).coerceIn(0.0, 1.0).toFloat(),
    currentPage = page?.takeIf { it > 0 },
    pageCount = pageCount,
    lastReadAtEpochMillis = lastReadAtEpochMillis,
)

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object AppViewModelTestAccess {
    fun buildItemProgressSummary(
        pageIndex: Int,
        pageCount: Int,
    ): ItemProgressSummary? = com.example.unifiedreader.buildItemProgressSummary(
        pageIndex = pageIndex,
        pageCount = pageCount,
    )
}
