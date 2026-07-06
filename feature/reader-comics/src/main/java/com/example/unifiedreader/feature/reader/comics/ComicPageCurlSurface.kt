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
uniform float2 sheetOrigin;
uniform float2 sheetSize;
uniform float2 origin;
uniform float2 dir;
uniform float radius;

const float PI = 3.141592653589793;

bool inSheet(float2 p) {
    float2 q = p - sheetOrigin;
    return q.x >= 0.0 && q.y >= 0.0 && q.x <= sheetSize.x && q.y <= sheetSize.y;
}

half4 backFace(float2 paperPos, float shade) {
    half4 c = front.eval(paperPos);
    half3 paper = mix(half3(0.965, 0.955, 0.94), c.rgb, 0.12);
    return half4(paper * half3(shade), 1.0);
}

half4 underneathWithShadow(float2 p, float x) {
    half4 c = underneath.eval(p);
    float shadowDistance = max(x, 0.0);
    float contactShadow = 1.0 - smoothstep(0.0, radius * 1.8, shadowDistance);
    float sh = 1.0 - 0.35 * contactShadow;
    return half4(c.rgb * half3(sh), c.a);
}

half4 main(float2 p) {
    if (!inSheet(p)) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    float x = dot(p - origin, dir);

    if (x <= 0.0) {
        float d = PI * radius - x;
        float2 pp = p + dir * (d - x);
        if (inSheet(pp)) {
            return backFace(pp, 1.0);
        }
        half4 c = front.eval(p);
        float sh = 1.0 - 0.22 * smoothstep(-radius, 0.0, x);
        return half4(c.rgb * half3(sh), c.a);
    }

    if (x < radius) {
        float theta = asin(x / radius);

        float d2 = (PI - theta) * radius;
        float2 p2 = p + dir * (d2 - x);
        if (inSheet(p2)) {
            float shade = 1.0 - 0.38 * pow(x / radius, 2.0);
            return backFace(p2, shade);
        }

        float d1 = theta * radius;
        float2 p1 = p + dir * (d1 - x);
        if (inSheet(p1)) {
            float shade = 1.0 - 0.45 * (x / radius);
            half4 c = front.eval(p1);
            return half4(c.rgb * half3(shade), c.a);
        }

        return underneathWithShadow(p, x);
    }

    return underneathWithShadow(p, x);
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
    sheetBounds: ComicRasterRect,
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
            sheetBounds = sheetBounds,
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
    sheetBounds: ComicRasterRect,
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
                        grabY = position.y - sheetBounds.top,
                        containerHeight = sheetBounds.height.toFloat(),
                    )
                },
                onHorizontalDrag = { _, dragAmount ->
                    coroutineScope.launch {
                        state.dragBy(
                            dragAmount = dragAmount,
                            containerWidth = sheetBounds.width.toFloat(),
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
        if (!sheetBounds.hasArea) return@Canvas
        shader.setInputBuffer("front", currentPageShader)
        shader.setInputBuffer("underneath", underneathPageShader)
        val axis = calculateComicPageCurlAxisForSheet(
            progress = state.progress.value,
            tilt = state.tilt,
            sheetBounds = sheetBounds,
            readingDirection = readingDirection,
            target = target,
        )
        shader.setFloatUniform("sheetOrigin", sheetBounds.left.toFloat(), sheetBounds.top.toFloat())
        shader.setFloatUniform("sheetSize", sheetBounds.width.toFloat(), sheetBounds.height.toFloat())
        shader.setFloatUniform("origin", axis.originX, axis.originY)
        shader.setFloatUniform("dir", axis.directionX, axis.directionY)
        shader.setFloatUniform("radius", axis.radius)

        drawRect(
            brush = brush,
            topLeft = Offset(sheetBounds.left.toFloat(), sheetBounds.top.toFloat()),
            size = Size(sheetBounds.width.toFloat(), sheetBounds.height.toFloat()),
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
