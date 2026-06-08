package com.example.unifiedreader.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.example.unifiedreader.ui.theme.UnifiedReaderSelectionTreatment
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment

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
    val railRecipe = UnifiedReaderThemeTokens.components.rail
    val railShape = RoundedCornerShape(presentation.controlCornerRadius)
    val currentOnSectionSelected by rememberUpdatedState(onSectionSelected)
    var railHeightPx by remember { mutableIntStateOf(0) }
    var draggedSection by remember { mutableStateOf<String?>(null) }

    fun selectSectionAt(position: Offset) {
        if (railHeightPx <= 0) return
        val sectionIndex = (
            position.y.coerceIn(0f, railHeightPx.toFloat() - 1f) /
                railHeightPx.toFloat() *
                sections.size
            )
            .toInt()
            .coerceIn(sections.indices)
        val section = sections[sectionIndex]
        if (section != draggedSection) {
            draggedSection = section
            currentOnSectionSelected(section)
        }
    }

    Box(
        modifier = modifier
            .width(presentation.alphabetRailWidth)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size -> railHeightPx = size.height }
                .clip(railShape)
                .background(
                    semanticColors.surfaceBase.copy(alpha = railRecipe.containerAlpha),
                )
                .unifiedReaderComponentTreatment(
                    recipe = railRecipe,
                    accentColor = semanticColors.readerAccent,
                    secondaryColor = semanticColors.syncAccent,
                    overlayColor = MaterialTheme.colorScheme.onSurface,
                    shape = railShape,
                )
                .pointerInput(sections, railHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = ::selectSectionAt,
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            selectSectionAt(change.position)
                        },
                        onDragEnd = { draggedSection = null },
                        onDragCancel = { draggedSection = null },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEach { section ->
                AlphabetNavigationItem(
                    label = section,
                    selected = activeSection == section || draggedSection == section,
                    onClick = { currentOnSectionSelected(section) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        draggedSection?.let { section ->
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-58).dp)
                    .size(48.dp),
                shape = RoundedCornerShape(presentation.controlCornerRadius),
                color = semanticColors.readerAccentContainer,
                contentColor = semanticColors.onReaderAccentContainer,
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = section,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
    val selectionTreatment = UnifiedReaderThemeTokens.components.rail.selectionTreatment
    val backgroundColor = when {
        !selected -> Color.Transparent
        selectionTreatment == UnifiedReaderSelectionTreatment.Inverse -> semanticColors.readerAccent
        selectionTreatment == UnifiedReaderSelectionTreatment.Stamp -> semanticColors.syncAccentContainer
        selectionTreatment == UnifiedReaderSelectionTreatment.Underline ||
            selectionTreatment == UnifiedReaderSelectionTreatment.SideMark -> Color.Transparent
        else -> semanticColors.surfaceSelected
    }
    val textColor = when {
        !selected -> semanticColors.sourceAccent
        selectionTreatment == UnifiedReaderSelectionTreatment.Inverse -> semanticColors.onReaderAccent
        selectionTreatment == UnifiedReaderSelectionTreatment.Stamp -> semanticColors.onSyncAccentContainer
        selectionTreatment == UnifiedReaderSelectionTreatment.Underline ||
            selectionTreatment == UnifiedReaderSelectionTreatment.SideMark -> semanticColors.readerAccent
        else -> semanticColors.onSurfaceSelected
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
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
        Text(
            text = label,
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
    val recipe = UnifiedReaderThemeTokens.components.backToTop
    val shape = RoundedCornerShape(presentation.controlCornerRadius)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(shape)
            .background(
                semanticColors.surfaceRaised.copy(alpha = recipe.containerAlpha),
            )
            .unifiedReaderComponentTreatment(
                recipe = recipe,
                accentColor = semanticColors.readerAccent,
                secondaryColor = semanticColors.syncAccent,
                overlayColor = MaterialTheme.colorScheme.onSurface,
                shape = shape,
                active = true,
            )
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
