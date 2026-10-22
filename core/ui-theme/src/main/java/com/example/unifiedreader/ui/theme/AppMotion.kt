package com.example.unifiedreader.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's motion: three springs, so an interrupted animation keeps its velocity instead of
 * restarting. Spatial moves things (slides, scale, placement), spatialFast is the small
 * feedback under a finger, effects changes opacity and color. Compose finishes every one of
 * them at once when the system animator scale is 0.
 */
object AppMotion {
    fun <T> spatial(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = visibilityThreshold)

    fun <T> spatialFast(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 800f, visibilityThreshold = visibilityThreshold)

    fun <T> effects(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 1600f, visibilityThreshold = visibilityThreshold)

    /** A screen change first fades the outgoing screen out over this, linearly. */
    const val screenFadeOutMillis: Int = 90

    /** Then the incoming screen fades in over this. */
    const val screenFadeInMillis: Int = 210

    /** A fade-through's incoming screen grows from this scale to 1. */
    const val fadeThroughInitialScale: Float = 0.96f

    /** A shared-axis change slides both screens this far, forward toward the start edge. */
    val sharedAxisOffset: Dp = 30.dp

    /** Covers, chips and buttons shrink to this while pressed. */
    const val pressedScale: Float = 0.96f

    /** A pressed pill button rounds toward a square with corners this size. */
    val pressedCornerRadius: Dp = 14.dp

    /**
     * Predictive back, at full progress: the screen or panel being left has moved this far
     * (screens with the finger, panels toward their own edge)...
     */
    val predictiveBackShift: Dp = 60.dp

    /** ...has shrunk by this fraction... */
    const val predictiveBackShrink: Float = 0.12f

    /** ...and a screen has rounded its corners to this. */
    val predictiveBackCornerRadius: Dp = 28.dp

    /** The screen underneath grows from this scale to 1 as the gesture goes. */
    const val predictiveBackUnderneathScale: Float = 0.94f
}
