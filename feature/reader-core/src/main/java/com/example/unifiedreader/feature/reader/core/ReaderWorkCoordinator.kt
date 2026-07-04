package com.example.unifiedreader.feature.reader.core

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class ReaderWorkCoordinator(
    private val openingGraceMillis: Long = ReaderOpeningGraceMillis,
    private val backgroundRetryDelayMillis: Long = BackgroundRetryDelayMillis,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val activeReaderCountsByItemId = mutableMapOf<String, Int>()
    private val visiblePageLoadCountsByItemId = mutableMapOf<String, Int>()
    private var openingUntilEpochMillis: Long = 0L

    val isReaderOpeningOrActive: Boolean
        get() = synchronized(lock) {
            activeReaderCountsByItemId.isNotEmpty() || nowEpochMillis() < openingUntilEpochMillis
        }

    val hasVisiblePageLoads: Boolean
        get() = synchronized(lock) {
            visiblePageLoadCountsByItemId.isNotEmpty()
        }

    val isReaderBestEffortWorkAllowed: Boolean
        get() = !hasVisiblePageLoads

    fun notifyReaderOpening(
        itemId: String,
    ) {
        synchronized(lock) {
            openingUntilEpochMillis = max(openingUntilEpochMillis, nowEpochMillis() + openingGraceMillis)
        }
    }

    fun beginReaderSession(
        itemId: String,
    ): ReaderWorkLease {
        synchronized(lock) {
            openingUntilEpochMillis = max(openingUntilEpochMillis, nowEpochMillis() + openingGraceMillis)
            activeReaderCountsByItemId[itemId] = (activeReaderCountsByItemId[itemId] ?: 0) + 1
        }
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
        while (isReaderOpeningOrActive) {
            delay(backgroundRetryDelayMillis)
        }
    }

    suspend fun awaitReaderBestEffortWorkAllowed() {
        while (!isReaderBestEffortWorkAllowed) {
            delay(backgroundRetryDelayMillis)
        }
    }

    private fun endReaderSession(
        itemId: String,
    ) {
        synchronized(lock) {
            val activeCount = activeReaderCountsByItemId[itemId] ?: return
            if (activeCount <= 1) {
                activeReaderCountsByItemId.remove(itemId)
            } else {
                activeReaderCountsByItemId[itemId] = activeCount - 1
            }
        }
    }

    private fun beginVisiblePageLoad(
        itemId: String,
    ): ReaderWorkLease {
        synchronized(lock) {
            visiblePageLoadCountsByItemId[itemId] = (visiblePageLoadCountsByItemId[itemId] ?: 0) + 1
        }
        return ReaderWorkLease(
            itemId = itemId,
            release = ::endVisiblePageLoad,
        )
    }

    private fun endVisiblePageLoad(
        itemId: String,
    ) {
        synchronized(lock) {
            val activeCount = visiblePageLoadCountsByItemId[itemId] ?: return
            if (activeCount <= 1) {
                visiblePageLoadCountsByItemId.remove(itemId)
            } else {
                visiblePageLoadCountsByItemId[itemId] = activeCount - 1
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
private const val BackgroundRetryDelayMillis = 120L
