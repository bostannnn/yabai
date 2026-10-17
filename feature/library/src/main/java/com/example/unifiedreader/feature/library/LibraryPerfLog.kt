package com.example.unifiedreader.feature.library

/**
 * Opt-in, near-zero-overhead timing for the library recompute so back-nav / progress-update cost can
 * be measured on device without a profiler:
 *
 *   adb logcat -s LibraryPerf
 *
 * It reports whether each `compute` was a cache HIT (cheap) or MISS, and for a MISS breaks the work
 * down by pass (source index, facets, download availability, visible-items sort, shelves) so we can
 * see exactly where the time goes instead of guessing. Flip [ENABLED] to false to strip all logging.
 *
 * Uses monotonic `System.nanoTime` and tolerates `android.util.Log` being unavailable (plain JVM unit
 * tests), so it never affects test behaviour.
 */
internal object LibraryPerfLog {
    const val ENABLED = true
    private const val TAG = "LibraryPerf"

    inline fun <T> section(label: String, block: () -> T): T {
        if (!ENABLED) return block()
        val startNanos = System.nanoTime()
        val result = block()
        logSince(label, startNanos)
        return result
    }

    fun startNanos(): Long = System.nanoTime()

    fun logSince(label: String, startNanos: Long) {
        if (!ENABLED) return
        val ms = (System.nanoTime() - startNanos) / 1_000_000.0
        runCatching { android.util.Log.d(TAG, "%s: %.2f ms".format(label, ms)) }
    }
}
