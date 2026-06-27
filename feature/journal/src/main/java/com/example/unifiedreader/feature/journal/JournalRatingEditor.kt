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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.floor

private const val MaxRating = 10
private const val MaxStars = 5
private val RatingStarSlotSize = 46.dp
private val MinRatingStarSlotSize = 34.dp
private val RatingStarGap = 6.dp
private val RatingClearButtonSize = 48.dp
private val RatingClearButtonGap = 4.dp

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
    val hapticFeedback = LocalHapticFeedback.current
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
            onClearRating = {
                previewRating = null
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onRatingSelected(null)
            },
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
    onClearRating: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val fitsInline = ratingControlsFitInline(maxWidth)
        val starSlotSize = ratingStarSlotSizeFor(
            maxWidth = maxWidth,
            includeClearButton = fitsInline,
        )
        val stars: @Composable () -> Unit = {
            JournalRatingStars(
                displayedRating = displayedRating,
                selectedRating = selectedRating,
                previewRating = previewRating,
                onPreviewRatingChanged = onPreviewRatingChanged,
                onRatingSelected = onRatingSelected,
                starSlotSize = starSlotSize,
            )
        }
        val clearButton: @Composable () -> Unit = {
            IconButton(
                onClick = onClearRating,
                enabled = selectedRating != null,
                modifier = Modifier
                    .size(RatingClearButtonSize)
                    .testTag(JournalRatingClearTag),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear rating",
                    tint = if (selectedRating != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        }

        if (fitsInline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = RatingClearButtonGap,
                    alignment = Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                stars()
                clearButton()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                stars()
                clearButton()
            }
        }
    }
}

internal fun ratingControlsFitInline(maxWidth: Dp): Boolean =
    ratingStarRowWidth(
        starSlotSize = MinRatingStarSlotSize,
        starGap = RatingStarGap,
    ) + RatingClearButtonGap + RatingClearButtonSize <= maxWidth

internal fun ratingStarSlotSizeFor(
    maxWidth: Dp,
    includeClearButton: Boolean,
): Dp {
    val reservedWidth = if (includeClearButton) {
        RatingClearButtonSize + RatingClearButtonGap
    } else {
        0.dp
    }
    val availableForStars = (maxWidth - reservedWidth).coerceAtLeast(0.dp)
    val availableForSlots = (
        availableForStars - (RatingStarGap.value * (MaxStars - 1)).dp
    ).coerceAtLeast(0.dp)
    val computedSlotSize = (availableForSlots.value / MaxStars).dp
    return if (includeClearButton) {
        computedSlotSize.coerceIn(MinRatingStarSlotSize, RatingStarSlotSize)
    } else {
        computedSlotSize
            .coerceAtMost(RatingStarSlotSize)
            .coerceAtLeast(0.dp)
    }
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
                contentDescription = "Rating, ${journalRatingLabel(displayedRating)}"
                customActions = journalRatingAccessibilityActions(
                    selectedRating = selectedRating,
                    onRatingSelected = onRatingSelected,
                )
            }
            .pointerInput(onRatingSelected, starSlotWidthPx, starGapWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var currentRating = updatePreview(down.position.x)
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
                            if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                                shouldCommit = true
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                ratingFromStarRowOffset(
                                    offsetX = change.position.x,
                                    starSlotWidth = starSlotWidthPx,
                                    starGapWidth = starGapWidthPx,
                                )?.let {
                                    currentRating = updatePreview(change.position.x)
                                }
                                change.consume()
                            }
                        }
                    } finally {
                        if (shouldCommit) {
                            currentRating?.let(onRatingSelected)
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
            imageVector = Icons.Default.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(starIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (selectedStarRating > 0 || isActivePreview) 0.58f else 0.45f,
            ),
        )
        if (starRating > 0) {
            Icon(
                imageVector = if (starRating == 1) {
                    Icons.AutoMirrored.Filled.StarHalf
                } else {
                    Icons.Default.Star
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
    val starIndex = floor(clampedOffset / starPitch)
        .toInt()
        .coerceAtMost(starCount - 1)
    val starOffset = clampedOffset - (starIndex * starPitch)
    if (starOffset > starSlotWidth) return null
    val halfStep = if (starOffset < starSlotWidth / 2f) 1 else 2
    return ((starIndex * 2) + halfStep).coerceIn(1, starCount * 2)
}
