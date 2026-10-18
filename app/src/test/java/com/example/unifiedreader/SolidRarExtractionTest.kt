package com.example.unifiedreader

import java.io.File
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SolidRarExtractionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `the first read extracts the whole archive once and later pages read from disk`() {
        val archive = FakeSolidArchive(pages = listOf("001.jpg", "002.jpg", "ComicInfo.xml", "003.jpg"))
        val directory = File(temporaryFolder.root, "solid/book-a")

        val third = solidArchiveEntryBytes(directory, "003.jpg", archive::extractAll)
        val first = solidArchiveEntryBytes(directory, "001.jpg", archive::extractAll)
        val second = solidArchiveEntryBytes(directory, "002.jpg", archive::extractAll)

        assertEquals(1, archive.extractions)
        assertArrayEquals(archive.bytesOf("003.jpg"), third)
        assertArrayEquals(archive.bytesOf("001.jpg"), first)
        assertArrayEquals(archive.bytesOf("002.jpg"), second)
        assertNull(solidArchiveEntryBytes(directory, "missing.jpg", archive::extractAll))
    }

    @Test
    fun `an extraction that fails part way is redone on the next read`() {
        val archive = FakeSolidArchive(pages = listOf("001.jpg", "002.jpg"), failOnFirstExtraction = true)
        val directory = File(temporaryFolder.root, "solid/book-a")

        val failed = runCatching { solidArchiveEntryBytes(directory, "002.jpg", archive::extractAll) }
        val retried = solidArchiveEntryBytes(directory, "002.jpg", archive::extractAll)

        assertTrue(failed.isFailure)
        assertEquals(2, archive.extractions)
        assertArrayEquals(archive.bytesOf("002.jpg"), retried)
    }

    @Test
    fun `only the two most recent extractions are kept`() {
        val root = File(temporaryFolder.root, "solid")
        listOf("book-a", "book-b", "book-c").forEachIndexed { index, name ->
            val archive = FakeSolidArchive(pages = listOf("001.jpg"))
            solidArchiveEntryBytes(File(root, name), "001.jpg", archive::extractAll)
            File(root, name).setLastModified(1_000L * (index + 1))
        }

        assertFalse(File(root, "book-a").exists())
        assertTrue(File(root, "book-b").exists())
        assertTrue(File(root, "book-c").exists())
    }
}

private class FakeSolidArchive(
    private val pages: List<String>,
    private var failOnFirstExtraction: Boolean = false,
) {
    var extractions = 0
        private set

    fun bytesOf(name: String): ByteArray = "page:$name".toByteArray()

    // Like a solid archive: every file is produced in order, in one pass.
    fun extractAll(output: (entryName: String) -> OutputStream) {
        extractions += 1
        pages.forEachIndexed { index, name ->
            if (failOnFirstExtraction && index == pages.lastIndex) {
                failOnFirstExtraction = false
                throw IOException("CRC error")
            }
            output(name).use { stream -> stream.write(bytesOf(name)) }
        }
    }
}
