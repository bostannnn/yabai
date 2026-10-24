package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.graphics.Color
import com.example.unifiedreader.ui.theme.ReaderPageColor

/** A book's page colour, and whether it is the book's own or the default for books not set. */
internal data class EpubResolvedPageColor(
    val color: ReaderPageColor,
    val isBookOwn: Boolean,
)

/** A saved page colour: its name, or Light and Dark from before there were five. */
internal fun readerPageColorOrNull(name: String): ReaderPageColor? = when (name) {
    "Light" -> ReaderPageColor.Paper
    "Dark" -> ReaderPageColor.Black
    else -> ReaderPageColor.entries.firstOrNull { color -> color.name == name }
}

/**
 * The page colour of a book you haven't set: the last one picked in any book. Before the first
 * pick, Paper on a light app theme and Black on a dark one, as Follow app gave.
 */
internal fun defaultEpubPageColor(
    lastPicked: String?,
    appBackgroundIsLight: Boolean,
): ReaderPageColor = lastPicked?.let(::readerPageColorOrNull)
    ?: if (appBackgroundIsLight) ReaderPageColor.Paper else ReaderPageColor.Black

/**
 * The colour a book opens on. A book has its own once a colour was picked in it, or when it was
 * saved with a colour before the reader kept the override flag.
 */
internal fun resolveRestoredPageColor(
    restoredPreferences: EpubReaderPreferences,
    rawPreferences: Map<String, String>,
    defaultColor: ReaderPageColor,
): EpubResolvedPageColor {
    val hasLegacyThemeValue = rawPreferences.containsKey(EpubThemeKey) &&
        !rawPreferences.containsKey(EpubThemeOverrideKey)
    val isBookOwn = restoredPreferences.themeOverride || hasLegacyThemeValue
    return EpubResolvedPageColor(
        color = if (isBookOwn) restoredPreferences.theme else defaultColor,
        isBookOwn = isBookOwn,
    )
}

/** The back of a turning page: the page itself, or on a dark page its raised colour so the leaf shows. */
internal fun ReaderPageColor.curlBack(): Color = if (colors.isLight) colors.page else colors.raised
