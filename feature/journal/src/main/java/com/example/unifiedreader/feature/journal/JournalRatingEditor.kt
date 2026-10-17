package com.example.unifiedreader.feature.journal

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppIcons
import java.util.Locale
import kotlin.math.floor

private const val MaxRating = 10
private const val MaxStars = 5
private val RatingStarSlotSize = 46.dp
private val RatingStarGap = 6.dp

internal const val JournalRatingStarsTag = "journal-rating-stars"
internal const val JournalRatingClearTag = "journal-rating-clear"

@Composable
fun JournalRatingEditor(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
) {
    val safeRating = selectedRating?.coerceIn(1, MaxRating)
    var previewRating by remember { mutableStateOf<Int?>(null) }
    val displayedRating = previewRating ?: safeRating
    Column(
        modifier = Modifier.fillMaxWidth(),
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
) {
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
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppIcons.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(starIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (selectedStarRating > 0 || isActivePreview) 0.58f else 0.45f,
            ),
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
