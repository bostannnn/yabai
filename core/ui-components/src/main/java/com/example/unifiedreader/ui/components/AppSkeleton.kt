package com.example.unifiedreader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.coroutineContext

/**
 * The loading pulse of placeholder blocks: full opacity down to [AppSkeletonDefaults.minAlpha]
 * and back over [AppSkeletonDefaults.periodMillis]. Every placeholder reads the same clock, so
 * a screen of them breathes together. Still when the system turns animations off. While one is
 * on screen at launch, the splash stays up instead (see [AppLaunchSplashHold]).
 */
@Composable
fun Modifier.appSkeletonPulse(): Modifier {
    HoldLaunchSplash()
    val alpha = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
        while (true) {
            withInfiniteAnimationFrameMillis { frameMillis ->
                alpha.floatValue = skeletonPulseAlpha(frameMillis)
            }
        }
    }
    return graphicsLayer { this.alpha = alpha.floatValue }
}

/** The pulse at [timeMillis] on the shared clock: 1 at the start of each period, the minimum halfway. */
internal fun skeletonPulseAlpha(timeMillis: Long): Float {
    val period = AppSkeletonDefaults.periodMillis
    val phase = (timeMillis % period).toFloat() / period
    val towardMin = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
    return 1f - (1f - AppSkeletonDefaults.minAlpha) * FastOutSlowInEasing.transform(towardMin)
}

object AppSkeletonDefaults {
    const val periodMillis: Long = 1400L
    const val minAlpha: Float = 0.45f
}
