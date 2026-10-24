package com.example.unifiedreader.feature.reader.epub

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.example.unifiedreader.ui.components.AppToastIcon
import com.example.unifiedreader.ui.components.AppToastMessage
import com.example.unifiedreader.ui.components.LocalAppToastState
import kotlinx.coroutines.delay
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.mediatype.MediaType

/** Where Go to note left from: the [locator], [page] and [progress] to return to, and the note's file ([noteHref]). */
internal data class EpubNoteReturn(
    val locator: Locator,
    val page: Int,
    val progress: Double,
    val noteHref: String,
)

/** Opens a note at the back of the book and marks it for a moment, so the eye finds it. */
internal data class EpubNoteNavigationRequest(
    override val id: Int,
    val link: Link,
) : EpubNavigationRequest

/** Back to a place the reader saved, such as the page a note was opened from. */
internal data class EpubLocatorNavigationRequest(
    override val id: Int,
    val locator: Locator,
) : EpubNavigationRequest

/**
 * The way back from a note opened with Go to note: a toast "Back to page 4" for 6 s, and the
 * system Back while you are still in the note's file. Turning out of that file ends it
 * ([onLeftNote]); [onReturn] goes back.
 */
@Composable
internal fun EpubNoteReturnEffects(
    noteReturn: EpubNoteReturn?,
    currentHref: String?,
    onReturn: () -> Unit,
    onLeftNote: () -> Unit,
) {
    val toastState = LocalAppToastState.current
    val latestOnReturn by rememberUpdatedState(onReturn)
    var arrived by remember(noteReturn) { mutableStateOf(false) }
    var shownToast by remember { mutableStateOf<AppToastMessage?>(null) }
    LaunchedEffect(noteReturn) {
        // Back, or turning away from the note, ends the offer: the toast goes too.
        shownToast?.let { toast -> toastState?.dismiss(toast) }
        shownToast = null
        val jump = noteReturn ?: return@LaunchedEffect
        val toast = AppToastMessage(
            text = epubNoteReturnLabel(jump.page),
            icon = AppToastIcon.Back,
            actionLabel = "Back",
            onAction = { latestOnReturn() },
        )
        shownToast = toast
        toastState?.show(toast)
    }
    LaunchedEffect(noteReturn, currentHref) {
        val jump = noteReturn ?: return@LaunchedEffect
        if (currentHref == jump.noteHref) {
            arrived = true
        } else if (arrived) {
            onLeftNote()
        }
    }
    BackHandler(enabled = noteReturn != null && arrived) {
        onReturn()
    }
}

internal fun epubNoteReturnLabel(page: Int): String = if (page > 0) "Back to page $page" else "Back to where you were"

/** Highlights the note [link] points at for [EpubNoteMarkMillis], once its page has drawn. */
internal suspend fun EpubNavigatorFragment.markEpubNote(
    link: Link,
    tint: Int,
) {
    val fragment = link.url().fragment ?: return
    val locator = Locator(
        href = link.url().removeFragment(),
        mediaType = MediaType.XHTML,
        locations = Locator.Locations(fragments = listOf(fragment)),
    )
    delay(EpubNoteMarkDelayMillis)
    applyDecorations(listOf(Decoration(id = "note", locator = locator, style = Decoration.Style.Highlight(tint = tint))), EpubNoteMarkGroup)
    delay(EpubNoteMarkMillis)
    applyDecorations(emptyList(), EpubNoteMarkGroup)
}

private const val EpubNoteMarkGroup = "note-jump"
private const val EpubNoteMarkDelayMillis = 300L
private const val EpubNoteMarkMillis = 1_600L
