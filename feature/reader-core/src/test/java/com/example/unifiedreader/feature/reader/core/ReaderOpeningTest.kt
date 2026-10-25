package com.example.unifiedreader.feature.reader.core

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import java.io.FileNotFoundException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReaderOpeningTest {
    private val kavitaBook = ReadableItem(
        id = "kavita:item:1",
        libraryId = "kavita:library:1",
        sourceType = SourceType.KAVITA,
        title = "The Lantern Keepers",
        format = FormatType.EPUB,
    )
    private val localBook = kavitaBook.copy(id = "local:1", sourceType = SourceType.LOCAL)

    @Test
    fun `a slow open says what it is doing in plain words`() {
        assertEquals("Getting it from Grimmory…", readerOpeningWords(ReaderOpeningStep.Fetching, SourceType.GRIMMORY))
        assertEquals("Opening…", readerOpeningWords(ReaderOpeningStep.Opening, SourceType.LOCAL))
        assertEquals("Finding your page…", readerOpeningWords(ReaderOpeningStep.FindingPage, SourceType.KAVITA))
    }

    @Test
    fun `a server book's file is fetched while it comes, a local one is opened`() {
        assertEquals(ReaderOpeningStep.Fetching, ReaderDocumentState.Loading.openingStep(kavitaBook))
        assertEquals(ReaderOpeningStep.Opening, ReaderDocumentState.Loading.openingStep(localBook))
    }

    @Test
    fun `reasons name the server and say what to do, without developer words`() {
        assertEquals(
            "Kavita didn’t answer. Check that you’re on the same network as your server, then try again.",
            ReaderOpenFailure.ServerUnreachable(SourceType.KAVITA).reason(),
        )
        assertEquals("yabai can’t open CBR files", ReaderOpenFailure.Unsupported(FormatType.CBR).title())
        assertEquals("Couldn’t open this book", ReaderOpenFailure.LocalFileGone.title())
        val everyFailure = listOf(
            ReaderOpenFailure.ServerUnreachable(SourceType.STUMP),
            ReaderOpenFailure.ServerNotConnected(SourceType.KAVITA),
            ReaderOpenFailure.LocalFileGone,
            ReaderOpenFailure.DownloadGone,
            ReaderOpenFailure.NotReadable,
            ReaderOpenFailure.NoComicPages,
            ReaderOpenFailure.NoPdfPages,
            ReaderOpenFailure.PdfUnreadable,
            ReaderOpenFailure.Unsupported(FormatType.PDF),
            ReaderOpenFailure.Unknown,
        )
        everyFailure.forEach { failure ->
            assertFalse(failure.reason(), failure.reason().contains("Readium") || failure.reason().contains("EPUB with"))
        }
    }

    @Test
    fun `a broken download is fetched again, a server that didn't answer is tried again, a gone file is left`() {
        assertEquals(
            listOf(ReaderErrorAction.DownloadAgain),
            readerErrorActions(ReaderOpenFailure.DownloadGone, canRetry = true, canDownloadAgain = true),
        )
        assertEquals(
            listOf(ReaderErrorAction.TryAgain),
            readerErrorActions(ReaderOpenFailure.ServerUnreachable(SourceType.KAVITA), canRetry = true, canDownloadAgain = false),
        )
        assertEquals(emptyList<ReaderErrorAction>(), readerErrorActions(ReaderOpenFailure.LocalFileGone, canRetry = true, canDownloadAgain = false))
        assertEquals(emptyList<ReaderErrorAction>(), readerErrorActions(ReaderOpenFailure.Unsupported(FormatType.CBR), canRetry = true, canDownloadAgain = true))
        assertEquals(emptyList<ReaderErrorAction>(), readerErrorActions(ReaderOpenFailure.Unknown, canRetry = false, canDownloadAgain = false))
    }

    @Test
    fun `failures getting the file mean what they say`() {
        assertEquals(ReaderOpenFailure.LocalFileGone, readerOpenFailureOf(FileNotFoundException(), localBook))
        assertEquals(ReaderOpenFailure.DownloadGone, readerOpenFailureOf(FileNotFoundException(), kavitaBook))
        assertEquals(ReaderOpenFailure.ServerUnreachable(SourceType.KAVITA), readerOpenFailureOf(UnknownHostException(), kavitaBook))
        assertEquals(ReaderOpenFailure.Unknown, readerOpenFailureOf(IllegalStateException(), kavitaBook))
        assertEquals(ReaderOpenFailure.LocalFileGone, readerFileMissingFailure(localBook))
        assertEquals(ReaderOpenFailure.ServerUnreachable(SourceType.KAVITA), readerFileMissingFailure(kavitaBook))
    }
}
