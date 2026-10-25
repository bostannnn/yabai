package com.example.unifiedreader.feature.reader.core

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A reader's file: still coming, here, or not, with why. */
sealed interface ReaderDocumentState {
    val cacheKey: String

    data object Loading : ReaderDocumentState {
        override val cacheKey: String = "loading"
    }

    data class Ready(val uri: Uri) : ReaderDocumentState {
        override val cacheKey: String = uri.toString()
    }

    data class Failed(val failure: ReaderOpenFailure) : ReaderDocumentState {
        override val cacheKey: String = "failed:$failure"
    }
}

/**
 * The file a book reader opens: [documentUri] when the caller has it, else what
 * [documentUriProvider] finds (which may fetch it from the server). A new [attempt] asks again,
 * for Try again.
 */
@Composable
fun rememberReaderDocument(
    item: ReadableItem,
    documentUri: Uri?,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)?,
    attempt: Int,
): State<ReaderDocumentState> = produceState(
    initialValue = documentUri?.let(ReaderDocumentState::Ready) ?: ReaderDocumentState.Loading,
    item.id,
    documentUri?.toString(),
    documentUriProvider,
    attempt,
) {
    if (documentUri != null) {
        value = ReaderDocumentState.Ready(documentUri)
        return@produceState
    }
    value = ReaderDocumentState.Loading
    value = withContext(Dispatchers.IO) {
        try {
            documentUriProvider?.invoke(item)?.let(ReaderDocumentState::Ready)
                ?: ReaderDocumentState.Failed(readerFileMissingFailure(item))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ReaderDocumentState.Failed(readerOpenFailureOf(error, item))
        }
    }
}

/** No file for [item]: an imported file is gone; a server book's server didn't give it. */
fun readerFileMissingFailure(item: ReadableItem): ReaderOpenFailure =
    if (item.sourceType == SourceType.LOCAL) ReaderOpenFailure.LocalFileGone else ReaderOpenFailure.ServerUnreachable(item.sourceType)

/** What a failure while getting or reading [item]'s file means to the reader. */
fun readerOpenFailureOf(
    error: Throwable,
    item: ReadableItem,
): ReaderOpenFailure = when {
    error is FileNotFoundException || error is SecurityException ->
        if (item.sourceType == SourceType.LOCAL) ReaderOpenFailure.LocalFileGone else ReaderOpenFailure.DownloadGone
    error is IOException && item.sourceType != SourceType.LOCAL -> ReaderOpenFailure.ServerUnreachable(item.sourceType)
    else -> ReaderOpenFailure.Unknown
}

/** The step to show while [document] is still coming: a server book's file is fetched, a local one opened. */
fun ReaderDocumentState.openingStep(item: ReadableItem): ReaderOpeningStep =
    if (this is ReaderDocumentState.Loading && item.sourceType != SourceType.LOCAL) {
        ReaderOpeningStep.Fetching
    } else {
        ReaderOpeningStep.Opening
    }
