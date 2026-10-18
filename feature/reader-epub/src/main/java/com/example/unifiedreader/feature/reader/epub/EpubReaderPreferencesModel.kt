package com.example.unifiedreader.feature.reader.epub

import kotlin.math.roundToInt

/** The "Pages" choice: the orientation default plus the three fixed layouts. */
internal enum class EpubPagesChoice(
    val label: String,
    val layoutMode: EpubReaderLayoutMode?,
) {
    Auto("Auto", null),
    Scroll("Scroll", EpubReaderLayoutMode.Scroll),
    Page("Page", EpubReaderLayoutMode.SinglePage),
    Spread("Spread", EpubReaderLayoutMode.DoublePage),
}

internal fun epubPagesChoice(
    useOrientationLayoutDefault: Boolean,
    layoutMode: EpubReaderLayoutMode,
): EpubPagesChoice = if (useOrientationLayoutDefault) {
    EpubPagesChoice.Auto
} else {
    EpubPagesChoice.entries.first { it.layoutMode == layoutMode }
}

/** Panel wording for the typography mode: "Original" reads better as what it is, the book's own styling. */
internal fun epubTypographyModeLabel(mode: EpubTypographyMode): String = when (mode) {
    EpubTypographyMode.Original -> "Book's own"
    EpubTypographyMode.Custom -> "Custom"
}

/** Segment labels short enough for three in a 220 dp control; the row label says "Alignment". */
internal fun epubTextAlignLabel(align: EpubReaderTextAlign): String = when (align) {
    EpubReaderTextAlign.Default -> "Auto"
    EpubReaderTextAlign.Start -> "Left"
    EpubReaderTextAlign.Justify -> "Justify"
}

internal const val EpubFontSizeMin = 0.8
internal const val EpubFontSizeMax = 2.4
internal const val EpubFontSizeStep = 0.1

/** One stepper tap: moves [value] by [EpubFontSizeStep] in [direction] (+1 or -1), on a clean tenth, inside the range. */
internal fun stepEpubFontSize(value: Double, direction: Int): Double {
    val tenths = (value * 10).roundToInt() + direction.coerceIn(-1, 1)
    return (tenths / 10.0).coerceIn(EpubFontSizeMin, EpubFontSizeMax)
}

internal fun canStepEpubFontSize(value: Double, direction: Int): Boolean =
    stepEpubFontSize(value, direction) != (value * 10).roundToInt() / 10.0
