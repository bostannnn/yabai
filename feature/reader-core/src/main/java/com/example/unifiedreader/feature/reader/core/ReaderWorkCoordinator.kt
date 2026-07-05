package com.example.unifiedreader.feature.reader.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class ReaderWorkCoordinator(
    private val openingGraceMillis: Long = ReaderOpeningGraceMillis,
    // Kept for source compatibility with callers/tests from the old polling implementation.
    @Suppress("UNUSED_PARAMETER")
    backgroundRetryDelayMillis: Long = 120L,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val activeReaderCountsByItemId = mutableMapOf<String, Int>()
    private val visiblePageLoadCountsByItemId = mutableMapOf<String, Int>()
    private var openingUntilEpochMillis: Long = 0L
    private var stateChanged = CompletableDeferred<Unit>()

    val isReaderOpeningOrActive: Boolean
        get() = synchronized(lock) {
            isReaderOpeningOrActiveLocked(nowEpochMillis())
        }

    val hasVisiblePageLoads: Boolean
        get() = synchronized(lock) {
            hasVisiblePageLoadsLocked()
        }

    val isReaderBestEffortWorkAllowed: Boolean
        get() = !hasVisiblePageLoads

    fun notifyReaderOpening(
        itemId: String,
    ) {
        val signalToComplete = synchronized(lock) {
            val nextOpeningUntil = max(openingUntilEpochMillis, nowEpochMillis() + openingGraceMillis)
            if (nextOpeningUntil == openingUntilEpochMillis) {
                null
            } else {
                openingUntilEpochMillis = nextOpeningUntil
                stateChangedSignalLocked()
            }
        }
        signalToComplete?.complete(Unit)
    }

    fun beginReaderSession(
        itemId: String,
    ): ReaderWorkLease {
        val signalToComplete = synchronized(lock) {
            openingUntilEpochMillis = max(openingUntilEpochMillis, nowEpochMillis() + openingGraceMillis)
            activeReaderCountsByItemId[itemId] = (activeReaderCountsByItemId[itemId] ?: 0) + 1
            stateChangedSignalLocked()
        }
        signalToComplete.complete(Unit)
        return ReaderWorkLease(
            itemId = itemId,
            release = ::endReaderSession,
        )
    }

    suspend fun <T> runVisiblePageLoad(
        itemId: String,
        block: suspend () -> T,
    ): T {
        val lease = beginVisiblePageLoad(itemId)
        return try {
            block()
        } finally {
            lease.close()
        }
    }

    suspend fun awaitBackgroundWorkAllowed() {
        while (true) {
            val wait = synchronized(lock) {
                backgroundWorkWaitLocked(nowEpochMillis())
            } ?: return
            wait.await()
        }
    }

    suspend fun awaitReaderBestEffortWorkAllowed() {
        while (true) {
            val signal = synchronized(lock) {
                if (hasVisiblePageLoadsLocked()) stateChanged else null
            } ?: return
            signal.await()
        }
    }

    private fun endReaderSession(
        itemId: String,
    ) {
        val signalToComplete = synchronized(lock) {
            val activeCount = activeReaderCountsByItemId[itemId] ?: return@synchronized null
            if (activeCount <= 1) {
                activeReaderCountsByItemId.remove(itemId)
            } else {
                activeReaderCountsByItemId[itemId] = activeCount - 1
            }
            stateChangedSignalLocked()
        }
        signalToComplete?.complete(Unit)
    }

    private fun beginVisiblePageLoad(
        itemId: String,
    ): ReaderWorkLease {
        val signalToComplete = synchronized(lock) {
            visiblePageLoadCountsByItemId[itemId] = (visiblePageLoadCountsByItemId[itemId] ?: 0) + 1
            stateChangedSignalLocked()
        }
        signalToComplete.complete(Unit)
        return ReaderWorkLease(
            itemId = itemId,
            release = ::endVisiblePageLoad,
        )
    }

    private fun endVisiblePageLoad(
        itemId: String,
    ) {
        val signalToComplete = synchronized(lock) {
            val activeCount = visiblePageLoadCountsByItemId[itemId] ?: return@synchronized null
            if (activeCount <= 1) {
                visiblePageLoadCountsByItemId.remove(itemId)
            } else {
                visiblePageLoadCountsByItemId[itemId] = activeCount - 1
            }
            stateChangedSignalLocked()
        }
        signalToComplete?.complete(Unit)
    }

    private fun backgroundWorkWaitLocked(
        nowEpochMillis: Long,
    ): BackgroundWorkWait? {
        if (activeReaderCountsByItemId.isNotEmpty()) {
            return BackgroundWorkWait(
                signal = stateChanged,
                openingGraceRemainingMillis = null,
            )
        }
        val openingGraceRemainingMillis = openingUntilEpochMillis - nowEpochMillis
        return if (openingGraceRemainingMillis > 0L) {
            BackgroundWorkWait(
                signal = stateChanged,
                openingGraceRemainingMillis = openingGraceRemainingMillis,
            )
        } else {
            null
        }
    }

    private fun isReaderOpeningOrActiveLocked(
        nowEpochMillis: Long,
    ): Boolean = activeReaderCountsByItemId.isNotEmpty() || nowEpochMillis < openingUntilEpochMillis

    private fun hasVisiblePageLoadsLocked(): Boolean = visiblePageLoadCountsByItemId.isNotEmpty()

    private fun stateChangedSignalLocked(): CompletableDeferred<Unit> {
        val signal = stateChanged
        stateChanged = CompletableDeferred()
        return signal
    }
}

private data class BackgroundWorkWait(
    val signal: CompletableDeferred<Unit>,
    val openingGraceRemainingMillis: Long?,
) {
    suspend fun await() {
        val remainingMillis = openingGraceRemainingMillis
        if (remainingMillis == null) {
            signal.await()
        } else {
            withTimeoutOrNull(remainingMillis) {
                signal.await()
            }
        }
    }
}

class ReaderWorkLease internal constructor(
    private val itemId: String,
    private val release: (String) -> Unit,
) {
    private val closed = AtomicBoolean(false)

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        release(itemId)
    }
}

private const val ReaderOpeningGraceMillis = 2_500L
