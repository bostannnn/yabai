package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.ReadableItem

/** The issue a series' reading card offers: the one being read, or the next one to start. */
data class LibrarySeriesNext(
    val item: ReadableItem,
    val inProgress: Boolean,
)

/**
 * The issue to read next in a series whose [issues] are in reading order: the most recently read
 * issue that is started and not finished; otherwise the first issue not yet started after the
 * last finished one. Null when every issue is finished.
 */
fun librarySeriesNextIssue(
    issues: List<ReadableItem>,
    progressByItemId: Map<String, ItemProgressSummary>,
): LibrarySeriesNext? {
    issues
        .filter { issue -> progressByItemId[issue.id]?.let { it.hasStarted && !it.isComplete } == true }
        .maxByOrNull { issue -> progressByItemId.getValue(issue.id).lastReadAtEpochMillis }
        ?.let { reading -> return LibrarySeriesNext(reading, inProgress = true) }
    val lastFinished = issues.indexOfLast { issue -> progressByItemId[issue.id]?.isComplete == true }
    return issues
        .drop(lastFinished + 1)
        .firstOrNull { issue -> progressByItemId[issue.id]?.isComplete != true }
        ?.let { next -> LibrarySeriesNext(next, inProgress = false) }
}
