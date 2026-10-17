package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

internal const val AlphabetNavigationSectionTagPrefix = "alphabet-navigation-section-"
internal const val LibraryBackToTopTag = "library-back-to-top"

@Composable
internal fun AlphabetNavigationRail(
    sections: List<String>,
    activeSection: String?,
    onSectionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sections.isEmpty()) return

    val presentation = UnifiedReaderThemeTokens.presentation
    val semanticColors = UnifiedReaderThemeTokens.colors
    val railShape = RoundedCornerShape(presentation.controlCornerRadius)
    val currentOnSectionSelected by rememberUpdatedState(onSectionSelected)
    val railHeightPx = remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .width(presentation.alphabetRailWidth)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> railHeightPx.intValue = size.height }
                .clip(railShape)
                .background(semanticColors.surfaceBase.copy(alpha = 0.94f))
                .pointerInput(sections, railHeightPx.intValue) {
                    var lastSelectedSection: String? = null
                    fun selectSectionAt(position: Offset) {
                        if (railHeightPx.intValue <= 0) return
                        val sectionIndex = (
                            position.y.coerceIn(0f, railHeightPx.intValue.toFloat() - 1f) /
                                railHeightPx.intValue.toFloat() *
                                sections.size
                            )
                            .toInt()
                            .coerceIn(sections.indices)
                        val section = sections[sectionIndex]
                        if (section != lastSelectedSection) {
                            lastSelectedSection = section
                            currentOnSectionSelected(section)
                        }
                    }
                    detectVerticalDragGestures(
                        onDragStart = ::selectSectionAt,
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            selectSectionAt(change.position)
                        },
                        onDragEnd = { lastSelectedSection = null },
                        onDragCancel = { lastSelectedSection = null },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEach { section ->
                AlphabetNavigationItem(
                    label = section,
                    selected = activeSection == section,
                    onClick = { currentOnSectionSelected(section) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AlphabetNavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val backgroundColor = if (selected) semanticColors.surfaceSelected else Color.Transparent
    val textColor = if (selected) semanticColors.onSurfaceSelected else semanticColors.sourceAccent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AlphabetNavigationSectionTagPrefix + label)
            .semantics {
                contentDescription = "Jump to $label"
                this.selected = selected
                role = Role.Button
            }
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val selectedMarker = if (selected && backgroundColor != Color.Transparent) {
            Modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        } else {
            Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
        }
        Text(
            text = label,
            modifier = selectedMarker,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun LibraryBackToTopButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = UnifiedReaderThemeTokens.presentation
    val semanticColors = UnifiedReaderThemeTokens.colors
    val shape = RoundedCornerShape(presentation.controlCornerRadius)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(semanticColors.surfaceRaised.copy(alpha = 0.94f))
            .testTag(LibraryBackToTopTag)
            .semantics {
                contentDescription = "Back to top"
                role = Role.Button
            }
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.VerticalAlignTop,
            contentDescription = null,
            tint = semanticColors.readerAccent,
        )
    }
}

internal fun alphabetNavigationSections(
    sectionKeys: List<String>,
): List<String> = sectionKeys
    .distinct()
    .sortedWith(
        compareBy<String> { section -> section != "#" }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { section -> section },
    )

internal fun alphabetSectionStarts(
    sectionKeys: List<String?>,
): Map<String, Int> = buildMap {
    sectionKeys.forEachIndexed { index, section ->
        section?.let { putIfAbsent(it, index) }
    }
}

internal fun activeAlphabetSection(
    sectionKeys: List<String?>,
    firstVisibleItemIndex: Int,
    leadingContentItemCount: Int,
): String? = sectionKeys.getOrNull(firstVisibleItemIndex - leadingContentItemCount)

internal fun shouldShowLibraryBackToTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    leadingContentItemCount: Int,
    columnCount: Int,
): Boolean {
    val revealIndex = leadingContentItemCount + columnCount.coerceAtLeast(1) * 2
    return firstVisibleItemIndex >= revealIndex ||
        firstVisibleItemIndex > 0 && firstVisibleItemScrollOffset > 0 ||
        firstVisibleItemScrollOffset >= BackToTopOffsetThresholdPx
}

private const val BackToTopOffsetThresholdPx = 240
