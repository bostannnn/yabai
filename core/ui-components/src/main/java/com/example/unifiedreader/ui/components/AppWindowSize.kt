package com.example.unifiedreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * The window's width class, the app's one set of layout breakpoints: Compact under 600 dp
 * (phones in portrait), Medium from 600 dp (small tablets, tablets in portrait), Expanded from
 * 840 dp (tablets in landscape, where the navigation rail shows). Screens ask for the class
 * instead of comparing the window against dp values of their own. A component that adapts to
 * the space it is given (a form row, a journal section) measures that space instead.
 *
 * The classes compare in order, so `width >= AppWindowWidth.Medium` reads "not a phone".
 */
enum class AppWindowWidth {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        const val MediumMinDp: Int = 600
        const val ExpandedMinDp: Int = 840

        fun of(widthDp: Int): AppWindowWidth = when {
            widthDp < MediumMinDp -> Compact
            widthDp < ExpandedMinDp -> Medium
            else -> Expanded
        }
    }
}

/**
 * The window's height class: Compact under 480 dp (a phone in landscape), Medium from 480 dp,
 * Expanded from 900 dp. Only the navigation rail asks for it, so a phone turned sideways keeps
 * the drawer.
 */
enum class AppWindowHeight {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        const val MediumMinDp: Int = 480
        const val ExpandedMinDp: Int = 900

        fun of(heightDp: Int): AppWindowHeight = when {
            heightDp < MediumMinDp -> Compact
            heightDp < ExpandedMinDp -> Medium
            else -> Expanded
        }
    }
}

/** The current window's [AppWindowWidth]. */
@Composable
@ReadOnlyComposable
fun currentAppWindowWidth(): AppWindowWidth = AppWindowWidth.of(LocalConfiguration.current.screenWidthDp)

/** The current window's [AppWindowHeight]. */
@Composable
@ReadOnlyComposable
fun currentAppWindowHeight(): AppWindowHeight = AppWindowHeight.of(LocalConfiguration.current.screenHeightDp)
