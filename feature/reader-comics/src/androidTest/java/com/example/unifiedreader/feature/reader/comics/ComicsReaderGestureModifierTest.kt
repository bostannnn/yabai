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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
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
        tapGestureSurface(TapPosition.Center)
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-visible")

        tapGestureSurface(TapPosition.Center)
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-hidden")
    }

    @Test
    fun hiddenOverlayEdgeTapsNavigateInLtr() {
        composeRule.setContent {
            GestureSurfaceHarness(readingDirection = ComicReadingDirection.LTR)
        }

        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-hidden")
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")

        tapGestureSurface(TapPosition.LeftEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav--1")
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-hidden")

        tapGestureSurface(TapPosition.RightEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-hidden")
    }

    @Test
    fun visibleOverlayEdgeTapsNavigateInLtr() {
        composeRule.setContent {
            GestureSurfaceHarness(
                readingDirection = ComicReadingDirection.LTR,
                initialOverlayVisible = true,
            )
        }

        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-visible")
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")

        tapGestureSurface(TapPosition.RightEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-1")
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-visible")

        tapGestureSurface(TapPosition.LeftEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")
        composeRule.onNodeWithTag("overlay-state").assertContentDescriptionEquals("overlay-visible")
    }

    @Test
    fun edgeTapsMirrorNavigationInRtl() {
        composeRule.setContent {
            GestureSurfaceHarness(readingDirection = ComicReadingDirection.RTL)
        }

        tapGestureSurface(TapPosition.LeftEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-1")

        tapGestureSurface(TapPosition.RightEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")
    }

    @Test
    fun edgeTapsUseUpdatedReadingDirection() {
        lateinit var updateReadingDirection: (ComicReadingDirection) -> Unit

        composeRule.setContent {
            var readingDirection by remember { mutableStateOf(ComicReadingDirection.LTR) }
            updateReadingDirection = { readingDirection = it }
            GestureSurfaceHarness(readingDirection = readingDirection)
        }

        tapGestureSurface(TapPosition.LeftEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav--1")

        composeRule.runOnIdle {
            updateReadingDirection(ComicReadingDirection.RTL)
        }
        composeRule.waitForIdle()

        tapGestureSurface(TapPosition.LeftEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")

        tapGestureSurface(TapPosition.RightEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav--1")
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

    @Test
    fun doubleTapZoomsTowardTappedSide() {
        composeRule.setContent {
            GestureSurfaceHarness(readingDirection = ComicReadingDirection.LTR)
        }

        composeRule.onNodeWithTag("gesture-surface").performTouchInput { doubleClick(centerLeft) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("zoom-state").assertContentDescriptionEquals("zoom-active")
        composeRule.onNodeWithTag("viewport-state").assertContentDescriptionEquals("offset-right")
    }

    @Test
    fun zoomedPanHandsEdgeRemainderToPager() {
        composeRule.setContent {
            GesturePagerHarness(
                readingDirection = ComicReadingDirection.LTR,
                initialPage = 1,
            )
        }

        composeRule.onNodeWithTag("gesture-surface").performTouchInput { doubleClick(centerLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gesture-surface").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pager-page").assertContentDescriptionEquals("page-0")
    }

    @Test
    fun zoomedEdgeTapsDoNotNavigatePages() {
        composeRule.setContent {
            GestureSurfaceHarness(readingDirection = ComicReadingDirection.LTR)
        }

        composeRule.onNodeWithTag("gesture-surface").performTouchInput { doubleClick(centerLeft) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("zoom-state").assertContentDescriptionEquals("zoom-active")
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")

        tapGestureSurface(TapPosition.RightEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")

        tapGestureSurface(TapPosition.LeftEdge)
        composeRule.onNodeWithTag("nav-state").assertContentDescriptionEquals("nav-0")
    }

    private fun tapGestureSurface(position: TapPosition) {
        composeRule.onNodeWithTag("gesture-surface").performTouchInput {
            click(
                when (position) {
                    TapPosition.LeftEdge -> Offset(width * 0.1f, height / 2f)
                    TapPosition.Center -> center
                    TapPosition.RightEdge -> Offset(width * 0.9f, height / 2f)
                },
            )
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }
}

private enum class TapPosition {
    LeftEdge,
    Center,
    RightEdge,
}

@Composable
private fun GesturePagerHarness(
    readingDirection: ComicReadingDirection,
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
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
    initialOverlayVisible: Boolean = false,
) {
    val viewportState = rememberComicPageViewportState(pageId = "test-page")
    val animationScope = rememberCoroutineScope()
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    val imageSize = IntSize(width = 1200, height = 1800)
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayVisible by remember { mutableStateOf(initialOverlayVisible) }
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
                    animationScope = animationScope,
                    nestedScrollDispatcher = nestedScrollDispatcher,
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
                contentDescription = when {
                    viewportState.offset.x > 1f -> "offset-right"
                    viewportState.offset.x < -1f -> "offset-left"
                    else -> "offset-center"
                }
            }
            .testTag("viewport-state"),
    )
    Box(
        modifier = Modifier
            .semantics {
                contentDescription = "nav-$navigateCount"
            }
            .testTag("nav-state"),
    )
}
