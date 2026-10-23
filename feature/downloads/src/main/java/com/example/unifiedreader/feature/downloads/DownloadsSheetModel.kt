package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.core.model.libraryPrimaryText
import com.example.unifiedreader.core.model.librarySecondaryText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The sheet's sections, top to bottom. */
internal enum class DownloadsSheetSection(val title: String) {
    Downloading("Downloading"),
    Waiting("Waiting"),
    Failed("Couldn't download"),
    Finished("Finished"),
}

internal fun DownloadEntry.sheetSection(): DownloadsSheetSection = when (status) {
    DownloadEntryStatus.Running,
    DownloadEntryStatus.Canceling -> DownloadsSheetSection.Downloading
    DownloadEntryStatus.Queued,
    DownloadEntryStatus.PausedConnectivity -> DownloadsSheetSection.Waiting
    DownloadEntryStatus.Failed -> DownloadsSheetSection.Failed
    DownloadEntryStatus.Completed,
    DownloadEntryStatus.Canceled -> DownloadsSheetSection.Finished
}

/** The line under the sheet's title: "1 downloading · 2 waiting". Null while there is nothing. */
internal fun downloadsSheetSummary(snapshot: DownloadEntriesSnapshot): String? {
    if (snapshot.entries.isEmpty()) return null
    val counts = snapshot.entries.groupingBy(DownloadEntry::sheetSection).eachCount()
    val parts = listOfNotNull(
        counts[DownloadsSheetSection.Downloading]?.let { count -> "$count downloading" },
        counts[DownloadsSheetSection.Waiting]?.let { count -> "$count waiting" },
        counts[DownloadsSheetSection.Failed]?.let { count -> "$count couldn't download" },
    )
    return parts.ifEmpty { listOf("Nothing downloading") }.joinToString(" · ")
}

/** Finished downloads under one day heading, newest first. */
internal data class DownloadsDayGroup(
    val label: String,
    val entries: List<DownloadEntry>,
)

internal val DownloadEntry.finishedAtEpochMillis: Long
    get() = completedAtEpochMillis ?: updatedAtEpochMillis

/** Finished (and stopped) downloads under Today, Yesterday and Earlier, newest first. */
internal fun finishedDownloadsByDay(
    entries: List<DownloadEntry>,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<DownloadsDayGroup> {
    val today = localDateOf(nowEpochMillis, zone)
    return entries
        .filter { entry -> entry.sheetSection() == DownloadsSheetSection.Finished }
        .sortedByDescending { entry -> entry.finishedAtEpochMillis }
        .groupBy { entry ->
            when (localDateOf(entry.finishedAtEpochMillis, zone)) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> "Earlier"
            }
        }
        .map { (label, group) -> DownloadsDayGroup(label, group) }
}

/** "10:42" for today and yesterday (the heading says which), "18 Oct" before, "18 Oct 2025" in another year. */
internal fun downloadFinishedTimeLabel(
    epochMillis: Long,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val then = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = localDateOf(nowEpochMillis, zone)
    val pattern = when {
        !then.toLocalDate().isBefore(today.minusDays(1)) -> "HH:mm"
        then.year == today.year -> "d MMM"
        else -> "d MMM yyyy"
    }
    return then.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/** "24 files" for a download of several files; null for one. */
internal fun DownloadEntry.fileCountLabel(): String? =
    totalItemCount.takeIf { count -> count > 1 }?.let { count -> "$count files" }

/** "12 of 24 files" while several files download; null for one file, whose bar moves on its own. */
internal fun DownloadEntry.fileProgressLabel(): String? =
    if (totalItemCount > 1) "${completedItemCount.coerceAtMost(totalItemCount)} of $totalItemCount files" else null

/** How far a download of several files has got; null for one file. */
internal fun DownloadEntry.fileProgressFraction(): Float? =
    if (totalItemCount > 1) completedItemCount.coerceAtMost(totalItemCount).toFloat() / totalItemCount else null

/** The book's second line (an author, a series), when it says more than its title. */
internal fun downloadItemDetail(item: ReadableItem?): String? =
    item?.librarySecondaryText()
        ?.takeIf { detail -> detail.isNotBlank() && detail != item.libraryPrimaryText() && detail != item.title }

/** The running download's line about now: "Downloading Neon Ronin #13", or "Stopping after this file". */
internal fun DownloadEntry.activityLabel(): String = when (status) {
    DownloadEntryStatus.Canceling -> "Stopping after this file"
    else -> currentItemTitle?.takeIf(String::isNotBlank)?.let { title -> "Downloading $title" } ?: "Downloading"
}

/** A waiting row's line: what it is, or which server it waits for. */
internal fun DownloadEntry.waitingLabel(item: ReadableItem?): String = when (status) {
    DownloadEntryStatus.PausedConnectivity -> "Waiting for ${sourceType.displayName()}"
    else -> downloadItemDetail(item) ?: fileCountLabel() ?: "Waiting"
}

/** A failed row's line: how far it got and what went wrong. */
internal fun DownloadEntry.failureLabel(): String {
    val got = if (totalItemCount > 1 && completedItemCount > 0) {
        "Got ${completedItemCount.coerceAtMost(totalItemCount)} of $totalItemCount files"
    } else {
        null
    }
    val reason = errorMessage?.trim()?.takeIf(String::isNotEmpty)?.removeSuffix(".") ?: "The download stopped"
    return listOfNotNull(got, reason).joinToString(". ")
}

/** A finished row's line: what it is (or how far a stopped one got) and when. */
internal fun DownloadEntry.finishedLabel(
    item: ReadableItem?,
    nowEpochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val what = when (status) {
        DownloadEntryStatus.Canceled -> if (totalItemCount > 1 && completedItemCount > 0) {
            "Stopped after ${completedItemCount.coerceAtMost(totalItemCount)} of $totalItemCount files"
        } else {
            "Stopped"
        }
        else -> downloadItemDetail(item) ?: fileCountLabel()
    }
    return listOfNotNull(what, downloadFinishedTimeLabel(finishedAtEpochMillis, nowEpochMillis, zone)).joinToString(" · ")
}

/** A finished download of one book gets a Read button; one of several files opens its page. */
internal val DownloadEntry.opensDirectly: Boolean
    get() = status == DownloadEntryStatus.Completed && totalItemCount == 1

// LocalDate.ofInstant is newer than the app's minimum API level.
private fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
