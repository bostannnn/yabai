package com.example.unifiedreader.feature.reader.epub

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppTextButton

internal data class EpubFootnoteContent(
    val title: String,
    val body: String,
)

internal data class EpubFootnotePopupColors(
    val scrim: Color,
    val container: Color,
    val title: Color,
    val body: Color,
    val action: Color,
)

internal fun epubFootnotePopupColors(
    colorScheme: ColorScheme,
): EpubFootnotePopupColors = EpubFootnotePopupColors(
    scrim = colorScheme.scrim.copy(alpha = 0.32f),
    container = colorScheme.surfaceContainerHigh,
    title = colorScheme.onSurfaceVariant,
    body = colorScheme.onSurface,
    action = colorScheme.primary,
)

@Composable
internal fun EpubFootnotePopup(
    footnote: EpubFootnoteContent?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renderedFootnote by remember { mutableStateOf<EpubFootnoteContent?>(null) }
    LaunchedEffect(footnote) {
        if (footnote != null) {
            renderedFootnote = footnote
        }
    }

    BackHandler(enabled = footnote != null) {
        onDismissRequest()
    }

    val colors = epubFootnotePopupColors(MaterialTheme.colorScheme)

    AnimatedVisibility(
        visible = footnote != null && renderedFootnote != null,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.98f),
        modifier = modifier
            .fillMaxSize()
            .zIndex(3f),
    ) {
        val note = renderedFootnote ?: return@AnimatedVisibility
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .clickable(onClick = onDismissRequest),
        ) {
            val popupWidth = when {
                maxWidth < 360.dp -> maxWidth
                maxWidth < 520.dp -> maxWidth * 0.86f
                else -> 420.dp
            }
            val popupMaxHeight = when {
                maxHeight < 360.dp -> maxHeight * 0.72f
                else -> 260.dp
            }
            AppPanel(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(popupWidth)
                    .heightIn(max = popupMaxHeight)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
                color = colors.container,
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    AppTextButton(onClick = onDismissRequest) {
                        Text(
                            text = "Close",
                            color = colors.action,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note.body.ifBlank { "No footnote text" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.body,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                )
                }
            }
        }
    }
}
