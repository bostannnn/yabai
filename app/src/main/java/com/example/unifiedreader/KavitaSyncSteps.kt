package com.example.unifiedreader

import com.example.unifiedreader.ui.components.appCount

/**
 * What a Kavita sync's bar says at each step (design system, AppProgressBar: a sync's bar). Connecting
 * and the book list are one request each whose length can't be known, so the bar moves (a null
 * fraction). Then it fills by work it can count, most of it a request for each series' books, and each
 * step after the save moves it on, so it doesn't sit near the end while the uploads run.
 */
internal object KavitaSyncSteps {
    val connecting = KavitaSyncProgress(message = "Connecting", fraction = null)

    val bookList = KavitaSyncProgress(message = "Getting the book list", fraction = null)

    // A fast sync: the book list, the server's recent changes and what this device already has.
    val checkingChanges = KavitaSyncProgress(message = "Checking for changes", fraction = null)

    fun bookDetails(loaded: Int, total: Int) = KavitaSyncProgress(
        message = "Book details · ${appCount(loaded)} of ${appCount(total)} series",
        fraction = BOOK_DETAILS_SHARE * shareOf(loaded, total),
    )

    fun readingProgress(loaded: Int, total: Int) = KavitaSyncProgress(
        message = "Reading progress · ${appCount(loaded)} of ${appCount(total)} ${if (total == 1) "book" else "books"}",
        fraction = BOOK_DETAILS_SHARE + READING_PROGRESS_SHARE * shareOf(loaded, total),
    )

    // The shared catalog step reports its save at 0.92 and the progress it applies at 0.97.
    fun saving(catalogStepFraction: Float) = KavitaSyncProgress(
        message = "Saving the library",
        fraction = if (catalogStepFraction < 0.95f) 0.82f else 0.86f,
    )

    val uploadingProgress = KavitaSyncProgress(message = "Uploading reading progress", fraction = 0.88f)

    val readingHistory = KavitaSyncProgress(message = "Reading history", fraction = 0.92f)

    val uploadingJournal = KavitaSyncProgress(message = "Uploading journal changes", fraction = 0.96f)
}

private fun shareOf(
    loaded: Int,
    total: Int,
): Float = if (total <= 0) 0f else (loaded.toFloat() / total).coerceIn(0f, 1f)

private const val BOOK_DETAILS_SHARE = 0.78f
private const val READING_PROGRESS_SHARE = 0.02f
