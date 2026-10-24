package com.example.unifiedreader.feature.library.shelves

import android.text.format.DateUtils
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.librarySecondaryText

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

/** When it was finished: "3 days ago". */
internal fun RecentlyFinishedShelfItem.tileMetaText(nowEpochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        progress.lastReadAtEpochMillis,
        nowEpochMillis,
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
