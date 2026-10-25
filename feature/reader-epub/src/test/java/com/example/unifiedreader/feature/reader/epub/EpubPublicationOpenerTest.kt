package com.example.unifiedreader.feature.reader.epub

import android.net.Uri
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.reader.core.ReaderOpenFailure
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EpubPublicationOpenerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val item = ReadableItem(
        id = "local:ferry",
        libraryId = "local:library",
        sourceType = SourceType.LOCAL,
        title = "Ferry Notes",
        format = FormatType.EPUB,
    )

    @Test
    fun `an imported file that is gone says so`() {
        val missing = Uri.fromFile(File(context.cacheDir, "nowhere/ferry.epub"))

        val state = runBlocking { openEpubPublication(context, item, missing) }

        assertEquals(EpubPublicationState.Error(ReaderOpenFailure.LocalFileGone), state)
    }

    @Test
    fun `a file Readium can't open isn't a book it can read`() {
        val notAnEpub = File(context.cacheDir, "not-an-epub.epub").apply { writeText("This is not a zip archive.") }

        val state = runBlocking { openEpubPublication(context, item, Uri.fromFile(notAnEpub)) }

        assertEquals(EpubPublicationState.Error(ReaderOpenFailure.NotReadable), state)
    }
}
