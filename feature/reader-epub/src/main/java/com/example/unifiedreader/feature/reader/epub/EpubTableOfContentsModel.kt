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

/** Which chapter the reader is in, counted among the chapter entries ([epubTocChapterIndices]). */
internal data class EpubTocChapterPosition(
    val index: Int,
    val count: Int,
)

/**
 * The 1-based index of the chapter entry that contains [activeEntry] (itself, or the chapter it
 * is nested in), and the number of chapters. Null when nothing is active or the active entry is
 * outside every chapter (a foreword next to the entry that wraps the chapters).
 */
internal fun List<EpubTocEntry>.epubTocChapterPosition(
    activeEntry: EpubTocEntry?,
): EpubTocChapterPosition? {
    if (activeEntry == null) return null
    val activeIndex = indexOfFirst { it.id == activeEntry.id }
    if (activeIndex < 0) return null
    val chapters = epubTocChapterIndices()
    val chapter = chapters.lastOrNull { it <= activeIndex } ?: return null
    if (activeIndex >= epubTocSubtreeEnd(chapter, size)) return null
    return EpubTocChapterPosition(index = chapters.indexOf(chapter) + 1, count = chapters.size)
}

/**
 * Indices of the entries that count as chapters: the top-level ones, unless one of them wraps
 * most of the contents (a book-title entry holding the chapters, as FB2 conversions have: a
 * foreword, "Девица Кристина" with I to XVIII, notes). Its children are the chapters then,
 * with the same rule applied inside it.
 */
internal fun List<EpubTocEntry>.epubTocChapterIndices(): List<Int> {
    var start = 0
    var end = size
    var depth = 0
    while (true) {
        val level = (start until end).filter { this[it].depth == depth }
        val wrapper = level.firstOrNull { index ->
            val subtreeEnd = epubTocSubtreeEnd(index, end)
            val children = (index + 1 until subtreeEnd).count { this[it].depth == depth + 1 }
            children >= 2 && (subtreeEnd - index - 1) * 2 > end - start
        } ?: return level
        end = epubTocSubtreeEnd(wrapper, end)
        start = wrapper + 1
        depth += 1
    }
}

// The index after the last entry nested under the entry at [index], [limit] at most.
private fun List<EpubTocEntry>.epubTocSubtreeEnd(
    index: Int,
    limit: Int,
): Int = (index + 1 until limit).firstOrNull { this[it].depth <= this[index].depth } ?: limit

/** An entry the reader has passed: it starts before the active entry does. Entries without a progression are never "finished". */
internal fun EpubTocEntry.isEpubTocEntryFinished(activeEntry: EpubTocEntry?): Boolean {
    val activeProgression = activeEntry?.progression ?: return false
    val own = progression ?: return false
    return id != activeEntry.id && own < activeProgression
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
