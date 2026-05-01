package com.example.unifiedreader.feature.library

import java.util.Locale

data class LibraryRatingBadgeState(
    val label: String,
    val contentDescription: String,
)

internal fun itemRatingBadgeState(
    rating: Int,
): LibraryRatingBadgeState = ratingBadgeState(rating)

fun ratingBadgeState(
    rating: Int,
): LibraryRatingBadgeState {
    val label = ratingValueLabel(rating)
    return LibraryRatingBadgeState(
        label = label,
        contentDescription = "Rated $label",
    )
}

internal fun seriesAverageRatingBadgeState(
    ratings: List<Int>,
): LibraryRatingBadgeState? {
    if (ratings.isEmpty()) return null
    val average = ratings
        .map { rating -> rating.coerceIn(MinRating, MaxRating) / 2.0 }
        .average()
    val label = String.format(Locale.US, "Avg %.1f", average)
    return LibraryRatingBadgeState(
        label = label,
        contentDescription = "Series average rating ${String.format(Locale.US, "%.1f", average)}",
    )
}

private fun ratingValueLabel(
    rating: Int,
): String = String.format(Locale.US, "%.1f", rating.coerceIn(MinRating, MaxRating) / 2f)

private const val MinRating = 1
private const val MaxRating = 10
