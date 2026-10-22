package com.example.unifiedreader.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.ui.components.appPressScale

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LibraryCoverActionCard(
    item: ReadableItem,
    title: String,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    imageVariant: LibraryCoverImageVariant,
    isDownloaded: Boolean,
    isRead: Boolean,
    fadeRead: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier.fillMaxWidth(),
    titleStyle: TextStyle = MaterialTheme.typography.labelMedium,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingText: String? = null,
    supportingStyle: TextStyle = MaterialTheme.typography.bodySmall,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingText: String? = null,
    trailingStyle: TextStyle = MaterialTheme.typography.labelSmall,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    metadataTopPadding: Dp = 6.dp,
    metadataVerticalSpacing: Dp = 3.dp,
    menuContent: (@Composable () -> Unit)? = null,
    coverOverlay: (@Composable BoxScope.() -> Unit)? = null,
    emptyCoverContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    // Pressing the cover shrinks it a little.
    val coverInteractionSource = remember { MutableInteractionSource() }
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .appPressScale(coverInteractionSource)
                .libraryCoverClickable(
                    interactionSource = coverInteractionSource,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            ResolvedLibraryCover(
                item = item,
                coverImageUrlProvider = coverImageUrlProvider,
                isDownloaded = isDownloaded,
                isRead = isRead,
                fadeRead = fadeRead,
                imageVariant = imageVariant,
                modifier = coverModifier,
                emptyCoverContent = emptyCoverContent,
            )
            coverOverlay?.invoke(this)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = metadataTopPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(metadataVerticalSpacing),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.libraryCoverClickable(
                        interactionSource = null,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ).then(title.marqueeModifier()),
                    style = titleStyle,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingText?.let {
                    Text(
                        text = it,
                        style = supportingStyle,
                        color = supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                trailingText?.let {
                    Text(
                        text = it,
                        style = trailingStyle,
                        color = trailingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (menuContent != null) {
                menuContent()
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun String.marqueeModifier(): Modifier =
    if (length >= LibraryCardTitleMarqueeMinLength) {
        // Bounded on purpose: an endless marquee keeps the idle library redrawing every frame.
        Modifier.basicMarquee(iterations = LibraryCardTitleMarqueeIterations)
    } else {
        Modifier
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.libraryCoverClickable(
    interactionSource: MutableInteractionSource?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
): Modifier = if (onLongClick == null) {
    clickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick,
    )
} else {
    combinedClickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

private const val LibraryCardTitleMarqueeMinLength = 32

private const val LibraryCardTitleMarqueeIterations = 1
