package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.w3c.dom.Element

// Readium already shows `epub:type="noteref"` links as notes (HyperlinkNavigator.FootnoteContext).
// These decide the rest from the link's target, since the Link Readium passes is built from the URL
// and carries no anchor text: the target opens as a note popup only when it is a note, and anything
// else navigates, such as the book's own table of contents, appendix and Notes chapter.

// A note's text starts with its marker: "12. ...", "[3] ...", "* ...".
private val EpubNoteTextMarkerPattern = Regex("""^[\[(]?(\d{1,3}|[*†‡§¶]{1,3})[\])]?[.:]?\s""")

// "fn12", "n3", "_ftn4", "endnote_7"; a back-link id such as "fnref12" or "_ftnref4" is not one.
private val EpubNumberedNoteIdPattern = Regex(
    pattern = """_?(fn|ftn|note|footnote|endnote|enote|edn|en|nt|n)[-_.:]?\d+""",
    option = RegexOption.IGNORE_CASE,
)
private val EpubNoteTypes = setOf("footnote", "endnote", "rearnote", "note")
private val EpubNoteRoles = setOf("doc-footnote", "doc-endnote")
private val EpubNoteClasses = setOf("footnote", "endnote", "rearnote")
private val EpubNoteBlockTags = setOf("p", "li", "div", "aside", "dd", "dt", "blockquote", "td")
private val EpubContainerTags = setOf("html", "body", "section", "article", "nav", "main", "header", "footer")

// A note found only by its link or id, with nothing marking it as one, is shown when it is note-sized.
private const val EpubUnmarkedNoteMaxLength = 2_000

internal suspend fun Publication.extractEpubFootnoteOrNull(
    link: Link,
): EpubFootnoteContent? {
    val fragment = link.url().fragment ?: return null
    val html = get(link)?.read()
        ?.getOrNull()
        ?.toString(Charsets.UTF_8)
        .orEmpty()
    val body = extractEpubNoteText(html = html, targetId = fragment) ?: return null
    return EpubFootnoteContent(
        title = link.title?.takeIf(String::isNotBlank) ?: "Footnote",
        body = body,
    )
}

/**
 * The text of the note [targetId] points at, or null to navigate there instead. A target marked as a
 * note (`epub:type`, a DPUB role, a note class), or inside one, is the note. Otherwise the nearest
 * paragraph-like block around the target is, when it is note-sized and looks like a note: a numbered
 * note id, or text that starts with a marker. A heading, section, chapter or body never is, nor is a
 * body paragraph that a back-link from a note points into.
 */
internal fun extractEpubNoteText(
    html: String,
    targetId: String,
): String? {
    if (html.isBlank()) return null
    val text = runCatching { extractNoteTextFromDocument(html, targetId) }.getOrNull()
        ?: extractUnmarkedNoteHtml(html, targetId)?.let(::htmlFootnoteToPlainText)
    return text
        ?.takeIf(String::isNotBlank)
        ?.takeIf { noteText -> noteText.length > 3 }
}

private fun extractNoteTextFromDocument(
    html: String,
    targetId: String,
): String? {
    // The parser refuses any DOCTYPE (no external entities), and nearly every EPUB 3 file has
    // `<!DOCTYPE html>`, so drop it; &nbsp; is the one HTML entity books commonly use.
    val xml = html
        .replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace("&nbsp;", "&#160;")
    val document = secureDocumentBuilderFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray()))
    document.documentElement?.normalize()
    val target = document.documentElement?.findElementById(targetId) ?: return null
    val ancestors = target.ancestorsOrSelf()
    ancestors.firstOrNull(Element::isMarkedEpubNote)?.let { note -> return note.normalizedText() }
    val block = ancestors
        .takeWhile { element -> element.tagName.lowercase() !in EpubContainerTags }
        .firstOrNull { element -> element.tagName.lowercase() in EpubNoteBlockTags }
        ?: return null
    val text = block.normalizedText()
        ?.takeIf { noteText -> noteText.length <= EpubUnmarkedNoteMaxLength }
        ?: return null
    val looksLikeNote = targetId.matches(EpubNumberedNoteIdPattern) ||
        block.getAttribute("id").matches(EpubNumberedNoteIdPattern) ||
        EpubNoteTextMarkerPattern.containsMatchIn(text)
    return text.takeIf { looksLikeNote }
}

// For a document the XML parser rejects: the target element's own markup, when it has a numbered
// note id and is note-sized.
private fun extractUnmarkedNoteHtml(
    html: String,
    targetId: String,
): String? {
    if (!targetId.matches(EpubNumberedNoteIdPattern)) return null
    val escapedTargetId = Regex.escape(targetId)
    val pattern = Regex(
        pattern = """<([A-Za-z][A-Za-z0-9:_-]*)(?=[^>]*(?:\sid|xml:id)\s*=\s*["']$escapedTargetId["'])[^>]*>.*?</\1>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val match = pattern.find(html) ?: return null
    if (match.groupValues[1].lowercase() in EpubContainerTags) return null
    return match.value.takeIf { markup -> markup.length <= EpubUnmarkedNoteMaxLength * 2 }
}

internal fun htmlFootnoteToPlainText(
    html: String,
): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    .toString()
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

/** Opens a web or mail link from the book in another app; other schemes are ignored. */
internal fun Context.openEpubExternalLink(url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme?.lowercase() !in setOf("http", "https", "mailto")) return false
    return runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.isSuccess
}

private fun Element.isMarkedEpubNote(): Boolean {
    if (tagName.lowercase() in EpubContainerTags) return false
    val epubTypes = getAttribute("epub:type").tokens()
    val roles = getAttribute("role").tokens()
    val classes = getAttribute("class").tokens()
    return epubTypes.any(EpubNoteTypes::contains) ||
        roles.any(EpubNoteRoles::contains) ||
        classes.any(EpubNoteClasses::contains)
}

private fun String.tokens(): List<String> = lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)

private fun Element.normalizedText(): String? = textContent
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.takeIf(String::isNotBlank)

private fun Element.ancestorsOrSelf(): List<Element> = generateSequence(this) { element ->
    (element.parentNode as? Element)?.takeUnless { parent -> parent.tagName.equals("html", ignoreCase = true) }
}.toList()

private fun Element.findElementById(
    targetId: String,
): Element? {
    if (getAttribute("id") == targetId || getAttribute("xml:id") == targetId) return this
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index) as? Element ?: continue
        val match = child.findElementById(targetId)
        if (match != null) return match
    }
    return null
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
