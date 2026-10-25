package com.example.unifiedreader.feature.reader.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.theme.ReaderPageColors

/** Where a reader is on its way to the first page. */
enum class ReaderOpeningStep {
    /** A server book's file is coming down. */
    Fetching,

    /** The file is being read. */
    Opening,

    /** The reader is looking for your place. */
    FindingPage,
}

/** Why a book couldn't open, as the error screen says it. */
sealed interface ReaderOpenFailure {
    data class ServerUnreachable(val source: SourceType) : ReaderOpenFailure

    data class ServerNotConnected(val source: SourceType) : ReaderOpenFailure

    /** An imported file that was moved or deleted. */
    data object LocalFileGone : ReaderOpenFailure

    /** A server book's download whose file is gone. */
    data object DownloadGone : ReaderOpenFailure

    data object NotReadable : ReaderOpenFailure

    data object NoComicPages : ReaderOpenFailure

    data object NoPdfPages : ReaderOpenFailure

    data object PdfUnreadable : ReaderOpenFailure

    data class Unsupported(val format: FormatType) : ReaderOpenFailure

    data object Unknown : ReaderOpenFailure
}

/** How a reader is doing: still opening, open, or failed. */
sealed interface ReaderOpenStatus {
    data class Opening(val step: ReaderOpeningStep) : ReaderOpenStatus

    data object Open : ReaderOpenStatus

    data class Failed(val failure: ReaderOpenFailure) : ReaderOpenStatus
}

/**
 * What the reader host shows over a book until its first page is there, or instead of it when it
 * can't open. Readers report to it with [ReportReaderOpening].
 */
@Stable
class ReaderOpeningState(initialStatus: ReaderOpenStatus = ReaderOpenStatus.Opening(ReaderOpeningStep.Opening)) {
    var status by mutableStateOf(initialStatus)
        internal set

    /** The colours the book opens on; the app's own until the reader says. */
    var colors by mutableStateOf<ReaderPageColors?>(null)
        internal set

    /** What Try again does, when the reader can try again. */
    internal var retry: (() -> Unit)? = null
}

val LocalReaderOpening = staticCompositionLocalOf<ReaderOpeningState?> { null }

/**
 * A reader's report to its host: where it is ([status]), the [colors] it opens on, and what
 * [retry] does after a failure. Outside a reader host (previews, tests) it does nothing.
 */
@Composable
fun ReportReaderOpening(
    status: ReaderOpenStatus,
    colors: ReaderPageColors? = null,
    retry: (() -> Unit)? = null,
) {
    val state = LocalReaderOpening.current ?: return
    SideEffect {
        state.status = status
        if (colors != null) state.colors = colors
        state.retry = retry
    }
}

/** The line under the author once an open takes longer than a moment. */
fun readerOpeningWords(
    step: ReaderOpeningStep,
    source: SourceType,
): String = when (step) {
    ReaderOpeningStep.Fetching -> "Getting it from ${source.displayName()}…"
    ReaderOpeningStep.Opening -> "Opening…"
    ReaderOpeningStep.FindingPage -> "Finding your page…"
}

/** The error screen's heading. */
fun ReaderOpenFailure.title(): String = when (this) {
    is ReaderOpenFailure.Unsupported -> "yabai can’t open ${format.name} files"
    else -> "Couldn’t open this book"
}

/** The error screen's reason, in a sentence that says what to do. */
fun ReaderOpenFailure.reason(): String = when (this) {
    is ReaderOpenFailure.ServerUnreachable ->
        "${source.displayName()} didn’t answer. Check that you’re on the same network as your server, then try again."
    is ReaderOpenFailure.ServerNotConnected -> "This book’s server isn’t connected. Connect it in Settings, then try again."
    ReaderOpenFailure.LocalFileGone -> "The file was moved or deleted. Import it again from where it is now."
    ReaderOpenFailure.DownloadGone -> "The downloaded file is gone. Download it again."
    ReaderOpenFailure.NotReadable -> "This file isn’t a book yabai can read. It may be damaged."
    ReaderOpenFailure.NoComicPages -> "This archive has no pages yabai can show."
    ReaderOpenFailure.NoPdfPages -> "This PDF has no pages."
    ReaderOpenFailure.PdfUnreadable -> "This PDF is damaged or locked with a password."
    is ReaderOpenFailure.Unsupported -> "It reads EPUB, PDF, CBZ and CBR."
    ReaderOpenFailure.Unknown -> "Something went wrong while opening it."
}

/** The error screen's actions besides Back to library, the first filled. */
enum class ReaderErrorAction { TryAgain, DownloadAgain }

/**
 * A broken download is fetched again rather than retried; a file that is gone, or a format no
 * reader takes, can only be left.
 */
fun readerErrorActions(
    failure: ReaderOpenFailure,
    canRetry: Boolean,
    canDownloadAgain: Boolean,
): List<ReaderErrorAction> {
    val downloadFixes = failure == ReaderOpenFailure.DownloadGone ||
        failure == ReaderOpenFailure.NotReadable ||
        failure == ReaderOpenFailure.NoComicPages ||
        failure == ReaderOpenFailure.NoPdfPages ||
        failure == ReaderOpenFailure.PdfUnreadable
    if (canDownloadAgain && downloadFixes) return listOf(ReaderErrorAction.DownloadAgain)
    val retryHelps = when (failure) {
        is ReaderOpenFailure.ServerUnreachable,
        is ReaderOpenFailure.ServerNotConnected,
        ReaderOpenFailure.Unknown,
        -> true
        else -> false
    }
    return if (canRetry && retryHelps) listOf(ReaderErrorAction.TryAgain) else emptyList()
}

/**
 * "Download again" for a server book whose download is broken: whether [item] has a download
 * ([isDownloaded], read in composition so the host follows it), and [start], which removes the
 * broken one and queues a fresh one.
 */
class ReaderDownloadAgain(
    val isDownloaded: (ReadableItem) -> Boolean,
    val start: (ReadableItem) -> Unit,
)
