package com.example.unifiedreader.feature.library.browse

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.LibraryBrowseChip
import com.example.unifiedreader.core.model.LibraryBrowseRow
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryItemActionContext
import com.example.unifiedreader.feature.library.tags.LibraryTagSortingContext
import com.example.unifiedreader.feature.library.tags.LocalLibraryTagSorting
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

    // Where these books' tags were sorted; "Espionage", "Love and relationships" and "Resistance" stay in About.
    private val sorted = LibraryTagSorting().with(
        listOf("China" to LibraryPlaceRegion.ASIA, "Japan" to LibraryPlaceRegion.ASIA, "London" to LibraryPlaceRegion.EUROPE, "Berlin" to LibraryPlaceRegion.EUROPE)
            .map { (place, region) -> LibraryTagSortChange(LibraryTagField.TAG, place, LibraryTagChoice(LibraryTagKind.PLACE, region)) } +
            listOf("World War II", "21st century", "1950s")
                .map { era -> LibraryTagSortChange(LibraryTagField.TAG, era, LibraryTagChoice(LibraryTagKind.ERA)) },
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

    @Test
    fun beforeAnythingIsSortedEveryTagIsAboutAndHoldingOneSortsIt() {
        var sorting by mutableStateOf(LibraryTagSorting())
        var opened: SourceType? = null
        setBrowse(
            request = { LibraryBrowseRequest() },
            onRequestChanged = {},
            sorting = { sorting },
            onSort = { changes -> sorting = sorting.with(changes) },
            onOpenTagSettings = { source -> opened = source },
        )

        waitForText("Places and times")
        composeRule.onNodeWithText("Check the guesses").performClick()
        composeRule.runOnIdle { assertEquals(SourceType.GRIMMORY, opened) }
        assertEquals(0, composeRule.onAllNodesWithText("Set in").fetchSemanticsNodes().size)

        composeRule.onAllNodesWithText("China").onFirst().performTouchInput { longClick() }
        waitForText("Browse shows it in")
        composeRule.onAllNodesWithText("Set in").onFirst().performClick()

        composeRule.runOnIdle {
            assertEquals(LibraryTagChoice(LibraryTagKind.PLACE, LibraryPlaceRegion.ASIA), sorting.choiceOf(LibraryTagField.TAG, "China"))
        }
        waitForText("Region")
    }

    private fun setBrowse(
        request: () -> LibraryBrowseRequest,
        onRequestChanged: (LibraryBrowseRequest) -> Unit,
        sorting: () -> LibraryTagSorting = { sorted },
        onSort: (List<LibraryTagSortChange>) -> Unit = {},
        onOpenTagSettings: ((SourceType) -> Unit)? = null,
    ) {
        composeRule.setContent {
            val tags = LibraryTagSortingContext(
                sortingBySource = mapOf(SourceType.GRIMMORY to sorting()),
                items = books,
                onSort = { _, changes -> onSort(changes) },
                coverImageUrlProvider = { _, _ -> null },
                onOpenTagSettings = onOpenTagSettings,
            )
            MaterialTheme {
                CompositionLocalProvider(LocalLibraryTagSorting provides tags) {
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
