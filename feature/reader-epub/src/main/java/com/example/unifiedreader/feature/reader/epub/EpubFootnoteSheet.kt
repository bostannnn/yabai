package com.example.unifiedreader.feature.reader.epub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.ReaderPageColor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link

/** A note's text, and [endnoteLink] when the note lives in another file of the book and can be opened there. */
internal data class EpubFootnoteContent(
    val body: String,
    val endnoteLink: Link? = null,
)

/** How the note card reads: the book's font at its size and line height. */
internal data class EpubFootnoteType(
    val fontFamily: FontFamily,
    val fontSize: Double,
    val lineHeight: Double,
)

/** The note card's type: the book's font at its size, or a serif at 100% when the book keeps its own styling. */
@Composable
internal fun rememberEpubFootnoteType(
    typographyMode: EpubTypographyMode,
    fontFamily: EpubReaderFontFamily,
    fontSize: Double,
    lineHeight: Double,
): EpubFootnoteType {
    val family = rememberEpubFontPreviewFamily(fontFamily)
    return remember(typographyMode, family, fontSize, lineHeight) {
        if (typographyMode == EpubTypographyMode.Original) {
            EpubFootnoteType(fontFamily = FontFamily.Serif, fontSize = 1.0, lineHeight = 1.5)
        } else {
            EpubFootnoteType(fontFamily = family ?: FontFamily.Serif, fontSize = fontSize, lineHeight = lineHeight)
        }
    }
}

/**
 * A footnote as a card that rises from the bottom of the page, in the page's raised colour and
 * the book's own type, under its mark ("Note 12"). Tap the page, swipe the card down or press
 * Back to close it. For a note at the back of the book, Go to note opens it there. Drawn in the
 * reader's own window, so the system bars stay hidden.
 */
@Composable
internal fun BoxScope.EpubFootnoteCard(
    footnote: EpubFootnoteContent?,
    pageColor: ReaderPageColor,
    type: EpubFootnoteType,
    onDismissRequest: () -> Unit,
    onGoToNote: (Link) -> Unit,
) {
    // The last note stays on the card while it slides away.
    var shownNote by remember { mutableStateOf(footnote) }
    if (footnote != null && footnote != shownNote) shownNote = footnote
    val colors = pageColor.colors
    BackHandler(enabled = footnote != null, onBack = onDismissRequest)
    AnimatedVisibility(
        visible = footnote != null,
        enter = fadeIn(AppMotion.effects()),
        exit = fadeOut(AppMotion.effects()),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.text.copy(alpha = EpubFootnoteScrimAlpha))
                .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } },
        )
    }
    AnimatedVisibility(
        visible = footnote != null,
        enter = slideInVertically(AppMotion.spatial()) { height -> height },
        exit = slideOutVertically(AppMotion.spatial()) { height -> height },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        val note = shownNote ?: return@AnimatedVisibility
        EpubFootnoteCardBody(
            note = note,
            pageColor = pageColor,
            type = type,
            onDismissRequest = onDismissRequest,
            onGoToNote = onGoToNote,
        )
    }
}

@Composable
private fun EpubFootnoteCardBody(
    note: EpubFootnoteContent,
    pageColor: ReaderPageColor,
    type: EpubFootnoteType,
    onDismissRequest: () -> Unit,
    onGoToNote: (Link) -> Unit,
) {
    val colors = pageColor.colors
    val heading = remember(note.body) { epubFootnoteHeading(note.body) }
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * EpubFootnoteMaxHeightFraction
    val textSize = EpubBaseTextSize * type.fontSize
    val dismissDistance = with(LocalDensity.current) { EpubFootnoteDismissDistance.toPx() }
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismissRequest)
    var dragOffset by remember(note) { mutableFloatStateOf(0f) }
    // Dragging the card down (past the top of a long note) moves it; let go far enough or fast enough and it closes.
    val dragToDismiss = remember(note, dismissDistance) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || dragOffset <= 0f) return Offset.Zero
                val consumed = available.y.coerceAtLeast(-dragOffset)
                dragOffset += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                dragOffset += available.y
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset <= 0f) return Velocity.Zero
                if (dragOffset > dismissDistance || available.y > EpubFootnoteDismissVelocity) {
                    latestOnDismiss()
                } else {
                    scope.launch { animate(dragOffset, 0f, animationSpec = AppMotion.spatial()) { value, _ -> dragOffset = value } }
                }
                return available
            }
        }
    }
    Column(
        modifier = Modifier
            .widthIn(max = EpubFootnoteMaxWidth)
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .background(colors.raised, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .semantics { dismiss(label = "Close note") { latestOnDismiss(); true } }
            .nestedScroll(dragToDismiss)
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 32.dp)
            .testTag(EpubFootnoteSheetTag),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .background(colors.number, RoundedCornerShape(2.dp)),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = heading.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.number,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = heading.text.ifBlank { "This note has no text." },
            fontFamily = type.fontFamily,
            fontSize = textSize.sp,
            lineHeight = (textSize * type.lineHeight).sp,
            color = colors.text,
        )
        note.endnoteLink?.let { link ->
            Spacer(modifier = Modifier.height(16.dp))
            AppTonalButton(
                onClick = { onGoToNote(link) },
                colors = AppButtonDefaults.filledColors(
                    containerColor = colors.text.copy(alpha = 0.08f),
                    contentColor = colors.text,
                ),
            ) {
                Text(text = "Go to note")
            }
        }
    }
}

// Readium sets 100% text at 16 CSS px, which the web view draws at 16 dp.
private const val EpubBaseTextSize = 16.0
private const val EpubFootnoteMaxHeightFraction = 0.45f
private const val EpubFootnoteScrimAlpha = 0.12f
private const val EpubFootnoteDismissVelocity = 1_200f
private val EpubFootnoteMaxWidth = 560.dp
private val EpubFootnoteDismissDistance = 96.dp

internal const val EpubFootnoteSheetTag = "epub-footnote-sheet"
