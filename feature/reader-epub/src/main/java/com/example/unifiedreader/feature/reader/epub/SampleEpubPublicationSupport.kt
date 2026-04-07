package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun ensureSampleEpubFile(
    context: Context,
    item: ReadableItem,
): File = withContext(Dispatchers.IO) {
    val outputDirectory = File(context.cacheDir, "epub-samples").apply { mkdirs() }
    val outputFile = File(outputDirectory, "${item.id}.epub")
    val chapters = buildSampleChapters(item)
    ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
        zipStoredEntry(
            zip = zip,
            name = "mimetype",
            content = "application/epub+zip",
        )
        zipTextEntry(
            zip = zip,
            name = "META-INF/container.xml",
            content = containerXml(),
        )
        zipTextEntry(
            zip = zip,
            name = "OEBPS/nav.xhtml",
            content = navigationDocument(item, chapters),
        )
        chapters.forEachIndexed { index, chapter ->
            zipTextEntry(
                zip = zip,
                name = "OEBPS/chapter-${index + 1}.xhtml",
                content = chapterDocument(item, chapter),
            )
        }
        zipTextEntry(
            zip = zip,
            name = "OEBPS/content.opf",
            content = packageDocument(item, chapters),
        )
    }
    outputFile
}

internal suspend fun prepareImportedEpubFile(
    context: Context,
    item: ReadableItem,
    documentUri: Uri,
): File = withContext(Dispatchers.IO) {
    val outputDirectory = File(context.cacheDir, "epub-imports").apply { mkdirs() }
    val outputFile = File(outputDirectory, "${item.id}.epub")
    context.contentResolver.openInputStream(documentUri)?.use { input ->
        FileOutputStream(outputFile).use { output ->
            input.copyTo(output)
        }
    } ?: error("Unable to open imported EPUB file.")
    outputFile
}

private data class SampleChapter(
    val title: String,
    val paragraphs: List<String>,
)

private fun buildSampleChapters(
    item: ReadableItem,
): List<SampleChapter> {
    val synopsis = item.description?.takeIf(String::isNotBlank)
        ?: "A generated EPUB sample used to exercise the real Readium navigator before remote book downloads exist in the app."
    return listOf(
        SampleChapter(
            title = "Opening",
            paragraphs = listOf(
                "${item.title} is currently delivered from a generated EPUB package so the reader module can exercise a real EPUB engine.",
                synopsis,
                "The important part of this slice is not the prose. It is that the app now opens a publication package with Readium, restores the last locator, and persists the current locator back through the shared session store.",
            ),
        ),
        SampleChapter(
            title = "Edges",
            paragraphs = listOf(
                "Reader-specific behavior stays inside the EPUB module. The app shell still only knows how to host a reader and display progress summaries.",
                "That boundary matters because real Grimmory or local-file integration can replace this generated package later without another shell refactor.",
                "The locator bridge already stores a serialized Readium locator plus total progression for library summaries.",
            ),
        ),
        SampleChapter(
            title = "Typography",
            paragraphs = listOf(
                "Readium owns layout and pagination. The module only translates app-level preferences such as font size and line height into navigator preferences.",
                "Persisted settings are restored on reopen, so the reader now has real session behavior instead of placeholder Compose text.",
            ),
        ),
        SampleChapter(
            title = "Next",
            paragraphs = listOf(
                "The next step after this is replacing the generated EPUB file with an actual acquisition path from Grimmory or local imports.",
                "At that point the same navigator host, session codec, and preference bridge can remain in place.",
            ),
        ),
    )
}

private fun containerXml(): String = """
    <?xml version="1.0" encoding="UTF-8"?>
    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
      <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml" />
      </rootfiles>
    </container>
""".trimIndent()

private fun navigationDocument(
    item: ReadableItem,
    chapters: List<SampleChapter>,
): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine("""<!DOCTYPE html>""")
    appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="en">""")
    appendLine("<head>")
    appendLine("""  <title>${escapeXml(item.title)}</title>""")
    appendLine("""  <meta charset="utf-8" />""")
    appendLine("</head>")
    appendLine("<body>")
    appendLine("""  <nav epub:type="toc" id="toc">""")
    appendLine("    <h1>Contents</h1>")
    appendLine("    <ol>")
    chapters.forEachIndexed { index, chapter ->
        appendLine("""      <li><a href="chapter-${index + 1}.xhtml">${escapeXml(chapter.title)}</a></li>""")
    }
    appendLine("    </ol>")
    appendLine("  </nav>")
    appendLine("</body>")
    appendLine("</html>")
}

private fun chapterDocument(
    item: ReadableItem,
    chapter: SampleChapter,
): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine("""<!DOCTYPE html>""")
    appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" lang="en">""")
    appendLine("<head>")
    appendLine("""  <title>${escapeXml(chapter.title)}</title>""")
    appendLine("""  <meta charset="utf-8" />""")
    appendLine("</head>")
    appendLine("<body>")
    appendLine("""  <section aria-label="${escapeXml(chapter.title)}">""")
    appendLine("""    <h1>${escapeXml(chapter.title)}</h1>""")
    appendLine("""    <p>${escapeXml(item.title)}</p>""")
    chapter.paragraphs.forEach { paragraph ->
        appendLine("""    <p>${escapeXml(paragraph)}</p>""")
    }
    appendLine("  </section>")
    appendLine("</body>")
    appendLine("</html>")
}

private fun packageDocument(
    item: ReadableItem,
    chapters: List<SampleChapter>,
): String = buildString {
    appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    appendLine(
        """<package version="3.0" unique-identifier="book-id" xmlns="http://www.idpf.org/2007/opf">""",
    )
    appendLine("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">")
    appendLine("""    <dc:identifier id="book-id">${escapeXml(item.id)}</dc:identifier>""")
    appendLine("""    <dc:title>${escapeXml(item.title)}</dc:title>""")
    appendLine("""    <dc:language>en</dc:language>""")
    appendLine("""    <meta property="dcterms:modified">2026-04-06T00:00:00Z</meta>""")
    appendLine("  </metadata>")
    appendLine("  <manifest>")
    appendLine("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav" />""")
    chapters.forEachIndexed { index, _ ->
        appendLine(
            """    <item id="chapter-${index + 1}" href="chapter-${index + 1}.xhtml" media-type="application/xhtml+xml" />""",
        )
    }
    appendLine("  </manifest>")
    appendLine("  <spine>")
    chapters.forEachIndexed { index, _ ->
        appendLine("""    <itemref idref="chapter-${index + 1}" />""")
    }
    appendLine("  </spine>")
    appendLine("</package>")
}

private fun zipStoredEntry(
    zip: ZipOutputStream,
    name: String,
    content: String,
) {
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    val crc = CRC32().apply { update(bytes) }
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = bytes.size.toLong()
        compressedSize = bytes.size.toLong()
        this.crc = crc.value
    }
    zip.putNextEntry(entry)
    zip.write(bytes)
    zip.closeEntry()
}

private fun zipTextEntry(
    zip: ZipOutputStream,
    name: String,
    content: String,
) {
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    zip.putNextEntry(ZipEntry(name))
    zip.write(bytes)
    zip.closeEntry()
}

private fun escapeXml(
    value: String,
): String = buildString(value.length) {
    value.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&apos;"
                else -> character.toString()
            },
        )
    }
}
