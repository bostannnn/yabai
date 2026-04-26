package com.example.unifiedreader.feature.library.covers

enum class LibraryCoverVariant(
    val fileKey: String,
    val width: Int,
    val height: Int,
) {
    Grid(
        fileKey = "grid",
        width = 240,
        height = 360,
    ),
    GridLarge(
        fileKey = "grid-large",
        width = 480,
        height = 720,
    ),
    GridExtraLarge(
        fileKey = "grid-extra-large",
        width = 720,
        height = 1080,
    ),
    Shelf(
        fileKey = "shelf",
        width = 144,
        height = 216,
    ),
    Detail(
        fileKey = "detail",
        width = 320,
        height = 480,
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
