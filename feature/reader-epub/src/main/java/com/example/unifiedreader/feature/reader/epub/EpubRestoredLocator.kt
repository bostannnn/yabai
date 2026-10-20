package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.Url

/**
 * The saved [locator] pointed at the reading-order resource it names, or null when it names none.
 *
 * Readium reports locations only after the first resource it loads is equivalent to the initial
 * locator's href, fragment included. A saved href with a fragment (Grimmory's web reader saves its
 * contents entry's, `OEBPS/text/part03.xhtml#ch3`) or one relative to the package document
 * (`text/part03.xhtml`) still opens the page, but the navigator then stays silent for the whole
 * session: the progress, the current chapter and the saved position stop moving. The href's
 * fragment goes first in the locator's fragments, so the page still opens at that anchor.
 */
internal fun Publication.resolveEpubRestoredLocator(
    locator: Locator,
): Locator? {
    val resource = readingOrder.epubResourceFor(locator.href) ?: return null
    val anchor = locator.href.fragment
    return locator.copy(
        href = resource.url(),
        mediaType = resource.mediaType ?: locator.mediaType,
        locations = locator.locations.copy(
            fragments = listOfNotNull(anchor) + locator.locations.fragments.filterNot { it == anchor },
        ),
    )
}

// The exact resource, else the only one whose path ends with the saved one (or the other way round).
private fun List<Link>.epubResourceFor(
    href: Url,
): Link? {
    val path = href.epubResourcePath() ?: return null
    return firstOrNull { link -> link.url().epubResourcePath() == path }
        ?: filter { link ->
            val candidate = link.url().epubResourcePath() ?: return@filter false
            candidate.endsWith("/$path") || path.endsWith("/$candidate")
        }.singleOrNull()
}

private fun Url.epubResourcePath(): String? =
    removeFragment().path
        ?.removePrefix("./")
        ?.trimStart('/')
        ?.takeIf(String::isNotEmpty)

// A PAGE position on an EPUB comes from Kavita, which stores the reading-order index of the
// chapter; open that chapter at its start.
internal fun ReaderProgressSnapshot.toReadingOrderStartLocator(
    publication: Publication,
): Locator? {
    if (locatorType != LocatorType.PAGE || isFinished) return null
    val link = publication.readingOrder.getOrNull(page ?: return null) ?: return null
    return publication.locatorFromLink(link)
}

internal suspend fun ReaderProgressSnapshot.toProgressionFallbackLocator(
    publication: Publication,
): Locator? {
    val progress = percent ?: return null
    return publication.locateProgression(progress.coerceIn(0.0, 1.0))
}
