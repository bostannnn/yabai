package com.example.unifiedreader.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableItemManualReadStatusActionTest {
    @Test
    fun `stump items hide manual read status actions while workaround is active`() {
        assertFalse(
            readableItem(sourceType = SourceType.STUMP).supportsManualReadStatusAction(),
        )
    }

    @Test
    fun `kavita items still expose manual read status actions`() {
        assertTrue(
            readableItem(sourceType = SourceType.KAVITA).supportsManualReadStatusAction(),
        )
    }

    @Test
    fun `grimmory items still expose manual read status actions`() {
        assertTrue(
            readableItem(sourceType = SourceType.GRIMMORY).supportsManualReadStatusAction(),
        )
    }
}

private fun readableItem(
    sourceType: SourceType,
): ReadableItem = ReadableItem(
    id = sourceType.remoteItemId("item-1"),
    sourceType = sourceType,
    libraryId = sourceType.remoteLibraryId("library-1"),
    remoteId = "item-1",
    title = "Item",
    format = FormatType.CBZ,
)
