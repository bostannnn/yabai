package com.example.unifiedreader

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.junrar.Archive
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// rar4-solid.rar is junrar's own solid RAR4 test archive (src/test/resources/com/github/junrar/solid).
@RunWith(RobolectricTestRunner::class)
class RarComicArchiveCodecSolidTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val archiveFile = File(
        checkNotNull(javaClass.classLoader?.getResource("comic-archives/rar4-solid.rar")).toURI(),
    )

    @Test
    fun `the fixture's later files cannot be read from a fresh archive`() {
        val headers = Archive(archiveFile).use { archive -> archive.fileHeaders.filterNot { it.isDirectory } }
        assertTrue(headers.size >= 2)
        assertTrue(headers.drop(1).all { header -> header.isSolid })
        assertNull(readFromFreshArchive(headers.last().fileName))
    }

    @Test
    fun `every file of a solid archive reads, not only the first`() = runBlocking {
        val codec = RarComicArchiveCodec(context)
        val uri = Uri.fromFile(archiveFile)
        val expected = extractInOrder()

        val entries = codec.listEntries(context, uri)

        assertTrue(entries.size >= 2)
        // Last first: any order works once the archive is extracted.
        entries.reversed().forEach { entryName ->
            assertArrayEquals(entryName, expected.getValue(entryName), codec.readEntryBytes(context, uri, entryName))
        }
    }

    private fun extractInOrder(): Map<String, ByteArray> = Archive(archiveFile).use { archive ->
        archive.fileHeaders.filterNot { it.isDirectory }.associate { header ->
            header.fileName to ByteArrayOutputStream().also { output -> archive.extractFile(header, output) }.toByteArray()
        }
    }

    private fun readFromFreshArchive(
        entryName: String,
    ): ByteArray? = Archive(archiveFile).use { archive ->
        val header = archive.fileHeaders.first { it.fileName == entryName }
        runCatching {
            ByteArrayOutputStream().also { output -> archive.extractFile(header, output) }.toByteArray()
        }.getOrNull()
    }
}
