package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.net.Uri
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderOpenFailure
import com.example.unifiedreader.feature.reader.core.readerOpenFailureOf
import kotlin.coroutines.cancellation.CancellationException
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** The book Readium opened, still opening, or why it couldn't. */
internal sealed interface EpubPublicationState {
    data object Loading : EpubPublicationState

    data class Ready(
        val publication: Publication,
    ) : EpubPublicationState

    data class Error(
        val failure: ReaderOpenFailure,
    ) : EpubPublicationState
}

/**
 * Copies [documentUri] to the reader's own file and opens it with Readium. A file that can't be
 * copied is gone; one Readium can't open isn't a book it can read.
 */
internal suspend fun openEpubPublication(
    context: Context,
    item: ReadableItem,
    documentUri: Uri,
): EpubPublicationState {
    val publicationFile = try {
        prepareImportedEpubFile(context = context, item = item, documentUri = documentUri)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        return EpubPublicationState.Error(readerOpenFailureOf(error, item))
    }
    val httpClient = DefaultHttpClient()
    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    val parser = DefaultPublicationParser(
        context = context,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null,
    )
    val asset = assetRetriever.retrieve(
        publicationFile,
        FormatHints(mediaTypes = listOf(MediaType.EPUB)),
    ).getOrNull() ?: return EpubPublicationState.Error(ReaderOpenFailure.NotReadable)
    return PublicationOpener(publicationParser = parser).open(
        asset = asset,
        credentials = null,
        allowUserInteraction = true,
        onCreatePublication = {},
        warnings = null,
    ).fold(
        onSuccess = { publication -> EpubPublicationState.Ready(publication) },
        onFailure = { EpubPublicationState.Error(ReaderOpenFailure.NotReadable) },
    )
}
