package com.example.unifiedreader.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.unifiedReaderSurfacePattern(
    treatment: UnifiedReaderSurfaceTreatment,
    accentColor: Color,
    overlayColor: Color = Color.White,
): Modifier = when (treatment) {
    UnifiedReaderSurfaceTreatment.Plain -> this
    else -> drawWithCache {
        onDrawBehind {
            drawUnifiedReaderSurfaceTreatment(
                treatment = treatment,
                accentColor = accentColor,
                overlayColor = overlayColor,
            )
        }
    }
}

fun Modifier.unifiedReaderContentOverlay(
    treatment: UnifiedReaderSurfaceTreatment,
    accentColor: Color,
    overlayColor: Color = Color.White,
): Modifier = when (treatment) {
    UnifiedReaderSurfaceTreatment.Plain -> this
    else -> drawWithCache {
        onDrawWithContent {
            drawContent()
            drawUnifiedReaderSurfaceTreatment(
                treatment = treatment,
                accentColor = accentColor,
                overlayColor = overlayColor,
            )
        }
    }
}

fun Modifier.unifiedReaderBorderTreatment(
    treatment: UnifiedReaderBorderTreatment,
    color: Color,
    cornerRadius: Dp,
    width: Dp = 1.dp,
    alpha: Float = 1f,
): Modifier = drawWithCache {
    val strokeWidth = width.toPx().coerceAtLeast(1f)
    val cornerPx = cornerRadius.toPx().coerceAtLeast(0f)
    val borderColor = color.copy(alpha = alpha)
    val inkShadow = Color.Black.copy(alpha = (alpha * 0.20f).coerceAtMost(0.20f))
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)

    onDrawWithContent {
        drawContent()
        when (treatment) {
            UnifiedReaderBorderTreatment.Single -> {
                drawRoundedBorder(
                    color = borderColor,
                    cornerPx = cornerPx,
                    strokeWidth = strokeWidth,
                )
            }
            UnifiedReaderBorderTreatment.Double -> {
                drawRoundedBorder(
                    color = borderColor,
                    cornerPx = cornerPx,
                    strokeWidth = strokeWidth,
                )
                val inset = strokeWidth * 2.4f
                drawRoundedBorder(
                    color = borderColor.copy(alpha = borderColor.alpha * 0.72f),
                    cornerPx = (cornerPx - inset).coerceAtLeast(0f),
                    strokeWidth = (strokeWidth * 0.74f).coerceAtLeast(1f),
                    inset = inset,
                )
            }
            UnifiedReaderBorderTreatment.Ink -> {
                drawRoundedBorder(
                    color = inkShadow,
                    cornerPx = cornerPx,
                    strokeWidth = strokeWidth,
                    inset = 1.5f,
                )
                drawRoundedBorder(
                    color = borderColor,
                    cornerPx = cornerPx,
                    strokeWidth = strokeWidth * 1.35f,
                )
                drawRoundedBorder(
                    color = Color.White.copy(alpha = alpha * 0.18f),
                    cornerPx = (cornerPx - strokeWidth).coerceAtLeast(0f),
                    strokeWidth = 1f,
                    inset = strokeWidth * 1.5f,
                )
            }
            UnifiedReaderBorderTreatment.Dashed -> {
                drawRoundedBorder(
                    color = borderColor,
                    cornerPx = cornerPx,
                    strokeWidth = strokeWidth,
                    pathEffect = dashEffect,
                )
            }
        }
    }
}

fun Modifier.unifiedReaderProgressTreatment(
    treatment: UnifiedReaderProgressTreatment,
    fraction: Float,
    progressColor: Color,
    trackColor: Color,
): Modifier = drawWithCache {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    onDrawBehind {
        val width = size.width
        val height = size.height
        val progressWidth = width * clampedFraction
        drawRect(trackColor)
        when (treatment) {
            UnifiedReaderProgressTreatment.Solid -> {
                drawRect(progressColor, size = Size(progressWidth, height))
            }
            UnifiedReaderProgressTreatment.Segmented -> {
                val gap = 3f
                val segmentWidth = 10f
                var x = 0f
                while (x < progressWidth) {
                    drawRect(
                        color = progressColor,
                        topLeft = Offset(x, 0f),
                        size = Size(minOf(segmentWidth, progressWidth - x), height),
                    )
                    x += segmentWidth + gap
                }
            }
            UnifiedReaderProgressTreatment.TapeCounter -> {
                drawRect(progressColor.copy(alpha = 0.84f), size = Size(progressWidth, height))
                var x = 8f
                while (x < width) {
                    val tickAlpha = if (x <= progressWidth) 0.40f else 0.18f
                    drawLine(
                        color = Color.White.copy(alpha = tickAlpha),
                        start = Offset(x, 0f),
                        end = Offset(x, height),
                        strokeWidth = 1f,
                    )
                    x += 12f
                }
            }
            UnifiedReaderProgressTreatment.Dashed -> {
                val dashWidth = 12f
                val gap = 6f
                var x = 0f
                while (x < progressWidth) {
                    drawRect(
                        color = progressColor,
                        topLeft = Offset(x, 0f),
                        size = Size(minOf(dashWidth, progressWidth - x), height),
                    )
                    x += dashWidth + gap
                }
            }
        }
    }
}

private fun DrawScope.drawUnifiedReaderSurfaceTreatment(
    treatment: UnifiedReaderSurfaceTreatment,
    accentColor: Color,
    overlayColor: Color,
) {
    val width = size.width
    val height = size.height
    val minorGrid = 18f
    val majorGrid = minorGrid * 4f
    val softAccent = accentColor.copy(alpha = 0.12f)
    val faintAccent = accentColor.copy(alpha = 0.055f)
    val faintOverlay = overlayColor.copy(alpha = 0.045f)

    when (treatment) {
        UnifiedReaderSurfaceTreatment.TerminalGrid -> {
            var x = 0f
            while (x <= width) {
                drawLine(faintAccent, Offset(x, 0f), Offset(x, height), strokeWidth = 1f)
                x += majorGrid
            }
            var y = 0f
            while (y <= height) {
                drawLine(faintAccent, Offset(0f, y), Offset(width, y), strokeWidth = 1f)
                y += majorGrid
            }
        }
        UnifiedReaderSurfaceTreatment.GothicVignette -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)),
                    center = Offset(width * 0.5f, height * 0.45f),
                    radius = maxOf(width, height) * 0.78f,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        overlayColor.copy(alpha = 0.05f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f),
                    ),
                ),
            )
        }
        UnifiedReaderSurfaceTreatment.ComicHalftone -> {
            var y = 10f
            while (y < height) {
                var x = 10f
                while (x < width) {
                    drawCircle(
                        color = softAccent,
                        radius = if ((x + y).toInt() % 3 == 0) 1.9f else 1.25f,
                        center = Offset(x, y),
                    )
                    x += 16f
                }
                y += 16f
            }
        }
        UnifiedReaderSurfaceTreatment.BlueprintGrid -> {
            var x = 0f
            while (x <= width) {
                val major = x % majorGrid == 0f
                drawLine(
                    color = if (major) softAccent else faintAccent,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = if (major) 1.2f else 0.7f,
                )
                x += minorGrid
            }
            var y = 0f
            while (y <= height) {
                val major = y % majorGrid == 0f
                drawLine(
                    color = if (major) softAccent else faintAccent,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = if (major) 1.2f else 0.7f,
                )
                y += minorGrid
            }
            drawLine(softAccent, Offset(0f, 0f), Offset(width, height), strokeWidth = 0.8f)
        }
        UnifiedReaderSurfaceTreatment.VhsScanlines -> {
            var y = 0f
            while (y <= height) {
                drawRect(
                    color = if ((y / 6f).toInt() % 2 == 0) faintOverlay else Color.Black.copy(alpha = 0.035f),
                    topLeft = Offset(0f, y),
                    size = Size(width, 1.4f),
                )
                y += 6f
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.06f),
                        Color.Transparent,
                        overlayColor.copy(alpha = 0.045f),
                    ),
                ),
            )
        }
        UnifiedReaderSurfaceTreatment.DossierPaper -> {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        overlayColor.copy(alpha = 0.10f),
                        accentColor.copy(alpha = 0.035f),
                        Color.Black.copy(alpha = 0.045f),
                    ),
                    start = Offset.Zero,
                    end = Offset(width, height),
                ),
            )
            var y = 20f
            while (y < height) {
                drawLine(
                    color = accentColor.copy(alpha = 0.08f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f,
                )
                y += 28f
            }
        }
        UnifiedReaderSurfaceTreatment.Plain -> Unit
    }
}

private fun DrawScope.drawRoundedBorder(
    color: Color,
    cornerPx: Float,
    strokeWidth: Float,
    inset: Float = 0f,
    pathEffect: PathEffect? = null,
) {
    if (size.width <= inset * 2f || size.height <= inset * 2f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(inset + strokeWidth / 2f, inset + strokeWidth / 2f),
        size = Size(
            width = size.width - (inset * 2f) - strokeWidth,
            height = size.height - (inset * 2f) - strokeWidth,
        ),
        cornerRadius = CornerRadius(cornerPx, cornerPx),
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}
