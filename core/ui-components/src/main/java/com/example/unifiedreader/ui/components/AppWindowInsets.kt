package com.example.unifiedreader.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

/**
 * The system bars where they sit when shown, and the display cutout: what the app's screens keep
 * clear of. Unlike `WindowInsets.systemBars` these don't drop to zero while a book hides the bars,
 * so the library under a closing book is already where it stays when the bars come back.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.appSystemBars: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = systemBarsIgnoringVisibility.union(displayCutout)
