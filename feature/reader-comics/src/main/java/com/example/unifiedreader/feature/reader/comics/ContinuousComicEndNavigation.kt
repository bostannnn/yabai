package com.example.unifiedreader.feature.reader.comics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

private const val ContinuousReaderEndNavigationThresholdPx = 24f

internal fun shouldNavigatePastContinuousEnd(
    canScrollForward: Boolean,
    availableY: Float,
    source: NestedScrollSource,
    alreadyTriggered: Boolean,
    thresholdPx: Float = ContinuousReaderEndNavigationThresholdPx,
): Boolean = !alreadyTriggered &&
    !canScrollForward &&
    source == NestedScrollSource.UserInput &&
    availableY < -thresholdPx

@Composable
internal fun rememberContinuousEndNavigationModifier(
    canScrollForward: Boolean,
    alreadyTriggered: Boolean,
    onNavigatePastEnd: () -> Unit,
): Modifier {
    val connection = remember(canScrollForward, alreadyTriggered, onNavigatePastEnd) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    shouldNavigatePastContinuousEnd(
                        canScrollForward = canScrollForward,
                        availableY = available.y,
                        source = source,
                        alreadyTriggered = alreadyTriggered,
                    )
                ) {
                    onNavigatePastEnd()
                }
                return Offset.Zero
            }
        }
    }
    return Modifier.nestedScroll(connection)
}
