package com.example.unifiedreader.data.grimmory

import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

internal fun GrimmoryEpubProgressUpdate.toRequestDto(): GrimmoryReadProgressRequestDto {
    return toGenericProgressUpdate().toRequestDto()
}

internal fun GrimmoryEpubProgressUpdate.toGenericProgressUpdate(): GrimmoryProgressUpdate = GrimmoryProgressUpdate(
    bookId = bookId,
    bookFileId = bookFileId,
    bookType = "EPUB",
    positionData = cfi,
    positionHref = href,
    percent = percent,
    ttsPositionCfi = ttsPositionCfi,
)

internal fun GrimmoryProgressUpdate.toRequestDto(): GrimmoryReadProgressRequestDto {
    val parsedBookId = bookId.toLongOrNull()
        ?: error("Grimmory bookId must be numeric: $bookId")
    val parsedBookFileId = bookFileId?.toLongOrNull()
    val clampedPercent = percent.toGrimmoryProgressPercent()
    return GrimmoryReadProgressRequestDto(
        bookId = parsedBookId,
        fileProgress = parsedBookFileId?.let { fileId ->
            GrimmoryBookFileProgressDto(
                bookFileId = fileId,
                positionData = positionData,
                positionHref = positionHref,
                progressPercent = clampedPercent,
                ttsPositionCfi = ttsPositionCfi,
                contentSourceProgressPercent = contentSourceProgressPercent?.coerceIn(0.0, 100.0)?.toFloat(),
            )
        },
        deprecatedEpubProgress = if (parsedBookFileId == null) {
            toDeprecatedEpubProgress(clampedPercent)
        } else {
            null
        },
        deprecatedPdfProgress = if (parsedBookFileId == null) {
            toDeprecatedPdfProgress(clampedPercent)
        } else {
            null
        },
        deprecatedCbxProgress = if (parsedBookFileId == null) {
            toDeprecatedCbxProgress(clampedPercent)
        } else {
            null
        },
        dateFinished = when {
            isFinished -> updatedAtEpochMillis
                ?.let(Instant::ofEpochMilli)
                ?.toString()
                ?.let(::JsonPrimitive)
            clearsFinishedState -> JsonNull
            else -> null
        },
    )
}

private fun GrimmoryProgressUpdate.toDeprecatedEpubProgress(
    clampedPercent: Float,
): GrimmoryEpubProgressDto? {
    if (bookType?.equals("EPUB", ignoreCase = true) != true) return null
    return GrimmoryEpubProgressDto(
        cfi = positionData,
        href = positionHref,
        percentage = clampedPercent,
        ttsPositionCfi = ttsPositionCfi,
        contentSourceProgressPercent = contentSourceProgressPercent?.coerceIn(0.0, 100.0)?.toFloat(),
    )
}

private fun GrimmoryProgressUpdate.toDeprecatedPdfProgress(
    clampedPercent: Float,
): GrimmoryPdfProgressDto? {
    if (bookType?.equals("PDF", ignoreCase = true) != true) return null
    return GrimmoryPdfProgressDto(
        page = positionData?.toIntOrNull(),
        percentage = clampedPercent,
    )
}

private fun GrimmoryProgressUpdate.toDeprecatedCbxProgress(
    clampedPercent: Float,
): GrimmoryCbxProgressDto? {
    val type = bookType ?: return null
    if (ComicBookTypes.none { comicType -> comicType.equals(type, ignoreCase = true) }) return null
    return GrimmoryCbxProgressDto(
        page = positionData?.toIntOrNull(),
        percentage = clampedPercent,
    )
}

// Grimmory files comics as CBX; local items may carry the archive format instead.
private val ComicBookTypes = listOf("CBX", "CBZ", "CBR")

private fun Double.toGrimmoryProgressPercent(): Float = (coerceIn(0.0, 1.0) * 100.0).toFloat()
