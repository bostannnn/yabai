package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
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
)

internal data class ImportedEpubMetadata(
    val title: String? = null,
    val author: String? = null,
    val coverEntryName: String? = null,
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
    return href.takeIf(String::isNotEmpty)?.resolveRelativeToPackagePath(packagePath)
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

private fun String.resolveRelativeToPackagePath(
    packagePath: String,
): String = java.net.URI(packagePath).resolve(this).path
