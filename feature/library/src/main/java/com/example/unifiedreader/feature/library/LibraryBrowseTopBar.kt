package com.example.unifiedreader.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment

@Composable
fun LibraryBrowseTopBarContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    val themeColors = UnifiedReaderThemeTokens.colors
    val chromeRecipe = UnifiedReaderThemeTokens.components.chrome
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(
                themeColors.surfaceBase.copy(alpha = chromeRecipe.containerAlpha * 0.46f),
            )
            .unifiedReaderComponentTreatment(
                recipe = chromeRecipe,
                accentColor = themeColors.readerAccent,
                secondaryColor = themeColors.syncAccent,
                overlayColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(0.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        content = content,
    )
}

@Composable
fun LibraryBrowseSearchModeTopBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    compactToolbar: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    queryContentDescription: String = "Library search query",
) {
    if (compactToolbar) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                )
            }
            LibraryBrowseTopBarSearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                autoFocus = true,
                queryContentDescription = queryContentDescription,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                )
            }
            LibraryBrowseTopBarSearchField(
                query = query,
                onQueryChanged = onQueryChanged,
                autoFocus = true,
                queryContentDescription = queryContentDescription,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(WideLibrarySearchWidthFraction)
                    .widthIn(max = ExpandedLibrarySearchMaxWidth),
            )
        }
    }
}

@Composable
private fun LibraryBrowseTopBarSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    queryContentDescription: String,
) {
    val colors = MaterialTheme.colorScheme
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    val componentRecipe = UnifiedReaderThemeTokens.components.control
    val controlShape = RoundedCornerShape(presentation.controlCornerRadius)
    val active = query.isNotEmpty()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            focusRequester.requestFocus()
        }
    }

    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .height(presentation.searchHeight)
            .focusRequester(focusRequester)
            .semantics { contentDescription = queryContentDescription }
            .testTag(LibraryBrowseTopBarSearchFieldTag),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(semanticColors.sourceAccent),
        decorationBox = { innerTextField ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .unifiedReaderComponentTreatment(
                        recipe = componentRecipe,
                        accentColor = semanticColors.sourceAccent,
                        secondaryColor = semanticColors.syncAccent,
                        overlayColor = colors.onSurface,
                        shape = controlShape,
                        active = active,
                    ),
                shape = controlShape,
                color = if (active) {
                    colors.surfaceContainerHigh.copy(
                        alpha = maxOf(0.92f, componentRecipe.containerAlpha),
                    )
                } else {
                    colors.surfaceContainerHigh.copy(
                        alpha = maxOf(0.52f, componentRecipe.containerAlpha),
                    )
                },
                border = if (componentRecipe.borderAlpha > 0f) {
                    null
                } else {
                    BorderStroke(
                        1.dp,
                        if (active) {
                            semanticColors.sourceAccent.copy(alpha = 0.82f)
                        } else {
                            colors.outlineVariant.copy(alpha = 0.36f)
                        },
                    )
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 12.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (active) semanticColors.sourceAccent else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

private val ExpandedLibrarySearchMaxWidth = 640.dp
private const val WideLibrarySearchWidthFraction = 0.62f
const val LibraryBrowseTopBarSearchFieldTag = "library-search-field"
