package com.example.unifiedreader.feature.reader.core.curl

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntRect

/**
 * The canvas-sized pictures a turn shows (see [PageCurlAgsl]): the turning leaf's front and back,
 * what it uncovers, and everything off the leaf.
 */
data class PageCurlFrames(
    val sheetFront: ImageBitmap,
    val sheetBack: ImageBitmap,
    val revealed: ImageBitmap,
    val outside: ImageBitmap,
)

/** Frames for [turn] from the reader's current view and the view of the page it turns to. */
fun pageCurlFrames(
    turn: PageCurlTurn,
    currentPage: ImageBitmap,
    adjacentPage: ImageBitmap,
): PageCurlFrames {
    val sheetFront = if (turn.sheetIsAdjacentPage) adjacentPage else currentPage
    return PageCurlFrames(
        sheetFront = sheetFront,
        sheetBack = if (turn.backIsFacingPage) adjacentPage else sheetFront,
        revealed = if (turn.sheetIsAdjacentPage) currentPage else adjacentPage,
        outside = currentPage,
    )
}

/** Where the grabbed edge of the turning leaf currently is; unspecified while no turn is shown. */
@Stable
class PageCurlState {
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

/** The paper colour of a page back that is not another page (single pages). */
val DefaultPageCurlBackTint = Color(red = 0.965f, green = 0.955f, blue = 0.94f)

/**
 * Draws [turn] with the leaf grabbed at [grab] and its edge at [state]'s edge (at rest while that is
 * unspecified). Below API 33 there is no shader, and the current view is drawn as is.
 */
@Composable
fun PageCurlSurface(
    turn: PageCurlTurn,
    grab: Offset,
    frames: PageCurlFrames,
    state: PageCurlState,
    modifier: Modifier = Modifier,
    backTint: Color = DefaultPageCurlBackTint,
) {
    if (isPageCurlShaderSupported()) {
        PageCurlShaderSurfaceApi33(
            turn = turn,
            grab = grab,
            frames = frames,
            state = state,
            backTint = backTint,
            modifier = modifier,
        )
    } else {
        Canvas(modifier = modifier) {
            drawImage(frames.outside)
        }
    }
}

fun isPageCurlShaderSupported(
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PageCurlShaderSurfaceApi33(
    turn: PageCurlTurn,
    grab: Offset,
    frames: PageCurlFrames,
    state: PageCurlState,
    backTint: Color,
    modifier: Modifier,
) {
    val shader = remember { RuntimeShader(PageCurlAgsl) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val sheetFrontShader = rememberPageCurlBitmapShader(frames.sheetFront)
    val sheetBackShader = rememberPageCurlBitmapShader(frames.sheetBack)
    val revealedShader = rememberPageCurlBitmapShader(frames.revealed)
    val outsideShader = rememberPageCurlBitmapShader(frames.outside)

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        if (turn.paperBounds.isEmpty || turn.bookBounds.isEmpty) return@Canvas
        val edge = state.edge.takeIf { it.isSpecified } ?: turn.restingEdge(grab)
        val axis = calculatePageCurlAxis(
            sheet = turn.sheet,
            grab = grab,
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
        shader.setFloatUniform("maxRadius", turn.sheet.width * DefaultPageCurlMaxRadiusFraction)
        shader.setFloatUniform("hingeX", turn.sheet.hingeX)
        shader.setFloatUniform("backMirrored", if (turn.backIsFacingPage) 1f else 0f)
        shader.setFloatUniform("backTint", backTint.red, backTint.green, backTint.blue, 1f)

        drawRect(
            brush = brush,
            topLeft = Offset(turn.bookBounds.left.toFloat(), turn.bookBounds.top.toFloat()),
            size = Size(turn.bookBounds.width.toFloat(), turn.bookBounds.height.toFloat()),
        )
    }
}

@Composable
private fun rememberPageCurlBitmapShader(
    image: ImageBitmap,
): BitmapShader = remember(image) {
    BitmapShader(
        image.asAndroidBitmap(),
        Shader.TileMode.CLAMP,
        Shader.TileMode.CLAMP,
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun RuntimeShader.setRectUniform(
    name: String,
    rect: IntRect,
) {
    setFloatUniform(
        name,
        rect.left.toFloat(),
        rect.top.toFloat(),
        rect.right.toFloat(),
        rect.bottom.toFloat(),
    )
}
