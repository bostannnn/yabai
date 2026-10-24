package com.example.unifiedreader.feature.reader.epub

import java.util.Locale
import kotlin.math.abs
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

/** One step of a spacing setting: what the reader reads ("Wide") and what Readium gets. */
internal data class EpubStep(
    val label: String,
    val value: Double,
)

/** The step closest to [value], for a value saved between steps. */
internal fun List<EpubStep>.nearest(value: Double): EpubStep = minBy { step -> abs(step.value - value) }

// Readium's page margins are a multiple of its side gutter: 100, 120 and 160 dp of side space on an 800 dp page.
internal val EpubMarginSteps = listOf(EpubStep("Narrow", 0.5), EpubStep("Normal", 1.0), EpubStep("Wide", 2.0))

// Space between paragraphs, in rem.
internal val EpubParagraphSpacingSteps = listOf(
    EpubStep("None", 0.0),
    EpubStep("Small", 0.5),
    EpubStep("Medium", 1.0),
    EpubStep("Large", 1.5),
)

// A paragraph's first-line indent, in em.
internal val EpubParagraphIndentSteps = listOf(
    EpubStep("None", 0.0),
    EpubStep("Small", 1.0),
    EpubStep("Medium", 2.0),
    EpubStep("Large", 3.0),
)

internal const val EpubLineHeightMin = 1.1
internal const val EpubLineHeightMax = 2.2

/** Line height in tenths, inside its range. */
internal fun snapEpubLineHeight(value: Double): Double =
    ((value * 10).roundToInt() / 10.0).coerceIn(EpubLineHeightMin, EpubLineHeightMax)

/** "1.5×". */
internal fun formatEpubLineHeight(value: Double): String = String.format(Locale.getDefault(), "%.1f\u00d7", value)
