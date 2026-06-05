package com.example.unifiedreader.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.unifiedReaderSurfacePattern(
    treatment: UnifiedReaderSurfaceTreatment,
    accentColor: Color,
    overlayColor: Color = Color.White,
    intensity: Float = 1f,
): Modifier = when (treatment) {
    UnifiedReaderSurfaceTreatment.Plain -> this
    else -> drawWithCache {
        onDrawBehind {
            drawUnifiedReaderSurfaceTreatment(
                treatment = treatment,
                accentColor = accentColor,
                overlayColor = overlayColor,
                intensity = intensity,
            )
        }
    }
}

fun Modifier.unifiedReaderContentOverlay(
    treatment: UnifiedReaderSurfaceTreatment,
    accentColor: Color,
    overlayColor: Color = Color.White,
    intensity: Float = 1f,
    shape: Shape? = null,
): Modifier = when (treatment) {
    UnifiedReaderSurfaceTreatment.Plain -> this
    else -> drawWithCache {
        val outline = shape?.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawClippedToOutline(outline) {
                drawUnifiedReaderSurfaceTreatment(
                    treatment = treatment,
                    accentColor = accentColor,
                    overlayColor = overlayColor,
                    intensity = intensity,
                )
            }
        }
    }
}

fun Modifier.unifiedReaderComponentTreatment(
    recipe: UnifiedReaderComponentRecipe,
    accentColor: Color,
    secondaryColor: Color,
    overlayColor: Color,
    shape: Shape,
    active: Boolean = false,
): Modifier {
    val effectiveBorderAlpha = if (active) {
        (recipe.borderAlpha * 1.35f).coerceAtMost(1f)
    } else {
        recipe.borderAlpha
    }
    val effectiveFrameAlpha = if (active) {
        (recipe.frameAlpha * 1.35f).coerceAtMost(1f)
    } else {
        recipe.frameAlpha
    }
    return this
        .unifiedReaderContentOverlay(
            treatment = recipe.surfaceTreatment,
            accentColor = accentColor,
            overlayColor = overlayColor,
            intensity = recipe.patternIntensity * ComponentOverlayPatternIntensityScale,
            shape = shape,
        )
        .unifiedReaderSelectionTreatment(
            treatment = recipe.selectionTreatment,
            accentColor = accentColor,
            secondaryColor = secondaryColor,
            shape = shape,
            active = active,
        )
        .then(
            if (effectiveBorderAlpha > 0f) {
                Modifier.unifiedReaderBorderTreatment(
                    treatment = recipe.borderTreatment,
                    color = accentColor,
                    shape = shape,
                    width = recipe.borderWidth,
                    alpha = effectiveBorderAlpha,
                )
            } else {
                Modifier
            },
        )
        .unifiedReaderFrameTreatment(
            treatment = recipe.frameTreatment,
            accentColor = accentColor,
            secondaryColor = secondaryColor,
            shape = shape,
            alpha = effectiveFrameAlpha,
        )
}

private const val ComponentOverlayPatternIntensityScale = 0.18f
private const val CompactSelectionAlpha = 0.78f

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

fun Modifier.unifiedReaderBorderTreatment(
    treatment: UnifiedReaderBorderTreatment,
    color: Color,
    shape: Shape,
    width: Dp = 1.dp,
    alpha: Float = 1f,
): Modifier = drawWithCache {
    val strokeWidth = width.toPx().coerceAtLeast(1f)
    val borderColor = color.copy(alpha = alpha)
    val inkShadow = Color.Black.copy(alpha = (alpha * 0.20f).coerceAtMost(0.20f))
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    val outline = shape.createOutline(size, layoutDirection, this)

    onDrawWithContent {
        drawContent()
        when (treatment) {
            UnifiedReaderBorderTreatment.Single -> {
                drawOutlineBorder(
                    outline = outline,
                    color = borderColor,
                    strokeWidth = strokeWidth,
                )
            }
            UnifiedReaderBorderTreatment.Double -> {
                drawOutlineBorder(
                    outline = outline,
                    color = borderColor,
                    strokeWidth = strokeWidth,
                )
                val inset = strokeWidth * 2.4f
                drawOutlineBorder(
                    outline = outline,
                    color = borderColor.copy(alpha = borderColor.alpha * 0.72f),
                    strokeWidth = (strokeWidth * 0.74f).coerceAtLeast(1f),
                    inset = inset,
                )
            }
            UnifiedReaderBorderTreatment.Ink -> {
                drawOutlineBorder(
                    outline = outline,
                    color = inkShadow,
                    strokeWidth = strokeWidth * 1.6f,
                    inset = 1.5f,
                )
                drawOutlineBorder(
                    outline = outline,
                    color = borderColor,
                    strokeWidth = strokeWidth * 1.1f,
                )
                drawOutlineBorder(
                    outline = outline,
                    color = Color.White.copy(alpha = alpha * 0.16f),
                    strokeWidth = 1f,
                    inset = strokeWidth * 1.5f,
                )
            }
            UnifiedReaderBorderTreatment.Dashed -> {
                drawOutlineBorder(
                    outline = outline,
                    color = borderColor,
                    strokeWidth = strokeWidth,
                    pathEffect = dashEffect,
                )
            }
        }
    }
}

private fun Modifier.unifiedReaderSelectionTreatment(
    treatment: UnifiedReaderSelectionTreatment,
    accentColor: Color,
    secondaryColor: Color,
    shape: Shape,
    active: Boolean,
): Modifier {
    if (!active) return this
    return when (treatment) {
        UnifiedReaderSelectionTreatment.Underline,
        UnifiedReaderSelectionTreatment.SideMark,
        UnifiedReaderSelectionTreatment.Stamp -> drawWithCache {
            val strokeWidth = 2.dp.toPx().coerceAtLeast(1f)
            val inset = 5.dp.toPx()
            val markWidth = 3.dp.toPx().coerceAtLeast(strokeWidth)
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawContent()
                drawClippedToOutline(outline) {
                    when (treatment) {
                        UnifiedReaderSelectionTreatment.Underline -> drawLine(
                            color = secondaryColor.copy(alpha = CompactSelectionAlpha),
                            start = Offset(inset, size.height - strokeWidth / 2f),
                            end = Offset(size.width - inset, size.height - strokeWidth / 2f),
                            strokeWidth = strokeWidth,
                        )
                        UnifiedReaderSelectionTreatment.SideMark -> drawRect(
                            color = accentColor.copy(alpha = CompactSelectionAlpha),
                            size = Size(markWidth, size.height),
                        )
                        UnifiedReaderSelectionTreatment.Stamp -> {
                            drawOutlineBorder(
                                outline = outline,
                                color = secondaryColor.copy(alpha = CompactSelectionAlpha),
                                strokeWidth = strokeWidth,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f),
                            )
                            drawLine(
                                color = accentColor.copy(alpha = CompactSelectionAlpha * 0.56f),
                                start = Offset(size.width * 0.18f, size.height * 0.74f),
                                end = Offset(size.width * 0.82f, size.height * 0.26f),
                                strokeWidth = 1.dp.toPx().coerceAtLeast(1f),
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
        UnifiedReaderSelectionTreatment.Fill,
        UnifiedReaderSelectionTreatment.Inverse -> this
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

private fun Modifier.unifiedReaderFrameTreatment(
    treatment: UnifiedReaderFrameTreatment,
    accentColor: Color,
    secondaryColor: Color,
    shape: Shape,
    alpha: Float,
): Modifier = when (treatment) {
    UnifiedReaderFrameTreatment.None -> this
    else -> if (alpha <= 0f) {
        this
    } else {
        drawWithCache {
            val thin = 1.dp.toPx().coerceAtLeast(1f)
            val medium = 2.dp.toPx().coerceAtLeast(thin)
            val inset = 5.dp.toPx()
            val detail = 10.dp.toPx()
            val primary = accentColor.copy(alpha = (accentColor.alpha * alpha).coerceIn(0f, 1f))
            val secondary = secondaryColor.copy(alpha = (secondaryColor.alpha * alpha).coerceIn(0f, 1f))
            val outline = shape.createOutline(size, layoutDirection, this)
            val frameVisibility = outline.frameVisibility()

            onDrawWithContent {
                drawContent()
                drawClippedToOutline(outline) {
                    when (treatment) {
                        UnifiedReaderFrameTreatment.Terminal -> {
                            if (frameVisibility.topStart) {
                                drawLine(primary, Offset.Zero, Offset(detail, 0f), medium)
                                drawLine(primary, Offset.Zero, Offset(0f, detail), medium)
                            }
                            if (frameVisibility.bottomEnd) {
                                drawLine(primary, Offset(size.width - detail, size.height), Offset(size.width, size.height), medium)
                                drawLine(primary, Offset(size.width, size.height - detail), Offset(size.width, size.height), medium)
                            }
                            if (frameVisibility.bottom) {
                                drawRect(
                                    color = secondary.copy(alpha = secondary.alpha * 0.72f),
                                    topLeft = Offset(inset, size.height - medium * 2f),
                                    size = Size(detail * 0.65f, medium),
                                )
                            }
                        }
                        UnifiedReaderFrameTreatment.Ornate -> {
                            val centerX = size.width / 2f
                            if (frameVisibility.top) {
                                drawLine(primary, Offset(inset, 0f), Offset(centerX - detail, 0f), thin)
                                drawLine(primary, Offset(centerX + detail, 0f), Offset(size.width - inset, 0f), thin)
                                drawCircle(secondary, radius = medium * 1.2f, center = Offset(centerX, 0f))
                            }
                            if (frameVisibility.bottom) {
                                drawLine(primary, Offset(inset, size.height), Offset(centerX - detail, size.height), thin)
                                drawLine(primary, Offset(centerX + detail, size.height), Offset(size.width - inset, size.height), thin)
                                drawCircle(secondary, radius = medium * 1.2f, center = Offset(centerX, size.height))
                            }
                        }
                        UnifiedReaderFrameTreatment.Comic -> {
                            if (frameVisibility.bottom) {
                                drawLine(Color.Black.copy(alpha = 0.34f * alpha), Offset(3f, size.height - medium), Offset(size.width, size.height - medium), medium)
                            }
                            if (frameVisibility.end) {
                                drawLine(Color.Black.copy(alpha = 0.34f * alpha), Offset(size.width - medium, 3f), Offset(size.width - medium, size.height), medium)
                            }
                            if (frameVisibility.top) {
                                drawLine(primary, Offset(0f, 0f), Offset(detail, 0f), medium)
                                drawLine(secondary, Offset(size.width - detail, 0f), Offset(size.width, 0f), medium)
                            }
                        }
                        UnifiedReaderFrameTreatment.Blueprint -> {
                            drawCornerMarks(
                                color = primary,
                                length = detail,
                                strokeWidth = medium,
                                visibility = frameVisibility,
                            )
                            if (frameVisibility.top) {
                                drawLine(primary.copy(alpha = primary.alpha * 0.64f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, inset), thin)
                            }
                            if (frameVisibility.bottom) {
                                drawLine(primary.copy(alpha = primary.alpha * 0.64f), Offset(size.width / 2f, size.height - inset), Offset(size.width / 2f, size.height), thin)
                            }
                            if (frameVisibility.start) {
                                drawLine(primary.copy(alpha = primary.alpha * 0.64f), Offset(0f, size.height / 2f), Offset(inset, size.height / 2f), thin)
                            }
                            if (frameVisibility.end) {
                                drawLine(primary.copy(alpha = primary.alpha * 0.64f), Offset(size.width - inset, size.height / 2f), Offset(size.width, size.height / 2f), thin)
                            }
                        }
                        UnifiedReaderFrameTreatment.Vhs -> {
                            if (frameVisibility.top) {
                                drawLine(primary, Offset(inset, 0f), Offset(size.width * 0.62f, 0f), medium)
                            }
                            if (frameVisibility.bottom) {
                                drawLine(secondary, Offset(size.width * 0.38f, size.height), Offset(size.width - inset, size.height), medium)
                            }
                            if (frameVisibility.start) {
                                drawLine(primary.copy(alpha = primary.alpha * 0.32f), Offset(0f, inset), Offset(detail * 1.8f, inset), thin)
                            }
                            if (frameVisibility.end) {
                                drawLine(secondary.copy(alpha = secondary.alpha * 0.32f), Offset(size.width - detail * 1.8f, size.height - inset), Offset(size.width, size.height - inset), thin)
                            }
                        }
                        UnifiedReaderFrameTreatment.Dossier -> {
                            when {
                                frameVisibility.start -> drawLine(secondary, Offset(inset, 0f), Offset(inset, size.height), medium)
                                frameVisibility.end -> drawLine(secondary, Offset(size.width - inset, 0f), Offset(size.width - inset, size.height), medium)
                            }
                            if (frameVisibility.top) {
                                drawLine(primary, Offset(0f, inset), Offset(size.width, inset), thin)
                                drawRect(
                                    color = secondary.copy(alpha = secondary.alpha * 0.48f),
                                    topLeft = Offset(size.width - detail * 1.4f, 0f),
                                    size = Size(detail, medium),
                                )
                            }
                            if (frameVisibility.bottom) {
                                drawLine(primary.copy(alpha = primary.alpha * 0.52f), Offset(0f, size.height - inset), Offset(size.width, size.height - inset), thin)
                            }
                        }
                        UnifiedReaderFrameTreatment.None -> Unit
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawUnifiedReaderSurfaceTreatment(
    treatment: UnifiedReaderSurfaceTreatment,
    accentColor: Color,
    overlayColor: Color,
    intensity: Float,
) {
    val width = size.width
    val height = size.height
    val minorGrid = 18f
    val majorGrid = minorGrid * 4f
    val clampedIntensity = intensity.coerceIn(0f, 1.5f)
    val softAccent = accentColor.copy(alpha = (0.12f * clampedIntensity).coerceAtMost(1f))
    val faintAccent = accentColor.copy(alpha = (0.055f * clampedIntensity).coerceAtMost(1f))
    val faintOverlay = overlayColor.copy(alpha = (0.045f * clampedIntensity).coerceAtMost(1f))

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
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f * clampedIntensity)),
                    center = Offset(width * 0.5f, height * 0.45f),
                    radius = maxOf(width, height) * 0.78f,
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        overlayColor.copy(alpha = 0.05f * clampedIntensity),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f * clampedIntensity),
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
                    color = if ((y / 6f).toInt() % 2 == 0) {
                        faintOverlay
                    } else {
                        Color.Black.copy(alpha = 0.035f * clampedIntensity)
                    },
                    topLeft = Offset(0f, y),
                    size = Size(width, 1.4f),
                )
                y += 6f
            }
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.06f * clampedIntensity),
                        Color.Transparent,
                        overlayColor.copy(alpha = 0.045f * clampedIntensity),
                    ),
                ),
            )
        }
        UnifiedReaderSurfaceTreatment.DossierPaper -> {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        overlayColor.copy(alpha = 0.10f * clampedIntensity),
                        accentColor.copy(alpha = 0.035f * clampedIntensity),
                        Color.Black.copy(alpha = 0.045f * clampedIntensity),
                    ),
                    start = Offset.Zero,
                    end = Offset(width, height),
                ),
            )
            var y = 20f
            while (y < height) {
                drawLine(
                    color = accentColor.copy(alpha = 0.08f * clampedIntensity),
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

private fun DrawScope.drawCornerMarks(
    color: Color,
    length: Float,
    strokeWidth: Float,
    visibility: FrameVisibility = FrameVisibility.All,
) {
    if (visibility.topStart) {
        drawLine(color, Offset.Zero, Offset(length, 0f), strokeWidth)
        drawLine(color, Offset.Zero, Offset(0f, length), strokeWidth)
    }
    if (visibility.topEnd) {
        drawLine(color, Offset(size.width - length, 0f), Offset(size.width, 0f), strokeWidth)
        drawLine(color, Offset(size.width, 0f), Offset(size.width, length), strokeWidth)
    }
    if (visibility.bottomStart) {
        drawLine(color, Offset(0f, size.height - length), Offset(0f, size.height), strokeWidth)
        drawLine(color, Offset(0f, size.height), Offset(length, size.height), strokeWidth)
    }
    if (visibility.bottomEnd) {
        drawLine(color, Offset(size.width - length, size.height), Offset(size.width, size.height), strokeWidth)
        drawLine(color, Offset(size.width, size.height - length), Offset(size.width, size.height), strokeWidth)
    }
}

private inline fun DrawScope.drawClippedToOutline(
    outline: Outline?,
    crossinline block: DrawScope.() -> Unit,
) {
    when (outline) {
        null -> block()
        is Outline.Rectangle -> clipRect(
            left = outline.rect.left,
            top = outline.rect.top,
            right = outline.rect.right,
            bottom = outline.rect.bottom,
        ) {
            block()
        }
        is Outline.Rounded -> clipPath(
            Path().apply { addRoundRect(outline.roundRect) },
        ) {
            block()
        }
        is Outline.Generic -> clipPath(outline.path) {
            block()
        }
    }
}

private fun DrawScope.drawOutlineBorder(
    outline: Outline,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
    inset: Float = 0f,
) {
    val stroke = Stroke(width = strokeWidth, pathEffect = pathEffect)
    when (outline) {
        is Outline.Rectangle -> {
            val strokeInset = inset + strokeWidth / 2f
            if (outline.rect.width <= strokeInset * 2f || outline.rect.height <= strokeInset * 2f) return
            drawRect(
                color = color,
                topLeft = Offset(outline.rect.left + strokeInset, outline.rect.top + strokeInset),
                size = Size(outline.rect.width - strokeInset * 2f, outline.rect.height - strokeInset * 2f),
                style = stroke,
            )
        }
        is Outline.Rounded -> {
            val roundedRect = outline.roundRect.insetForStroke(inset, strokeWidth) ?: return
            drawPath(
                path = Path().apply { addRoundRect(roundedRect) },
                color = color,
                style = stroke,
            )
        }
        is Outline.Generic -> {
            if (inset > 0f) return
            drawPath(
                path = outline.path,
                color = color,
                style = stroke,
            )
        }
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

private data class FrameVisibility(
    val top: Boolean,
    val bottom: Boolean,
    val start: Boolean,
    val end: Boolean,
    val topStart: Boolean,
    val topEnd: Boolean,
    val bottomStart: Boolean,
    val bottomEnd: Boolean,
) {
    companion object {
        val All = FrameVisibility(
            top = true,
            bottom = true,
            start = true,
            end = true,
            topStart = true,
            topEnd = true,
            bottomStart = true,
            bottomEnd = true,
        )
    }
}

private fun Outline.frameVisibility(): FrameVisibility = when (this) {
    is Outline.Rectangle,
    is Outline.Generic -> FrameVisibility.All
    is Outline.Rounded -> {
        val topStart = roundRect.topLeftCornerRadius.hasRadius()
        val topEnd = roundRect.topRightCornerRadius.hasRadius()
        val bottomStart = roundRect.bottomLeftCornerRadius.hasRadius()
        val bottomEnd = roundRect.bottomRightCornerRadius.hasRadius()
        val hasRoundedEdge = topStart || topEnd || bottomStart || bottomEnd
        if (!hasRoundedEdge) {
            FrameVisibility.All
        } else {
            FrameVisibility(
                top = topStart || topEnd,
                bottom = bottomStart || bottomEnd,
                start = topStart || bottomStart,
                end = topEnd || bottomEnd,
                topStart = topStart,
                topEnd = topEnd,
                bottomStart = bottomStart,
                bottomEnd = bottomEnd,
            )
        }
    }
}

private fun RoundRect.insetForStroke(inset: Float, strokeWidth: Float): RoundRect? {
    val strokeInset = inset + strokeWidth / 2f
    if (width <= strokeInset * 2f || height <= strokeInset * 2f) return null
    return RoundRect(
        rect = Rect(
            left = left + strokeInset,
            top = top + strokeInset,
            right = right - strokeInset,
            bottom = bottom - strokeInset,
        ),
        topLeft = topLeftCornerRadius.shrink(strokeInset),
        topRight = topRightCornerRadius.shrink(strokeInset),
        bottomRight = bottomRightCornerRadius.shrink(strokeInset),
        bottomLeft = bottomLeftCornerRadius.shrink(strokeInset),
    )
}

private fun CornerRadius.hasRadius(): Boolean = x > 0.5f || y > 0.5f

private fun CornerRadius.shrink(amount: Float): CornerRadius = CornerRadius(
    x = (x - amount).coerceAtLeast(0f),
    y = (y - amount).coerceAtLeast(0f),
)
