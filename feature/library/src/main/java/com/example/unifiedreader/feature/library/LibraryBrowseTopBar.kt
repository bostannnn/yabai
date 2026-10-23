package com.example.unifiedreader.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppTopBar
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
fun LibraryBrowseTopBarContainer(
    modifier: Modifier = Modifier,
    surfaceVisible: Boolean = true,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    AppTopBar(
        modifier = modifier,
        surfaceVisible = surfaceVisible,
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
            AppIconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
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
            AppIconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = AppIcons.ArrowBack,
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
            AppPanel(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                border = if (active) BorderStroke(1.dp, semanticColors.sourceAccent) else null,

            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.Search,
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
                        AppIconButton(
                            onClick = { onQueryChanged("") },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = AppIcons.Close,
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
