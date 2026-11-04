package com.example.unifiedreader.feature.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverAspectRatio
import com.example.unifiedreader.feature.library.coverTint
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppDialogCardDefaults
import com.example.unifiedreader.ui.components.AppDialogHost
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSheet
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.components.AppTonalIconButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.AppCoverAccentTheme
import com.example.unifiedreader.ui.theme.AppCoverColors
import com.example.unifiedreader.ui.theme.AppMotion
import com.example.unifiedreader.ui.theme.UnifiedReaderDefaultPresentationTokens
import com.example.unifiedreader.ui.theme.isAppThemeDark
import kotlinx.coroutines.delay

/** The item after the finished one: its cover, [title] under a [label] ("Up next in …"), and [onRead]. */
class JournalFinishedNext(
    val item: ReadableItem,
    val title: String,
    val label: String,
    val onRead: () -> Unit,
)

/**
 * The screen at the end of a book: one surface in the cover's [colors] with the cover, "Finished",
 * the title and [subtitle], the journal's stars (a tap saves through [onRatingSelected]), a private
 * note kept in [reviewState] for the host to save when the screen closes, the [next] item, and Back
 * to library. On tablets a 640 dp card over the last page with the cover on a band beside the rest;
 * on phones a sheet, and on windows too narrow for that card a 440 dp one, with everything centred.
 * ×, a tap outside or Back calls [onDismiss], which leaves the reader on its last page.
 */
@Composable
fun JournalFinishedScreen(
    item: ReadableItem,
    title: String,
    subtitle: String?,
    colors: AppCoverColors,
    reviewState: JournalReviewState,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    onRatingSelected: (Int?) -> Unit,
    onBackToLibrary: () -> Unit,
    // "Back to the list" when the book was opened from one.
    backLabel: String = "Back to library",
    onDismiss: () -> Unit,
    next: JournalFinishedNext? = null,
) {
    val content = @Composable { layout: JournalFinishedLayout, modifier: Modifier ->
        JournalFinishedContent(
            item = item,
            title = title,
            subtitle = subtitle,
            colors = colors,
            reviewState = reviewState,
            coverContent = coverContent,
            onRatingSelected = onRatingSelected,
            onBackToLibrary = onBackToLibrary,
            backLabel = backLabel,
            onDismiss = onDismiss,
            next = next,
            layout = layout,
            modifier = modifier,
        )
    }
    if (currentAppWindowWidth() == AppWindowWidth.Compact) {
        AppSheet(onDismissRequest = onDismiss, containerColor = colors.tintStart, skipPartiallyExpanded = true) {
            content(JournalFinishedLayout.Sheet, Modifier.coverTint(colors))
        }
    } else {
        val layout = journalFinishedCardLayout(LocalConfiguration.current.screenWidthDp.dp)
        AppDialogHost(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // The card fades in from a little smaller; the sheet on phones slides up by itself.
            val shown = remember { Animatable(0f) }
            LaunchedEffect(Unit) { shown.animateTo(1f, AppMotion.effects()) }
            val shape = RoundedCornerShape(AppDialogCardDefaults.cornerRadius)
            content(
                layout,
                Modifier
                    .padding(AppDialogCardDefaults.windowMargin)
                    .widthIn(
                        max = if (layout == JournalFinishedLayout.Beside) {
                            JournalFinishedDefaults.besideCardWidth
                        } else {
                            JournalFinishedDefaults.cardWidth
                        },
                    )
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = shown.value
                        scaleX = AppMotion.fadeThroughInitialScale + (1f - AppMotion.fadeThroughInitialScale) * shown.value
                        scaleY = scaleX
                    }
                    .clip(shape)
                    .coverTint(colors),
            )
        }
    }
}

/** The cover beside the rest; everything centred in a card; or everything centred in a sheet. */
internal enum class JournalFinishedLayout { Beside, Card, Sheet }

/** The card for a window [windowWidth] wide: the cover beside the rest when 640 dp and its margins fit. */
internal fun journalFinishedCardLayout(windowWidth: Dp): JournalFinishedLayout =
    if (windowWidth >= JournalFinishedDefaults.besideCardWidth + AppDialogCardDefaults.windowMargin * 2) {
        JournalFinishedLayout.Beside
    } else {
        JournalFinishedLayout.Card
    }

@Composable
private fun JournalFinishedContent(
    item: ReadableItem,
    title: String,
    subtitle: String?,
    colors: AppCoverColors,
    reviewState: JournalReviewState,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    onRatingSelected: (Int?) -> Unit,
    onBackToLibrary: () -> Unit,
    backLabel: String,
    onDismiss: () -> Unit,
    next: JournalFinishedNext?,
    layout: JournalFinishedLayout,
    modifier: Modifier,
) {
    // Quiet fills on the tint: the text colour at a low alpha, a little stronger on dark tints.
    val dark = isAppThemeDark
    val quiet = colors.content.copy(alpha = if (dark) 0.10f else 0.08f)
    val tonal = colors.content.copy(alpha = if (dark) 0.12f else 0.10f)
    val stars = @Composable { stacked: Boolean, starsModifier: Modifier ->
        JournalRatingEditor(
            selectedRating = reviewState.selectedRating,
            onRatingSelected = { rating ->
                reviewState.selectedRating = rating
                onRatingSelected(rating)
            },
            layout = if (stacked) JournalRatingLayout.Stacked else JournalRatingLayout.Spread,
            animateIn = true,
            modifier = starsModifier,
        )
    }
    val nextStrip = @Composable { stripModifier: Modifier ->
        next?.let { nextItem -> JournalFinishedNextStrip(next = nextItem, coverContent = coverContent, modifier = stripModifier) }
    }
    val actions = @Composable { actionsModifier: Modifier ->
        JournalFinishedActions(
            colors = colors,
            tonal = tonal,
            next = next,
            onBackToLibrary = onBackToLibrary,
            backLabel = backLabel,
            modifier = actionsModifier,
        )
    }
    AppCoverAccentTheme(colors) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Box(modifier = modifier) {
                if (layout == JournalFinishedLayout.Beside) {
                    // The band: the tint deepened behind the cover, the card's whole height.
                    val band = if (dark) Color.Black.copy(alpha = 0.22f) else colors.content.copy(alpha = 0.06f)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .drawBehind {
                                val bandWidth = JournalFinishedDefaults.bandWidth.toPx()
                                val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - bandWidth
                                drawRect(band, topLeft = Offset(left, 0f), size = Size(bandWidth, size.height))
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(JournalFinishedDefaults.bandWidth)
                                .padding(vertical = JournalFinishedDefaults.bandCoverMargin),
                            contentAlignment = Alignment.Center,
                        ) {
                            JournalFinishedCover(item = item, coverWidth = JournalFinishedDefaults.bandCoverWidth, coverContent = coverContent)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(AppDialogCardDefaults.padding),
                        ) {
                            JournalFinishedHeading(
                                title = title,
                                subtitle = subtitle,
                                accent = colors.accent,
                                centred = false,
                                // Room for the × at the top end of the card.
                                modifier = Modifier.padding(end = JournalFinishedDefaults.closeClearance),
                            )
                            stars(false, Modifier.padding(top = AppLayout.sectionGap))
                            JournalFinishedNote(reviewState = reviewState, fill = quiet, centred = false)
                            nextStrip(Modifier.padding(top = JournalFinishedDefaults.nextGap))
                            actions(Modifier.padding(top = AppLayout.sectionGap))
                        }
                    }
                } else {
                    val sheet = layout == JournalFinishedLayout.Sheet
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                if (sheet) {
                                    PaddingValues(
                                        start = AppLayout.inset,
                                        end = AppLayout.inset,
                                        top = JournalFinishedDefaults.sheetTop,
                                        bottom = AppLayout.inset,
                                    )
                                } else {
                                    PaddingValues(AppDialogCardDefaults.padding)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        JournalFinishedCover(
                            item = item,
                            coverWidth = if (sheet) JournalFinishedDefaults.sheetCoverWidth else JournalFinishedDefaults.cardCoverWidth,
                            coverContent = coverContent,
                        )
                        JournalFinishedHeading(
                            title = title,
                            subtitle = subtitle,
                            accent = colors.accent,
                            centred = true,
                            modifier = Modifier.padding(top = JournalFinishedDefaults.coverGap),
                        )
                        stars(true, Modifier.padding(top = JournalFinishedDefaults.coverGap))
                        JournalFinishedNote(reviewState = reviewState, fill = quiet, centred = true)
                        nextStrip(Modifier.padding(top = AppLayout.sectionGap))
                        actions(Modifier.padding(top = AppLayout.sectionGap))
                    }
                }
                if (layout != JournalFinishedLayout.Sheet) {
                    AppTonalIconButton(
                        onClick = onDismiss,
                        colors = AppButtonDefaults.tonalIconColors(containerColor = quiet, contentColor = colors.content),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(JournalFinishedDefaults.closeMargin)
                            .size(JournalFinishedDefaults.closeSize),
                    ) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "Close", modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** The cover, rising into place as the screen opens. */
@Composable
private fun JournalFinishedCover(
    item: ReadableItem,
    coverWidth: Dp,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor != 0f) delay(CoverRiseDelayMillis)
        rise.animateTo(1f, AppMotion.spatial())
    }
    val coverShape = RoundedCornerShape(JournalFinishedDefaults.coverCornerRadius)
    Box(
        modifier = Modifier
            .width(coverWidth)
            .aspectRatio(LibraryCoverAspectRatio)
            .graphicsLayer {
                translationY = (1f - rise.value) * JournalFinishedDefaults.coverRise.toPx()
                alpha = rise.value.coerceIn(0f, 1f)
            }
            .shadow(elevation = 12.dp, shape = coverShape, clip = false)
            .clip(coverShape),
    ) {
        coverContent(item, Modifier.fillMaxSize())
    }
}

/** "Finished" in the accent over the title and the subtitle, at the start or centred. */
@Composable
private fun JournalFinishedHeading(
    title: String,
    subtitle: String?,
    accent: Color,
    centred: Boolean,
    modifier: Modifier = Modifier,
) {
    val align = if (centred) TextAlign.Center else TextAlign.Start
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (centred) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Finished",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            color = accent,
            maxLines = 1,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (centred) 24.sp else 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            textAlign = align,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = LocalContentColor.current.copy(alpha = 0.74f),
                textAlign = align,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "Add a private note" as a pill that hugs its label until it is tapped, then the field across
 * the column (open from the start when a note was written before). The host saves it when the
 * screen closes.
 */
@Composable
private fun JournalFinishedNote(
    reviewState: JournalReviewState,
    fill: Color,
    centred: Boolean,
) {
    var open by rememberSaveable { mutableStateOf(reviewState.reviewText.isNotBlank()) }
    var focusOnOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    if (open) {
        AppTextField(
            value = reviewState.reviewText,
            onValueChange = { reviewState.reviewText = it },
            label = { Text(text = "Private note") },
            placeholder = { Text(text = "Only you can see this") },
            minLines = 3,
            shape = RoundedCornerShape(UnifiedReaderDefaultPresentationTokens.controlCornerRadius),
            modifier = Modifier
                .padding(top = JournalFinishedDefaults.noteGap)
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )
        LaunchedEffect(focusOnOpen) {
            if (focusOnOpen) {
                focusRequester.requestFocus()
                focusOnOpen = false
            }
        }
    } else {
        Box(
            modifier = Modifier
                .padding(top = JournalFinishedDefaults.noteGap)
                .fillMaxWidth(),
            contentAlignment = if (centred) Alignment.Center else Alignment.CenterStart,
        ) {
            AppButton(
                onClick = {
                    open = true
                    focusOnOpen = true
                },
                colors = AppButtonDefaults.filledColors(containerColor = fill, contentColor = LocalContentColor.current),
                contentPadding = JournalFinishedDefaults.noteButtonPadding,
                modifier = Modifier.height(JournalFinishedDefaults.noteButtonHeight),
            ) {
                Icon(
                    imageVector = AppIcons.Edit,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = JournalFinishedDefaults.iconGap)
                        .size(18.dp),
                )
                Text(text = "Add a private note", style = AppDialogCardDefaults.buttonTextStyle, maxLines = 1)
            }
        }
    }
}

/** The next item's cover and title on a strip of its own; Read next is with the other buttons. */
@Composable
private fun JournalFinishedNextStrip(
    next: JournalFinishedNext,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The strip sits a step away from the tint: deeper on dark tints, paler on light ones.
    val strip = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (isAppThemeDark) 0.3f else 0.55f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(JournalFinishedDefaults.nextCornerRadius))
            .background(strip)
            .padding(JournalFinishedDefaults.nextPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(JournalFinishedDefaults.nextCoverWidth)
                .aspectRatio(LibraryCoverAspectRatio)
                .clip(RoundedCornerShape(JournalFinishedDefaults.coverCornerRadius)),
        ) {
            coverContent(next.item, Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = next.label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                color = LocalContentColor.current.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = next.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Back to library and Read next across the column, half each; Back to library is tonal beside
 * Read next and the accent when it is the only way on.
 */
@Composable
private fun JournalFinishedActions(
    colors: AppCoverColors,
    tonal: Color,
    next: JournalFinishedNext?,
    onBackToLibrary: () -> Unit,
    // "Back to the list" when the book was opened from one.
    backLabel: String,
    modifier: Modifier = Modifier,
) {
    val accent = AppButtonDefaults.filledColors(containerColor = colors.accent, contentColor = colors.onAccent)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppButton(
            onClick = onBackToLibrary,
            colors = if (next != null) AppButtonDefaults.filledColors(containerColor = tonal, contentColor = colors.content) else accent,
            contentPadding = JournalFinishedDefaults.buttonPadding,
            modifier = Modifier
                .weight(1f)
                .height(JournalFinishedDefaults.buttonHeight),
        ) {
            Text(text = backLabel, style = AppDialogCardDefaults.buttonTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        next?.let { nextItem ->
            AppButton(
                onClick = nextItem.onRead,
                colors = accent,
                contentPadding = JournalFinishedDefaults.buttonPadding,
                modifier = Modifier
                    .weight(1f)
                    .height(JournalFinishedDefaults.buttonHeight),
            ) {
                Icon(
                    imageVector = AppIcons.MenuBook,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = JournalFinishedDefaults.iconGap)
                        .size(18.dp),
                )
                Text(text = "Read next", style = AppDialogCardDefaults.buttonTextStyle, maxLines = 1)
            }
        }
    }
}

private const val CoverRiseDelayMillis = 60L

object JournalFinishedDefaults {
    /** The cover beside the rest, on tablets. */
    val besideCardWidth: Dp = 640.dp

    /** Everything centred, on a window too narrow for [besideCardWidth]. */
    val cardWidth: Dp = 440.dp

    /** The band behind the cover on [besideCardWidth], and the cover in it. */
    val bandWidth: Dp = 232.dp
    val bandCoverWidth: Dp = 152.dp
    val bandCoverMargin: Dp = 36.dp

    val cardCoverWidth: Dp = 120.dp
    val sheetCoverWidth: Dp = 96.dp
    val coverCornerRadius: Dp = UnifiedReaderDefaultPresentationTokens.coverCornerRadius

    /** The cover rises this far into place as the screen opens. */
    val coverRise: Dp = 12.dp

    /** Under the sheet's handle, and between the cover, the heading and the stars when centred. */
    val sheetTop: Dp = 4.dp
    val coverGap: Dp = 20.dp

    /** × at the top end, and what the heading keeps clear of it. */
    val closeSize: Dp = 40.dp
    val closeMargin: Dp = 16.dp
    val closeClearance: Dp = 40.dp

    val noteGap: Dp = 16.dp
    val noteButtonHeight: Dp = 40.dp
    val noteButtonPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
    val iconGap: Dp = 8.dp

    val nextGap: Dp = 20.dp
    val nextPadding: Dp = 12.dp
    val nextCoverWidth: Dp = 48.dp
    val nextCornerRadius: Dp = UnifiedReaderDefaultPresentationTokens.cardCornerRadius

    val buttonHeight: Dp = 48.dp
    val buttonPadding: PaddingValues = PaddingValues(horizontal = 18.dp)
}
