package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant

@Stable
class JournalReviewState(
    initialRating: Int?,
    initialReviewText: String,
) {
    var selectedRating by mutableStateOf(initialRating)
    var reviewText by mutableStateOf(initialReviewText)
}

@Composable
fun rememberJournalReviewState(
    itemId: String,
    initialRating: Int?,
    initialReviewText: String,
): JournalReviewState = rememberSaveable(
    itemId,
    initialRating,
    initialReviewText,
    saver = listSaver(
        save = { state ->
            listOf(state.selectedRating, state.reviewText)
        },
        restore = { restored ->
            JournalReviewState(
                initialRating = restored.getOrNull(0) as? Int,
                initialReviewText = restored.getOrNull(1) as? String ?: "",
            )
        },
    ),
) {
    JournalReviewState(
        initialRating = initialRating,
        initialReviewText = initialReviewText,
    )
}

@Composable
fun JournalItemReviewCard(
    item: JournalItemRef,
    reviewState: JournalReviewState,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    activityText: String?,
    onRatingSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JournalItemReviewHeader(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                activityText = activityText,
            )
            JournalRatingEditor(
                selectedRating = reviewState.selectedRating,
                onRatingSelected = { rating ->
                    reviewState.selectedRating = rating
                    onRatingSelected(rating)
                },
            )
            OutlinedTextField(
                value = reviewState.reviewText,
                onValueChange = { reviewState.reviewText = it },
                label = { Text(text = "Private review") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
private fun JournalItemReviewHeader(
    item: JournalItemRef,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    activityText: String?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        JournalCoverArt(
            item = item,
            coverImageUrlProvider = coverImageUrlProvider,
            onCoverVisible = onCoverVisible,
            modifier = Modifier
                .width(72.dp)
                .aspectRatio(0.68f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            item.displaySubtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${item.sourceType.displayName()} · ${item.format.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            activityText?.takeIf(String::isNotBlank)?.let { activity ->
                Text(
                    text = activity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun JournalRatingEditor(
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = selectedRating?.let { "$it / 10" } ?: "No rating",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(1..5, 6..10).forEach { rowRatings ->
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowRatings.forEach { rating ->
                        JournalRatingButton(
                            rating = rating,
                            isSelected = selectedRating != null && rating <= selectedRating,
                            onClick = { onRatingSelected(rating) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalRatingButton(
    rating: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
    ) {
        Text(
            text = rating.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
            },
        )
    }
}

fun shouldSaveJournalReview(
    initialRating: Int?,
    initialReviewText: String,
    rating: Int?,
    reviewText: String,
): Boolean =
    initialRating != null ||
        initialReviewText.isNotBlank() ||
        rating != null ||
        reviewText.isNotBlank()
