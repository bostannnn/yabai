package com.example.unifiedreader.feature.reader.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem

@Composable
fun ReaderCompletionDialog(
    item: ReadableItem,
    itemTitle: String,
    itemSubtitle: String?,
    nextItem: ReadableItem?,
    nextItemTitle: String?,
    nextItemSubtitle: String?,
    initialRating: Int?,
    initialReviewText: String,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    onRatingChanged: (Int, String) -> Unit,
    onSaveReview: (Int?, String) -> Unit,
    onStartNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedRating by rememberSaveable(item.id, initialRating) {
        mutableStateOf(initialRating)
    }
    var reviewText by rememberSaveable(item.id, initialReviewText) {
        mutableStateOf(initialReviewText)
    }
    val saveReviewIfNeeded = {
        if (shouldSaveCompletionReview(
                initialRating = initialRating,
                initialReviewText = initialReviewText,
                rating = selectedRating,
                reviewText = reviewText,
            )
        ) {
            onSaveReview(selectedRating, reviewText)
        }
    }

    AlertDialog(
        onDismissRequest = {
            saveReviewIfNeeded()
            onDismiss()
        },
        title = { Text(text = "Finished") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ReaderCompletionItemHeader(
                    item = item,
                    title = itemTitle,
                    subtitle = itemSubtitle,
                    coverContent = coverContent,
                )
                ReaderCompletionRatingEditor(
                    selectedRating = selectedRating,
                    onRatingSelected = { rating ->
                        selectedRating = rating
                        onRatingChanged(rating, reviewText)
                    },
                )
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    label = { Text(text = "Private review") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nextItem != null) {
                    ReaderCompletionNextItemCard(
                        item = nextItem,
                        title = nextItemTitle ?: nextItem.title,
                        subtitle = nextItemSubtitle,
                        coverContent = coverContent,
                        onClick = {
                            saveReviewIfNeeded()
                            onStartNext()
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saveReviewIfNeeded()
                    onDismiss()
                },
            ) {
                Text(text = "Save review")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    saveReviewIfNeeded()
                    onDismiss()
                },
            ) {
                Text(text = "Close")
            }
        },
    )
}

private fun shouldSaveCompletionReview(
    initialRating: Int?,
    initialReviewText: String,
    rating: Int?,
    reviewText: String,
): Boolean =
    initialRating != null ||
        initialReviewText.isNotBlank() ||
        rating != null ||
        reviewText.isNotBlank()

@Composable
private fun ReaderCompletionItemHeader(
    item: ReadableItem,
    title: String,
    subtitle: String?,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        coverContent(
            item,
            Modifier
                .width(76.dp)
                .aspectRatio(0.68f),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "Finished",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReaderCompletionRatingEditor(
    selectedRating: Int?,
    onRatingSelected: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = selectedRating?.let { "$it / 5" } ?: "No rating",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (1..5).forEach { rating ->
                val isSelected = selectedRating != null && rating <= selectedRating
                IconButton(
                    onClick = { onRatingSelected(rating) },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "$rating star rating",
                        modifier = Modifier.size(30.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderCompletionNextItemCard(
    item: ReadableItem,
    title: String,
    subtitle: String?,
    coverContent: @Composable (ReadableItem, Modifier) -> Unit,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            coverContent(
                item,
                Modifier
                    .width(54.dp)
                    .aspectRatio(0.68f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Start next",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Spacer(modifier = Modifier.height(2.dp))
}
