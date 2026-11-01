package com.example.unifiedreader.feature.reader.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderWorkCoordinatorTest {
    @Test
    fun `background work waits for active reader lease and resumes immediately after close signal`() = runTest {
        val coordinator = ReaderWorkCoordinator(
            openingGraceMillis = 0L,
            backgroundRetryDelayMillis = 50L,
        )
        val lease = coordinator.beginReaderSession("item-1")

        val backgroundWork = async {
            coordinator.awaitBackgroundWorkAllowed()
        }
        runCurrent()

        assertTrue(backgroundWork.isActive)

        advanceTimeBy(500L)
        runCurrent()

        assertTrue(backgroundWork.isActive)

        lease.close()
        runCurrent()

        assertTrue(backgroundWork.isCompleted)
    }

    @Test
    fun `a reader visit runs from the first lease to the last`() {
        var now = 1_000L
        val coordinator = ReaderWorkCoordinator(nowEpochMillis = { now })

        val first = coordinator.beginReaderSession("item-1")
        now = 2_000L
        val second = coordinator.beginReaderSession("item-1")

        assertEquals(setOf("item-1"), coordinator.activeReaderItemIds.value)
        assertEquals(1_000L, coordinator.readerVisitStartedAt("item-1"))
        assertNull(coordinator.readerVisitStartedAt("item-2"))

        first.close()
        assertEquals(1_000L, coordinator.readerVisitStartedAt("item-1"))

        second.close()
        assertEquals(emptySet<String>(), coordinator.activeReaderItemIds.value)
        assertNull(coordinator.readerVisitStartedAt("item-1"))
    }

    @Test
    fun `a reader back on the same book at once continues the visit`() {
        var now = 1_000L
        val coordinator = ReaderWorkCoordinator(nowEpochMillis = { now })

        coordinator.beginReaderSession("item-1").close()
        now = 3_000L
        val recreated = coordinator.beginReaderSession("item-1")

        assertEquals(1_000L, coordinator.readerVisitStartedAt("item-1"))

        recreated.close()
        now = 60_000L
        coordinator.beginReaderSession("item-1")

        assertEquals(60_000L, coordinator.readerVisitStartedAt("item-1"))
    }

    @Test
    fun `another book starts a new visit`() {
        var now = 1_000L
        val coordinator = ReaderWorkCoordinator(nowEpochMillis = { now })

        coordinator.beginReaderSession("item-1").close()
        now = 2_000L
        coordinator.beginReaderSession("item-2")

        assertEquals(setOf("item-2"), coordinator.activeReaderItemIds.value)
        assertEquals(2_000L, coordinator.readerVisitStartedAt("item-2"))
    }

    @Test
    fun `reader opening grace is time boxed`() = runTest {
        var now = 1_000L
        val coordinator = ReaderWorkCoordinator(
            openingGraceMillis = 250L,
            backgroundRetryDelayMillis = 50L,
            nowEpochMillis = { now },
        )

        coordinator.notifyReaderOpening("item-1")

        assertTrue(coordinator.isReaderOpeningOrActive)

        now += 251L

        assertFalse(coordinator.isReaderOpeningOrActive)
    }

    @Test
    fun `background work waits for opening grace deadline without polling active reader state`() = runTest {
        val coordinator = ReaderWorkCoordinator(
            openingGraceMillis = 250L,
            backgroundRetryDelayMillis = 50L,
            nowEpochMillis = { testScheduler.currentTime },
        )

        coordinator.notifyReaderOpening("item-1")

        val backgroundWork = async {
            coordinator.awaitBackgroundWorkAllowed()
        }
        runCurrent()

        assertTrue(backgroundWork.isActive)

        advanceTimeBy(249L)
        runCurrent()

        assertTrue(backgroundWork.isActive)

        advanceTimeBy(1L)
        runCurrent()

        assertTrue(backgroundWork.isCompleted)
    }

    @Test
    fun `best effort reader work waits for visible page load and resumes immediately after close signal`() = runTest {
        val coordinator = ReaderWorkCoordinator(
            openingGraceMillis = 0L,
            backgroundRetryDelayMillis = 50L,
        )
        val finishVisibleLoad = CompletableDeferred<Unit>()

        val visibleLoad = async {
            coordinator.runVisiblePageLoad("item-1") {
                finishVisibleLoad.await()
            }
        }
        runCurrent()

        val bestEffortWork = async {
            coordinator.awaitReaderBestEffortWorkAllowed()
        }
        runCurrent()

        assertTrue(coordinator.hasVisiblePageLoads)
        assertTrue(bestEffortWork.isActive)

        advanceTimeBy(500L)
        runCurrent()

        assertTrue(bestEffortWork.isActive)

        finishVisibleLoad.complete(Unit)
        runCurrent()

        assertFalse(coordinator.hasVisiblePageLoads)
        assertTrue(visibleLoad.isCompleted)
        assertTrue(bestEffortWork.isCompleted)
    }
}
