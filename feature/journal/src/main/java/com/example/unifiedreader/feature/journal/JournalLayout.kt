package com.example.unifiedreader.feature.journal

/** The journal's panels, as its grid places them. */
internal enum class JournalSlot {
    Summary,
    ReadingDays,
    WhenYouRead,
    Pages,
    TimeByMonth,
    WhatYouRead,
    Finished,
    NeedsRating,
    Lists,
    Ratings,
    MostRead,
}

/** A place in a row: [span] twelfths wide, its panels stacked one over the other. */
internal data class JournalCellLayout(val span: Int, val slots: List<JournalSlot>)

/** A group of rows under its label. */
internal data class JournalGroupLayout(val label: String, val rows: List<List<JournalCellLayout>>)

/**
 * The journal in three groups, Time, Books and Taste, each rows on a twelve-column grid where
 * [wide]: one big panel with up to two small ones stacked beside it. Below that every panel is a
 * row of its own, in the same order. Only the panels in [present] are placed: what is left in a
 * row widens to fill it, and a row or a group with nothing in it goes.
 */
internal fun journalLayout(
    month: Boolean,
    wide: Boolean,
    present: Set<JournalSlot>,
): List<JournalGroupLayout> {
    fun cell(span: Int, vararg slots: JournalSlot) = JournalCellLayout(span, slots.toList())
    val plan = if (month) {
        listOf(
            "Time" to listOf(
                listOf(cell(12, JournalSlot.Summary)),
                listOf(cell(7, JournalSlot.ReadingDays), cell(5, JournalSlot.WhenYouRead, JournalSlot.Pages)),
            ),
            "Books" to listOf(
                listOf(cell(7, JournalSlot.WhatYouRead), cell(5, JournalSlot.Finished, JournalSlot.NeedsRating)),
                listOf(cell(12, JournalSlot.Lists)),
            ),
            "Taste" to listOf(listOf(cell(4, JournalSlot.Ratings), cell(8, JournalSlot.MostRead))),
        )
    } else {
        listOf(
            "Time" to listOf(
                listOf(cell(12, JournalSlot.Summary)),
                listOf(cell(8, JournalSlot.ReadingDays), cell(4, JournalSlot.TimeByMonth, JournalSlot.Pages)),
            ),
            "Books" to listOf(
                listOf(cell(8, JournalSlot.Finished), cell(4, JournalSlot.NeedsRating)),
                listOf(cell(12, JournalSlot.Lists)),
            ),
            "Taste" to listOf(listOf(cell(4, JournalSlot.Ratings), cell(8, JournalSlot.MostRead))),
        )
    }
    return plan.mapNotNull { (label, rows) ->
        val laidOut = rows.flatMap { row ->
            val cells = row
                .map { cell -> cell.copy(slots = cell.slots.filter { slot -> slot in present }) }
                .filter { cell -> cell.slots.isNotEmpty() }
            when {
                cells.isEmpty() -> emptyList()
                !wide -> cells.flatMap { cell -> cell.slots.map { slot -> listOf(JournalCellLayout(12, listOf(slot))) } }
                else -> {
                    val last = cells.last()
                    listOf(cells.dropLast(1) + last.copy(span = last.span + 12 - cells.sumOf(JournalCellLayout::span)))
                }
            }
        }
        laidOut.takeIf { it.isNotEmpty() }?.let { JournalGroupLayout(label, it) }
    }
}
