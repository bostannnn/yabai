package com.example.unifiedreader.feature.library.shelves

/**
 * Opt-in timing for the shelves recompute, logged under the same tag as the derived-state timing so
 * both show together:
 *
 *   adb logcat -s LibraryPerf
 *
 * Uses monotonic `System.nanoTime` and tolerates `android.util.Log` being unavailable (plain JVM unit
 * tests). Flip [ENABLED] to false to strip all logging.
 */
internal object LibraryShelvesPerfLog {
    const val ENABLED = true
    private const val TAG = "LibraryPerf"

    fun startNanos(): Long = System.nanoTime()

    fun logSince(label: String, startNanos: Long) {
        if (!ENABLED) return
        val ms = (System.nanoTime() - startNanos) / 1_000_000.0
        runCatching { android.util.Log.d(TAG, "%s: %.2f ms".format(label, ms)) }
    }
}
