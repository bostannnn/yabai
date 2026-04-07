package com.example.unifiedreader.data.grimmory

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
    )
}

private fun GrimmoryProgressUpdate.toDeprecatedPdfProgress(
    clampedPercent: Float,
): GrimmoryPdfProgressDto? {
    if (bookType?.equals("PDF", ignoreCase = true) != true) return null
    return GrimmoryPdfProgressDto(
        page = positionData.toIntOrNull(),
        percentage = clampedPercent,
    )
}

private fun Double.toGrimmoryProgressPercent(): Float = (coerceIn(0.0, 1.0) * 100.0).toFloat()
