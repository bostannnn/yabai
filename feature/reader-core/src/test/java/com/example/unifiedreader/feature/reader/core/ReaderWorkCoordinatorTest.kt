package com.example.unifiedreader.feature.reader.core

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
    fun `background work waits for active reader lease and resumes after close`() = runTest {
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

        lease.close()
        advanceTimeBy(50L)
        runCurrent()

        assertFalse(backgroundWork.isActive)
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
    fun `best effort reader work waits for visible page load and resumes after it finishes`() = runTest {
        val coordinator = ReaderWorkCoordinator(
            openingGraceMillis = 0L,
            backgroundRetryDelayMillis = 50L,
        )

        val visibleLoad = async {
            coordinator.runVisiblePageLoad("item-1") {
                while (true) {
                    kotlinx.coroutines.delay(50L)
                }
            }
        }
        runCurrent()

        val bestEffortWork = async {
            coordinator.awaitReaderBestEffortWorkAllowed()
        }
        runCurrent()

        assertTrue(coordinator.hasVisiblePageLoads)
        assertTrue(bestEffortWork.isActive)

        visibleLoad.cancel()
        advanceTimeBy(50L)
        runCurrent()

        assertFalse(coordinator.hasVisiblePageLoads)
        assertFalse(bestEffortWork.isActive)
    }
}
