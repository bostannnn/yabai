package com.example.unifiedreader.feature.library

import com.example.unifiedreader.core.model.KavitaLibrarySection

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KavitaScopeLandingScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun landingGridShowsVisualCardsAndSelectsItem() {
        var selectedItemId: String? = null
        var switchedTo: KavitaLibrarySection? = null

        composeRule.setContent {
            MaterialTheme {
                KavitaScopeLandingScreen(
                    section = KavitaLibrarySection.Collections,
                    items = listOf(
                        KavitaScopeLandingItem(
                            id = "kavita:collection:1",
                            title = "Alan Moore",
                            itemCount = 10,
                        ),
                        KavitaScopeLandingItem(
                            id = "kavita:collection:2",
                            title = "All-Ages",
                            itemCount = 17,
                        ),
                    ),
                    isLoading = false,
                    errorMessage = null,
                    searchQuery = "",
                    gridCellSizeDp = defaultLibraryGridCellSizeDp,
                    onSectionSelected = { switchedTo = it },
                    onItemSelected = { selectedItemId = it },
                )
            }
        }

        // The name stays in the bar; the grid starts with the switch and the count, and each
        // collection counts its series on its meta line.
        composeRule.onNodeWithText("2 collections").assertExists()
        composeRule.onNodeWithText("10 series").assertExists()
        composeRule.onNodeWithText("Alan Moore").assertExists()
        composeRule.onNodeWithText("AM").assertExists()
        composeRule.onNodeWithText("CBZ").assertDoesNotExist()

        composeRule.onNodeWithTag("kavita-scope-card-kavita:collection:1")
            .performClick()

        assertEquals("kavita:collection:1", selectedItemId)

        composeRule.onNodeWithText("Reading lists").performClick()

        assertEquals(KavitaLibrarySection.ReadingLists, switchedTo)
    }
}
