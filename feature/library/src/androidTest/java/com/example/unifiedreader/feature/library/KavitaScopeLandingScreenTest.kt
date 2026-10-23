package com.example.unifiedreader.feature.library

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

        composeRule.setContent {
            MaterialTheme {
                KavitaScopeLandingScreen(
                    title = "Collections",
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
                    emptyMessage = "No Kavita collections yet",
                    onItemSelected = { selectedItemId = it },
                )
            }
        }

        // The section's name is in the top bar; the grid header carries only the count.
        composeRule.onNodeWithText("Collections").assertDoesNotExist()
        composeRule.onNodeWithText("2 Items").assertExists()
        composeRule.onNodeWithText("Alan Moore").assertExists()
        composeRule.onNodeWithText("AM").assertExists()
        composeRule.onNodeWithText("CBZ").assertDoesNotExist()

        composeRule.onNodeWithTag("kavita-scope-card-kavita:collection:1")
            .performClick()

        assertEquals("kavita:collection:1", selectedItemId)
    }
}
