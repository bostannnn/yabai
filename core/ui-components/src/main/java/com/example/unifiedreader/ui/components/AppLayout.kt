package com.example.unifiedreader.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The frame every screen sits on, from the design system (`docs/design/system/project/layout.md`).
 * Screens take their margins and gaps from here instead of writing dp values of their own, so
 * every screen lines up with the top bar and with each other.
 */
object AppLayout {
    /** Screen margin under 600 dp (phones). */
    val gutterCompact: Dp = 16.dp

    /** Screen margin from 600 dp (Medium and Expanded). */
    val gutterWide: Dp = 24.dp

    /** First content below [AppTopBarDefaults.contentInset], on every screen. */
    val contentTop: Dp = 8.dp

    /** After the last content of a scrolling screen. */
    val contentBottom: Dp = 24.dp

    /** Between sections on a page. */
    val sectionGap: Dp = 24.dp

    /** A heading or group label to the thing it heads. */
    val headingGap: Dp = 8.dp

    /** Between cover tiles, across and down, and between side-by-side panels. */
    val gridGap: Dp = 16.dp

    /** Inside every panel, card, sheet and row. */
    val inset: Dp = 20.dp

    /** Inside a dialog card and an empty state. */
    val dialogInset: Dp = 24.dp

    /** Inside a dropdown menu: items, headings and any control in it. */
    val menuInset: Dp = 16.dp

    /** Every side sheet; under 600 dp a sheet is a bottom sheet instead. */
    val sheetWidth: Dp = 400.dp

    /** The list pane of a two-pane screen (the settings hub beside its open page). */
    val listPaneWidth: Dp = 320.dp

    fun gutter(width: AppWindowWidth): Dp = if (width == AppWindowWidth.Compact) gutterCompact else gutterWide
}

/** The current window's screen margin: [AppLayout.gutterCompact] on phones, [AppLayout.gutterWide] from 600 dp. */
@Composable
@ReadOnlyComposable
fun appGutter(): Dp = AppLayout.gutter(currentAppWindowWidth())
