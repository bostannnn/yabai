package com.example.unifiedreader

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComicPagePrefetchJobsTest {
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val jobs = ComicPagePrefetchJobs(scope)

    @Test
    fun `loads the targets in window order and skips cached pages`() {
        val started = mutableListOf<String>()

        jobs.retarget(
            targets = listOf("p2", "p3", "p1").map { key -> key to suspend { started += key } },
            isCached = { key -> key == "p3" },
        )

        assertEquals(listOf("p2", "p1"), started)
    }

    @Test
    fun `retargeting cancels pages outside the new window and keeps the ones inside`() {
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val finished = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        fun load(key: String): suspend () -> Unit = {
            try {
                gates.getOrPut(key) { CompletableDeferred() }.await()
                finished += key
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                cancelled += key
                throw cancellation
            }
        }
        jobs.retarget(targets = listOf("p1", "p2").map { it to load(it) }, isCached = { false })

        var startedAgain = 0
        jobs.retarget(
            targets = listOf("p2" to suspend { startedAgain++ }, "p3" to load("p3")),
            isCached = { false },
        )
        gates.values.forEach { it.complete(Unit) }
        scope.runCurrent()

        assertEquals(listOf("p1"), cancelled)
        assertEquals(0, startedAgain)
        assertEquals(setOf("p2", "p3"), finished.toSet())
    }

    @Test
    fun `cancelWhere stops only the matching pages`() {
        val gate = CompletableDeferred<Unit>()
        val finished = mutableListOf<String>()
        jobs.retarget(
            targets = listOf("a::1", "b::1").map { key -> key to suspend { gate.await(); finished += key } },
            isCached = { false },
        )

        jobs.cancelWhere { key -> key.startsWith("a::") }
        gate.complete(Unit)
        scope.runCurrent()

        assertEquals(listOf("b::1"), finished)
    }
}
