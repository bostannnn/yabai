package com.example.unifiedreader.feature.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

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
