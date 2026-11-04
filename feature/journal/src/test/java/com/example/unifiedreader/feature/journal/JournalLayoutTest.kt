package com.example.unifiedreader.feature.journal

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalLayoutTest {
    private val everything = JournalSlot.entries.toSet()

    @Test
    fun `a full month is Time, Books and Taste in rows of twelve`() {
        val groups = journalLayout(month = true, wide = true, present = everything)

        assertEquals(listOf("Time", "Books", "Taste"), groups.map(JournalGroupLayout::label))
        assertEquals(
            listOf(
                listOf(JournalCellLayout(12, listOf(JournalSlot.Summary))),
                listOf(
                    JournalCellLayout(7, listOf(JournalSlot.ReadingDays)),
                    JournalCellLayout(5, listOf(JournalSlot.WhenYouRead, JournalSlot.Pages)),
                ),
            ),
            groups[0].rows,
        )
        groups.flatMap(JournalGroupLayout::rows).forEach { row -> assertEquals(12, row.sumOf(JournalCellLayout::span)) }
    }

    @Test
    fun `an empty panel steps aside and its neighbour widens`() {
        val thin = setOf(JournalSlot.Summary, JournalSlot.ReadingDays, JournalSlot.WhenYouRead, JournalSlot.WhatYouRead)

        val groups = journalLayout(month = true, wide = true, present = thin)

        assertEquals(listOf("Time", "Books"), groups.map(JournalGroupLayout::label))
        assertEquals(
            listOf(JournalCellLayout(7, listOf(JournalSlot.ReadingDays)), JournalCellLayout(5, listOf(JournalSlot.WhenYouRead))),
            groups[0].rows[1],
        )
        assertEquals(listOf(listOf(JournalCellLayout(12, listOf(JournalSlot.WhatYouRead)))), groups[1].rows)
    }

    @Test
    fun `below the grid every panel is a row of its own, in the same order`() {
        val groups = journalLayout(month = true, wide = false, present = everything)

        assertEquals(
            listOf(
                JournalSlot.Summary, JournalSlot.ReadingDays, JournalSlot.WhenYouRead, JournalSlot.Pages,
                JournalSlot.WhatYouRead, JournalSlot.Finished, JournalSlot.NeedsRating, JournalSlot.Lists,
                JournalSlot.Ratings, JournalSlot.MostRead,
            ),
            groups.flatMap { group -> group.rows.map { row -> row.single().slots.single() } },
        )
    }

    @Test
    fun `the year puts its calendar beside time and pages by month`() {
        val groups = journalLayout(month = false, wide = true, present = everything)

        assertEquals(
            listOf(
                JournalCellLayout(8, listOf(JournalSlot.ReadingDays)),
                JournalCellLayout(4, listOf(JournalSlot.TimeByMonth, JournalSlot.Pages)),
            ),
            groups[0].rows[1],
        )
        assertEquals(false, groups.any { group -> group.rows.flatten().any { cell -> JournalSlot.WhatYouRead in cell.slots } })
    }
}
