package com.example.unifiedreader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The search field: a `surfaceContainerHigh` pill with a search icon, a placeholder and, once
 * something is typed, a clear button. An accent edge marks an active query.
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = AppSearchFieldDefaults.height,
    autoFocus: Boolean = false,
    contentDescription: String = placeholder,
) {
    val colors = MaterialTheme.colorScheme
    val accent = UnifiedReaderThemeTokens.colors.sourceAccent
    val active = query.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .height(height)
            .focusRequester(focusRequester)
            .semantics { this.contentDescription = contentDescription },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results show as the query is typed; the keyboard's search key only gets it out of the way.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { innerTextField ->
            AppPanel(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                border = if (active) BorderStroke(1.dp, accent) else null,
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
                        tint = if (active) accent else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    if (active) {
                        AppIconButton(
                            onClick = { onQueryChange("") },
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

object AppSearchFieldDefaults {
    val height: Dp = 40.dp
}
