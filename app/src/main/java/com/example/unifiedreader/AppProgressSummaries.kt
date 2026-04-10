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
            val pageCount = sessionPageCount ?: item.pageCount ?: when (item.format) {
                FormatType.CBZ,
                FormatType.CBR -> localPageCountProvider(item)
                FormatType.EPUB -> 0
                FormatType.PDF -> 0
            }
            val pageIndex = progress.page ?: if (progress.isFinished) {
                (pageCount - 1).coerceAtLeast(0)
            } else {
                return@associateNotNull null
            }
            buildItemProgressSummary(
                pageIndex = pageIndex,
                pageCount = pageCount,
                isFinished = progress.isFinished,
                lastReadAtEpochMillis = progress.updatedAtEpochMillis,
            )
        }

        LocatorType.PERCENT -> buildPercentProgressSummary(
            percent = progress.percent,
            pageCount = sessionPageCount,
            isFinished = progress.isFinished,
            lastReadAtEpochMillis = progress.updatedAtEpochMillis,
        )
        LocatorType.CFI -> buildSavedLocationProgressSummary(
            percent = progress.percent,
            page = progress.page,
            pageCount = sessionPageCount,
            isFinished = progress.isFinished,
            lastReadAtEpochMillis = progress.updatedAtEpochMillis,
        )
    } ?: return@associateNotNull null
    item.id to summary
}

private fun buildItemProgressSummary(
    pageIndex: Int,
    pageCount: Int,
    isFinished: Boolean = false,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary? {
    if (pageCount <= 0) {
        return if (isFinished) {
            ItemProgressSummary(
                label = "Finished",
                fraction = 1f,
                lastReadAtEpochMillis = lastReadAtEpochMillis,
                isFinished = true,
            )
        } else {
            null
        }
    }
    val clampedPageIndex = pageIndex.coerceIn(0, pageCount - 1)
    return ItemProgressSummary(
        label = if (isFinished) "Finished" else "Resume on page ${clampedPageIndex + 1} of $pageCount",
        fraction = if (isFinished) 1f else (clampedPageIndex + 1) / pageCount.toFloat(),
        currentPage = if (isFinished) pageCount else clampedPageIndex + 1,
        pageCount = pageCount,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
        isFinished = isFinished,
    )
}

private fun buildPercentProgressSummary(
    percent: Double?,
    pageCount: Int? = null,
    isFinished: Boolean = false,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary? {
    val normalizedPercent = if (isFinished) {
        1.0
    } else {
        percent?.coerceIn(0.0, 1.0) ?: return null
    }
    val percentLabel = (normalizedPercent * 100).toInt()
    return ItemProgressSummary(
        label = if (isFinished) "Finished" else "Resume at $percentLabel%",
        fraction = normalizedPercent.toFloat(),
        pageCount = pageCount,
        lastReadAtEpochMillis = lastReadAtEpochMillis,
        isFinished = isFinished,
    )
}

private fun buildSavedLocationProgressSummary(
    percent: Double?,
    page: Int? = null,
    pageCount: Int? = null,
    isFinished: Boolean = false,
    lastReadAtEpochMillis: Long = 0L,
): ItemProgressSummary = ItemProgressSummary(
    label = if (isFinished) "Finished" else "Resume from saved location",
    fraction = if (isFinished) 1f else (percent ?: 0.0).coerceIn(0.0, 1.0).toFloat(),
    currentPage = if (isFinished) pageCount else page?.takeIf { it > 0 },
    pageCount = pageCount,
    lastReadAtEpochMillis = lastReadAtEpochMillis,
    isFinished = isFinished,
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
