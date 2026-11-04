package com.example.unifiedreader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.example.unifiedreader.ui.theme.AppMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.feature.library.LibraryBrowseTopBarContainer
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.appBadgeDot

/**
 * The settings bar. The hub has no name in it (the rail or the drawer already says Settings); a
 * page opened over the hub shows Back and its [title]. No glass until something scrolls under it.
 */
@Composable
fun SettingsTopBar(
    title: String?,
    onDrawerClick: (() -> Unit)?,
    onBack: (() -> Unit)?,
    scrolled: Boolean = false,
) {
    ThemedTopBarContainer(surfaceVisible = scrolled) {
        // A settings page's title and back arrow change with the page, in step with its slide.
        AnimatedContent(
            targetState = title to onBack,
            contentKey = { (shownTitle, _) -> shownTitle },
            transitionSpec = { topBarFadeThrough() },
            contentAlignment = Alignment.CenterStart,
            label = "settingsTopBar",
        ) { (title, onBack) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = "Back to settings",
                        )
                    }
                } else if (onDrawerClick != null) {
                    DrawerMenuButton(onClick = onDrawerClick)
                }
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

/**
 * The bar's half of a screen change: the old bar fades out and the new one fades in on the same
 * timing as [com.example.unifiedreader.ui.components.AppScreenHost]'s fade-through, so bar and
 * screen change together.
 */
internal fun topBarFadeThrough(): ContentTransform = ContentTransform(
    targetContentEnter = fadeIn(
        tween(
            durationMillis = AppMotion.screenFadeInMillis,
            delayMillis = AppMotion.screenFadeOutMillis,
            easing = LinearOutSlowInEasing,
        ),
    ),
    initialContentExit = fadeOut(tween(AppMotion.screenFadeOutMillis, easing = LinearEasing)),
    sizeTransform = null,
)

/**
 * The journal's bar: only the menu while the drawer is the navigation (the journal's period is its
 * title, and the rail or the drawer already says Journal). No glass until something scrolls under
 * it. Importing a file is the library's.
 */
@Composable
fun JournalTopBar(
    onDrawerClick: (() -> Unit)?,
    scrolled: Boolean = false,
) {
    ThemedTopBarContainer(surfaceVisible = scrolled) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDrawerClick != null) {
                DrawerMenuButton(onClick = onDrawerClick)
            }
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
    AppIconButton(onClick = onClick) {
        Icon(
            imageVector = AppIcons.Menu,
            contentDescription = if (hasDot) "Open navigation drawer, update available" else "Open navigation drawer",
            modifier = Modifier.appBadgeDot(visible = hasDot, color = MaterialTheme.colorScheme.primary),
        )
    }
}
