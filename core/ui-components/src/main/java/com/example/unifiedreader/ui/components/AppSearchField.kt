package com.example.unifiedreader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
 * The search field: a pill on [containerColor] with a search icon, a placeholder and, once
 * something is typed, a clear button. An accent edge marks an active query. Inside a dialog,
 * which is already `surfaceContainerHigh`, pass [AppSearchFieldDefaults.dialogContainerColor].
 * [trailingContent] sits at the end, before the clear button (a status badge, say).
 *
 * With [onClick] the field is a door rather than a field: it can't be typed in, and a tap calls
 * [onClick] (the library bar's field, which opens the search panel); Clear still clears. [onSearch]
 * is what the keyboard's search key does; without it the key only hides the keyboard. Without
 * [showActiveEdge] an active query draws no accent edge, and without [showClear] there is no Clear
 * (a field that is a panel's own row, whose Close sits beside it).
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    height: Dp = AppSearchFieldDefaults.height,
    containerColor: Color = AppSearchFieldDefaults.containerColor,
    autoFocus: Boolean = false,
    contentDescription: String = placeholder,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    showActiveEdge: Boolean = true,
    showClear: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val accent = UnifiedReaderThemeTokens.colors.sourceAccent
    val active = query.isNotEmpty()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    // The caret starts after the words already there, so typing adds to them.
    var value by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    if (value.text != query) value = TextFieldValue(query, TextRange(query.length))
    BasicTextField(
        value = value,
        onValueChange = { next ->
            value = next
            if (next.text != query) onQueryChange(next.text)
        },
        modifier = modifier
            .height(height)
            .focusRequester(focusRequester)
            .semantics { this.contentDescription = contentDescription },
        enabled = onClick == null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results show as the query is typed; the keyboard's search key only gets it out of the way.
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() ?: keyboard?.hide() }),
        decorationBox = { innerTextField ->
            val shape = RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius)
            AppPanel(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (onClick != null) {
                            Modifier
                                .clip(shape)
                                .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
                        } else {
                            Modifier
                        },
                    ),
                shape = shape,
                color = containerColor,
                border = if (active && showActiveEdge) BorderStroke(1.dp, accent) else null,
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
                    trailingContent?.invoke()
                    AnimatedVisibility(
                        visible = active && showClear,
                        enter = fadeIn(AppMotion.effects()) + scaleIn(AppMotion.spatial(), initialScale = AppMotion.pressedScale),
                        exit = fadeOut(AppMotion.effects()) + scaleOut(AppMotion.spatial(), targetScale = AppMotion.pressedScale),
                    ) {
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

    val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

    /** One step up from [AppDialogDefaults.containerColor], like the dialog's tonal buttons. */
    val dialogContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
}
