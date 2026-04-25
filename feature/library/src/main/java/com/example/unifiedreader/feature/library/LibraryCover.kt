package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
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
        CoverSurfaceTreatment()
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
private fun BoxScope.CoverSurfaceTreatment() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f),
                    ),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .fillMaxHeight()
            .width(7.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.18f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(1.dp)
            .height(18.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

private data class LibraryCoverStyle(
    val elevation: Dp,
    val shape: RoundedCornerShape,
    val borderAlpha: Float,
)

private fun libraryCoverStyle(variant: LibraryCoverImageVariant): LibraryCoverStyle = when (variant) {
    LibraryCoverImageVariant.Grid -> LibraryCoverStyle(
        elevation = 8.dp,
        shape = RoundedCornerShape(6.dp),
        borderAlpha = 0.64f,
    )
    LibraryCoverImageVariant.Detailed -> LibraryCoverStyle(
        elevation = 7.dp,
        shape = RoundedCornerShape(6.dp),
        borderAlpha = 0.60f,
    )
    LibraryCoverImageVariant.ShelfPreview -> LibraryCoverStyle(
        elevation = 6.dp,
        shape = RoundedCornerShape(5.dp),
        borderAlpha = 0.58f,
    )
    LibraryCoverImageVariant.ShelfRow -> LibraryCoverStyle(
        elevation = 3.dp,
        shape = RoundedCornerShape(4.dp),
        borderAlpha = 0.56f,
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
