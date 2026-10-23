package com.example.unifiedreader.feature.downloads

import com.example.unifiedreader.core.model.SourceType
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadsSheetModelTest {
    private val zone = ZoneOffset.UTC
    private val now = at(2026, 10, 23, 15, 0)

    @Test
    fun `the summary counts what runs, waits and failed`() {
        val snapshot = DownloadEntriesSnapshot(
            listOf(
                entry("a", DownloadEntryStatus.Running),
                entry("b", DownloadEntryStatus.Queued),
                entry("c", DownloadEntryStatus.PausedConnectivity),
                entry("d", DownloadEntryStatus.Failed),
                entry("e", DownloadEntryStatus.Completed),
            ),
        )

        assertEquals("1 downloading · 2 waiting · 1 couldn't download", downloadsSheetSummary(snapshot))
    }

    @Test
    fun `only finished downloads say nothing is downloading, and an empty sheet has no summary`() {
        assertEquals(
            "Nothing downloading",
            downloadsSheetSummary(DownloadEntriesSnapshot(listOf(entry("a", DownloadEntryStatus.Completed)))),
        )
        assertNull(downloadsSheetSummary(DownloadEntriesSnapshot(emptyList())))
    }

    @Test
    fun `stopping counts as downloading and a stopped download as finished`() {
        assertEquals(DownloadsSheetSection.Downloading, entry("a", DownloadEntryStatus.Canceling).sheetSection())
        assertEquals(DownloadsSheetSection.Finished, entry("a", DownloadEntryStatus.Canceled).sheetSection())
    }

    @Test
    fun `finished downloads group by day, newest first`() {
        val entries = listOf(
            entry("old", DownloadEntryStatus.Completed, finishedAt = at(2026, 10, 12, 9, 0)),
            entry("today-early", DownloadEntryStatus.Completed, finishedAt = at(2026, 10, 23, 9, 15)),
            entry("yesterday", DownloadEntryStatus.Canceled, finishedAt = at(2026, 10, 22, 21, 3)),
            entry("today-late", DownloadEntryStatus.Completed, finishedAt = at(2026, 10, 23, 10, 42)),
            entry("running", DownloadEntryStatus.Running),
        )

        val days = finishedDownloadsByDay(entries, now, zone)

        assertEquals(listOf("Today", "Yesterday", "Earlier"), days.map(DownloadsDayGroup::label))
        assertEquals(listOf("today-late", "today-early"), days[0].entries.map(DownloadEntry::id))
        assertEquals(listOf("old"), days[2].entries.map(DownloadEntry::id))
    }

    @Test
    fun `times read as a clock for two days, then as a date`() {
        assertEquals("10:42", downloadFinishedTimeLabel(at(2026, 10, 23, 10, 42), now, zone))
        assertEquals("21:03", downloadFinishedTimeLabel(at(2026, 10, 22, 21, 3), now, zone))
        assertEquals("18 Oct", downloadFinishedTimeLabel(at(2026, 10, 18, 8, 0), now, zone))
        assertEquals("30 Dec 2025", downloadFinishedTimeLabel(at(2025, 12, 30, 8, 0), now, zone))
    }

    @Test
    fun `several files count by file, one file has no count`() {
        val several = entry("a", DownloadEntryStatus.Running, files = 24, done = 12)
        val one = entry("b", DownloadEntryStatus.Running, files = 1)

        assertEquals("12 of 24 files", several.fileProgressLabel())
        assertEquals(0.5f, several.fileProgressFraction())
        assertNull(one.fileProgressLabel())
        assertNull(one.fileProgressFraction())
    }

    @Test
    fun `the running line names the file, or says it is stopping`() {
        assertEquals(
            "Downloading Neon Ronin #13",
            entry("a", DownloadEntryStatus.Running).copy(currentItemTitle = "Neon Ronin #13").activityLabel(),
        )
        assertEquals("Downloading", entry("a", DownloadEntryStatus.Running).activityLabel())
        assertEquals("Stopping after this file", entry("a", DownloadEntryStatus.Canceling).activityLabel())
    }

    @Test
    fun `a download held for its server says which`() {
        assertEquals("Waiting for Kavita", entry("a", DownloadEntryStatus.PausedConnectivity).waitingLabel(null))
        assertEquals("8 files", entry("a", DownloadEntryStatus.Queued, files = 8).waitingLabel(null))
    }

    @Test
    fun `a failure says how far it got and what went wrong`() {
        val failed = entry("a", DownloadEntryStatus.Failed, files = 4, done = 2).copy(errorMessage = "HTTP 500.")

        assertEquals("Got 2 of 4 files. HTTP 500", failed.failureLabel())
        assertEquals("The download stopped", entry("b", DownloadEntryStatus.Failed).failureLabel())
    }

    @Test
    fun `a finished row says what it is and when, a stopped one how far it got`() {
        val done = entry("a", DownloadEntryStatus.Completed, files = 3, done = 3, finishedAt = at(2026, 10, 23, 10, 42))
        val stopped = entry("b", DownloadEntryStatus.Canceled, files = 24, done = 6, finishedAt = at(2026, 10, 18, 8, 0))

        assertEquals("3 files · 10:42", done.finishedLabel(null, now, zone))
        assertEquals("Stopped after 6 of 24 files · 18 Oct", stopped.finishedLabel(null, now, zone))
    }

    @Test
    fun `only a finished single book opens straight away`() {
        assertEquals(true, entry("a", DownloadEntryStatus.Completed, files = 1, done = 1).opensDirectly)
        assertEquals(false, entry("b", DownloadEntryStatus.Completed, files = 3, done = 3).opensDirectly)
        assertEquals(false, entry("c", DownloadEntryStatus.Canceled, files = 1).opensDirectly)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).toInstant(zone).toEpochMilli()

    private fun entry(
        id: String,
        status: DownloadEntryStatus,
        files: Int = 1,
        done: Int = 0,
        finishedAt: Long = now,
    ): DownloadEntry {
        val items = (1..files).map { index ->
            QueuedDownloadItem(
                id = "$id-$index",
                sourceType = SourceType.KAVITA,
                libraryId = "library",
                remoteId = "$id-$index",
                remoteFileId = null,
                remoteFileType = null,
                title = "$id $index",
                formatName = "CBZ",
                pageCount = null,
            )
        }
        return DownloadEntry(
            id = id,
            selectedItemId = id,
            selectedItemTitle = id,
            sourceType = SourceType.KAVITA,
            actionItems = items,
            trackedItemIds = items.mapTo(mutableSetOf(), QueuedDownloadItem::id),
            completedItemIds = items.take(done).mapTo(mutableSetOf(), QueuedDownloadItem::id),
            status = status,
            createdAtEpochMillis = finishedAt,
            updatedAtEpochMillis = finishedAt,
            completedAtEpochMillis = finishedAt.takeIf { status == DownloadEntryStatus.Completed },
        )
    }
}
