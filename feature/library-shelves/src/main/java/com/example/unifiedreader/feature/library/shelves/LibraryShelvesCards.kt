package com.example.unifiedreader.feature.library.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.rememberLibraryCoverImageRequest

@Composable
internal fun ShelfPreviewCard(
    item: ReadableItem,
    title: String,
    supportingText: String,
    trailingText: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    coverLoadingEnabled: Boolean = true,
    onClick: () -> Unit,
    menuContent: (@Composable BoxScope.() -> Unit)? = null,
    progressFraction: Float? = null,
) {
    Card(
        modifier = Modifier
            .width(ShelfPreviewCardWidth)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box {
                ShelfCover(
                    item = item,
                    coverImageUrlProvider = coverImageUrlProvider,
                    onCoverVisible = { _, _ -> },
                    coverLoadingEnabled = coverLoadingEnabled,
                    notifyVisibleOnCompose = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CoverAspectRatio),
                )
                progressFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .clip(
                                RoundedCornerShape(
                                    bottomStart = 14.dp,
                                    bottomEnd = 14.dp,
                                ),
                            ),
                    )
                }
                menuContent?.invoke(this)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ShelfListRow(
    item: ReadableItem,
    title: String,
    supportingText: String,
    trailingText: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    coverLoadingEnabled: Boolean = true,
    onClick: () -> Unit,
    menuContent: (@Composable () -> Unit)? = null,
    progressFraction: Float? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 42.dp, height = 58.dp),
        ) {
            ShelfCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                onCoverVisible = onCoverVisible,
                coverLoadingEnabled = coverLoadingEnabled,
                modifier = Modifier.fillMaxSize(),
            )
            progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 14.dp,
                                bottomEnd = 14.dp,
                            ),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        menuContent?.invoke()
    }
}

@Composable
internal fun ShelfCover(
    item: ReadableItem,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onCoverVisible: (ReadableItem, LibraryCoverImageVariant) -> Unit,
    coverLoadingEnabled: Boolean = true,
    notifyVisibleOnCompose: Boolean = true,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(item.id, coverLoadingEnabled, notifyVisibleOnCompose) {
        if (coverLoadingEnabled && notifyVisibleOnCompose) {
            onCoverVisible(item, LibraryCoverImageVariant.ShelfRow)
        }
    }
    val variant = if (notifyVisibleOnCompose) {
        LibraryCoverImageVariant.ShelfRow
    } else {
        LibraryCoverImageVariant.ShelfPreview
    }
    val imageUrl = if (coverLoadingEnabled) {
        coverImageUrlProvider(item, variant)
    } else {
        null
    }
    if (imageUrl.isNullOrBlank()) {
        PlaceholderCover(
            title = item.title,
            modifier = modifier,
        )
        return
    }
    val coverModifier = modifier
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val request = rememberShelfCoverRequest(
        imageUrl = imageUrl,
        variant = variant,
    )
    AsyncImage(
        model = request,
        contentDescription = item.title,
        modifier = coverModifier,
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun rememberShelfCoverRequest(
    imageUrl: String,
    variant: LibraryCoverImageVariant,
): coil.request.ImageRequest {
    val context = LocalContext.current
    return rememberLibraryCoverImageRequest(
        context = context,
        imageUrl = imageUrl,
        variant = variant,
    )
}
@Composable
private fun PlaceholderCover(title: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val ShelfPreviewCardWidth = 132.dp
private const val CoverAspectRatio = 0.72f
