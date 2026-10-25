package com.example.unifiedreader.feature.reader.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppEmptyState
import com.example.unifiedreader.ui.components.AppEmptyStateBooks
import com.example.unifiedreader.ui.components.AppToastHost
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.ReaderPageColors
import kotlinx.coroutines.delay

/**
 * Over the book until its first page is there: the cover on the colour the book opens on, the
 * title and author, and a line that moves; a line of words after [ReaderOpeningWordsDelayMillis].
 * It fades into the page. When the book can't open it becomes the error screen, with Back, the
 * reason and a way forward.
 */
@Composable
internal fun BoxScope.ReaderOpeningLayer(
    state: ReaderOpeningState,
    item: ReadableItem,
    cover: (@Composable (ReadableItem, Modifier) -> Unit)?,
    downloadAgain: ReaderDownloadAgain?,
    onBack: () -> Unit,
) {
    val colors = state.colors ?: appReaderPageColors()
    // "Download again" waits here for the fresh download, then tries again.
    var awaitingDownload by remember(item.id) { mutableStateOf(false) }
    var downloadRemoved by remember(item.id) { mutableStateOf(false) }
    val downloaded = downloadAgain?.isDownloaded?.invoke(item) == true
    LaunchedEffect(awaitingDownload, downloaded) {
        if (!awaitingDownload) return@LaunchedEffect
        if (!downloaded) {
            downloadRemoved = true
        } else if (downloadRemoved) {
            awaitingDownload = false
            downloadRemoved = false
            state.retry?.invoke()
        }
    }
    val status = if (awaitingDownload) ReaderOpenStatus.Opening(ReaderOpeningStep.Fetching) else state.status
    AnimatedVisibility(
        visible = status !is ReaderOpenStatus.Open,
        enter = fadeIn(AppMotion.effects()),
        exit = fadeOut(AppMotion.effects()),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.page)
                // The page underneath isn't ready for taps yet.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                .testTag(ReaderOpeningLayerTag),
        ) {
            Crossfade(targetState = status as? ReaderOpenStatus.Failed, animationSpec = AppMotion.effects(), label = "readerOpening") { failed ->
                if (failed == null) {
                    ReaderOpeningContent(
                        item = item,
                        colors = colors,
                        step = (status as? ReaderOpenStatus.Opening)?.step ?: ReaderOpeningStep.Opening,
                        cover = cover,
                        wordsAtOnce = awaitingDownload,
                    )
                } else {
                    ReaderErrorContent(
                        item = item,
                        colors = colors,
                        failure = failed.failure,
                        actions = readerErrorActions(
                            failure = failed.failure,
                            canRetry = state.retry != null,
                            canDownloadAgain = downloaded && downloadAgain != null,
                        ),
                        onAction = { action ->
                            when (action) {
                                ReaderErrorAction.TryAgain -> state.retry?.invoke()
                                ReaderErrorAction.DownloadAgain -> {
                                    awaitingDownload = true
                                    downloadAgain?.start?.invoke(item)
                                }
                            }
                        },
                        onBack = onBack,
                    )
                }
            }
            // Over the book's own toasts while this covers it, as Download again's.
            AppToastHost(modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun ReaderOpeningContent(
    item: ReadableItem,
    colors: ReaderPageColors,
    step: ReaderOpeningStep,
    cover: (@Composable (ReadableItem, Modifier) -> Unit)?,
    wordsAtOnce: Boolean,
) {
    var showWords by remember { mutableStateOf(wordsAtOnce) }
    LaunchedEffect(Unit) {
        delay(ReaderOpeningWordsDelayMillis)
        showWords = true
    }
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    val coverShape = RoundedCornerShape(6.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // A little above the middle, where the eye goes first.
        Spacer(modifier = Modifier.weight(0.82f))
        cover?.invoke(
            item,
            Modifier
                .size(width = if (compact) 112.dp else 132.dp, height = if (compact) 168.dp else 198.dp)
                .shadow(10.dp, coverShape)
                .clip(coverShape),
        )
        Text(
            text = item.libraryPrimaryText(),
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 24.dp),
        )
        // The author, or a comic's issue.
        item.librarySecondaryText()?.let { secondLine ->
            Text(
                text = secondLine,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.number,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ReaderOpeningLine(colors = colors, modifier = Modifier.padding(top = 22.dp))
        // The words have their line kept, so nothing moves when they appear.
        val wordsAlpha by animateFloatAsState(if (showWords) 1f else 0f, AppMotion.effects(), label = "readerOpeningWords")
        Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            if (showWords) {
                Text(
                    text = readerOpeningWords(step, item.sourceType),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.number,
                    modifier = Modifier
                        .alpha(wordsAlpha)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

/** A 120 dp line whose brighter third slides across every 1.2 s. */
@Composable
private fun ReaderOpeningLine(
    colors: ReaderPageColors,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "readerOpeningLine")
    val position by transition.animateFloat(
        initialValue = -ReaderOpeningLineSegment,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_200, easing = LinearEasing), RepeatMode.Restart),
        label = "readerOpeningLinePosition",
    )
    Box(
        modifier = modifier
            .width(120.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.text.copy(alpha = 0.12f))
            .drawBehind {
                val start = (position * size.width).coerceAtLeast(0f)
                val end = ((position + ReaderOpeningLineSegment) * size.width).coerceAtMost(size.width)
                if (end > start) {
                    drawRoundRect(
                        color = colors.text.copy(alpha = 0.55f),
                        topLeft = Offset(start, 0f),
                        size = Size(end - start, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
            },
    )
}

@Composable
private fun ReaderErrorContent(
    item: ReadableItem,
    colors: ReaderPageColors,
    failure: ReaderOpenFailure,
    actions: List<ReaderErrorAction>,
    onAction: (ReaderErrorAction) -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        ReaderTopBar(
            title = item.libraryDisplayLabel(),
            subtitle = null,
            onBack = onBack,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(ReaderChromeDefaults.inset),
        )
        // The kit's empty state, in the page's colours.
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                background = colors.page,
                onSurface = colors.text,
                onSurfaceVariant = colors.text.copy(alpha = 0.72f),
            ),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
        ) {
            AppEmptyState(
                title = failure.title(),
                body = failure.reason(),
                drawing = { AppEmptyStateBooks(background = colors.page) },
                actions = {
                    actions.forEach { action ->
                        AppButton(
                            onClick = { onAction(action) },
                            colors = AppButtonDefaults.filledColors(containerColor = colors.text, contentColor = colors.page),
                        ) {
                            Text(text = if (action == ReaderErrorAction.TryAgain) "Try again" else "Download again")
                        }
                    }
                    AppTonalButton(
                        onClick = onBack,
                        colors = AppButtonDefaults.filledColors(containerColor = colors.text.copy(alpha = 0.1f), contentColor = colors.text),
                    ) {
                        Text(text = "Back to library")
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag(ReaderErrorTag),
            )
        }
    }
}

/** The app's own background and text, for a reader that hasn't said its colours. */
@Composable
private fun appReaderPageColors(): ReaderPageColors {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        ReaderPageColors(page = scheme.background, text = scheme.onBackground, number = scheme.outline, raised = scheme.surfaceContainerHigh)
    }
}

internal const val ReaderOpeningWordsDelayMillis = 600L
private const val ReaderOpeningLineSegment = 0.34f

const val ReaderOpeningLayerTag = "reader-opening"
const val ReaderErrorTag = "reader-error"
