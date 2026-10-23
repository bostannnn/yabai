package com.example.unifiedreader.core.model

/*
 * Kavita files a manga volume as a chapter numbered "v1" and titled "Chapter v1". Those are
 * Kavita's placeholders, not the book's names, so the library reads them as "Volume 1".
 */

private val VolumeNumberPattern = Regex("""(?i)^v(?:ol(?:ume)?)?\.?\s*(\d+(?:\.\d+)?)$""")
private val GeneratedVolumeTitlePattern = Regex("""(?i)^(?:chapter|issue)\s*#?\s*(v(?:ol(?:ume)?)?\.?\s*\d+(?:\.\d+)?)$""")

/** "1" when [issueNumber] names a whole volume ("v1", "vol. 1", "Volume 1"), else null. */
fun comicVolumeNumber(issueNumber: String?): String? =
    issueNumber?.trim()?.let { number -> VolumeNumberPattern.matchEntire(number)?.groupValues?.get(1) }

/** Whether [title] is only Kavita's name for a volume ("Chapter v1", "Issue v2"). */
fun isGeneratedComicVolumeTitle(title: String): Boolean = GeneratedVolumeTitlePattern.matches(title.trim())

/** "Volume 1". */
fun comicVolumeLabel(volumeNumber: String): String = "Volume $volumeNumber"

/** The volume this issue is, from its number or its generated title ("Chapter v1" → "1"), else null. */
fun ComicMetadata.volumeNumber(): String? =
    comicVolumeNumber(issueNumber) ?: issueTitle?.trim()
        ?.let { title -> GeneratedVolumeTitlePattern.matchEntire(title)?.groupValues?.get(1) }
        ?.let(::comicVolumeNumber)

/** The issue's own title: null when it has none or only a generated volume name. */
fun ComicMetadata.ownIssueTitle(): String? = issueTitle?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.takeUnless(::isGeneratedComicVolumeTitle)
