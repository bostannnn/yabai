package com.example.unifiedreader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.appBadgeDot
import com.example.unifiedreader.ui.theme.AppMotion

@Composable
fun SettingsTopBar(
    title: String,
    onDrawerClick: (() -> Unit)?,
    onBack: (() -> Unit)?,
    // While the hub's large title shows under the bar the bar has no name, and while nothing has
    // scrolled under it, no glass.
    titleOnScreen: Boolean = false,
    scrolled: Boolean = true,
) {
    val titleShown by animateFloatAsState(
        targetValue = if (titleOnScreen) 0f else 1f,
        animationSpec = AppMotion.effects(),
        label = "settingsTopBarTitle",
    )
    ThemedTopBarContainer(surfaceVisible = scrolled || !titleOnScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                AppIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "Back to settings",
                    )
                }
            } else if (onDrawerClick != null) {
                DrawerMenuButton(onClick = onDrawerClick)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .graphicsLayer { alpha = titleShown }
                    .then(if (titleOnScreen) Modifier.clearAndSetSemantics {} else Modifier),
            )
        }
    }
}

/**
 * The journal's bar: the menu, and the name once the journal's own "Journal" has scrolled away.
 * Like Settings, it has no glass until something scrolls under it. Importing a file is the
 * library's.
 */
@Composable
fun JournalTopBar(
    onDrawerClick: (() -> Unit)?,
    titleOnScreen: Boolean = false,
    scrolled: Boolean = true,
) {
    val titleShown by animateFloatAsState(
        targetValue = if (titleOnScreen) 0f else 1f,
        animationSpec = AppMotion.effects(),
        label = "journalTopBarTitle",
    )
    ThemedTopBarContainer(surfaceVisible = scrolled || !titleOnScreen) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDrawerClick != null) {
                DrawerMenuButton(onClick = onDrawerClick)
            }
            Text(
                text = "Journal",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .graphicsLayer { alpha = titleShown }
                    .then(if (titleOnScreen) Modifier.clearAndSetSemantics {} else Modifier),
            )
        }
    }
}

@Composable
internal fun ThemedTopBarContainer(
    modifier: Modifier = Modifier,
    surfaceVisible: Boolean = true,
    containerColor: Color? = null,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    LibraryBrowseTopBarContainer(
        modifier = modifier,
        surfaceVisible = surfaceVisible,
        containerColor = containerColor,
        content = content,
    )
}

/** True while something waits in the drawer (an app update), to dot the menu button. */
val LocalDrawerButtonDot = staticCompositionLocalOf { false }

@Composable
internal fun DrawerMenuButton(onClick: () -> Unit) {
    val hasDot = LocalDrawerButtonDot.current
    AppIconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
    ) {
        Icon(
            imageVector = AppIcons.Menu,
            contentDescription = if (hasDot) "Open navigation drawer, update available" else "Open navigation drawer",
            modifier = Modifier.appBadgeDot(visible = hasDot, color = MaterialTheme.colorScheme.primary),
        )
    }
}
