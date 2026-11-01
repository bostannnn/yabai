package com.example.unifiedreader.feature.reader.core.curl

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageCurlDragSessionTest {
    @Test
    fun `first drag sample preserves movement beyond slop in both directions`() {
        assertEquals(Offset(480f, 204f), pageCurlDragStart(Offset(500f, 200f), Offset(400f, 220f), 20f))
        assertEquals(Offset(120f, 196f), pageCurlDragStart(Offset(100f, 200f), Offset(200f, 180f), 20f))
    }

    @Test
    fun `flick released before images load gets a visible animation before commit`() = runTest {
        val ready = CompletableDeferred<Boolean>()
        val drag = TestDrag(ready)
        val input = request()
        val state = PageCurlState()
        var committed: Boolean? = null
        var ended = false
        launch(frameClock()) {
            drivePageCurlDrag(input, drag, state, { _, result -> committed = result }, { ended = true })
        }
        runCurrent()
        input.sample.value = PageCurlDragSample(Offset(60f, 300f), released = true, horizontalVelocity = -1200f)
        advanceTimeBy(600)
        assertNull(committed)
        assertFalse(ended)
        ready.complete(true)
        runCurrent()
        advanceTimeBy(100)
        assertNull("must not commit before a visible turn", committed)
        assertTrue(state.edge.x < drag.grab.x)
        advanceUntilIdle()
        assertEquals(true, committed)
        assertTrue(ended)
    }

    @Test
    fun `a held drag catches up to latest finger and follows subsequent movement`() = runTest {
        val ready = CompletableDeferred<Boolean>()
        val drag = TestDrag(ready)
        val input = request()
        val state = PageCurlState()
        var committed: Boolean? = null
        launch(frameClock()) {
            drivePageCurlDrag(input, drag, state, { _, result -> committed = result }, {})
        }
        runCurrent()
        input.sample.value = PageCurlDragSample(Offset(300f, 320f))
        ready.complete(true)
        runCurrent()
        assertEquals(drag.turn.edgeForFinger(drag.grab, input.start, Offset(300f, 320f)), state.edge)
        input.sample.value = PageCurlDragSample(Offset(230f, 340f))
        runCurrent()
        assertEquals(drag.turn.edgeForFinger(drag.grab, input.start, Offset(230f, 340f)), state.edge)
        input.sample.value = PageCurlDragSample(input.start, released = true)
        advanceUntilIdle()
        assertEquals(false, committed)
    }

    @Test
    fun `cancel during preparation restores the navigator before removing the overlay`() = runTest {
        val ready = CompletableDeferred<Boolean>()
        val events = mutableListOf<String>()
        val state = PageCurlState()
        val job = launch(frameClock()) {
            drivePageCurlDrag(request(), TestDrag(ready), state, { _, committed ->
                assertFalse(committed)
                delay(50)
                events += "restored"
            }, { events += "ended" })
        }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(listOf("restored", "ended"), events)
        assertEquals(Offset.Unspecified, state.edge)
    }

    @Test
    fun `failed preparation cannot commit a page`() = runTest {
        val ready = CompletableDeferred(false)
        val results = mutableListOf<Boolean>()
        drivePageCurlDrag(request(), TestDrag(ready), PageCurlState(), { _, result -> results += result }, {})
        assertEquals(listOf(false), results)
    }

    private fun TestScope.frameClock() = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            return onFrame(testScheduler.currentTime * 1_000_000)
        }
    }
}

private fun request() = PageCurlDragRequest(
    PageCurlTarget.Next, down = Offset(490f, 300f), start = Offset(480f, 300f), finger = Offset(450f, 300f),
)

private class TestDrag(private val ready: CompletableDeferred<Boolean>) : PageCurlDrag {
    override val turn = buildPageCurlTurn(
        PageCurlTarget.Next, IntRect(0, 0, 500, 600), IntRect(0, 0, 500, 600),
        1f, PageCurlFollow.Edge, false,
    )!!
    override val grab = turn.grabPoint(300f)
    override suspend fun awaitReady(): Boolean = ready.await()
}
