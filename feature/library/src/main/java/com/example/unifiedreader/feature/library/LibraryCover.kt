package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.ReadableItem

@Composable
fun LibraryCover(
    item: ReadableItem,
    coverUrl: String? = item.coverUrl,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    downloadBadgeBottomInset: Dp = 0.dp,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.Grid,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val coverStyle = libraryCoverStyle(imageVariant)
    val coverBorderColor = colors.outlineVariant.copy(alpha = coverStyle.borderAlpha)
    val coverBackdrop = Brush.linearGradient(
        colors = listOf(
            Color(0xFF283846),
            colors.primary.copy(alpha = 0.48f),
            Color(0xFF111820),
        ),
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = coverStyle.elevation,
                shape = coverStyle.shape,
                clip = false,
            )
            .clip(coverStyle.shape)
            .background(coverBackdrop)
            .border(
                width = 1.dp,
                color = coverBorderColor,
                shape = coverStyle.shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val imageUrl = coverUrl?.takeIf(String::isNotBlank)
        if (imageUrl != null) {
            BucketedCoverImage(
                imageUrl = imageUrl,
                contentDescription = item.title,
                variant = imageVariant,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = formatBadgeText(item.format),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        CoverSurfaceTreatment(
            coverStyle = coverStyle,
            modifier = Modifier.fillMaxSize(),
        )
        if (isRead) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
            )
            LibraryReadStatusBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            )
        }
        if (isDownloaded) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp + downloadBadgeBottomInset),
                shape = RoundedCornerShape(8.dp),
                color = colors.primaryContainer.copy(alpha = 0.96f),
                tonalElevation = 2.dp,
                shadowElevation = 3.dp,
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = colors.onPrimaryContainer,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CoverSurfaceTreatment(
    coverStyle: LibraryCoverStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawWithCache {
            val width = size.width
            val height = size.height
            val spineWidth = width * coverStyle.spineWidthFraction
            val outerShadowWidth = spineWidth * 0.32f
            val ridgeX = spineWidth * 0.34f
            val ridgeGlowWidth = spineWidth * 0.42f
            val ridgeCoreWidth = (width * coverStyle.highlightWidthFraction).coerceAtLeast(1f)
            val creaseX = spineWidth * 0.58f
            val creaseWidth = spineWidth * 0.22f
            val innerSheenX = spineWidth * 0.74f
            val topSheenHeight = height * coverStyle.topSheenHeightFraction
            val rightEdgeWidth = width * 0.035f
            val bottomEdgeHeight = height * 0.035f

            val pageSheen = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.10f),
                ),
                startY = 0f,
                endY = height,
            )
            val spineFace = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.28f),
                    Color.Black.copy(alpha = 0.16f),
                    Color.Black.copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                startX = 0f,
                endX = spineWidth,
            )
            val outerShadow = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.50f),
                    Color.Black.copy(alpha = 0.22f),
                    Color.Transparent,
                ),
                startX = 0f,
                endX = outerShadowWidth,
            )
            val ridgeGlow = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.18f),
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                startX = ridgeX - (ridgeGlowWidth * 0.45f),
                endX = ridgeX + ridgeGlowWidth,
            )
            val ridgeCore = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
                startX = ridgeX - ridgeCoreWidth,
                endX = ridgeX + ridgeCoreWidth,
            )
            val creaseShadow = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.44f),
                    Color.Black.copy(alpha = 0.18f),
                    Color.Transparent,
                ),
                startX = creaseX - creaseWidth,
                endX = creaseX + creaseWidth,
            )
            val innerSheen = Brush.horizontalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                startX = innerSheenX,
                endX = spineWidth,
            )
            val faceFalloff = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                startX = spineWidth,
                endX = spineWidth + (width * 0.10f),
            )
            val topSheen = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = topSheenHeight,
            )
            val rightEdgeShadow = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.16f),
                ),
                startX = width - rightEdgeWidth,
                endX = width,
            )
            val bottomEdgeShadow = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.14f),
                ),
                startY = height - bottomEdgeHeight,
                endY = height,
            )

            onDrawBehind {
                drawRect(brush = pageSheen)
                drawRect(
                    brush = spineFace,
                    topLeft = Offset.Zero,
                    size = Size(spineWidth, height),
                )
                drawRect(
                    brush = outerShadow,
                    topLeft = Offset.Zero,
                    size = Size(outerShadowWidth, height),
                )
                drawRect(
                    brush = ridgeGlow,
                    topLeft = Offset(ridgeX - (ridgeGlowWidth * 0.45f), 0f),
                    size = Size(ridgeGlowWidth * 1.45f, height),
                )
                drawRect(
                    brush = ridgeCore,
                    topLeft = Offset(ridgeX - ridgeCoreWidth, 0f),
                    size = Size(ridgeCoreWidth * 2f, height),
                )
                drawRect(
                    brush = creaseShadow,
                    topLeft = Offset(creaseX - creaseWidth, 0f),
                    size = Size(creaseWidth * 2f, height),
                )
                drawRect(
                    brush = innerSheen,
                    topLeft = Offset(innerSheenX, 0f),
                    size = Size(spineWidth - innerSheenX, height),
                )
                drawRect(
                    brush = faceFalloff,
                    topLeft = Offset(spineWidth, 0f),
                    size = Size(width * 0.10f, height),
                )
                drawRect(
                    brush = topSheen,
                    topLeft = Offset(1f, 1f),
                    size = Size(width - 2f, topSheenHeight),
                )
                drawRect(
                    brush = rightEdgeShadow,
                    topLeft = Offset(width - rightEdgeWidth, 0f),
                    size = Size(rightEdgeWidth, height),
                )
                drawRect(
                    brush = bottomEdgeShadow,
                    topLeft = Offset(0f, height - bottomEdgeHeight),
                    size = Size(width, bottomEdgeHeight),
                )
            }
        },
    )
}

private data class LibraryCoverStyle(
    val elevation: Dp,
    val shape: RoundedCornerShape,
    val borderAlpha: Float,
    val spineWidthFraction: Float,
    val highlightWidthFraction: Float,
    val topSheenHeightFraction: Float,
)

private fun libraryCoverStyle(variant: LibraryCoverImageVariant): LibraryCoverStyle = when (variant) {
    LibraryCoverImageVariant.Grid,
    LibraryCoverImageVariant.GridLarge -> LibraryCoverStyle(
        elevation = 8.dp,
        shape = RoundedCornerShape(6.dp),
        borderAlpha = 0.64f,
        spineWidthFraction = 0.064f,
        highlightWidthFraction = 0.0040f,
        topSheenHeightFraction = 0.055f,
    )
    LibraryCoverImageVariant.Detailed -> LibraryCoverStyle(
        elevation = 7.dp,
        shape = RoundedCornerShape(6.dp),
        borderAlpha = 0.60f,
        spineWidthFraction = 0.064f,
        highlightWidthFraction = 0.0040f,
        topSheenHeightFraction = 0.060f,
    )
    LibraryCoverImageVariant.ShelfPreview -> LibraryCoverStyle(
        elevation = 6.dp,
        shape = RoundedCornerShape(5.dp),
        borderAlpha = 0.58f,
        spineWidthFraction = 0.060f,
        highlightWidthFraction = 0.0038f,
        topSheenHeightFraction = 0.060f,
    )
    LibraryCoverImageVariant.ShelfRow -> LibraryCoverStyle(
        elevation = 3.dp,
        shape = RoundedCornerShape(4.dp),
        borderAlpha = 0.56f,
        spineWidthFraction = 0.056f,
        highlightWidthFraction = 0.0036f,
        topSheenHeightFraction = 0.065f,
    )
}

@Composable
fun ResolvedLibraryCover(
    item: ReadableItem,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    downloadBadgeBottomInset: Dp = 0.dp,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.Grid,
    modifier: Modifier = Modifier,
) {
    LibraryCover(
        item = item,
        coverUrl = coverImageUrlProvider(item, imageVariant),
        isDownloaded = isDownloaded,
        isRead = isRead,
        downloadBadgeBottomInset = downloadBadgeBottomInset,
        imageVariant = imageVariant,
        modifier = modifier,
    )
}

@Composable
internal fun BucketedCoverImage(
    imageUrl: String,
    contentDescription: String?,
    variant: LibraryCoverImageVariant,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = rememberLibraryCoverImageRequest(
        context = context,
        imageUrl = imageUrl,
        variant = variant,
    )

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
