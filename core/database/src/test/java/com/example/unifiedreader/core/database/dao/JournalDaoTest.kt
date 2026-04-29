package com.example.unifiedreader.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.ItemJournalEntryEntity
import com.example.unifiedreader.core.database.entity.JournalReadingEventEntity
import com.example.unifiedreader.core.database.entity.JournalReadingSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JournalDaoTest {
    @Test
    fun `replace clean source journal cache preserves dirty local entry conflicts`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(journalEntry(itemId = "dirty-item", reviewText = "local", syncState = "DIRTY"))

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = listOf(
                    journalEntry(itemId = "dirty-item", reviewText = "remote", syncState = "CLEAN"),
                    journalEntry(itemId = "clean-item", reviewText = "remote clean", syncState = "CLEAN"),
                ),
            )

            assertEquals("local", dao.getJournalEntry("dirty-item")?.reviewText)
            assertEquals("DIRTY", dao.getJournalEntry("dirty-item")?.syncState)
            assertEquals("remote clean", dao.getJournalEntry("clean-item")?.reviewText)
            assertEquals("CLEAN", dao.getJournalEntry("clean-item")?.syncState)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can preserve existing clean review fields`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(
                journalEntry(
                    itemId = "clean-item",
                    reviewText = "cached review",
                    syncState = "CLEAN",
                    remoteReviewNoteId = "note-12",
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = listOf(
                    journalEntry(itemId = "clean-item", reviewText = "", syncState = "CLEAN"),
                ),
                preserveReviewFieldsWhenIncomingBlank = true,
            )

            assertEquals("cached review", dao.getJournalEntry("clean-item")?.reviewText)
            assertEquals("note-12", dao.getJournalEntry("clean-item")?.remoteReviewNoteId)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can preserve review only clean rows`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(
                journalEntry(
                    itemId = "review-only-item",
                    reviewText = "cached review",
                    syncState = "CLEAN",
                    remoteReviewNoteId = "note-12",
                    remoteUpdatedAtEpochMillis = 999L,
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                preserveReviewFieldsWhenIncomingBlank = true,
            )

            assertEquals("cached review", dao.getJournalEntry("review-only-item")?.reviewText)
            assertEquals("note-12", dao.getJournalEntry("review-only-item")?.remoteReviewNoteId)
            assertEquals(null, dao.getJournalEntry("review-only-item")?.rating)
            assertEquals(100L, dao.getJournalEntry("review-only-item")?.updatedAtEpochMillis)
            assertEquals(null, dao.getJournalEntry("review-only-item")?.remoteUpdatedAtEpochMillis)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can preserve review only rating when requested`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(
                journalEntry(
                    itemId = "review-only-item",
                    reviewText = "cached review",
                    syncState = "CLEAN",
                    remoteReviewNoteId = "note-12",
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                preserveReviewFieldsWhenIncomingBlank = true,
                preserveRatingWhenIncomingBlank = true,
            )

            assertEquals("cached review", dao.getJournalEntry("review-only-item")?.reviewText)
            assertEquals(7, dao.getJournalEntry("review-only-item")?.rating)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can preserve existing clean events`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingEvent(journalEvent(id = "cached-event", itemId = "item-1", occurredAt = 100L))

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = listOf(journalEvent(id = "incoming-event", itemId = "item-2", occurredAt = 200L)),
                entries = emptyList(),
                preserveReadingEventsWhenIncomingIncomplete = true,
            )

            val eventIds = dao.getReadingEvents().map(JournalReadingEventEntity::id).toSet()
            assertEquals(setOf("cached-event", "incoming-event"), eventIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can replace clean events for scoped items`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingEvent(journalEvent(id = "preserved-event", itemId = "item-1", occurredAt = 100L))
            dao.insertReadingEvent(journalEvent(id = "stale-event", itemId = "item-2", occurredAt = 150L))

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = listOf(journalEvent(id = "fresh-event", itemId = "item-2", occurredAt = 200L)),
                entries = emptyList(),
                preserveReadingEventsWhenIncomingIncomplete = true,
                readingEventReplacementItemIds = setOf("item-2"),
            )

            val eventIds = dao.getReadingEvents().map(JournalReadingEventEntity::id).toSet()
            assertEquals(setOf("preserved-event", "fresh-event"), eventIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can replace clean journal entries for scoped items`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(journalEntry(itemId = "preserved-item", reviewText = "", syncState = "CLEAN"))
            dao.insertJournalEntry(journalEntry(itemId = "stale-item", reviewText = "", syncState = "CLEAN"))

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = listOf(journalEntry(itemId = "stale-item", reviewText = "fresh", syncState = "CLEAN")),
                journalEntryReplacementItemIds = setOf("stale-item"),
            )

            assertEquals(7, dao.getJournalEntry("preserved-item")?.rating)
            assertEquals("fresh", dao.getJournalEntry("stale-item")?.reviewText)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache can preserve missing rating only entries`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(journalEntry(itemId = "rating-only-item", reviewText = "", syncState = "CLEAN"))
            dao.insertJournalEntry(
                journalEntry(
                    itemId = "review-backed-item",
                    reviewText = "deleted remotely",
                    syncState = "CLEAN",
                    remoteReviewNoteId = "review-1",
                ),
            )
            dao.insertJournalEntry(journalEntry(itemId = "known-empty-item", reviewText = "", syncState = "CLEAN"))

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                preserveRatingOnlyEntriesWhenIncomingMissing = true,
                ratingOnlyPreservationExcludedItemIds = setOf("known-empty-item"),
            )

            assertEquals(7, dao.getJournalEntry("rating-only-item")?.rating)
            assertEquals("", dao.getJournalEntry("review-backed-item")?.reviewText)
            assertEquals(7, dao.getJournalEntry("review-backed-item")?.rating)
            assertEquals(null, dao.getJournalEntry("review-backed-item")?.remoteReviewNoteId)
            assertEquals(null, dao.getJournalEntry("known-empty-item"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache preserves cached review with rating before rating only rows`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertJournalEntry(
                journalEntry(
                    itemId = "review-with-rating",
                    reviewText = "cached review",
                    syncState = "CLEAN",
                    remoteReviewNoteId = "review-1",
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                preserveReviewFieldsWhenIncomingBlank = true,
                preserveRatingWhenIncomingBlank = true,
                preserveRatingOnlyEntriesWhenIncomingMissing = true,
            )

            assertEquals("cached review", dao.getJournalEntry("review-with-rating")?.reviewText)
            assertEquals(7, dao.getJournalEntry("review-with-rating")?.rating)
            assertEquals("review-1", dao.getJournalEntry("review-with-rating")?.remoteReviewNoteId)
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache replaces clean sessions and preserves dirty sessions`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingSession(journalSession(id = "dirty-session", itemId = "dirty-item", syncState = "DIRTY"))
            dao.insertReadingSession(journalSession(id = "old-clean-session", itemId = "clean-item", syncState = "CLEAN"))

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                sessions = listOf(journalSession(id = "new-clean-session", itemId = "clean-item", syncState = "CLEAN")),
            )

            assertEquals(
                listOf("dirty-session"),
                dao.getReadingSessionsForItem("dirty-item").map(JournalReadingSessionEntity::id),
            )
            assertEquals(
                listOf("new-clean-session"),
                dao.getReadingSessionsForItem("clean-item").map(JournalReadingSessionEntity::id),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache preserves non-overlapping local clean sessions`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingSession(
                journalSession(
                    id = "local:item-1:100:200",
                    itemId = "item-1",
                    syncState = "CLEAN",
                    startedAt = 100L,
                    endedAt = 200L,
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                sessions = listOf(
                    journalSession(
                        id = "remote-session",
                        itemId = "item-1",
                        syncState = "CLEAN",
                        startedAt = 300L,
                        endedAt = 400L,
                        remoteSessionId = "remote-1",
                    ),
                ),
            )

            assertEquals(
                setOf("local:item-1:100:200", "remote-session"),
                dao.getReadingSessionsForItem("item-1").map(JournalReadingSessionEntity::id).toSet(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache replaces overlapping local clean sessions`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingSession(
                journalSession(
                    id = "local:item-1:100:200",
                    itemId = "item-1",
                    syncState = "CLEAN",
                    startedAt = 100L,
                    endedAt = 200L,
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                sessions = listOf(
                    journalSession(
                        id = "remote-session",
                        itemId = "item-1",
                        syncState = "CLEAN",
                        startedAt = 150L,
                        endedAt = 250L,
                        remoteSessionId = "remote-1",
                    ),
                ),
            )

            assertEquals(
                listOf("remote-session"),
                dao.getReadingSessionsForItem("item-1").map(JournalReadingSessionEntity::id),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache replaces overlapping legacy dirty local sessions`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingSession(
                journalSession(
                    id = "local:item-1:100:200",
                    itemId = "item-1",
                    syncState = "DIRTY",
                    startedAt = 100L,
                    endedAt = 200L,
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                sessions = listOf(
                    journalSession(
                        id = "remote-session",
                        itemId = "item-1",
                        syncState = "CLEAN",
                        startedAt = 150L,
                        endedAt = 250L,
                        remoteSessionId = "remote-1",
                    ),
                ),
            )

            assertEquals(
                listOf("remote-session"),
                dao.getReadingSessionsForItem("item-1").map(JournalReadingSessionEntity::id),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache preserves non-overlapping dirty local sessions as dirty`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingSession(
                journalSession(
                    id = "local:item-1:100:200",
                    itemId = "item-1",
                    syncState = "DIRTY",
                    startedAt = 100L,
                    endedAt = 200L,
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                sessions = listOf(
                    journalSession(
                        id = "remote-session",
                        itemId = "item-1",
                        syncState = "CLEAN",
                        startedAt = 300L,
                        endedAt = 400L,
                        remoteSessionId = "remote-1",
                    ),
                ),
            )

            val sessions = dao.getReadingSessionsForItem("item-1")
            assertEquals(
                setOf("local:item-1:100:200", "remote-session"),
                sessions.map(JournalReadingSessionEntity::id).toSet(),
            )
            assertEquals(
                mapOf(
                    "local:item-1:100:200" to "DIRTY",
                    "remote-session" to "CLEAN",
                ),
                sessions.associate { session -> session.id to session.syncState },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun `replace clean source journal cache replaces overlapping failed local sessions`() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UnifiedReaderDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()

        try {
            val dao = database.journalDao()
            dao.insertReadingSession(
                journalSession(
                    id = "local:item-1:100:200",
                    itemId = "item-1",
                    syncState = "FAILED",
                    startedAt = 100L,
                    endedAt = 200L,
                ),
            )

            dao.replaceCleanSourceJournalCache(
                sourceType = "KAVITA",
                events = emptyList(),
                entries = emptyList(),
                sessions = listOf(
                    journalSession(
                        id = "remote-session",
                        itemId = "item-1",
                        syncState = "CLEAN",
                        startedAt = 150L,
                        endedAt = 250L,
                        remoteSessionId = "remote-1",
                    ),
                ),
            )

            assertEquals(
                listOf("remote-session"),
                dao.getReadingSessionsForItem("item-1").map(JournalReadingSessionEntity::id),
            )
        } finally {
            database.close()
        }
    }
}

private fun journalEntry(
    itemId: String,
    reviewText: String,
    syncState: String,
    remoteReviewNoteId: String? = null,
    remoteUpdatedAtEpochMillis: Long? = null,
): ItemJournalEntryEntity = ItemJournalEntryEntity(
    itemId = itemId,
    sourceType = "KAVITA",
    libraryId = "library",
    itemTitle = itemId,
    coverUrl = null,
    itemKind = "BOOK",
    format = "EPUB",
    rating = 7,
    reviewText = reviewText,
    reviewedAtEpochMillis = 100L,
    updatedAtEpochMillis = 200L,
    remoteReviewNoteId = remoteReviewNoteId,
    remoteUpdatedAtEpochMillis = remoteUpdatedAtEpochMillis,
    syncState = syncState,
)

private fun journalEvent(
    id: String,
    itemId: String,
    occurredAt: Long,
): JournalReadingEventEntity = JournalReadingEventEntity(
    id = id,
    itemId = itemId,
    sourceType = "KAVITA",
    libraryId = "library",
    itemTitle = itemId,
    coverUrl = null,
    itemKind = "BOOK",
    format = "EPUB",
    eventType = "FINISHED",
    fromPercent = 0.0,
    toPercent = 1.0,
    fromPage = null,
    toPage = null,
    pageCount = null,
    occurredAtEpochMillis = occurredAt,
    syncState = "CLEAN",
)

private fun journalSession(
    id: String,
    itemId: String,
    syncState: String,
    startedAt: Long = 100L,
    endedAt: Long = 200L,
    remoteSessionId: String? = null,
): JournalReadingSessionEntity = JournalReadingSessionEntity(
    id = id,
    itemId = itemId,
    sourceType = "KAVITA",
    libraryId = "library",
    itemTitle = itemId,
    coverUrl = null,
    itemKind = "BOOK",
    format = "EPUB",
    mediaType = "BOOK",
    startedAtEpochMillis = startedAt,
    endedAtEpochMillis = endedAt,
    durationMillis = endedAt - startedAt,
    fromPercent = null,
    toPercent = null,
    fromPage = null,
    toPage = null,
    pageCount = null,
    remoteSessionId = remoteSessionId,
    syncState = syncState,
)
