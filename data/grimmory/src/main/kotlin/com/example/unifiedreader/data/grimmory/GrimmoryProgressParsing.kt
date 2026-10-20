package com.example.unifiedreader.data.grimmory

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadingProgress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    val locatorJson = normalizedCfi?.takeIf(String::isReaderLocatorJson)
        ?: toReaderLocatorJson(cfi = normalizedCfi, percent = localPercent)
        ?: normalizedCfi
    val locatorType = if (locatorJson == null) LocatorType.PERCENT else LocatorType.CFI
    if (locatorType == LocatorType.PERCENT && localPercent == null) return null
    return ReadingProgress(
        itemId = itemId,
        locatorType = locatorType,
        cfi = locatorJson,
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

// The EPUB reader's locator for a Grimmory position: its chapter file, with Grimmory's CFI as a
// fragment and the progress within the chapter (Kobo positions and the app's own have one).
// Null without a chapter file.
private fun GrimmoryEpubProgressDto.toReaderLocatorJson(
    cfi: String?,
    percent: Double?,
): String? {
    val locatorHref = href?.takeIf(String::isNotBlank) ?: return null
    return buildJsonObject {
        put("href", JsonPrimitive(locatorHref.toEncodedEpubHref()))
        put("type", JsonPrimitive("application/xhtml+xml"))
        put(
            "locations",
            buildJsonObject {
                cfi?.let { value ->
                    put("fragments", buildJsonArray { add(JsonPrimitive(value)) })
                }
                contentSourceProgressPercent?.let { value ->
                    put("progression", JsonPrimitive((value / 100f).toDouble().coerceIn(0.0, 1.0)))
                }
                percent?.let { value ->
                    put("totalProgression", JsonPrimitive(value))
                }
            },
        )
    }.toString()
}

// Before 2026-10-20 the app uploaded its whole reader locator as the CFI; it is the best locator there is.
private fun String.isReaderLocatorJson(): Boolean = trimStart().startsWith("{") &&
    runCatching {
        val locator = Json.parseToJsonElement(this).jsonObject
        locator["href"]?.jsonPrimitive?.isString == true && locator["type"]?.jsonPrimitive?.isString == true
    }.getOrDefault(false)

/**
 * [this] href percent-encoded where it is not already: the EPUB reader only parses an encoded URL,
 * and Grimmory's web reader saves the decoded path (`OEBPS/Text/Глава 1.xhtml`).
 */
internal fun String.toEncodedEpubHref(): String {
    val path = substringBefore('#')
    val fragment = substringAfter('#', missingDelimiterValue = "").takeIf { contains('#') }
    return path.percentEncoded(isAllowed = ::isEpubPathChar) +
        fragment?.let { "#" + it.percentEncoded(isAllowed = { char -> isEpubPathChar(char) || char == '?' }) }.orEmpty()
}

private fun String.percentEncoded(
    isAllowed: (Char) -> Boolean,
): String = buildString {
    val source = this@percentEncoded
    var index = 0
    while (index < source.length) {
        val char = source[index]
        if (char == '%' && index + 2 < source.length &&
            source[index + 1].isHexDigit() && source[index + 2].isHexDigit()
        ) {
            append(source, index, index + 3)
            index += 3
            continue
        }
        if (char.code < 0x80 && isAllowed(char)) {
            append(char)
            index += 1
            continue
        }
        val codePoint = source.codePointAt(index)
        String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).forEach { byte ->
            append('%')
            append(HexDigits[(byte.toInt() shr 4) and 0xF])
            append(HexDigits[byte.toInt() and 0xF])
        }
        index += Character.charCount(codePoint)
    }
}

// RFC 3986 pchar and "/": unreserved, sub-delims, ":" and "@".
private fun isEpubPathChar(char: Char): Boolean =
    char.isLetterOrDigit() || char in "-._~!$&'()*+,;=:@/"

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val HexDigits = "0123456789ABCDEF"
