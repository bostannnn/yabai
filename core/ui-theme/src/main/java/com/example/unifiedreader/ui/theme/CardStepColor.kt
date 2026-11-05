package com.example.unifiedreader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/** How far a card stands from the ground: 1.2:1, seen in every theme without turning into a second ground. */
const val CardStepContrast: Float = 1.2f

/**
 * [ground] moved toward [ink] (the text colour on it) in small steps until the two are [minContrast]
 * apart: a card that reads as a card on any theme's ground, light or dark. Picking a surface role
 * instead fails where a theme puts it next to the ground (Sepia's and Panel Pop's surfaceContainer).
 * Where that step would leave [faintText] (the card's quietest text) under 4.5:1 on it, as on a mid-tone
 * ground like Detective File's, the card steps the other way, away from the ink.
 */
fun cardStepColor(
    ground: Color,
    ink: Color,
    faintText: Color = ink,
    minContrast: Float = CardStepContrast,
): Color {
    val towardInk = stepToward(ground, ink, minContrast)
    if (contrastRatio(faintText, towardInk) >= TextContrast) return towardInk
    val away = if (ink.luminance() < ground.luminance()) Color.White else Color.Black
    return stepToward(ground, away, minContrast)
}

private fun stepToward(ground: Color, target: Color, minContrast: Float): Color {
    var fraction = CardStepStart
    while (fraction < CardStepLimit) {
        val step = lerp(ground, target, fraction)
        if (contrastRatio(ground, step) >= minContrast) return step
        fraction += CardStepIncrement
    }
    return lerp(ground, target, CardStepLimit)
}

/** The WCAG contrast ratio of two opaque colours, 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

private const val TextContrast = 4.5f
private const val CardStepStart = 0.02f
private const val CardStepIncrement = 0.005f
private const val CardStepLimit = 0.5f
