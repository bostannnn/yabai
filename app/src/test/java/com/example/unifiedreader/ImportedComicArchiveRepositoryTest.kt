package com.example.unifiedreader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.dao.ImportedComicArchiveDao
import com.example.unifiedreader.core.database.entity.ImportedComicArchiveEntity
import com.example.unifiedreader.core.model.ComicMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.SourceType
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImportedComicArchiveRepositoryTest {
    @Test
    fun `epub import reads package title and author`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao()
        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val epubFile = createZipBackedFile(
            name = "topic-of-cancer.epub",
            entries = mapOf(
                "mimetype" to IMPORTED_EPUB_MIMETYPE.encodeToByteArray(),
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent().encodeToByteArray(),
                "OPS/content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>The Dispossessed</dc:title>
                        <dc:creator>Ursula K. Le Guin</dc:creator>
                        <meta name="cover" content="cover-image"/>
                      </metadata>
                      <manifest>
                        <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg"/>
                      </manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/images/cover.jpg" to singlePixelJpegBytes(),
                "OPS/images/page-1.jpg" to singlePixelJpegBytes(),
            ),
        )

        val imported = repository.import(Uri.fromFile(epubFile))

        assertNotNull(imported)
        assertEquals(FormatType.EPUB, imported?.format)
        assertEquals(SourceType.LOCAL, imported?.sourceType)
        assertEquals(LOCAL_LIBRARY_ID, imported?.libraryId)
        assertEquals("The Dispossessed", imported?.title)
        assertEquals("Ursula K. Le Guin", imported?.bookMetadata?.author)
        assertEquals("Imported local EPUB book.", imported?.description)
        assertTrue(imported?.coverUrl?.startsWith("file:") == true)
    }

    @Test
    fun `comic archive import reads comic info metadata`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = RepositoryImportedComicArchiveDao(),
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val comicFile = createZipBackedFile(
            name = "akira-001.cbz",
            entries = mapOf(
                "ComicInfo.xml" to """
                    <?xml version="1.0" encoding="utf-8"?>
                    <ComicInfo>
                      <Series>Akira</Series>
                      <Title>The Bike Chase</Title>
                      <Volume>1</Volume>
                      <Number>001</Number>
                    </ComicInfo>
                """.trimIndent().encodeToByteArray(),
                "001.jpg" to singlePixelJpegBytes(),
                "002.jpg" to singlePixelJpegBytes(),
            ),
        )

        val imported = repository.import(Uri.fromFile(comicFile))

        assertNotNull(imported)
        assertEquals(FormatType.CBZ, imported?.format)
        assertEquals(SourceType.LOCAL, imported?.sourceType)
        assertEquals(LOCAL_LIBRARY_ID, imported?.libraryId)
        assertEquals("akira-001", imported?.title)
        assertEquals(2, imported?.pageCount)
        assertEquals(
            ComicMetadata(
                series = "Akira",
                issueTitle = "The Bike Chase",
                volume = "1",
                issueNumber = "001",
            ),
            imported?.comicMetadata,
        )
        assertTrue(imported?.coverUrl?.startsWith("file:") == true)
    }

    @Test
    fun `invalid epub file is rejected during import`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = RepositoryImportedComicArchiveDao(),
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val invalidEpub = createFile(
            name = "not-a-book.epub",
            bytes = "plain text".encodeToByteArray(),
        )

        val imported = repository.import(Uri.fromFile(invalidEpub))

        assertEquals(null, imported)
    }

    @Test
    fun `legacy persisted pdf rows are ignored during hydration`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao().apply {
            insertOrReplace(
                ImportedComicArchiveEntity(
                    itemId = "pdf-legacy",
                    documentUri = "content://imports/legacy.pdf",
                    displayName = "Legacy PDF",
                    title = "Legacy PDF",
                    coverFileName = null,
                    format = FormatType.PDF.name,
                    pageCount = 0,
                    pageEntryNamesJson = "[]",
                    author = null,
                    series = null,
                    issueTitle = null,
                    volume = null,
                    issueNumber = null,
                    updatedAtEpochMillis = 1L,
                ),
            )
        }

        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        repository.isHydratedFlow.first { it }

        assertTrue(repository.getImportedItems().isEmpty())
    }

    @Test
    fun `hydration sorts imported items by visible title`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao().apply {
            insertOrReplace(
                listOf(
                    ImportedComicArchiveEntity(
                        itemId = "epub-z",
                        documentUri = "content://imports/z.epub",
                        displayName = "zzz.epub",
                        title = "A Fire Upon the Deep",
                        coverFileName = null,
                        format = FormatType.EPUB.name,
                        pageCount = 0,
                        pageEntryNamesJson = "[]",
                        author = "Vernor Vinge",
                        series = null,
                        issueTitle = null,
                        volume = null,
                        issueNumber = null,
                        updatedAtEpochMillis = 1L,
                    ),
                    ImportedComicArchiveEntity(
                        itemId = "epub-a",
                        documentUri = "content://imports/a.epub",
                        displayName = "aaa.epub",
                        title = "Zorba the Greek",
                        coverFileName = null,
                        format = FormatType.EPUB.name,
                        pageCount = 0,
                        pageEntryNamesJson = "[]",
                        author = "Nikos Kazantzakis",
                        series = null,
                        issueTitle = null,
                        volume = null,
                        issueNumber = null,
                        updatedAtEpochMillis = 1L,
                    ),
                ),
            )
        }

        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        repository.isHydratedFlow.first { it }

        assertEquals(
            listOf("A Fire Upon the Deep", "Zorba the Greek"),
            repository.getImportedItems().map { it.title },
        )
    }

    @Test
    fun `remove imported item deletes persisted row and publishes updated list`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao()
        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val comicFile = createZipBackedFile(
            name = "akira-001.cbz",
            entries = mapOf(
                "001.jpg" to byteArrayOf(1, 2, 3),
            ),
        )
        val imported = repository.import(Uri.fromFile(comicFile))!!

        val removed = repository.removeById(imported.id)

        assertTrue(removed)
        assertTrue(repository.getImportedItems().isEmpty())
        assertEquals(null, dao.getByItemId(imported.id))
    }

    @Test
    fun `reimporting same uri refreshes cached comic cover`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = RepositoryImportedComicArchiveDao(),
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val initialCoverBytes = singlePixelPngBytes(android.graphics.Color.BLACK)
        val updatedCoverBytes = singlePixelPngBytes(android.graphics.Color.WHITE)
        val comicFile = createZipBackedFile(
            name = "akira-001.cbz",
            entries = mapOf(
                "001.png" to initialCoverBytes,
            ),
        )

        val firstImport = repository.import(Uri.fromFile(comicFile))!!
        val firstCoverFile = File(requireNotNull(Uri.parse(firstImport.coverUrl).path))
        rewriteZipBackedFile(
            file = comicFile,
            entries = mapOf(
                "001.png" to updatedCoverBytes,
            ),
        )

        val secondImport = repository.import(Uri.fromFile(comicFile))!!

        assertEquals(firstImport.id, secondImport.id)
        val cachedCoverFile = File(requireNotNull(Uri.parse(secondImport.coverUrl).path))
        assertArrayEquals(updatedCoverBytes, cachedCoverFile.readBytes())
        assertTrue(!firstCoverFile.exists())
    }

    @Test
    fun `persisted cover metadata survives repository restart`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao()
        val firstRepository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val comicFile = createZipBackedFile(
            name = "akira-001.cbz",
            entries = mapOf(
                "001.png" to singlePixelPngBytes(android.graphics.Color.BLACK),
                "002.png" to singlePixelPngBytes(android.graphics.Color.WHITE),
            ),
        )

        val imported = firstRepository.import(Uri.fromFile(comicFile))!!
        val persistedEntity = requireNotNull(dao.getByItemId(imported.id))

        assertNotNull(persistedEntity.coverFileName)
        assertTrue(File(requireNotNull(Uri.parse(imported.coverUrl).path)).exists())

        val restartedRepository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        restartedRepository.isHydratedFlow.first { it }

        val hydrated = restartedRepository.getImportedItems().single()
        assertEquals(imported.coverUrl, hydrated.coverUrl)
        assertEquals(persistedEntity.coverFileName, dao.getByItemId(imported.id)?.coverFileName)
    }

    @Test
    fun `reimport preserves existing epub cover when refreshed cover cannot be read`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao()
        val repository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val epubFile = createZipBackedFile(
            name = "topic-of-cancer.epub",
            entries = mapOf(
                "mimetype" to IMPORTED_EPUB_MIMETYPE.encodeToByteArray(),
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent().encodeToByteArray(),
                "OPS/content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Tropic of Cancer</dc:title>
                        <dc:creator>Henry Miller</dc:creator>
                        <meta name="cover" content="cover-image"/>
                      </metadata>
                      <manifest>
                        <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg"/>
                      </manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/images/cover.jpg" to singlePixelJpegBytes(),
            ),
        )

        val firstImport = repository.import(Uri.fromFile(epubFile))!!
        rewriteZipBackedFile(
            file = epubFile,
            entries = mapOf(
                "mimetype" to IMPORTED_EPUB_MIMETYPE.encodeToByteArray(),
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                """.trimIndent().encodeToByteArray(),
                "OPS/content.opf" to """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package version="3.0" xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Tropic of Cancer</dc:title>
                        <dc:creator>Henry Miller</dc:creator>
                        <meta name="cover" content="cover-image"/>
                      </metadata>
                      <manifest>
                        <item id="cover-image" href="images/missing-cover.jpg" media-type="image/jpeg"/>
                      </manifest>
                    </package>
                """.trimIndent().encodeToByteArray(),
            ),
        )

        val secondImport = repository.import(Uri.fromFile(epubFile))!!

        assertEquals(firstImport.id, secondImport.id)
        assertEquals(firstImport.coverUrl, secondImport.coverUrl)
        assertNotNull(dao.getByItemId(secondImport.id)?.coverFileName)
    }

    @Test
    fun `hydration regenerates zero byte persisted cover files`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao()
        val firstRepository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val comicFile = createZipBackedFile(
            name = "akira-001.cbz",
            entries = mapOf(
                "001.png" to singlePixelPngBytes(android.graphics.Color.BLACK),
            ),
        )

        val imported = firstRepository.import(Uri.fromFile(comicFile))!!
        val coverFile = File(requireNotNull(Uri.parse(imported.coverUrl).path))
        coverFile.writeBytes(byteArrayOf())
        assertEquals(0L, coverFile.length())

        val restartedScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restartedRepository = LocalDocumentRepository(
                context = context,
                importedComicArchiveDao = dao,
                scope = restartedScope,
            )
            restartedRepository.isHydratedFlow.first { it }
            val hydratedItems = withTimeoutOrNull(5_000) {
                restartedRepository.importedItemsFlow.first { items ->
                    items.singleOrNull()?.coverUrl?.let { coverUrl ->
                        File(requireNotNull(Uri.parse(coverUrl).path)).length() > 0L
                    } == true
                }
            } ?: error(
                "Expected zero-byte cover to be regenerated. " +
                    "items=${restartedRepository.getImportedItems()} " +
                    "coverFileLength=${coverFile.length()} " +
                    "persistedCoverFileName=${dao.getByItemId(imported.id)?.coverFileName}",
            )

            val hydrated = hydratedItems.single()
            assertEquals(imported.id, hydrated.id)
            assertTrue(File(requireNotNull(Uri.parse(hydrated.coverUrl).path)).length() > 0L)
        } finally {
            restartedScope.cancel()
        }
    }

    @Test
    fun `hydration ignores persisted temp cover files`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val dao = RepositoryImportedComicArchiveDao()
        val firstRepository = LocalDocumentRepository(
            context = context,
            importedComicArchiveDao = dao,
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Unconfined),
        )
        val comicFile = createZipBackedFile(
            name = "akira-001.cbz",
            entries = mapOf(
                "001.png" to singlePixelPngBytes(android.graphics.Color.BLACK),
            ),
        )

        val imported = firstRepository.import(Uri.fromFile(comicFile))!!
        val originalCoverFile = File(requireNotNull(Uri.parse(imported.coverUrl).path))
        val tempCoverFile = File(originalCoverFile.parentFile, "${imported.id}.orphan.jpg.tmp")
        tempCoverFile.writeBytes(singlePixelPngBytes(android.graphics.Color.WHITE))
        assertTrue(tempCoverFile.length() > 0L)
        assertTrue(originalCoverFile.delete())
        dao.insertOrReplace(requireNotNull(dao.getByItemId(imported.id)).copy(coverFileName = tempCoverFile.name))

        val restartedScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val restartedRepository = LocalDocumentRepository(
                context = context,
                importedComicArchiveDao = dao,
                scope = restartedScope,
            )
            restartedRepository.isHydratedFlow.first { it }
            val hydratedItems = withTimeoutOrNull(5_000) {
                restartedRepository.importedItemsFlow.first { items ->
                    items.singleOrNull()?.coverUrl?.let { coverUrl ->
                        val coverFile = File(requireNotNull(Uri.parse(coverUrl).path))
                        coverFile.length() > 0L && !coverFile.name.endsWith(".tmp")
                    } == true
                }
            } ?: error("Expected temp cover to be ignored and regenerated.")

            val hydrated = hydratedItems.single()
            assertEquals(imported.id, hydrated.id)
            assertTrue(!File(requireNotNull(Uri.parse(hydrated.coverUrl).path)).name.endsWith(".tmp"))
            assertTrue(dao.getByItemId(imported.id)?.coverFileName?.endsWith(".tmp") == false)
        } finally {
            restartedScope.cancel()
        }
    }

    private fun createZipBackedFile(
        name: String,
        entries: Map<String, ByteArray>,
    ): File {
        val outputDirectory = Files.createTempDirectory("imported-epub-test").toFile()
        val outputFile = File(outputDirectory, name)
        rewriteZipBackedFile(outputFile, entries)
        outputFile.deleteOnExit()
        return outputFile
    }

    private fun rewriteZipBackedFile(
        file: File,
        entries: Map<String, ByteArray>,
    ) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun createFile(
        name: String,
        bytes: ByteArray,
    ): File {
        val outputDirectory = Files.createTempDirectory("imported-epub-test").toFile()
        val outputFile = File(outputDirectory, name)
        FileOutputStream(outputFile).use { output ->
            output.write(bytes)
        }
        outputFile.deleteOnExit()
        return outputFile
    }
}

private fun singlePixelJpegBytes(): ByteArray = ByteArrayOutputStream().use { output ->
    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(android.graphics.Color.BLACK) }
        .compress(Bitmap.CompressFormat.JPEG, 90, output)
    output.toByteArray()
}

private fun singlePixelPngBytes(
    color: Int,
): ByteArray = ByteArrayOutputStream().use { output ->
    Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color) }
        .compress(Bitmap.CompressFormat.PNG, 100, output)
    output.toByteArray()
}

private class RepositoryImportedComicArchiveDao : ImportedComicArchiveDao {
    private val storedEntities = linkedMapOf<String, ImportedComicArchiveEntity>()

    override fun getAll(): List<ImportedComicArchiveEntity> = storedEntities.values.toList()

    override fun getByItemId(itemId: String): ImportedComicArchiveEntity? = storedEntities[itemId]

    override fun deleteByItemId(itemId: String) {
        storedEntities.remove(itemId)
    }

    override fun insertOrReplace(entity: ImportedComicArchiveEntity) {
        storedEntities[entity.itemId] = entity
    }

    override fun insertOrReplace(entities: List<ImportedComicArchiveEntity>) {
        entities.forEach(::insertOrReplace)
    }
}
