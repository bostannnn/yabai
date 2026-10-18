package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun GrimmoryBookDto.toReadingProgress(
    item: ReadableItem,
): ReadingProgress? {
    val updatedAtEpochMillis = lastReadTime?.toEpochMillisOrNull()
    val finishedAtEpochMillis = (dateFinished ?: dateFinishedSnake)?.toEpochMillisOrNull()
    if (finishedAtEpochMillis != null) {
        return ReadingProgress(
            itemId = item.id,
            locatorType = LocatorType.PERCENT,
            percent = 1.0,
            isFinished = true,
            updatedAtEpochMillis = finishedAtEpochMillis,
        )
    }
    return when (item.format) {
        FormatType.EPUB -> epubProgress?.toReadingProgress(
            itemId = item.id,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
        FormatType.PDF -> pdfProgress?.let { progress ->
            pageProgress(
                itemId = item.id,
                page = progress.page,
                percentage = progress.percentage,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        }
        FormatType.CBZ,
        FormatType.CBR -> cbxProgress?.let { progress ->
            pageProgress(
                itemId = item.id,
                page = progress.page,
                percentage = progress.percentage,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
        }
    }
}

private fun GrimmoryEpubProgressDto.toReadingProgress(
    itemId: String,
    updatedAtEpochMillis: Long?,
): ReadingProgress? {
    val localPercent = percentage.toLocalProgressPercent()
    val normalizedCfi = cfi.toMeaningfulGrimmoryCfi()
    if (normalizedCfi == null && (localPercent == null || localPercent <= 0.0)) return null
    val locatorType = if (normalizedCfi == null) LocatorType.PERCENT else LocatorType.CFI
    if (locatorType == LocatorType.PERCENT && localPercent == null) return null
    return ReadingProgress(
        itemId = itemId,
        locatorType = locatorType,
        cfi = normalizedCfi?.toReaderLocatorJson(
            href = href,
            percent = localPercent,
        ),
        percent = localPercent,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

// PDF and comic progress: Grimmory's page is one-based, the local page index zero-based.
private fun pageProgress(
    itemId: String,
    page: Int?,
    percentage: Float?,
    updatedAtEpochMillis: Long?,
): ReadingProgress? {
    val localPercent = percentage.toLocalProgressPercent()
    val pageIndex = page?.let { (it - 1).coerceAtLeast(0) }
    if (pageIndex == null && localPercent == null) return null
    return ReadingProgress(
        itemId = itemId,
        locatorType = if (pageIndex == null) LocatorType.PERCENT else LocatorType.PAGE,
        page = pageIndex,
        percent = localPercent,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun Float?.toLocalProgressPercent(): Double? = this?.let { value ->
    (value / 100f).toDouble().coerceIn(0.0, 1.0)
}

// Grimmory uses the string "0" for unread EPUB entries in some list responses.
private fun String?.toMeaningfulGrimmoryCfi(): String? = this
    ?.trim()
    ?.takeIf { value -> value.isNotEmpty() && value != "0" }

private fun String.toReaderLocatorJson(
    href: String?,
    percent: Double?,
): String {
    val locatorHref = href?.takeIf(String::isNotBlank) ?: return this
    return buildJsonObject {
        put("href", JsonPrimitive(locatorHref))
        put("type", JsonPrimitive("application/xhtml+xml"))
        put(
            "locations",
            buildJsonObject {
                put(
                    "fragments",
                    buildJsonArray {
                        add(JsonPrimitive(this@toReaderLocatorJson))
                    },
                )
                percent?.let { value ->
                    put("totalProgression", JsonPrimitive(value))
                }
            },
        )
    }.toString()
}
