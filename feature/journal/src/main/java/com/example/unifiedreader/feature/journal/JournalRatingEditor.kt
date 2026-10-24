package com.example.unifiedreader.feature.journal

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.theme.AppMotion
import java.util.Locale
import kotlin.math.floor
import kotlinx.coroutines.delay

private const val MaxRating = 10
private const val MaxStars = 5
private val RatingStarSlotSize = 46.dp
private val RatingStarGap = 6.dp
private val LeadingStarSlotSize = 44.dp
private val LeadingStarGap = 4.dp
private const val StarEntranceDelayMillis = 220L
private const val StarEntranceStaggerMillis = 50L

internal const val JournalRatingStarsTag = "journal-rating-stars"
internal const val JournalRatingClearTag = "journal-rating-clear"

/**
 * Where [JournalRatingEditor] puts things: the journal's stars centred under their label, or
 * the Finished screen's "Your rating" line over 40 dp stars at the start, in the content color.
 */
enum class JournalRatingLayout { Centered, Leading }

/**
 * Five stars on a 10-point scale: the left half of a star is a half. Tapping the current
 * rating clears it. With [animateIn] the stars pop in one after another when first shown.
 */
@Composable
fun JournalRatingEditor(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    layout: JournalRatingLayout = JournalRatingLayout.Centered,
    animateIn: Boolean = false,
) {
    val safeRating = selectedRating?.coerceIn(1, MaxRating)
    var previewRating by remember { mutableStateOf<Int?>(null) }
    val displayedRating = previewRating ?: safeRating
    if (layout == JournalRatingLayout.Leading) {
        JournalLeadingRatingEditor(
            displayedRating = displayedRating,
            selectedRating = safeRating,
            previewRating = previewRating,
            onPreviewRatingChanged = { previewRating = it },
            onRatingSelected = onRatingSelected,
            animateIn = animateIn,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = journalRatingFeedbackLabel(
                selectedRating = safeRating,
                previewRating = previewRating,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        JournalRatingControls(
            displayedRating = displayedRating,
            selectedRating = safeRating,
            previewRating = previewRating,
            onPreviewRatingChanged = { previewRating = it },
            onRatingSelected = onRatingSelected,
        )
    }
}

@Composable
private fun JournalLeadingRatingEditor(
    displayedRating: Int?,
    selectedRating: Int?,
    previewRating: Int?,
    onPreviewRatingChanged: (Int?) -> Unit,
    onRatingSelected: (Int?) -> Unit,
    animateIn: Boolean,
    modifier: Modifier,
) {
    val content = LocalContentColor.current
    val labelStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Your rating",
                style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = journalLeadingRatingHint(selectedRating = selectedRating, previewRating = previewRating),
                style = labelStyle.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                color = content.copy(alpha = 0.74f),
            )
        }
        JournalRatingStars(
            displayedRating = displayedRating,
            selectedRating = selectedRating,
            previewRating = previewRating,
            onPreviewRatingChanged = onPreviewRatingChanged,
            onRatingSelected = onRatingSelected,
            starSlotSize = LeadingStarSlotSize,
            starGap = LeadingStarGap,
            style = RatingStarStyle(empty = content.copy(alpha = 0.28f), emptyActive = content.copy(alpha = 0.4f)),
            animateIn = animateIn,
            // The slot's 2 dp inset, so the first star lines up with the text above it.
            modifier = Modifier.offset(x = (-2).dp),
        )
    }
}

/** The empty star colours: at rest, and once a star of the row is rated or being picked. */
private class RatingStarStyle(val empty: Color, val emptyActive: Color)

@Composable
private fun JournalRatingControls(
    displayedRating: Int?,
    selectedRating: Int?,
    previewRating: Int?,
    onPreviewRatingChanged: (Int?) -> Unit,
    onRatingSelected: (Int?) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        JournalRatingStars(
            displayedRating = displayedRating,
            selectedRating = selectedRating,
            previewRating = previewRating,
            onPreviewRatingChanged = onPreviewRatingChanged,
            onRatingSelected = onRatingSelected,
            starSlotSize = ratingStarSlotSizeFor(maxWidth),
        )
    }
}

internal fun ratingStarSlotSizeFor(maxWidth: Dp): Dp {
    val availableForStars = maxWidth.coerceAtLeast(0.dp)
    val availableForSlots = (
        availableForStars - (RatingStarGap.value * (MaxStars - 1)).dp
    ).coerceAtLeast(0.dp)
    val computedSlotSize = (availableForSlots.value / MaxStars).dp
    return computedSlotSize
        .coerceAtMost(RatingStarSlotSize)
        .coerceAtLeast(0.dp)
}

@Composable
private fun JournalRatingStars(
    displayedRating: Int?,
    selectedRating: Int?,
    previewRating: Int?,
    onPreviewRatingChanged: (Int?) -> Unit,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    starSlotSize: Dp = RatingStarSlotSize,
    starGap: Dp = RatingStarGap,
    style: RatingStarStyle? = null,
    animateIn: Boolean = false,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val starSlotWidthPx = with(density) { starSlotSize.toPx() }
    val starGapWidthPx = with(density) { starGap.toPx() }
    var lastFeedbackRating by remember { mutableStateOf<Int?>(null) }
    val rowWidth = ratingStarRowWidth(
        starSlotSize = starSlotSize,
        starGap = starGap,
    )

    fun updatePreview(offsetX: Float): Int? {
        val nextRating = ratingFromStarRowOffset(
            offsetX = offsetX,
            starSlotWidth = starSlotWidthPx,
            starGapWidth = starGapWidthPx,
        ) ?: return null
        onPreviewRatingChanged(nextRating)
        if (nextRating != lastFeedbackRating) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastFeedbackRating = nextRating
        }
        return nextRating
    }

    Row(
        modifier = modifier
            .width(rowWidth)
            .height(54.dp)
            .testTag(JournalRatingStarsTag)
            .semantics {
                contentDescription = journalRatingContentDescription(
                    displayedRating = displayedRating,
                    selectedRating = selectedRating,
                )
                customActions = journalRatingAccessibilityActions(
                    selectedRating = selectedRating,
                    onRatingSelected = onRatingSelected,
                )
                if (selectedRating != null && displayedRating == selectedRating) {
                    onClick(label = "Clear rating") {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRatingSelected(null)
                        true
                    }
                }
            }
            .pointerInput(onRatingSelected, selectedRating, starSlotWidthPx, starGapWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = down.position
                    var currentRating = updatePreview(down.position.x)
                    var movedPastTouchSlop = false
                    var shouldCommit = false
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                currentRating = null
                                break
                            }
                            currentRating = updatePreview(change.position.x) ?: currentRating
                            movedPastTouchSlop = movedPastTouchSlop ||
                                change.position.isPastTouchSlopFrom(
                                    origin = downPosition,
                                    touchSlop = viewConfiguration.touchSlop,
                                )
                            if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                                shouldCommit = true
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                            }
                        }
                    } finally {
                        if (shouldCommit && currentRating != null) {
                            val nextRating = ratingSelectionForRelease(
                                selectedRating = selectedRating,
                                resolvedRating = currentRating,
                                movedPastTouchSlop = movedPastTouchSlop,
                            )
                            if (nextRating == null) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onRatingSelected(nextRating)
                        }
                        onPreviewRatingChanged(null)
                        lastFeedbackRating = null
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(starGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(MaxStars) { index ->
            JournalRatingStar(
                starIndex = index,
                displayedRating = displayedRating,
                selectedRating = selectedRating,
                isPreviewing = previewRating != null,
                starSlotSize = starSlotSize,
                style = style,
                animateIn = animateIn,
            )
        }
    }
}

@Composable
private fun JournalRatingStar(
    starIndex: Int,
    displayedRating: Int?,
    selectedRating: Int?,
    isPreviewing: Boolean,
    starSlotSize: Dp,
    style: RatingStarStyle?,
    animateIn: Boolean,
) {
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    if (animateIn) {
        LaunchedEffect(Unit) {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) {
                appear.snapTo(1f)
                return@LaunchedEffect
            }
            delay(StarEntranceDelayMillis + starIndex * StarEntranceStaggerMillis)
            appear.animateTo(1f, AppMotion.spatialFast())
        }
    }
    val starRating = displayedRating?.minus(starIndex * 2)?.coerceIn(0, 2) ?: 0
    val selectedStarRating = selectedRating?.minus(starIndex * 2)?.coerceIn(0, 2) ?: 0
    val isActivePreview = isPreviewing && starRating > 0
    val starIconSize = (starSlotSize - 4.dp).coerceAtLeast(0.dp)
    val scale by animateFloatAsState(
        targetValue = if (isActivePreview) 1.08f else 1f,
        label = "rating-star-scale",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isActivePreview) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        label = "rating-star-background",
    )
    Box(
        modifier = Modifier
            .size(starSlotSize)
            .graphicsLayer {
                val shown = appear.value
                alpha = shown.coerceIn(0f, 1f)
                scaleX = StarEntranceScale + (1f - StarEntranceScale) * shown
                scaleY = scaleX
            }
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(starIconSize),
            tint = when {
                style == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (selectedStarRating > 0 || isActivePreview) 0.58f else 0.45f,
                )
                selectedStarRating > 0 || isActivePreview -> style.emptyActive
                else -> style.empty
            },
        )
        if (starRating > 0) {
            Icon(
                imageVector = if (starRating == 1) {
                    AppIcons.StarHalf
                } else {
                    AppIcons.Star
                },
                contentDescription = null,
                modifier = Modifier
                    .size(starIconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun ratingStarRowWidth(
    starSlotSize: Dp,
    starGap: Dp,
): Dp = ((starSlotSize.value * MaxStars) + (starGap.value * (MaxStars - 1))).dp

private const val StarEntranceScale = 0.6f

/** The Finished screen's hint after "Your rating": what a tap does, the pick, or the rating. */
internal fun journalLeadingRatingHint(
    selectedRating: Int?,
    previewRating: Int?,
): String = when {
    previewRating != null -> journalRatingValueLabel(previewRating)
    selectedRating != null -> "Rated ${journalRatingValueLabel(selectedRating)}"
    else -> "Tap to rate"
}

internal fun journalRatingLabel(rating: Int?): String =
    rating?.let { "Rated ${journalRatingValueLabel(it)}" } ?: "No rating"

private fun journalRatingFeedbackLabel(
    selectedRating: Int?,
    previewRating: Int?,
): String = previewRating?.let { "Selecting ${journalRatingValueLabel(it)}" }
    ?: journalRatingLabel(selectedRating)

internal fun journalRatingValueLabel(rating: Int): String {
    val value = rating.coerceIn(1, MaxRating) / 2f
    return if (value % 1f == 0f) {
        String.format(Locale.US, "%.1f", value)
    } else {
        value.toString()
    }
}

private fun journalRatingContentDescription(
    displayedRating: Int?,
    selectedRating: Int?,
): String {
    val ratingLabel = journalRatingLabel(displayedRating)
    return if (selectedRating != null && displayedRating == selectedRating) {
        "Rating, $ratingLabel. Tap again to clear"
    } else {
        "Rating, $ratingLabel"
    }
}

private fun journalRatingAccessibilityActions(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
): List<CustomAccessibilityAction> {
    val ratingActions = (1..MaxRating).map { rating ->
        CustomAccessibilityAction(
            label = "Rate ${journalRatingValueLabel(rating)} stars",
            action = {
                onRatingSelected(rating)
                true
            },
        )
    }
    if (selectedRating == null) return ratingActions
    return ratingActions + CustomAccessibilityAction(
        label = "Clear rating",
        action = {
            onRatingSelected(null)
            true
        },
    )
}

internal fun ratingSelectionForRelease(
    selectedRating: Int?,
    resolvedRating: Int,
    movedPastTouchSlop: Boolean,
): Int? = if (!movedPastTouchSlop && selectedRating == resolvedRating) {
    null
} else {
    resolvedRating
}

internal fun ratingFromStarRowOffset(
    offsetX: Float,
    starSlotWidth: Float,
    starGapWidth: Float,
    starCount: Int = MaxStars,
): Int? {
    if (starSlotWidth <= 0f || starGapWidth < 0f || starCount <= 0) return null
    val totalWidth = (starSlotWidth * starCount) + (starGapWidth * (starCount - 1))
    val clampedOffset = offsetX.coerceIn(0f, totalWidth)
    val starPitch = starSlotWidth + starGapWidth
    val starIndex = floor((clampedOffset + (starGapWidth / 2f)) / starPitch)
        .toInt()
        .coerceAtMost(starCount - 1)
        .coerceAtLeast(0)
    val starOffset = (clampedOffset - (starIndex * starPitch))
        .coerceIn(0f, starSlotWidth)
    val halfStep = if (starOffset < starSlotWidth / 2f) 1 else 2
    return ((starIndex * 2) + halfStep).coerceIn(1, starCount * 2)
}

private fun Offset.isPastTouchSlopFrom(
    origin: Offset,
    touchSlop: Float,
): Boolean = (this - origin).getDistance() > touchSlop
