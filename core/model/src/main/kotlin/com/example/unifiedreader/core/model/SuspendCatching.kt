package com.example.unifiedreader.core.model

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for blocks that call suspend functions.
 *
 * Plain `runCatching` also catches [CancellationException], so a cancelled coroutine would carry on as if
 * the call had merely failed. This rethrows cancellation and wraps every other failure.
 *
 * `inline`, like [runCatching], so the block can call suspend functions without being a suspend lambda.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runSuspendCatching(
    block: () -> T,
): Result<T> = try {
    Result.success(block())
} catch (throwable: Throwable) {
    if (throwable is CancellationException) {
        throw throwable
    }
    Result.failure(throwable)
}
