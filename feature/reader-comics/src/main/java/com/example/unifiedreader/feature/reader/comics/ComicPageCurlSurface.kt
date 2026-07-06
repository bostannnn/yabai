package com.example.unifiedreader.feature.reader.comics

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ComicPageCurlAgsl = """
uniform shader front;
uniform shader underneath;
uniform float2 paperOrigin;
uniform float2 paperSize;
uniform float2 origin;
uniform float2 dir;
uniform float radius;

const float PI = 3.141592653589793;

bool inPaper(float2 p) {
    float2 q = p - paperOrigin;
    return q.x >= 0.0 && q.y >= 0.0 && q.x <= paperSize.x && q.y <= paperSize.y;
}

half4 clearPixel() {
    return half4(0.0, 0.0, 0.0, 0.0);
}

float safeRadius() {
    return max(radius, 1.0);
}

float curveLight(float x) {
    float t = clamp(x / safeRadius(), 0.0, 1.0);
    return 1.0 - 0.08 * sin(t * PI);
}

float contactLight(float x) {
    float contact = 1.0 - smoothstep(0.0, safeRadius() * 1.5, abs(x));
    return 1.0 - 0.08 * contact;
}

half4 frontFace(float2 paperPos, float x) {
    half4 c = front.eval(paperPos);
    return half4(c.rgb * half3(curveLight(x)), c.a);
}

half4 backFace(float2 paperPos, float x) {
    half4 c = front.eval(paperPos);
    half3 paper = mix(half3(0.965, 0.955, 0.94), c.rgb, 0.12);
    return half4(paper * half3(curveLight(x)), 1.0);
}

half4 underneathWithShadow(float2 p, float x) {
    half4 c = underneath.eval(p);
    return half4(c.rgb * half3(contactLight(x)), c.a);
}

half4 main(float2 p) {
    float x = dot(p - origin, dir);

    if (x <= 0.0) {
        float d = PI * radius - x;
        float2 pp = p + dir * (d - x);
        if (inPaper(pp)) {
            return backFace(pp, x);
        }
        if (inPaper(p)) {
            return frontFace(p, x);
        }
        return clearPixel();
    }

    if (x < radius) {
        float theta = asin(x / radius);

        float d2 = (PI - theta) * radius;
        float2 p2 = p + dir * (d2 - x);
        if (inPaper(p2)) {
            return backFace(p2, x);
        }

        float d1 = theta * radius;
        float2 p1 = p + dir * (d1 - x);
        if (inPaper(p1)) {
            return frontFace(p1, x);
        }

        if (inPaper(p)) {
            return underneathWithShadow(p, x);
        }
        return clearPixel();
    }

    if (inPaper(p)) {
        return underneathWithShadow(p, x);
    }
    return clearPixel();
}
"""

@Stable
internal class ComicPageCurlState(
    initialTarget: ComicPageCurlTarget = ComicPageCurlTarget.Next,
) {
    val progress = Animatable(0f)

    var target by mutableStateOf(initialTarget)
        private set

    var tilt by mutableFloatStateOf(0f)
        private set

    fun startDrag(
        target: ComicPageCurlTarget,
        grabY: Float,
        containerHeight: Float,
    ) {
        this.target = target
        tilt = comicPageCurlTiltForGrab(
            grabY = grabY,
            containerHeight = containerHeight,
        )
    }

    suspend fun dragBy(
        dragAmount: Float,
        containerWidth: Float,
        readingDirection: ComicReadingDirection,
    ) {
        progress.snapTo(
            applyComicPageCurlProgressDelta(
                progress = progress.value,
                dragAmount = dragAmount,
                containerWidth = containerWidth,
                readingDirection = readingDirection,
                target = target,
            ),
        )
    }

    suspend fun animateToRest() {
        progress.animateTo(0f, tween(220))
    }

    suspend fun animateToCommitted() {
        progress.animateTo(1f, tween(280))
    }

    suspend fun reset() {
        progress.snapTo(0f)
        tilt = 0f
    }
}

@Composable
internal fun rememberComicPageCurlState(
    initialTarget: ComicPageCurlTarget = ComicPageCurlTarget.Next,
): ComicPageCurlState = remember {
    ComicPageCurlState(initialTarget = initialTarget)
}

@Composable
internal fun ComicPageCurlSurface(
    currentPage: ImageBitmap,
    underneathPage: ImageBitmap,
    state: ComicPageCurlState,
    readingDirection: ComicReadingDirection,
    paperBounds: ComicRasterRect,
    renderBounds: ComicRasterRect = paperBounds,
    modifier: Modifier = Modifier,
    target: ComicPageCurlTarget = state.target,
    gesturesEnabled: Boolean = false,
    onTurnCommitted: () -> Unit = {},
    onTurnCancelled: () -> Unit = {},
) {
    if (isComicPageCurlShaderSupported()) {
        ComicPageCurlShaderSurfaceApi33(
            currentPage = currentPage,
            underneathPage = underneathPage,
            state = state,
            readingDirection = readingDirection,
            paperBounds = paperBounds,
            renderBounds = renderBounds,
            modifier = modifier,
            target = target,
            gesturesEnabled = gesturesEnabled,
            onTurnCommitted = onTurnCommitted,
            onTurnCancelled = onTurnCancelled,
        )
    } else {
        ComicPageCurlFallbackSurface(
            currentPage = currentPage,
            modifier = modifier,
        )
    }
}

internal fun isComicPageCurlShaderSupported(
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ComicPageCurlShaderSurfaceApi33(
    currentPage: ImageBitmap,
    underneathPage: ImageBitmap,
    state: ComicPageCurlState,
    readingDirection: ComicReadingDirection,
    paperBounds: ComicRasterRect,
    renderBounds: ComicRasterRect,
    modifier: Modifier,
    target: ComicPageCurlTarget,
    gesturesEnabled: Boolean,
    onTurnCommitted: () -> Unit,
    onTurnCancelled: () -> Unit,
) {
    val shader = remember {
        traceComicReaderSection("ComicPageCurl.createRuntimeShader") {
            RuntimeShader(ComicPageCurlAgsl)
        }
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    val currentPageShader = remember(currentPage) {
        traceComicReaderSection("ComicPageCurl.createCurrentBitmapShader") {
            BitmapShader(
                currentPage.asAndroidBitmap(),
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
        }
    }
    val underneathPageShader = remember(underneathPage) {
        traceComicReaderSection("ComicPageCurl.createUnderneathBitmapShader") {
            BitmapShader(
                underneathPage.asAndroidBitmap(),
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP,
            )
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val surfaceModifier = if (gesturesEnabled) {
        modifier.pointerInput(state, readingDirection, target) {
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    state.startDrag(
                        target = target,
                        grabY = position.y - paperBounds.top,
                        containerHeight = paperBounds.height.toFloat(),
                    )
                },
                onHorizontalDrag = { _, dragAmount ->
                    coroutineScope.launch {
                        state.dragBy(
                            dragAmount = dragAmount,
                            containerWidth = paperBounds.width.toFloat(),
                            readingDirection = readingDirection,
                        )
                    }
                },
                onDragEnd = {
                    coroutineScope.launch {
                        finishComicPageCurlGesture(
                            state = state,
                            commit = shouldCommitComicPageCurl(progress = state.progress.value),
                            onTurnCommitted = onTurnCommitted,
                            onTurnCancelled = onTurnCancelled,
                        )
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        finishComicPageCurlGesture(
                            state = state,
                            commit = false,
                            onTurnCommitted = onTurnCommitted,
                            onTurnCancelled = onTurnCancelled,
                        )
                    }
                },
            )
        }
    } else {
        modifier
    }

    Canvas(modifier = surfaceModifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        if (!paperBounds.hasArea || !renderBounds.hasArea) return@Canvas
        shader.setInputBuffer("front", currentPageShader)
        shader.setInputBuffer("underneath", underneathPageShader)
        val axis = calculateComicPageCurlAxisForSheet(
            progress = state.progress.value,
            tilt = state.tilt,
            sheetBounds = paperBounds,
            readingDirection = readingDirection,
            target = target,
        )
        shader.setFloatUniform("paperOrigin", paperBounds.left.toFloat(), paperBounds.top.toFloat())
        shader.setFloatUniform("paperSize", paperBounds.width.toFloat(), paperBounds.height.toFloat())
        shader.setFloatUniform("origin", axis.originX, axis.originY)
        shader.setFloatUniform("dir", axis.directionX, axis.directionY)
        shader.setFloatUniform("radius", axis.radius)

        drawRect(
            brush = brush,
            topLeft = Offset(renderBounds.left.toFloat(), renderBounds.top.toFloat()),
            size = Size(renderBounds.width.toFloat(), renderBounds.height.toFloat()),
        )
    }
}

private suspend fun finishComicPageCurlGesture(
    state: ComicPageCurlState,
    commit: Boolean,
    onTurnCommitted: () -> Unit,
    onTurnCancelled: () -> Unit,
) {
    try {
        if (commit) {
            state.animateToCommitted()
            onTurnCommitted()
        } else {
            state.animateToRest()
            onTurnCancelled()
        }
    } finally {
        withContext(NonCancellable) {
            state.reset()
        }
    }
}

@Composable
private fun ComicPageCurlFallbackSurface(
    currentPage: ImageBitmap,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        drawImage(currentPage)
    }
}
