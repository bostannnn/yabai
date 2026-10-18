package com.example.unifiedreader.feature.library

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LibraryWindowBadgesResolveTest {
    private val finished = ItemProgressSummary(label = "Finished", fraction = 1f, isFinished = true)

    @Test
    fun `a badge the recompute drops leaves the window badges too`() {
        val items = listOf(book("kavita:item:1"), book("kavita:item:2"))
        // What the bounded sources say: item 1 is finished.
        var storedFinishedIds = setOf("kavita:item:1")
        val resolver: suspend (List<ReadableItem>) -> LibraryWindowBadges = { window ->
            LibraryWindowBadges(
                displayProgressByItemId = window
                    .filter { item -> item.id in storedFinishedIds }
                    .associate { item -> item.id to finished },
            )
        }
        var recomputedProgress by mutableStateOf(mapOf("kavita:item:1" to finished))
        var windowBadges = LibraryWindowBadges()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.get().setContent {
            windowBadges = rememberLibraryWindowBadges(
                renderedItems = items,
                resolver = resolver,
                recomputedBadgeInputs = listOf(recomputedProgress),
            )
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf("kavita:item:1"), windowBadges.displayProgressByItemId.keys)

        // "Mark as unread": the store drops the progress, then the recompute publishes without it.
        storedFinishedIds = emptySet()
        recomputedProgress = emptyMap()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(emptySet<String>(), (windowBadges.displayProgressByItemId + recomputedProgress).keys)
        controller.pause().stop().destroy()
    }

    private fun book(id: String) = ReadableItem(
        id = id,
        sourceType = SourceType.KAVITA,
        libraryId = "kavita:library:1",
        remoteId = id,
        title = id,
        format = FormatType.EPUB,
    )
}
