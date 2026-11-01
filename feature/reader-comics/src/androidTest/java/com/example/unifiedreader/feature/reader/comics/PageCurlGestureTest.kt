package com.example.unifiedreader.feature.reader.comics

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.feature.reader.core.curl.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageCurlGestureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val state = PageCurlState()
    private var size = IntSize.Zero
    private var selecting = false
    private var enabled by mutableStateOf(true)
    private var starts = 0
    private val commits = mutableListOf<Boolean>()

    @Test
    fun dragMovesCurlAndRevealsNextPageBeforeFingerRelease() {
        showReader()
        val surface = composeRule.onNodeWithTag("curl")
        surface.performTouchInput {
            down(Offset(width * 0.9f, height * 0.5f))
            moveTo(Offset(width * 0.35f, height * 0.5f), delayMillis = 40)
        }
        composeRule.runOnIdle {
            assertEquals(1, starts)
            assertTrue("curl must already follow the finger", state.edge.x < size.width * 0.5f)
            assertTrue(commits.isEmpty())
        }
        val pixels = surface.captureToImage().toPixelMap()
        val revealed = pixels[pixels.width - 5, pixels.height / 2]
        assertTrue("next page must be visibly revealed", revealed.blue > revealed.red)
        surface.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(listOf(true), commits)
    }

    @Test
    fun longPressWithoutSelectionDoesNotDisableNextSwipe() {
        showReader()
        composeRule.onNodeWithTag("curl").performTouchInput {
            down(center)
            advanceEventTime(800)
            up()
        }
        composeRule.onNodeWithTag("curl").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(1, starts)
        assertEquals(listOf(true), commits)
    }

    @Test
    fun selectionHandlesKeepTheirGestureAndCurlsResumeWhenSelectionEnds() {
        showReader()
        composeRule.runOnIdle { selecting = true }
        composeRule.onNodeWithTag("curl").performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals(0, starts); selecting = false }
        composeRule.onNodeWithTag("curl").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(1, starts)
        assertEquals(listOf(true), commits)
    }

    @Test
    fun twoQuickSwipesWhileFirstPageLoadsBothTurnExactlyOnce() {
        val ready = CompletableDeferred<Boolean>()
        showReader(ready)
        val surface = composeRule.onNodeWithTag("curl")
        surface.performTouchInput { swipeLeft(durationMillis = 80) }
        surface.performTouchInput { swipeLeft(durationMillis = 80) }
        composeRule.runOnIdle {
            assertEquals(1, starts)
            assertTrue(commits.isEmpty())
            ready.complete(true)
        }
        composeRule.waitForIdle()
        assertEquals(2, starts)
        assertEquals(listOf(true, true), commits)
    }

    @Test
    fun reversingHeldDragReturnsToOriginalPage() {
        showReader()
        val surface = composeRule.onNodeWithTag("curl")
        surface.performTouchInput {
            down(Offset(width * 0.9f, height * 0.5f))
            moveTo(Offset(width * 0.35f, height * 0.5f), delayMillis = 80)
        }
        composeRule.waitForIdle()
        surface.performTouchInput {
            moveTo(Offset(width * 0.9f, height * 0.5f), delayMillis = 80)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(false), commits)
    }

    @Test
    fun changingModeDuringAHeldTurnCancelsWithoutChangingPage() {
        showReader()
        val surface = composeRule.onNodeWithTag("curl")
        surface.performTouchInput {
            down(Offset(width * 0.9f, height * 0.5f))
            moveTo(Offset(width * 0.35f, height * 0.5f), delayMillis = 40)
        }
        composeRule.runOnIdle { enabled = false }
        composeRule.waitForIdle()
        surface.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(listOf(false), commits)
    }

    @Test
    fun systemCancelDoesNotCommitAHeldTurn() {
        showReader()
        val surface = composeRule.onNodeWithTag("curl")
        surface.performTouchInput {
            down(Offset(width * 0.9f, height * 0.5f))
            moveTo(Offset(width * 0.35f, height * 0.5f), delayMillis = 40)
        }
        composeRule.waitForIdle()
        surface.performTouchInput { cancel() }
        composeRule.waitForIdle()
        assertEquals(listOf(false), commits)
    }

    private fun showReader(ready: CompletableDeferred<Boolean> = CompletableDeferred(true)) {
        val turner = PageCurlTapTurner()
        var active by mutableStateOf<PageCurlDrag?>(null)
        composeRule.setContent {
            val scope = rememberCoroutineScope()
            Box(
                Modifier.size(300.dp, 400.dp).onSizeChanged { size = it }.background(Color.Red)
                    .pageCurlDragGesture(
                        enabled = enabled,
                        rightToLeft = false,
                        state = state,
                        gestureScope = scope,
                        turner = turner,
                        startTurn = { target, down ->
                            starts++
                            val turn = buildPageCurlTurn(
                                target, IntRect(0, 0, size.width, size.height),
                                IntRect(0, 0, size.width, size.height), 1f, PageCurlFollow.Edge, false,
                            )!!
                            object : PageCurlDrag {
                                override val turn = turn
                                override val grab = turn.grabPoint(down.y)
                                override suspend fun awaitReady(): Boolean = ready.await()
                            }.also { active = it }
                        },
                        onTurnSettled = { _, committed -> commits += committed },
                        onTurnEnded = { active = null },
                        yieldToLongPress = true,
                        isSelectingText = { selecting },
                    ).testTag("curl"),
            ) {
                active?.let { drag ->
                    val front = androidx.compose.runtime.remember(size) {
                        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                            .apply { eraseColor(android.graphics.Color.RED) }.asImageBitmap()
                    }
                    val next = androidx.compose.runtime.remember(size) {
                        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
                            .apply { eraseColor(android.graphics.Color.BLUE) }.asImageBitmap()
                    }
                    PageCurlSurface(
                        drag.turn, drag.grab, pageCurlFrames(drag.turn, front, next), state, Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
