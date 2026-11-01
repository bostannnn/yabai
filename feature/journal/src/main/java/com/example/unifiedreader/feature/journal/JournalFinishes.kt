package com.example.unifiedreader.feature.journal

/** Back in a book's first tenth after finishing it: it is being read again. */
internal const val JournalStartedOverPercent = 0.1

/**
 * The events with each read finished once. A book counts as finished again only after it was
 * started over (read again from its first tenth): opening a finished book for a look, or reading
 * its last chapters again, reaches the end without finishing it twice, and those finishes read as
 * progress. Each source reports finishes its own way (a Grimmory reading session that ends at the
 * end, a Kavita chapter completed in a session), so the rule holds here, over all of them.
 */
internal fun List<JournalReadingEvent>.withFinishesOncePerRead(): List<JournalReadingEvent> {
    val repeatedFinishIds = groupBy(JournalReadingEvent::itemId).values.flatMapTo(mutableSetOf()) { itemEvents ->
        var finished = false
        var startedOver = false
        itemEvents.sortedBy(JournalReadingEvent::occurredAtEpochMillis).mapNotNull { event ->
            val fromStart = event.fromPercent?.let { percent -> percent < JournalStartedOverPercent } == true
            if (event.eventType == JournalReadingEventType.FINISHED) {
                val counts = !finished || startedOver || fromStart
                finished = true
                startedOver = false
                event.id.takeUnless { counts }
            } else {
                if (fromStart || event.toPercent?.let { percent -> percent < JournalStartedOverPercent } == true) {
                    startedOver = true
                }
                null
            }
        }
    }
    if (repeatedFinishIds.isEmpty()) return this
    return map { event ->
        if (event.id in repeatedFinishIds) event.copy(eventType = JournalReadingEventType.CONTINUED) else event
    }
}
