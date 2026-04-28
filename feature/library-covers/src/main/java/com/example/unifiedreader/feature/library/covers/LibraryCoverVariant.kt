package com.example.unifiedreader.feature.library.covers

enum class LibraryCoverVariant(
    val fileKey: String,
    val width: Int,
    val height: Int,
) {
    Grid(
        fileKey = "grid-v2",
        width = 480,
        height = 720,
    ),
    GridLarge(
        fileKey = "grid-large-v2",
        width = 720,
        height = 1080,
    ),
    GridExtraLarge(
        fileKey = "grid-extra-large-v2",
        width = 960,
        height = 1440,
    ),
    Shelf(
        fileKey = "shelf-v2",
        width = 432,
        height = 648,
    ),
    Detail(
        fileKey = "detail-v2",
        width = 960,
        height = 1440,
    ),
}

fun thumbnailDirectoryName(
    storageDirectoryName: String,
    variant: LibraryCoverVariant,
): String = "$storageDirectoryName-thumbs-${variant.fileKey}"

fun thumbnailFileName(
    baseFileName: String,
    variant: LibraryCoverVariant,
): String = baseFileName.substringBeforeLast('.').plus("-${variant.fileKey}.jpg")
