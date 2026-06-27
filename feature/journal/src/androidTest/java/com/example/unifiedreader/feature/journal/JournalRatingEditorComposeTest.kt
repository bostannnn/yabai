package com.example.unifiedreader.feature.journal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalRatingEditorComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingFourAndHalfStarsCommitsReliably() {
        var rating by mutableStateOf<Int?>(null)
        val selections = mutableListOf<Int?>()
        setEditorContent(
            selectedRating = { rating },
            onRatingSelected = { selected ->
                rating = selected
                selections += selected
            },
        )

        composeRule.onNodeWithTag(JournalRatingStarsTag).performTouchInput {
            click(fourAndHalfStarPosition())
        }

        composeRule.runOnIdle {
            assertEquals(9, rating)
            assertEquals(listOf(9), selections)
        }
        composeRule.onNodeWithText("Rated 4.5").assertExists()
        composeRule.onNodeWithTag(JournalRatingStarsTag)
            .assertContentDescriptionContains("Rated 4.5")
    }

    @Test
    fun draggingAcrossStarsPreviewsAndCommitsFinalRating() {
        var rating by mutableStateOf<Int?>(null)
        val selections = mutableListOf<Int?>()
        setEditorContent(
            selectedRating = { rating },
            onRatingSelected = { selected ->
                rating = selected
                selections += selected
            },
        )

        composeRule.onNodeWithTag(JournalRatingStarsTag).performTouchInput {
            down(position = Offset(width * 0.05f, height / 2f))
            moveTo(position = Offset(width * 0.42f, height / 2f))
            moveTo(position = fourAndHalfStarPosition())
        }

        composeRule.onNodeWithText("Selecting 4.5").assertExists()
        composeRule.runOnIdle {
            assertEquals(null, rating)
            assertEquals(emptyList<Int?>(), selections)
        }

        composeRule.onNodeWithTag(JournalRatingStarsTag).performTouchInput {
            up()
        }

        composeRule.runOnIdle {
            assertEquals(9, rating)
            assertEquals(listOf(9), selections)
        }
        composeRule.onNodeWithText("Rated 4.5").assertExists()
    }

    @Test
    fun clearRatingButtonClearsSelectedRating() {
        var rating by mutableStateOf<Int?>(9)
        val selections = mutableListOf<Int?>()
        setEditorContent(
            selectedRating = { rating },
            onRatingSelected = { selected ->
                rating = selected
                selections += selected
            },
        )

        composeRule.onNodeWithTag(JournalRatingClearTag).performClick()

        composeRule.runOnIdle {
            assertEquals(null, rating)
            assertEquals(listOf(null), selections)
        }
        composeRule.onNodeWithText("No rating").assertExists()
    }

    @Test
    fun accessibilityActionsExposeHalfStarChoicesAndClear() {
        var rating by mutableStateOf<Int?>(null)
        setEditorContent(
            selectedRating = { rating },
            onRatingSelected = { selected -> rating = selected },
        )

        val actionLabels = composeRule.onNodeWithTag(JournalRatingStarsTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.CustomActions]
            .map { action -> action.label }
        val rateActionLabels = (1..10)
            .map { value -> "Rate ${journalRatingValueLabel(value)} stars" }

        assertEquals(rateActionLabels, actionLabels)

        val rateFourAndHalfAction = composeRule.onNodeWithTag(JournalRatingStarsTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.CustomActions]
            .first { action -> action.label == "Rate 4.5 stars" }
        composeRule.runOnIdle {
            assertTrue(rateFourAndHalfAction.action())
        }
        composeRule.runOnIdle {
            assertEquals(9, rating)
        }

        val ratedActionLabels = composeRule.onNodeWithTag(JournalRatingStarsTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.CustomActions]
            .map { action -> action.label }
        assertEquals(rateActionLabels + "Clear rating", ratedActionLabels)

        val clearAction = composeRule.onNodeWithTag(JournalRatingStarsTag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.CustomActions]
            .first { action -> action.label == "Clear rating" }
        composeRule.runOnIdle {
            assertTrue(clearAction.action())
            assertEquals(null, rating)
        }
    }

    private fun setEditorContent(
        selectedRating: () -> Int?,
        onRatingSelected: (Int?) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme {
                JournalRatingEditor(
                    selectedRating = selectedRating(),
                    onRatingSelected = onRatingSelected,
                )
            }
        }
    }

    private fun androidx.compose.ui.test.TouchInjectionScope.fourAndHalfStarPosition(): Offset =
        Offset(width * 0.86f, height / 2f)
}
