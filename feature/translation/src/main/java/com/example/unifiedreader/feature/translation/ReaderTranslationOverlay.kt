package com.example.unifiedreader.feature.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.ui.components.AppAnchoredCard
import com.example.unifiedreader.ui.components.AppFloatingPanel
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppPanelHeader

/** A reader's translation: one session per open book, kept across selections. */
@Composable
fun rememberTranslationSession(service: TranslationService): TranslationSession {
    val scope = rememberCoroutineScope()
    val session = remember(service) { TranslationSession(service, scope) }
    LaunchedEffect(service) { service.startPreferences() }
    DisposableEffect(session) { onDispose(session::close) }
    return session
}

/**
 * The card or the sheet over a book. One to three words open the card at the words; longer
 * selections, and Open full, open the sheet at the end edge. Place it in a full-size slot whose
 * coordinates the selection's bounds are in.
 */
@Composable
fun ReaderTranslationOverlay(
    session: TranslationSession,
    service: TranslationService,
    modifier: Modifier = Modifier,
) {
    val current by session.current.collectAsStateWithLifecycle()
    val state by service.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var addingDeepL by remember { mutableStateOf(false) }
    if (addingDeepL) {
        DeepLKeyDialog(service = service, hasKey = state.deepL.hasKey, onDismissRequest = { addingDeepL = false })
    }
    val translation = current
    // A new selection starts small again; Open full only applies to the one it was pressed on.
    LaunchedEffect(translation?.selection) { expanded = false }
    val close = {
        expanded = false
        session.close()
    }
    val actions = TranslationPanelActions(
        onPickFrom = { tag -> if (tag == null) session.resetFrom() else session.pickFrom(tag) },
        onPickTo = session::pickTo,
        onSwap = session::swap,
        onDownload = session::download,
        onRetry = session::retry,
        onClose = close,
        onOpenFull = { expanded = true },
        onPickProvider = service::setProvider,
        onAddDeepL = { addingDeepL = true },
    )
    val asCard = translation != null && !expanded &&
        (translation.selection.isShort || translation.selection.followsSelection)
    // The selection's bounds come in window pixels; the card is placed in this slot's.
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() })
    if (translation != null && asCard) {
        AppAnchoredCard(
            anchor = translation.selection.bounds?.translate(-origin),
            onDismissRequest = close,
            modifier = modifier,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TranslationLanguageRow(translation, state, actions, showClose = true)
                Column(
                    modifier = Modifier.heightIn(max = CardBodyMaxHeight).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TranslationPhaseBody(translation, asSheet = false, actions = actions)
                }
                TranslationCardFooter(translation, state, actions)
            }
        }
    }
    AppFloatingPanel(
        visible = translation != null && !asCard,
        placement = AppFloatingPanelPlacement.End,
        onDismissRequest = close,
        modifier = modifier,
    ) {
        val shown = translation ?: return@AppFloatingPanel
        AppPanelHeader(title = TranslationCopy.Translation, onClose = close)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppLayout.inset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TranslationLanguageRow(shown, state, actions.copyWithoutOpenFull(), showClose = false)
            TranslationPhaseBody(shown, asSheet = true, actions = actions)
        }
        Column(modifier = Modifier.fillMaxWidth().padding(AppLayout.inset)) {
            TranslationSheetFooter(shown, state, actions)
        }
    }
}

/** A passage in the card scrolls past this; Open full shows it in the sheet. */
private val CardBodyMaxHeight = 320.dp

private fun TranslationPanelActions.copyWithoutOpenFull() = TranslationPanelActions(
    onPickFrom = onPickFrom,
    onPickTo = onPickTo,
    onSwap = onSwap,
    onDownload = onDownload,
    onRetry = onRetry,
    onClose = onClose,
    onOpenFull = null,
    onPickProvider = onPickProvider,
    onAddDeepL = onAddDeepL,
)
