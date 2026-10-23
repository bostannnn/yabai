package com.example.unifiedreader.feature.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryBookPageTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val book = ReadableItem(
        id = "grimmory:item:1",
        sourceType = SourceType.GRIMMORY,
        libraryId = "grimmory:library:books",
        remoteId = "1",
        title = "The Salt Orchard",
        format = FormatType.EPUB,
        description = "<p>A harvest that never ends.</p>",
        bookMetadata = BookMetadata(author = "Mira Kalden", genres = listOf("Fantasy"), languages = listOf("English")),
    )

    @Test
    fun pageShowsTheBookAndRunsItsActions() {
        var downloaded: ReadableItem? = null
        var markedRead: Boolean? = null
        var genre: String? = null
        var opened: ReadableItem? = null

        composeRule.setContent {
            MaterialTheme {
                LibraryBookPage(
                    item = book,
                    relatedPool = listOf(book),
                    actionContext = LibraryItemActionContext(
                        downloadableItemIds = setOf(book.id),
                        onDownloadItem = { downloaded = it },
                        onSetReadStatus = { _, read -> markedRead = read },
                    ),
                    coverImageUrlProvider = { _, _ -> null },
                    onOpen = { opened = it },
                    onShowBook = {},
                    onDeleteImported = {},
                    onDismiss = {},
                    onFilterByGenre = { genre = it },
                )
            }
        }

        composeRule.onNodeWithText("The Salt Orchard").assertExists()
        composeRule.onNodeWithText("Mira Kalden").assertExists()
        composeRule.onNodeWithText("A harvest that never ends.").assertExists()
        composeRule.onNodeWithText("English").assertExists()

        composeRule.onNodeWithText("Download").performClick()
        composeRule.onNodeWithText("Mark finished").performClick()
        // A genre filters the library and closes the page.
        composeRule.onNodeWithText("Fantasy").performClick()
        composeRule.runOnIdle {
            assertEquals(book, downloaded)
            assertEquals(true, markedRead)
            assertEquals("Fantasy", genre)
            assertEquals(null, opened)
        }
    }

    @Test
    fun startReadingOpensTheBook() {
        var opened: ReadableItem? = null

        composeRule.setContent {
            MaterialTheme {
                LibraryBookPage(
                    item = book,
                    relatedPool = listOf(book),
                    actionContext = LibraryItemActionContext(),
                    coverImageUrlProvider = { _, _ -> null },
                    onOpen = { opened = it },
                    onShowBook = {},
                    onDeleteImported = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Start reading").performClick()
        composeRule.runOnIdle { assertEquals(book, opened) }
    }
}
