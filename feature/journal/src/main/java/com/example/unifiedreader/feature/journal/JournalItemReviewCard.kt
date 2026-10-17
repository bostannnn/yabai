package com.example.unifiedreader.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppPanelDefaults
import com.example.unifiedreader.ui.components.AppTextField

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
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    AppPanel(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = AppPanelDefaults.outlinedContainerColor,
        border = AppPanelDefaults.outlinedBorder,
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
            AppTextField(
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
