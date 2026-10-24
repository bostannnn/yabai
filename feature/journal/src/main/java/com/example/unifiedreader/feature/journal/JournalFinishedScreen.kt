package com.example.unifiedreader.feature.journal

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.example.unifiedreader.ui.components.AppSheet
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.components.AppTonalIconButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.theme.AppCoverAccentTheme
import com.example.unifiedreader.ui.theme.AppCoverColors
import com.example.unifiedreader.ui.theme.AppMotion
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
 * to library. A 440 dp card over the last page; a sheet on phones. ×, a tap outside or Back calls
 * [onDismiss], which leaves the reader on its last page.
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
    onDismiss: () -> Unit,
    next: JournalFinishedNext? = null,
) {
    val content = @Composable { phone: Boolean, modifier: Modifier ->
        JournalFinishedContent(
            item = item,
            title = title,
            subtitle = subtitle,
            colors = colors,
            reviewState = reviewState,
            coverContent = coverContent,
            onRatingSelected = onRatingSelected,
            onBackToLibrary = onBackToLibrary,
            onDismiss = onDismiss,
            next = next,
            phone = phone,
            modifier = modifier,
        )
    }
    if (currentAppWindowWidth() == AppWindowWidth.Compact) {
        AppSheet(onDismissRequest = onDismiss, containerColor = colors.tintStart, skipPartiallyExpanded = true) {
            content(true, Modifier.coverTint(colors))
        }
    } else {
        AppDialogHost(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // The card fades in from a little smaller; the sheet on phones slides up by itself.
            val shown = remember { Animatable(0f) }
            LaunchedEffect(Unit) { shown.animateTo(1f, AppMotion.effects()) }
            val shape = RoundedCornerShape(AppDialogCardDefaults.cornerRadius)
            content(
                false,
                Modifier
                    .padding(AppDialogCardDefaults.windowMargin)
                    .widthIn(max = JournalFinishedDefaults.cardWidth)
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
    onDismiss: () -> Unit,
    next: JournalFinishedNext?,
    phone: Boolean,
    modifier: Modifier,
) {
    // Quiet fills on the tint: the text colour at a low alpha, a little stronger on dark tints.
    val dark = isAppThemeDark
    val quiet = colors.content.copy(alpha = if (dark) 0.10f else 0.08f)
    val tonal = colors.content.copy(alpha = if (dark) 0.12f else 0.10f)
    AppCoverAccentTheme(colors) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            Box(modifier = modifier) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            if (phone) {
                                PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp)
                            } else {
                                PaddingValues(JournalFinishedDefaults.padding)
                            },
                        ),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    JournalFinishedHero(
                        item = item,
                        title = title,
                        subtitle = subtitle,
                        accent = colors.accent,
                        coverWidth = if (phone) JournalFinishedDefaults.phoneCoverWidth else JournalFinishedDefaults.coverWidth,
                        coverContent = coverContent,
                        // Room for the × at the top end of the card.
                        modifier = Modifier.padding(end = if (phone) 0.dp else 40.dp),
                    )
                    JournalRatingEditor(
                        selectedRating = reviewState.selectedRating,
                        onRatingSelected = { rating ->
                            reviewState.selectedRating = rating
                            onRatingSelected(rating)
                        },
                        layout = JournalRatingLayout.Leading,
                        animateIn = true,
                    )
                    JournalFinishedNote(reviewState = reviewState, fill = quiet)
                    next?.let { nextItem ->
                        JournalFinishedNextStrip(
                            next = nextItem,
                            colors = colors,
                            coverContent = coverContent,
                            showButton = !phone,
                        )
                    }
                    JournalFinishedActions(
                        colors = colors,
                        tonal = tonal,
                        next = next,
                        phone = phone,
                        onBackToLibrary = onBackToLibrary,
                    )
                }
                if (!phone) {
                    AppTonalIconButton(
                        onClick = onDismiss,
                        colors = AppButtonDefaults.tonalIconColors(containerColor = quiet, contentColor = colors.content),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(40.dp),
                    ) {
                        Icon(imageVector = AppIcons.Close, contentDescription = "Close", modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

/** The cover rising into place beside the overline, the title and the subtitle. */
@Composable
private fun JournalFinishedHero(
    item: ReadableItem,
    title: String,
    subtitle: String?,
    accent: Color,
    coverWidth: Dp,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor != 0f) delay(CoverRiseDelayMillis)
        rise.animateTo(1f, AppMotion.spatial())
    }
    val coverShape = RoundedCornerShape(JournalFinishedDefaults.coverCornerRadius)
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Box(
            modifier = Modifier
                .width(coverWidth)
                .aspectRatio(LibraryCoverAspectRatio)
                .graphicsLayer {
                    translationY = (1f - rise.value) * JournalFinishedDefaults.coverRise.toPx()
                    alpha = rise.value.coerceIn(0f, 1f)
                }
                .shadow(elevation = 18.dp, shape = coverShape, clip = false)
                .clip(coverShape),
        ) {
            coverContent(item, Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp),
        ) {
            Text(
                text = "Finished".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.08.em,
                ),
                color = accent,
                maxLines = 1,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    color = LocalContentColor.current.copy(alpha = 0.74f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * "Add a private note" as a pill until it is tapped, then the field (open from the start when a
 * note was written before). The host saves it when the screen closes.
 */
@Composable
private fun JournalFinishedNote(
    reviewState: JournalReviewState,
    fill: Color,
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
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
        val secondary = LocalContentColor.current.copy(alpha = 0.74f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(CircleShape)
                .background(fill)
                .clickable {
                    open = true
                    focusOnOpen = true
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = AppIcons.Edit, contentDescription = null, tint = secondary, modifier = Modifier.size(20.dp))
            Text(
                text = "Add a private note",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                color = secondary,
            )
        }
    }
}

/** The next item's cover and title on a strip of its own, with Read next at its end on tablets. */
@Composable
private fun JournalFinishedNextStrip(
    next: JournalFinishedNext,
    colors: AppCoverColors,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    showButton: Boolean,
) {
    // The strip sits a step away from the tint: deeper on dark tints, paler on light ones.
    val strip = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (isAppThemeDark) 0.3f else 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(JournalFinishedDefaults.nextCornerRadius))
            .background(strip)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .width(JournalFinishedDefaults.nextCoverWidth)
                .aspectRatio(LibraryCoverAspectRatio)
                .clip(RoundedCornerShape(4.dp)),
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
        if (showButton) JournalFinishedReadNext(colors = colors, onClick = next.onRead)
    }
}

/**
 * Back to library: tonal beside a next item, filled when it is the only way on. Phones put Read
 * next beside it, half the width each.
 */
@Composable
private fun JournalFinishedActions(
    colors: AppCoverColors,
    tonal: Color,
    next: JournalFinishedNext?,
    phone: Boolean,
    onBackToLibrary: () -> Unit,
) {
    val backColors = if (next != null) {
        AppButtonDefaults.filledColors(containerColor = tonal, contentColor = colors.content)
    } else {
        AppButtonDefaults.filledColors(containerColor = colors.accent, contentColor = colors.onAccent)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
    ) {
        AppButton(
            onClick = onBackToLibrary,
            colors = backColors,
            contentPadding = JournalFinishedDefaults.buttonPadding,
            modifier = Modifier
                .height(AppDialogCardDefaults.buttonHeight)
                .then(if (phone) Modifier.weight(1f) else Modifier),
        ) {
            Text(text = "Back to library", style = AppDialogCardDefaults.buttonTextStyle)
        }
        if (phone && next != null) {
            JournalFinishedReadNext(colors = colors, onClick = next.onRead, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun JournalFinishedReadNext(
    colors: AppCoverColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppButton(
        onClick = onClick,
        colors = AppButtonDefaults.filledColors(containerColor = colors.accent, contentColor = colors.onAccent),
        contentPadding = JournalFinishedDefaults.buttonPadding,
        modifier = modifier.height(AppDialogCardDefaults.buttonHeight),
    ) {
        Icon(
            imageVector = AppIcons.MenuBook,
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp),
        )
        Text(text = "Read next", style = AppDialogCardDefaults.buttonTextStyle, maxLines = 1)
    }
}

private const val CoverRiseDelayMillis = 60L

object JournalFinishedDefaults {
    val cardWidth: Dp = 440.dp
    val padding: Dp = 24.dp
    val coverWidth: Dp = 112.dp
    val phoneCoverWidth: Dp = 88.dp
    val coverCornerRadius: Dp = 6.dp

    /** The cover rises this far into place as the screen opens. */
    val coverRise: Dp = 12.dp
    val nextCoverWidth: Dp = 56.dp
    val nextCornerRadius: Dp = 20.dp
    val buttonPadding: PaddingValues = PaddingValues(horizontal = 18.dp)
}
