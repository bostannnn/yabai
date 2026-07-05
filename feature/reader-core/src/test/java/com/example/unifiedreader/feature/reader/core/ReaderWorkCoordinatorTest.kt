package com.example.unifiedreader.feature.reader.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
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
