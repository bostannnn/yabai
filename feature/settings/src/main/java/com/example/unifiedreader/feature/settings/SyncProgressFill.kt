package com.example.unifiedreader.feature.settings

/**
 * The fill a sync's bar shows once a step reports [reported], given the [current] one: never less, so a
 * step retried on the other address doesn't send the bar back, and still moving (null) until the first
 * step that can count its work. A step that can't count leaves the fill as it is.
 */
fun syncProgressFillAfter(
    current: Float?,
    reported: Float?,
): Float? {
    val fill = reported?.coerceIn(0f, 1f) ?: return current
    return if (current == null) fill else maxOf(current, fill)
}
