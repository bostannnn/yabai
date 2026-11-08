package com.example.unifiedreader.feature.library.covers

/**
 * The sizes a cached cover is written at. A cover is scaled to fit within its variant in its own proportions
 * (see LibraryCoverThumbnailGenerator). The file key names the folder and file of each copy: bump it when the
 * copies' content changes (v3: no longer cropped to 2:3), and the outdated folders go on the next cache warm-up.
 */
enum class LibraryCoverVariant(
    val fileKey: String,
    val width: Int,
    val height: Int,
) {
    Grid(
        fileKey = "grid-v3",
        width = 480,
        height = 720,
    ),
    GridLarge(
        fileKey = "grid-large-v3",
        width = 720,
        height = 1080,
    ),
    GridExtraLarge(
        fileKey = "grid-extra-large-v3",
        width = 960,
        height = 1440,
    ),
    Shelf(
        fileKey = "shelf-v3",
        width = 432,
        height = 648,
    ),
    Detail(
        fileKey = "detail-v3",
        width = 960,
        height = 1440,
    ),
}

fun thumbnailDirectoryName(
    storageDirectoryName: String,
    variant: LibraryCoverVariant,
): String = thumbnailDirectoryPrefix(storageDirectoryName) + variant.fileKey

/** What every thumbnail folder of a storage directory starts with, whatever its variant or file key. */
fun thumbnailDirectoryPrefix(
    storageDirectoryName: String,
): String = "$storageDirectoryName-thumbs-"

fun thumbnailFileName(
    baseFileName: String,
    variant: LibraryCoverVariant,
): String = baseFileName.substringBeforeLast('.').plus("-${variant.fileKey}.jpg")
