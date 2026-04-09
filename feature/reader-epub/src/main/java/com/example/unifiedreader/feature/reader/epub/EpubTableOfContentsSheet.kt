package com.example.unifiedreader.feature.reader.epub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@androidx.compose.runtime.Composable
internal fun EpubTableOfContentsSheet(
    title: String,
    entries: List<EpubTocEntry>,
    currentHref: String?,
    currentProgress: Double,
    isOpen: Boolean,
    bottomAnchorHeight: Dp,
    onDismissRequest: () -> Unit,
    onEntrySelected: (EpubTocEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = isOpen) {
        onDismissRequest()
    }
    val activeEntry = androidx.compose.runtime.remember(entries, currentProgress, currentHref) {
        entries.activeEpubTocEntry(
            currentHref = currentHref,
            currentProgress = currentProgress,
        )
    }
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = androidx.compose.runtime.remember {
                        MutableInteractionSource()
                    },
                    onClick = onDismissRequest,
                ),
        ) {
            val horizontalPadding = when {
                maxWidth < 480.dp -> 12.dp
                maxWidth < 840.dp -> 16.dp
                else -> 22.dp
            }
            val sheetWidth = when {
                maxWidth < 420.dp -> maxWidth - (horizontalPadding * 2)
                maxWidth < 840.dp -> 292.dp
                else -> 312.dp
            }
            val boundedSheetWidth = if (sheetWidth < EpubTocSheetMaxWidth) {
                sheetWidth
            } else {
                EpubTocSheetMaxWidth
            }
            val sheetMaxHeight = when {
                maxHeight < 540.dp -> maxHeight * 0.74f
                maxHeight < 900.dp -> maxHeight * 0.58f
                else -> maxHeight * 0.5f
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal))
                    .padding(
                        start = 10.dp,
                        bottom = bottomAnchorHeight + EpubTocSheetBottomGap,
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                AnimatedVisibility(
                    visible = isOpen,
                    enter = slideInVertically(initialOffsetY = { it / 3 }) + scaleIn(
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(0.12f, 1f),
                    ),
                    exit = slideOutVertically(targetOffsetY = { it / 4 }) + scaleOut(
                        targetScale = 0.94f,
                        transformOrigin = TransformOrigin(0.12f, 1f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 220.dp, max = boundedSheetWidth)
                            .heightIn(max = sheetMaxHeight)
                            .shadow(
                                elevation = 18.dp,
                                shape = EpubTocSheetShape,
                            )
                            .background(
                                color = Color(0xFA171A1F),
                                shape = EpubTocSheetShape,
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0x18F5F7FA),
                                shape = EpubTocSheetShape,
                            )
                            .clickable(
                                indication = null,
                                interactionSource = androidx.compose.runtime.remember {
                                    MutableInteractionSource()
                                },
                                onClick = {},
                            )
                            .padding(top = 14.dp, bottom = 8.dp)
                            .testTag(EpubTocSheetTag),
                    ) {
                        Text(
                            text = "Contents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF5F7FA),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFAEBBC7),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                start = 20.dp,
                                end = 20.dp,
                                top = 4.dp,
                                bottom = 12.dp,
                            ),
                        )
                        if (entries.isEmpty()) {
                            Text(
                                text = "No table of contents",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD6E0E8),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    bottom = 12.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(
                                    items = entries,
                                    key = { entry -> entry.id },
                                ) { entry ->
                                    EpubTableOfContentsSheetRow(
                                        entry = entry,
                                        selected = entry.id == activeEntry?.id,
                                        onClick = {
                                            onEntrySelected(entry)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EpubTableOfContentsSheetRow(
    entry: EpubTocEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val titleColor = when {
        selected -> Color(0xFFF5F7FA)
        entry.depth == 0 -> Color(0xFFE7EDF4)
        else -> Color(0xFFD0DAE4)
    }
    val titleStyle = when {
        selected -> MaterialTheme.typography.labelLarge
        entry.depth == 0 -> MaterialTheme.typography.bodyLarge
        else -> MaterialTheme.typography.bodyMedium
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (selected) Color(0x22F5F7FA) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Color(0x24F5F7FA) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(
                start = 16.dp + tocRowIndent(entry.depth),
                end = 14.dp,
                top = if (entry.depth == 0) 11.dp else 9.dp,
                bottom = if (entry.depth == 0) 11.dp else 9.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (entry.depth > 0) {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 6.dp)
                    .background(
                        color = if (selected) Color(0x99F5F7FA) else Color(0x48AEBBC7),
                        shape = RoundedCornerShape(100.dp),
                    ),
            )
        }
        Text(
            text = entry.title,
            style = titleStyle,
            fontWeight = if (selected || entry.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            color = titleColor,
            maxLines = if (entry.depth == 0) 3 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        entry.position?.let { position ->
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color(0xFFF5F7FA) else Color(0xFF8B9AAA),
                maxLines = 1,
            )
        }
    }
}

private fun tocRowIndent(depth: Int): Dp = (depth.coerceAtLeast(0) * 12).dp

internal const val EpubTocSheetTag = "epub-toc-sheet"
private val EpubTocSheetShape = RoundedCornerShape(22.dp)
private val EpubTocSheetMaxWidth = 312.dp
private val EpubTocSheetBottomGap = 4.dp
