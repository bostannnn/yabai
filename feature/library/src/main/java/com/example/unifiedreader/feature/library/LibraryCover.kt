package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.geometry.isSpecified
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.aspectRatio
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.isGeneratedComicVolumeTitle
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.ui.components.AppBadge
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
fun LibraryCover(
    item: ReadableItem,
    coverUrl: String? = item.coverUrl,
    isDownloaded: Boolean = false,
    isRead: Boolean = false,
    fadeRead: Boolean = false,
    downloadBadgeBottomInset: Dp = 0.dp,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.Grid,
    modifier: Modifier = Modifier,
    // Off while selecting: the selection check takes the corner, and two checks read as one state.
    showReadBadge: Boolean = true,
    emptyCoverContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val coverShape = RoundedCornerShape(presentation.coverCornerRadius)
    // Covers sit flat on the background: no shadow, no gradient, no spine treatment. The
    // hairline keeps light covers separate from light backgrounds.
    val coverEdge = MaterialTheme.colorScheme.outlineVariant.copy(alpha = CoverEdgeAlpha)
    val imageUrl = coverUrl?.takeIf(String::isNotBlank)
    // As printed: the cover keeps its own proportions on the bottom of its slot, once its image says what they are.
    val asPrinted = LocalLibraryCoverAsPrinted.current && imageUrl != null
    var printedAspect by remember(imageUrl) { mutableStateOf(imageUrl?.let(LibraryCoverAspectRatios::get)) }
    val printedShape = printedAspect?.takeIf { asPrinted }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .then(
                    if (printedShape == null) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier.aspectRatio(printedShape, matchHeightConstraintsFirst = printedShape < LibraryCoverAspectRatio)
                    },
                )
                .clip(coverShape)
                .background(semanticColors.surfaceRaised)
                .border(width = 1.dp, color = coverEdge, shape = coverShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (fadeRead && isRead) ReadCoverFadeAlpha else 1f),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    BucketedCoverImage(
                        identityKey = item.id,
                        imageUrl = imageUrl,
                        // Not Kavita's "Chapter v1" for a volume.
                        contentDescription = item.title.takeUnless(::isGeneratedComicVolumeTitle) ?: item.libraryDisplayLabel(),
                        variant = imageVariant,
                        modifier = Modifier.fillMaxSize(),
                        onAspectRatio = { aspect ->
                            LibraryCoverAspectRatios[imageUrl] = aspect
                            printedAspect = aspect
                        },
                    )
                } else if (emptyCoverContent != null) {
                    emptyCoverContent()
                } else {
                    LibraryCoverPlaceholder(
                        item = item,
                        reserveTopBadge = isRead,
                        reserveBottomBadge = isDownloaded,
                    )
                }
            }
            if (isRead && showReadBadge) {
                LibraryReadStatusBadge(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            if (isDownloaded) {
                AppBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp + downloadBadgeBottomInset),
                ) {
                    Icon(
                        imageVector = AppIcons.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = semanticColors.onSurfaceSelected,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp),
                    )
                }
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
    fadeRead: Boolean = false,
    downloadBadgeBottomInset: Dp = 0.dp,
    imageVariant: LibraryCoverImageVariant = LibraryCoverImageVariant.Grid,
    modifier: Modifier = Modifier,
    showReadBadge: Boolean = true,
    emptyCoverContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    LibraryCover(
        item = item,
        coverUrl = coverImageUrlProvider(item, imageVariant),
        isDownloaded = isDownloaded,
        isRead = isRead,
        fadeRead = fadeRead,
        downloadBadgeBottomInset = downloadBadgeBottomInset,
        imageVariant = imageVariant,
        modifier = modifier,
        showReadBadge = showReadBadge,
        emptyCoverContent = emptyCoverContent,
    )
}

/**
 * Whether covers show as printed (each in its own proportions, on the bottom of its 2:3 slot) instead of cropped
 * to 2:3: the sort menu's Cover shape, provided once for every screen that draws library covers.
 */
val LocalLibraryCoverAsPrinted = staticCompositionLocalOf { false }

// Each cover image's own proportions, by its address, so a cover that comes back on screen keeps its shape
// from the first frame instead of starting at 2:3.
private val LibraryCoverAspectRatios = ConcurrentHashMap<String, Float>()

// A cover as printed stays within these (a strip or a square page would look like an error, not a book).
private const val MinPrintedAspect = 0.5f
private const val MaxPrintedAspect = 1f

private const val ReadCoverFadeAlpha = 0.42f
private const val CoverEdgeAlpha = 0.4f

@Composable
internal fun BucketedCoverImage(
    identityKey: String,
    imageUrl: String,
    contentDescription: String?,
    variant: LibraryCoverImageVariant,
    modifier: Modifier = Modifier,
    // The image's own width over height, once it has loaded (for covers as printed).
    onAspectRatio: ((Float) -> Unit)? = null,
) {
    val context = LocalContext.current
    val request = rememberLibraryCoverImageRequest(
        context = context,
        imageUrl = imageUrl,
        variant = variant,
    )
    var retainedPainter by remember(identityKey) { mutableStateOf<Painter?>(null) }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = retainedPainter,
        error = retainedPainter,
        onSuccess = { state ->
            retainedPainter = state.painter
            val size = state.painter.intrinsicSize
            if (onAspectRatio != null && size.isSpecified && size.width > 0f && size.height > 0f) {
                onAspectRatio((size.width / size.height).coerceIn(MinPrintedAspect, MaxPrintedAspect))
            }
        },
        modifier = modifier,
    )
}
