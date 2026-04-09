package com.example.unifiedreader.feature.reader.epub

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder

internal suspend fun Publication.buildEpubBookProgress(): EpubBookProgress {
    val positionsByReadingOrder = positionsByReadingOrder()
    val positionCount = positionsByReadingOrder.flatten().size
    val pageCount = positionCount.takeIf { it > 0 } ?: readingOrder.size
    val positionMetadataByHref = positionsByReadingOrder
        .mapNotNull { positions ->
            val position = positions.firstOrNull() ?: return@mapNotNull null
            val progression = position.locations.totalProgression ?: return@mapNotNull null
            position.href.removeFragment().toString() to EpubTocPositionMetadata(
                progression = progression,
                position = position.locations.position,
            )
        }
        .toMap()

    val tocMarks = tableOfContents
        .flatMap { it.flattenEpubTocLinks() }
        .mapNotNull { link ->
            val locator = locatorFromLink(link)
            val href = (locator?.href ?: link.url()).removeFragment().toString()
            locator?.locations?.totalProgression
                ?: positionMetadataByHref[href]?.progression
        }
        .toChapterMarks()
    val tocEntries = buildEpubTocEntries(positionMetadataByHref)

    return EpubBookProgress(
        pageCount = pageCount,
        chapterMarks = tocMarks,
        tocEntries = tocEntries,
    )
}

private fun Publication.buildEpubTocEntries(
    positionMetadataByHref: Map<String, EpubTocPositionMetadata>,
): List<EpubTocEntry> = tableOfContents.flatMapIndexed { index, link ->
    buildEpubTocEntries(
        link = link,
        path = index.toString(),
        depth = 0,
        positionMetadataByHref = positionMetadataByHref,
    )
}

private fun Publication.buildEpubTocEntries(
    link: Link,
    path: String,
    depth: Int,
    positionMetadataByHref: Map<String, EpubTocPositionMetadata>,
): List<EpubTocEntry> {
    val locator = locatorFromLink(link)
    val href = (locator?.href ?: link.url()).removeFragment().toString()
    val positionMetadata = positionMetadataByHref[href]
    val title = link.title?.takeIf(String::isNotBlank)
        ?: "Section ${path.substringAfterLast('.').toIntOrNull()?.plus(1) ?: 1}"
    val entry = EpubTocEntry(
        id = "$path:$href:$title",
        title = title,
        depth = depth,
        href = href,
        progression = locator?.locations?.totalProgression
            ?: positionMetadata?.progression,
        position = locator?.locations?.position
            ?: positionMetadata?.position,
        link = link,
    )
    return listOf(entry) + link.children.flatMapIndexed { index, child ->
        buildEpubTocEntries(
            link = child,
            path = "$path.$index",
            depth = depth + 1,
            positionMetadataByHref = positionMetadataByHref,
        )
    }
}

private fun Link.flattenEpubTocLinks(): List<Link> =
    listOf(this) + children.flatMap { it.flattenEpubTocLinks() }

private fun List<Double>.toChapterMarks(): List<Float> =
    map { it.coerceIn(0.0, 1.0).toFloat() }
        .filter { it > 0f && it < 1f }
        .distinct()
        .sorted()

internal fun List<EpubTocEntry>.activeEpubTocEntry(
    currentProgress: Double,
    currentHref: String?,
): EpubTocEntry? {
    val clampedProgress = currentProgress.coerceIn(0.0, 1.0)
    return currentHref?.let { href ->
        lastOrNull { entry -> entry.href == href }
    } ?: asSequence()
        .filter { entry ->
            entry.progression != null &&
                entry.progression <= clampedProgress + EpubTocProgressEpsilon
        }
        .maxByOrNull { entry -> entry.progression ?: 0.0 }
}

internal data class EpubBookProgress(
    val pageCount: Int,
    val chapterMarks: List<Float>,
    val tocEntries: List<EpubTocEntry>,
)

internal data class EpubTocEntry(
    val id: String,
    val title: String,
    val depth: Int,
    val href: String,
    val progression: Double?,
    val position: Int?,
    val link: Link,
)

private data class EpubTocPositionMetadata(
    val progression: Double,
    val position: Int?,
)

private const val EpubTocProgressEpsilon = 0.0005
