package com.example.unifiedreader.feature.reader.epub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.ui.geometry.Rect
import com.example.unifiedreader.feature.translation.ReaderTextSelection
import com.example.unifiedreader.feature.translation.TranslationSession
import com.example.unifiedreader.feature.translation.TranslationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * What the reader does with selected text. With [openOnSelect] the card opens as you select and
 * follows the handles ([onFollow]); otherwise the toolbar's Translate opens it ([onTranslate]).
 * [isOpen]: the card or sheet shows now; when it closes the page's selection is cleared.
 * [onCleared]: the selection went away (a tap on the page, Copy).
 */
internal class EpubSelectionTranslation(
    val openOnSelect: Boolean,
    val isOpen: Boolean,
    val onTranslate: (ReaderTextSelection) -> Unit,
    val onFollow: (ReaderTextSelection) -> Unit,
    val onCleared: () -> Unit,
)

/** The reader's [EpubSelectionTranslation] for an open [session]; [hideChrome] hides its bars. */
internal fun epubSelectionTranslation(
    session: TranslationSession,
    state: TranslationState?,
    isOpen: Boolean,
    hideChrome: () -> Unit,
) = EpubSelectionTranslation(
    openOnSelect = state?.preferences?.openOnSelect ?: true,
    isOpen = isOpen,
    onTranslate = { selection ->
        hideChrome()
        session.open(selection)
    },
    onFollow = { selection ->
        hideChrome()
        session.follow(selection)
    },
    onCleared = {
        if (session.current.value?.selection?.followsSelection == true) session.close()
    },
)

/**
 * The toolbar over selected text. Readium hands the whole menu to the app, so without this the
 * toolbar is empty: it holds Translate (when the app has translation and doesn't open it by
 * itself) and Copy. [onSelectionChanged] runs when the toolbar opens and each time the WebView
 * refreshes it, which it does as a handle moves the selection. [delegate] is the page curl's
 * callback, which needs to know a selection is open.
 */
internal class EpubSelectionActionModeCallback(
    private val delegate: ActionMode.Callback,
    private val canTranslate: () -> Boolean,
    private val onTranslate: () -> Unit,
    private val onCopy: () -> Unit,
    private val onSelectionChanged: () -> Unit,
    private val onSelectionCleared: () -> Unit,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        delegate.onCreateActionMode(mode, menu)
        if (canTranslate()) menu.add(Menu.NONE, TranslateItemId, 0, TranslateLabel)
        menu.add(Menu.NONE, CopyItemId, 1, android.R.string.copy)
        onSelectionChanged()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        onSelectionChanged()
        return delegate.onPrepareActionMode(mode, menu)
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            TranslateItemId -> onTranslate()
            CopyItemId -> onCopy()
            else -> return delegate.onActionItemClicked(mode, item)
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        delegate.onDestroyActionMode(mode)
        onSelectionCleared()
    }

    private companion object {
        const val TranslateItemId = 0x7EAD01
        const val CopyItemId = 0x7EAD02
        const val TranslateLabel = "Translate"
    }
}

/**
 * Turns the toolbar's signals into the translation card's: Translate opens it, a changed
 * selection (once a handle rests) updates it, and a selection that is gone, or a card that
 * closed, clears the other side. Kept out of the navigator host, which is long enough.
 */
internal class EpubSelectionFollower(
    private val scope: CoroutineScope,
    private val navigator: () -> EpubNavigatorFragment?,
    private val bookLanguage: String?,
    private val translation: () -> EpubSelectionTranslation?,
) {
    private var followJob: Job? = null
    private var clearJob: Job? = null
    private var wasShown = false

    fun translate() {
        scope.withCurrentSelection(navigator(), bookLanguage) { selection -> translation()?.onTranslate?.invoke(selection) }
    }

    fun selectionChanged() {
        clearJob?.cancel()
        if (translation()?.openOnSelect != true) return
        // Wait for the handle to rest, so the card doesn't chase every step.
        followJob?.cancel()
        followJob = scope.withCurrentSelection(navigator(), bookLanguage, follows = true, delayMillis = FollowDelayMillis) { selection ->
            translation()?.onFollow?.invoke(selection)
        }
    }

    fun selectionCleared() {
        // Letting go of a handle closes the toolbar and opens it again at once;
        // only a toolbar that stays closed means the selection went away.
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(GoneDelayMillis)
            followJob?.cancel()
            translation()?.onCleared?.invoke()
        }
    }

    /** The card or sheet showing or not: when it closes, the words stop being selected too. */
    fun translationShown(shown: Boolean) {
        if (wasShown && !shown) {
            followJob?.cancel()
            navigator()?.clearSelection()
        }
        wasShown = shown
    }

    private companion object {
        /** How long a selection rests before the card follows it: long enough to skip a handle's steps. */
        const val FollowDelayMillis = 300L

        /** How long the toolbar may stay closed before the selection counts as gone. */
        const val GoneDelayMillis = 250L
    }
}

/**
 * Reads what is selected now, with its place on screen in window pixels, then runs [block].
 * [follows]: the card follows this selection, so it stays selected at any length.
 */
@OptIn(ExperimentalReadiumApi::class)
internal fun CoroutineScope.withCurrentSelection(
    navigator: EpubNavigatorFragment?,
    bookLanguage: String?,
    follows: Boolean = false,
    delayMillis: Long = 0,
    block: (ReaderTextSelection) -> Unit,
): Job? {
    val fragment = navigator ?: return null
    return launch {
        if (delayMillis > 0) delay(delayMillis)
        val selection = fragment.currentSelection() ?: return@launch
        val text = selection.locator.text.highlight?.takeIf { it.isNotBlank() } ?: return@launch
        val origin = IntArray(2).also { fragment.view?.getLocationInWindow(it) }
        val bounds = selection.rect?.let { rect ->
            Rect(rect.left + origin[0], rect.top + origin[1], rect.right + origin[0], rect.bottom + origin[1])
        }
        val read = ReaderTextSelection(
            text = text,
            before = selection.locator.text.before.orEmpty(),
            after = selection.locator.text.after.orEmpty(),
            bookLanguage = bookLanguage,
            bounds = bounds,
            followsSelection = follows,
        )
        // The sheet doesn't point at the text, and the WebView's handles would float over it.
        if (!read.isShort && !follows) fragment.clearSelection()
        block(read)
    }
}

internal fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}
