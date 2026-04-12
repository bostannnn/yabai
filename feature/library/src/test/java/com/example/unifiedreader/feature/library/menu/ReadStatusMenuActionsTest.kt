package com.example.unifiedreader.feature.library.menu

import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.ItemProgressSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadStatusMenuActionsTest {
    @Test
    fun `items without progress expose mark as read`() {
        var requestedReadState: Boolean? = null

        val action = buildReadStatusMenuActionState(
            item = remoteBook(),
            progress = null,
            onSetReadStatus = { _, markAsRead -> requestedReadState = markAsRead },
        )

        requireNotNull(action)
        assertEquals(listOf("Mark as read"), action.actions.map { it.label })
        action.actions.single().onClick()
        assertEquals(true, requestedReadState)
    }

    @Test
    fun `items with partial progress expose both read status actions`() {
        val requestedReadStates = mutableListOf<Boolean>()

        val action = buildReadStatusMenuActionState(
            item = remoteBook(),
            progress = ItemProgressSummary(
                label = "Resume",
                fraction = 0.35f,
            ),
            onSetReadStatus = { _, markAsRead -> requestedReadStates += markAsRead },
        )

        requireNotNull(action)
        assertEquals(listOf("Mark as read", "Mark as unread"), action.actions.map { it.label })
        action.actions.forEach { it.onClick() }
        assertEquals(listOf(true, false), requestedReadStates)
    }

    @Test
    fun `zero progress summary still exposes unread reset`() {
        val action = buildReadStatusMenuActionState(
            item = remoteBook(),
            progress = ItemProgressSummary(
                label = "Not started",
                fraction = 0f,
            ),
            onSetReadStatus = { _, _ -> },
        )

        requireNotNull(action)
        assertEquals(listOf("Mark as read", "Mark as unread"), action.actions.map { it.label })
    }

    @Test
    fun `completed progress exposes only mark as unread`() {
        val action = buildReadStatusMenuActionState(
            item = remoteBook(),
            progress = ItemProgressSummary(
                label = "Finished",
                fraction = 1f,
                isFinished = true,
            ),
            onSetReadStatus = { _, _ -> },
        )

        requireNotNull(action)
        assertEquals(listOf("Mark as unread"), action.actions.map { it.label })
    }

    @Test
    fun `local items still hide manual read status actions`() {
        val action = buildReadStatusMenuActionState(
            item = remoteBook(sourceType = SourceType.LOCAL),
            progress = null,
            onSetReadStatus = { _, _ -> },
        )

        assertNull(action)
    }
}

private fun remoteBook(
    sourceType: SourceType = SourceType.GRIMMORY,
): ReadableItem = ReadableItem(
    id = "${sourceType.name.lowercase()}:item:book-1",
    sourceType = sourceType,
    libraryId = "${sourceType.name.lowercase()}:library:books",
    remoteId = "book-1",
    title = "Book",
    format = FormatType.EPUB,
)
