package com.example.unifiedreader.feature.reader.comics

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComicsReaderGestureModifierTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun singleFingerSwipeMovesPagerPage() {
        composeRule.setContent {
            GesturePagerHarness(readingDirection = ComicReadingDirection.LTR)
        }

        composeRule.onNodeWithTag("pager-page").assertContentDescriptionEquals("page-0")
        composeRule.onNodeWithTag("gesture-surface").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pager-page").assertContentDescriptionEquals("page-1")

        composeRule.onNodeWithTag("gesture-surface").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pager-page").assertContentDescriptionEquals("page-0")
    }

    @Test
    fun centerTapTogglesOverlayState() {
        composeRule.setContent {
            GestureSurfaceHarness(readingDirection = ComicReadingDirection.LTR)
        }

        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-hidden")
        composeRule.onNodeWithTag("gesture-surface").performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-visible")
    }

    @Test
    fun twoFingerGestureActivatesZoomState() {
        composeRule.setContent {
            GestureSurfaceHarness(readingDirection = ComicReadingDirection.LTR)
        }

        composeRule.onNodeWithTag("zoom-state").assertContentDescriptionEquals("zoom-idle")
        composeRule.onNodeWithTag("gesture-surface").performTouchInput {
            val leftStart = centerLeft
            val rightStart = centerRight
            down(pointerId = 0, position = leftStart)
            down(pointerId = 1, position = rightStart)
            moveBy(pointerId = 0, delta = Offset(-80f, 0f))
            moveBy(pointerId = 1, delta = Offset(80f, 0f))
            up(pointerId = 0)
            up(pointerId = 1)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("zoom-state").assertContentDescriptionEquals("zoom-active")
    }
}

@Composable
private fun GesturePagerHarness(
    readingDirection: ComicReadingDirection,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    HorizontalPager(
        state = pagerState,
        reverseLayout = readingDirection == ComicReadingDirection.RTL,
        modifier = Modifier.fillMaxSize(),
    ) { pageIndex ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "page-$pageIndex"
                }
                .testTag("pager-page"),
        ) {
            GestureSurfaceHarness(readingDirection = readingDirection)
        }
    }
}

@Composable
private fun GestureSurfaceHarness(
    readingDirection: ComicReadingDirection,
) {
    val viewportState = rememberComicPageViewportState(pageId = "test-page")
    val imageSize = IntSize(width = 1200, height = 1800)
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayVisible by remember { mutableStateOf(false) }
    var navigateCount by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .size(300.dp, 400.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                .background(Color.Black)
                .comicGestureModifier(
                    gestureKey = "gesture-surface",
                    containerSize = containerSize,
                    imageSize = imageSize,
                    viewportState = viewportState,
                    readingDirection = readingDirection,
                    pageFitMode = ComicPageFitMode.FitPage,
                    overlayVisible = overlayVisible,
                    onInteraction = {},
                    onToggleOverlay = { overlayVisible = !overlayVisible },
                    onNavigatePrevious = { navigateCount-- },
                    onNavigateNext = { navigateCount++ },
                )
                .testTag("gesture-surface"),
        )
    }

    Box(
        modifier = Modifier
            .semantics {
                contentDescription = if (overlayVisible) "overlay-visible" else "overlay-hidden"
            }
            .testTag("overlay-state"),
    )
    Box(
        modifier = Modifier
            .semantics {
                contentDescription = if (viewportState.isZoomed) "zoom-active" else "zoom-idle"
            }
            .testTag("zoom-state"),
    )
    Box(
        modifier = Modifier
            .semantics {
                contentDescription = "nav-$navigateCount"
            }
            .testTag("nav-state"),
    )
}
