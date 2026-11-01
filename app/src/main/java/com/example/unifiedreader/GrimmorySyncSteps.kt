package com.example.unifiedreader

import com.example.unifiedreader.ui.components.appCount

/**
 * What a Grimmory sync's bar says at each step (design system, AppProgressBar: a sync's bar). Connecting and
 * the book list are one request each whose length can't be known, so the bar moves (a null fraction). From
 * the save on it fills by work it can count, most of it the reading history: a request for each book read.
 * The controller keeps the fill from going back when a step is retried on the other address.
 */
internal object GrimmorySyncSteps {
    val connecting = GrimmorySyncProgress(message = "Connecting", fraction = null)

    val bookList = GrimmorySyncProgress(message = "Getting the book list", fraction = null)

    // The shared catalog step reports its save at 0.92 and the progress it applies at 0.97.
    fun saving(catalogStepFraction: Float) = GrimmorySyncProgress(
        message = "Saving the library",
        fraction = if (catalogStepFraction < 0.95f) 0.02f else 0.06f,
    )

    val uploadingProgress = GrimmorySyncProgress(message = "Uploading reading progress", fraction = 0.08f)

    fun readingHistory(loaded: Int, total: Int) = GrimmorySyncProgress(
        message = if (total <= 0) {
            "Reading history"
        } else {
            "Reading history · ${appCount(loaded)} of ${appCount(total)} ${if (total == 1) "book" else "books"}"
        },
        fraction = READING_HISTORY_START +
            READING_HISTORY_SHARE * if (total <= 0) 0f else (loaded.toFloat() / total).coerceIn(0f, 1f),
    )

    val uploadingJournal = GrimmorySyncProgress(message = "Uploading journal changes", fraction = 0.96f)
}

private const val READING_HISTORY_START = 0.1f
private const val READING_HISTORY_SHARE = 0.85f
