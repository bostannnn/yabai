package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

internal data class ImportedComicMetadata(
    val series: String? = null,
    val issueTitle: String? = null,
    val volume: String? = null,
    val issueNumber: String? = null,
    /** ComicInfo's `Writer`, as written (a comma-separated list). */
    val writer: String? = null,
    val summary: String? = null,
)

internal data class ImportedEpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val coverEntryName: String? = null,
    /** `dc:description`, as written (often HTML). */
    val description: String? = null,
)

internal suspend fun extractEpubMetadata(
    context: Context,
    uri: Uri,
): ImportedEpubMetadata? {
    val containerBytes = readZipEntryBytes(
        context = context,
        uri = uri,
        entryName = "META-INF/container.xml",
    ) ?: return null
    val containerDocument = parseXmlDocument(containerBytes) ?: return null
    val rootFilePath = containerDocument.firstElementByLocalName("rootfile")
        ?.getAttribute("full-path")
        ?.trim()
        .orEmpty()
    if (rootFilePath.isEmpty()) return null
    val packageBytes = readZipEntryBytes(
        context = context,
        uri = uri,
        entryName = rootFilePath,
    ) ?: return null
    val packageDocument = parseXmlDocument(packageBytes) ?: return null
    return ImportedEpubMetadata(
        title = packageDocument.firstTextByLocalName("title"),
        author = packageDocument.firstTextByLocalName("creator"),
        coverEntryName = packageDocument.resolveEpubCoverEntryPath(rootFilePath),
        description = packageDocument.firstTextByLocalName("description"),
    )
}

internal suspend fun readZipEntryBytes(
    context: Context,
    uri: Uri,
    entryName: String,
): ByteArray? = withContext(Dispatchers.IO) {
    context.contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == entryName) {
                    return@withContext zip.readBytes()
                }
                zip.closeEntry()
            }
            null
        }
    }
}

internal fun isComicInfoEntryName(
    entryName: String,
): Boolean = entryName.substringAfterLast('/').equals("ComicInfo.xml", ignoreCase = true)

internal fun parseComicInfoMetadata(
    bytes: ByteArray,
): ImportedComicMetadata? {
    val document = parseXmlDocument(bytes) ?: return null
    return ImportedComicMetadata(
        series = document.firstTextByLocalName("Series"),
        issueTitle = document.firstTextByLocalName("Title"),
        volume = document.firstTextByLocalName("Volume"),
        issueNumber = document.firstTextByLocalName("Number"),
        writer = document.firstTextByLocalName("Writer"),
        summary = document.firstTextByLocalName("Summary"),
    )
}

private fun parseXmlDocument(
    bytes: ByteArray,
): Document? = runCatching {
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).also {
        it.documentElement?.normalize()
    }
}.getOrNull()

private fun Document.firstElementByLocalName(
    localName: String,
): Element? {
    val nodes = getElementsByTagNameNS("*", localName)
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        return element
    }
    return null
}

private fun Document.firstTextByLocalName(
    localName: String,
): String? {
    val nodes = getElementsByTagNameNS("*", localName)
    for (index in 0 until nodes.length) {
        val text = nodes.item(index)?.textContent?.trim().orEmpty()
        if (text.isNotEmpty()) return text
    }
    return null
}

private fun Document.resolveEpubCoverEntryPath(
    packagePath: String,
): String? {
    val manifestItems = manifestItemElements()
    val coverId = metaCoverId()
    val href = when {
        !coverId.isNullOrBlank() -> manifestItems.firstOrNull { it.getAttribute("id") == coverId }
            ?.getAttribute("href")
        else -> manifestItems.firstOrNull { item ->
            item.getAttribute("properties")
                .split(Regex("\\s+"))
                .any { it == "cover-image" }
        }?.getAttribute("href")
    }?.trim().orEmpty()
    return href.takeIf(String::isNotEmpty)?.let { resolveEpubHref(packagePath, it) }
}

private fun Document.metaCoverId(): String? {
    val nodes = getElementsByTagNameNS("*", "meta")
    for (index in 0 until nodes.length) {
        val element = nodes.item(index) as? Element ?: continue
        if (element.getAttribute("name").equals("cover", ignoreCase = true)) {
            return element.getAttribute("content").trim().takeIf(String::isNotEmpty)
        }
    }
    return null
}

private fun Document.manifestItemElements(): List<Element> {
    val manifest = firstElementByLocalName("manifest") ?: return emptyList()
    val children = manifest.childNodes
    return buildList {
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.localName == "item") {
                add(element)
            }
        }
    }
}

/**
 * Resolves an OPF manifest href to its zip entry name. Hrefs are URL references, so they may be
 * percent-encoded, but real books also carry raw spaces and stray `%`, which `java.net.URI` rejects
 * by throwing.
 */
internal fun resolveEpubHref(
    packagePath: String,
    href: String,
): String? {
    val path = href.substringBefore('#').percentDecoded()
    if (path.isEmpty()) return null
    val base = if (path.startsWith('/')) "" else packagePath.substringBeforeLast('/', missingDelimiterValue = "")
    val segments = ArrayDeque<String>()
    "$base/$path".split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> segments.removeLastOrNull()
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/").takeIf(String::isNotEmpty)
}

// Decodes %XX escapes as UTF-8 and keeps a `%` that doesn't start one.
private fun String.percentDecoded(): String {
    if ('%' !in this) return this
    val bytes = ByteArrayOutputStream(length)
    var index = 0
    while (index < length) {
        val high = if (this[index] == '%' && index + 2 < length) Character.digit(this[index + 1], 16) else -1
        val low = if (high >= 0) Character.digit(this[index + 2], 16) else -1
        if (low >= 0) {
            bytes.write(high * 16 + low)
            index += 3
        } else {
            val end = indexOf('%', startIndex = index + 1).takeIf { it >= 0 } ?: length
            bytes.write(substring(index, end).encodeToByteArray())
            index = end
        }
    }
    return bytes.toByteArray().decodeToString()
}
