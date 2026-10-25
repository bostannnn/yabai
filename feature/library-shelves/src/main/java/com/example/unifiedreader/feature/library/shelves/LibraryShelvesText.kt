package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.librarySecondaryText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun continueReadingSupportingText(
    item: ReadableItem,
): String = item.librarySecondaryText() ?: item.title

// A shelf tile's second line and meta line, the same on the home strip and in See all.

internal fun ContinueReadingShelfItem.tileSupportingText(): String = continueReadingSupportingText(item)

internal fun ContinueReadingShelfItem.tileMetaText(): String = progress.tileLabel.orEmpty()

internal fun NextInSeriesShelfItem.tileSupportingText(): String = item.librarySecondaryText() ?: item.title

internal fun NextInSeriesShelfItem.tileMetaText(): String =
    "After ${currentItem.librarySecondaryText() ?: currentItem.title}"

internal fun RecentlyFinishedShelfItem.tileSupportingText(): String = item.librarySecondaryText() ?: progress.label

/**
 * When it was finished: "3 days ago" within a week, then the date as the book page and the
 * journal write it, "17 Oct" ("17 Oct 2025" in another year).
 */
internal fun RecentlyFinishedShelfItem.tileMetaText(
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val finishedAt = progress.lastReadAtEpochMillis
    if (nowEpochMillis - finishedAt < DateUtils.WEEK_IN_MILLIS) {
        return DateUtils.getRelativeTimeSpanString(
            finishedAt,
            nowEpochMillis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
    val finished = Instant.ofEpochMilli(finishedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
    val pattern = if (finished.year == today.year) "d MMM" else "d MMM yyyy"
    return finished.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
