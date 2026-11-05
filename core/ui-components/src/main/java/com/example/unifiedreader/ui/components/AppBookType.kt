package com.example.unifiedreader.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The book face: a book's title under its cover, on the reading card and on the book page is set in it,
 * so the title reads as the book's and everything around it as the app's. The device's serif (Noto Serif
 * on Android); never bolder than Medium. A comic's title stays in the app's face: comics have lettering,
 * not a book face.
 */
object AppBookType {
    val family: FontFamily = FontFamily.Serif

    /** [style] in the book face, at Medium unless it asks for Regular. */
    fun title(style: TextStyle): TextStyle = style.copy(
        fontFamily = family,
        fontWeight = if (style.fontWeight == FontWeight.Normal) FontWeight.Normal else FontWeight.Medium,
        letterSpacing = style.letterSpacing,
    )

    /** [title] for a book; for a [comic], [style] as it is, at Medium. */
    fun title(style: TextStyle, comic: Boolean): TextStyle =
        if (comic) style.copy(fontWeight = FontWeight.Medium) else title(style)
}

/**
 * A cover as a bound book: 2 on the spine side and 5 on the fore-edge, a soft shadow under it and its spine
 * edge shaded. Covers are the one piece of content that is not flat on the ground. A comic is a stapled
 * issue, not a bound book: [comicShape] (2 on every corner), the same shadow, no spine; a comic series
 * lies as a pile of its issues ([comicPile]).
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

    val comicCorner: Dp = 2.dp
    val comicShape: Shape = RoundedCornerShape(comicCorner)

    /** How far each issue under a comic series' cover shows, down and toward the end. */
    val pileStep: Dp = 4.dp

    /** What the cover gives up at its end and bottom for the pile: two issues show under it. */
    val pileDepth: Dp = pileStep * 2

    // The far issue, then the near one: the cover's own art this much darker.
    private val pileShades = floatArrayOf(0.55f, 0.35f)
    private val pileEdge: Dp = 1.dp

    /**
     * The issues under a comic series' cover: what this draws (the cover, already [pileDepth] smaller than
     * its slot) twice more behind itself, [pileStep] and twice that down and toward the end, darkened, each
     * outlined in [gap] (the ground) so the issues read apart. Put it before the cover's shadow and clip.
     */
    fun Modifier.comicPile(gap: Color): Modifier = drawWithContent {
        val outline = comicShape.createOutline(size, layoutDirection, this)
        val toEnd = if (layoutDirection == LayoutDirection.Ltr) 1f else -1f
        val step = pileStep.toPx()
        val edge = Stroke(width = pileEdge.toPx())
        pileShades.forEachIndexed { index, shade ->
            val offset = step * (pileShades.size - index)
            translate(left = offset * toEnd, top = offset) {
                this@drawWithContent.drawContent()
                drawOutline(outline, Color.Black.copy(alpha = shade))
                drawOutline(outline, gap, style = edge)
            }
        }
        drawContent()
    }

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
