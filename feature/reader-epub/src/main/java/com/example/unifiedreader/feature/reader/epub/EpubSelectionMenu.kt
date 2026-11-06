package com.example.unifiedreader.feature.reader.epub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.ui.geometry.Rect
import com.example.unifiedreader.feature.translation.ReaderTextSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * The toolbar over selected text. Readium hands the whole menu to the app, so without this the
 * toolbar is empty: it holds Translate (when the app has translation) and Copy. [delegate] is
 * the page curl's callback, which needs to know a selection is open.
 */
internal class EpubSelectionActionModeCallback(
    private val delegate: ActionMode.Callback,
    private val canTranslate: () -> Boolean,
    private val onTranslate: () -> Unit,
    private val onCopy: () -> Unit,
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        delegate.onCreateActionMode(mode, menu)
        if (canTranslate()) menu.add(Menu.NONE, TranslateItemId, 0, TranslateLabel)
        menu.add(Menu.NONE, CopyItemId, 1, android.R.string.copy)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = delegate.onPrepareActionMode(mode, menu)

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        when (item.itemId) {
            TranslateItemId -> onTranslate()
            CopyItemId -> onCopy()
            else -> return delegate.onActionItemClicked(mode, item)
        }
        mode.finish()
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = delegate.onDestroyActionMode(mode)

    private companion object {
        const val TranslateItemId = 0x7EAD01
        const val CopyItemId = 0x7EAD02
        const val TranslateLabel = "Translate"
    }
}

/** Reads what is selected now, with its place on screen in window pixels, then runs [block]. */
@OptIn(ExperimentalReadiumApi::class)
internal fun CoroutineScope.withCurrentSelection(
    navigator: EpubNavigatorFragment?,
    bookLanguage: String?,
    block: (ReaderTextSelection) -> Unit,
) {
    val fragment = navigator ?: return
    launch {
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
        )
        // The sheet doesn't point at the text, and the WebView's handles would float over it.
        if (!read.isShort) fragment.clearSelection()
        block(read)
    }
}

internal fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}
