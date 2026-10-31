package com.example.unifiedreader.feature.library.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBrowsePageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val books = listOf(
        book("1", "Lust, Caution", "Eileen Chang", "Espionage", "China", "World War II"),
        book("2", "Slow Horses", "Mick Herron", "Espionage", "London", "21st century"),
        book("3", "Thousand Cranes", "Yasunari Kawabata", "Japan", "1950s", "Love and relationships"),
        book("4", "Alone in Berlin", "Hans Fallada", "Berlin", "World War II", "Resistance"),
    )

    @Test
    fun theLandingShowsRowsAndAChipNarrowsTheBooks() {
        var request by mutableStateOf(LibraryBrowseRequest())
        setBrowse(request = { request }, onRequestChanged = { request = it })

        waitForText("Set in")
        waitForText("Europe")
        composeRule.onAllNodesWithText("When").onFirst().assertExists()

        composeRule.onAllNodesWithText("Espionage").onFirst().performClick()

        waitForText("2 books")
        composeRule.runOnIdle {
            assertEquals(listOf(LibraryBrowseChip(LibraryBrowseRow.ABOUT, "Espionage")), request.picked)
        }
        waitForText("Lust, Caution")
        waitForText("Slow Horses")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Thousand Cranes").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aPickedChipComesOffWithItsCross() {
        var request by mutableStateOf(
            LibraryBrowseRequest(picked = listOf(LibraryBrowseChip(LibraryBrowseRow.WHEN, "World War II"))),
        )
        setBrowse(request = { request }, onRequestChanged = { request = it })

        waitForText("2 books")
        waitForText("Alone in Berlin")
        composeRule.onNodeWithContentDescription("Take off World War II").performClick()

        composeRule.runOnIdle { assertEquals(emptyList<LibraryBrowseChip>(), request.picked) }
        waitForText("Set in")
    }

    private fun setBrowse(
        request: () -> LibraryBrowseRequest,
        onRequestChanged: (LibraryBrowseRequest) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme {
                LibraryBrowsePage(
                    source = SourceType.GRIMMORY,
                    items = books,
                    request = request(),
                    onRequestChanged = onRequestChanged,
                    showRows = false,
                    onShowRowsChanged = {},
                    actionContext = LibraryItemActionContext(),
                    coverImageUrlProvider = { _, _ -> null },
                    gridCellSizeDp = 140,
                    contentTopInset = 0.dp,
                    onClose = {},
                    onItemSelected = {},
                    onScrolledChanged = {},
                )
            }
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun book(id: String, title: String, author: String, vararg tags: String) = ReadableItem(
        id = "grimmory:item:$id",
        sourceType = SourceType.GRIMMORY,
        libraryId = "grimmory:library:books",
        remoteId = id,
        title = title,
        format = FormatType.EPUB,
        bookMetadata = BookMetadata(author = author, tags = tags.toList()),
    )
}
