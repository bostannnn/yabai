package com.example.unifiedreader.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The book face: a book's title under its cover, on the reading card and on the book page is set in it,
 * so the title reads as the book's and everything around it as the app's. The device's serif (Noto Serif
 * on Android); never bolder than Medium.
 */
object AppBookType {
    val family: FontFamily = FontFamily.Serif

    /** [style] in the book face, at Medium unless it asks for Regular. */
    fun title(style: TextStyle): TextStyle = style.copy(
        fontFamily = family,
        fontWeight = if (style.fontWeight == FontWeight.Normal) FontWeight.Normal else FontWeight.Medium,
        letterSpacing = style.letterSpacing,
    )
}

/**
 * A cover as a bound book: 2 on the spine side and 5 on the fore-edge, a soft shadow under it and its spine
 * edge shaded. Covers are the one piece of content that is not flat on the ground.
 */
object AppCoverShape {
    val spineCorner: Dp = 2.dp
    val edgeCorner: Dp = 5.dp
    val elevation: Dp = 6.dp

    val shape: Shape = RoundedCornerShape(
        topStart = spineCorner,
        bottomStart = spineCorner,
        topEnd = edgeCorner,
        bottomEnd = edgeCorner,
    )

    // A dark line at the spine, a thin light one beside it, then nothing within the first 9% of the width.
    private const val SpineReach = 0.09f
    private val spineStops = arrayOf(
        0f to Color.Black.copy(alpha = 0.22f),
        0.28f to Color.White.copy(alpha = 0.08f),
        0.67f to Color.Black.copy(alpha = 0.06f),
        1f to Color.Transparent,
    )

    /** The spine's shading, drawn over the cover's art. */
    fun Modifier.coverSpine(): Modifier = drawWithContent {
        drawContent()
        val reach = size.width * SpineReach
        if (reach > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(colorStops = spineStops, startX = 0f, endX = reach),
                size = size.copy(width = reach),
            )
        }
    }
}
