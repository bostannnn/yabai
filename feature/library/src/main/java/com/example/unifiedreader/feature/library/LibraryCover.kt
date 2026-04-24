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
    val coverShape = RoundedCornerShape(4.dp)
    val coverBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .clip(coverShape)
            .background(Color(0xFF24313A))
            .border(
                width = 1.dp,
                color = coverBorderColor,
                shape = coverShape,
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
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                tonalElevation = 2.dp,
            ) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
    }
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
