package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.ceil

private const val MaxRating = 10
private const val MaxStars = 5

@Composable
fun JournalRatingEditor(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
) {
    val safeRating = selectedRating?.coerceIn(1, MaxRating)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = journalRatingLabel(safeRating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JournalRatingStars(
                selectedRating = safeRating,
                onRatingSelected = onRatingSelected,
                modifier = Modifier.weight(1f),
            )
            if (safeRating != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onRatingSelected(null) },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear rating",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalRatingStars(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 46.dp,
) {
    var touchWidthPx by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .height(52.dp)
            .onSizeChanged { size -> touchWidthPx = size.width }
            .semantics {
                contentDescription = "Rating, ${journalRatingLabel(selectedRating)}"
                customActions = journalRatingAccessibilityActions(onRatingSelected)
            }
            .pointerInput(onRatingSelected) {
                detectTapGestures { offset ->
                    ratingFromTapOffset(
                        offsetX = offset.x,
                        width = touchWidthPx,
                    )?.let(onRatingSelected)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(MaxStars) { index ->
                JournalRatingStar(
                    starIndex = index,
                    selectedRating = selectedRating,
                    starSize = starSize,
                )
            }
        }
    }
}

@Composable
private fun JournalRatingStar(
    starIndex: Int,
    selectedRating: Int?,
    starSize: Dp,
) {
    val starRating = selectedRating?.minus(starIndex * 2)?.coerceIn(0, 2) ?: 0
    Box(
        modifier = Modifier.size(starSize),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.StarBorder,
            contentDescription = null,
            modifier = Modifier.size(starSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        )
        if (starRating > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(if (starRating == 1) starSize / 2f else starSize)
                    .height(starSize)
                    .clipToBounds(),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(starSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal fun journalRatingLabel(rating: Int?): String =
    rating?.let { "Rated ${journalRatingValueLabel(it)}" } ?: "No rating"

internal fun journalRatingValueLabel(rating: Int): String {
    val value = rating.coerceIn(1, MaxRating) / 2f
    return if (value % 1f == 0f) {
        String.format(Locale.US, "%.1f", value)
    } else {
        value.toString()
    }
}

private fun journalRatingAccessibilityActions(
    onRatingSelected: (Int?) -> Unit,
): List<CustomAccessibilityAction> =
    (1..MaxRating).map { rating ->
        CustomAccessibilityAction(
            label = "Rate ${journalRatingValueLabel(rating)} stars",
            action = {
                onRatingSelected(rating)
                true
            },
        )
    }

internal fun ratingFromTapOffset(
    offsetX: Float,
    width: Int,
): Int? {
    if (width <= 0) return null
    return ceil((offsetX.coerceIn(0f, width.toFloat()) / width) * MaxRating)
        .toInt()
        .coerceIn(1, MaxRating)
}
