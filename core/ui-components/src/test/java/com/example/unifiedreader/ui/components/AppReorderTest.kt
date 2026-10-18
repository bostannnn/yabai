package com.example.unifiedreader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AppReorderTest {
    private val bounds = mapOf(
        0 to ReorderItemBounds(top = 0f, height = 60f),
        1 to ReorderItemBounds(top = 60f, height = 60f),
        2 to ReorderItemBounds(top = 120f, height = 60f),
    )

    @Test
    fun `an item stays put until its centre crosses a neighbour's centre`() {
        assertEquals(1, resolveReorderTarget(index = 1, draggedCenter = 90f, boundsByIndex = bounds))
        assertEquals(1, resolveReorderTarget(index = 1, draggedCenter = 31f, boundsByIndex = bounds))
        assertEquals(1, resolveReorderTarget(index = 1, draggedCenter = 149f, boundsByIndex = bounds))
    }

    @Test
    fun `crossing a neighbour moves one step in that direction`() {
        assertEquals(0, resolveReorderTarget(index = 1, draggedCenter = 29f, boundsByIndex = bounds))
        assertEquals(2, resolveReorderTarget(index = 1, draggedCenter = 151f, boundsByIndex = bounds))
    }

    @Test
    fun `the ends of the list have nowhere further to go`() {
        assertEquals(0, resolveReorderTarget(index = 0, draggedCenter = -500f, boundsByIndex = bounds))
        assertEquals(2, resolveReorderTarget(index = 2, draggedCenter = 500f, boundsByIndex = bounds))
    }

    @Test
    fun `movedItem shifts one element and ignores bad indices`() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").movedItem(0, 1))
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").movedItem(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").movedItem(2, 0))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").movedItem(1, 1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").movedItem(1, 7))
    }
}
