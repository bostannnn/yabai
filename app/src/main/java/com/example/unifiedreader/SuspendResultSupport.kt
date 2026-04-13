package com.example.unifiedreader

import kotlin.coroutines.cancellation.CancellationException

internal suspend inline fun <T> runSuspendCatchingPreservingCancellation(
    crossinline block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (throwable: Throwable) {
    if (throwable is CancellationException) {
        throw throwable
    }
    Result.failure(throwable)
}
