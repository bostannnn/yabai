package com.example.unifiedreader.feature.reader.comics

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap

/** A page turn in progress: the turn geometry, where the leaf was grabbed, and the composites it shows. */
internal data class ComicPageCurlInteraction(
    val turn: ComicPageCurlTurn,
    val targetSpreadIndex: Int,
    val grab: Offset,
    val sheetFront: ImageBitmap,
    val sheetBack: ImageBitmap,
    val revealed: ImageBitmap,
    val outside: ImageBitmap,
)

internal fun buildComicPageCurlInteraction(
    turn: ComicPageCurlTurn,
    targetSpreadIndex: Int,
    grab: Offset,
    currentPage: ImageBitmap,
    adjacentPage: ImageBitmap,
): ComicPageCurlInteraction {
    val sheetFront = if (turn.sheetIsAdjacentPage) adjacentPage else currentPage
    return ComicPageCurlInteraction(
        turn = turn,
        targetSpreadIndex = targetSpreadIndex,
        grab = grab,
        sheetFront = sheetFront,
        sheetBack = if (turn.backIsFacingPage) adjacentPage else sheetFront,
        revealed = if (turn.sheetIsAdjacentPage) currentPage else adjacentPage,
        outside = currentPage,
    )
}

/** Where the grabbed edge of the turning leaf currently is; unspecified while no turn is shown. */
@Stable
internal class ComicPageCurlState {
    var edge by mutableStateOf(Offset.Unspecified)
        private set

    fun snapTo(edge: Offset) {
        this.edge = edge
    }

    suspend fun animateTo(
        target: Offset,
        durationMillis: Int,
        easing: Easing = LinearOutSlowInEasing,
    ) {
        val start = edge
        if (!start.isSpecified) {
            edge = target
            return
        }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = easing),
        ) { fraction, _ ->
            edge = lerp(start, target, fraction)
        }
    }

    suspend fun animateAlong(
        durationMillis: Int,
        path: (fraction: Float) -> Offset,
    ) {
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        ) { fraction, _ ->
            edge = path(fraction)
        }
    }

    fun reset() {
        edge = Offset.Unspecified
    }
}

@Composable
internal fun ComicPageCurlSurface(
    interaction: ComicPageCurlInteraction,
    state: ComicPageCurlState,
    modifier: Modifier = Modifier,
) {
    if (isComicPageCurlShaderSupported()) {
        ComicPageCurlShaderSurfaceApi33(
            interaction = interaction,
            state = state,
            modifier = modifier,
        )
    } else {
        Canvas(modifier = modifier) {
            drawImage(interaction.outside)
        }
    }
}

internal fun isComicPageCurlShaderSupported(
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ComicPageCurlShaderSurfaceApi33(
    interaction: ComicPageCurlInteraction,
    state: ComicPageCurlState,
    modifier: Modifier,
) {
    val shader = remember {
        traceComicReaderSection("ComicPageCurl.createRuntimeShader") {
            RuntimeShader(ComicPageCurlAgsl)
        }
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    val sheetFrontShader = rememberComicPageCurlBitmapShader(interaction.sheetFront)
    val sheetBackShader = rememberComicPageCurlBitmapShader(interaction.sheetBack)
    val revealedShader = rememberComicPageCurlBitmapShader(interaction.revealed)
    val outsideShader = rememberComicPageCurlBitmapShader(interaction.outside)
    val turn = interaction.turn

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        if (!turn.paperBounds.hasArea || !turn.bookBounds.hasArea) return@Canvas
        val edge = state.edge.takeIf { it.isSpecified } ?: turn.restingEdge(interaction.grab)
        val axis = calculateComicPageCurlAxis(
            sheet = turn.sheet,
            grab = interaction.grab,
            edge = edge,
        )
        shader.setInputBuffer("sheetFront", sheetFrontShader)
        shader.setInputBuffer("sheetBack", sheetBackShader)
        shader.setInputBuffer("revealed", revealedShader)
        shader.setInputBuffer("outside", outsideShader)
        shader.setRectUniform("paper", turn.paperBounds)
        shader.setRectUniform("book", turn.bookBounds)
        shader.setFloatUniform("origin", axis.originX, axis.originY)
        shader.setFloatUniform("dir", axis.directionX, axis.directionY)
        shader.setFloatUniform("radius", axis.radius)
        shader.setFloatUniform("maxRadius", turn.sheet.width * DefaultCurlMaxRadiusFraction)
        shader.setFloatUniform("hingeX", turn.sheet.hingeX)
        shader.setFloatUniform("backMirrored", if (turn.backIsFacingPage) 1f else 0f)

        drawRect(
            brush = brush,
            topLeft = Offset(turn.bookBounds.left.toFloat(), turn.bookBounds.top.toFloat()),
            size = Size(turn.bookBounds.width.toFloat(), turn.bookBounds.height.toFloat()),
        )
    }
}

@Composable
private fun rememberComicPageCurlBitmapShader(
    image: ImageBitmap,
): BitmapShader = remember(image) {
    traceComicReaderSection("ComicPageCurl.createBitmapShader") {
        BitmapShader(
            image.asAndroidBitmap(),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setRectUniform(
    name: String,
    rect: ComicRasterRect,
) {
    setFloatUniform(
        name,
        rect.left.toFloat(),
        rect.top.toFloat(),
        rect.right.toFloat(),
        rect.bottom.toFloat(),
    )
}
