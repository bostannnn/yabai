package com.example.unifiedreader.feature.reader.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.components.AppFloatingPanel
import com.example.unifiedreader.ui.components.AppFloatingPanelPlacement
import com.example.unifiedreader.ui.components.AppFloatingPanelScrim
import com.example.unifiedreader.ui.components.AppGlassDefaults
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.appGlass
import com.example.unifiedreader.ui.components.currentAppWindowWidth

/*
 * The reader's chrome (UI redesign 8.7): a top bar and a bottom dock, both 28 dp-cornered glass
 * pills inset 12 dp from the screen (8 on phones), and the panels that open from their buttons.
 * Every reader (EPUB, comics, PDF) builds on these, so the three share one frame.
 */

/** The top bar: Back, a centred title over a second line that names where you are, and [actions]. */
@Composable
fun ReaderTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val tokens = readerOverlayTokens()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ReaderChromeDefaults.barHeight)
            .readerChromeSurface()
            .padding(horizontal = 7.dp),
    ) {
        ReaderBarButton(
            icon = AppIcons.ArrowBack,
            contentDescription = "Back to library",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                // Clear of a button on either side, so the title stays centred on the bar.
                .padding(horizontal = ReaderChromeDefaults.buttonSize * 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.primaryText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf(String::isNotBlank)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.secondaryText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/**
 * The bottom dock: an optional [strip] (the comics page strip) over the 60 dp row of the panel
 * button, the percentage, the slider and the position ([ReaderDockProgress]).
 */
@Composable
fun ReaderDock(
    modifier: Modifier = Modifier,
    strip: (@Composable () -> Unit)? = null,
    row: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .readerChromeSurface(),
    ) {
        strip?.let { content ->
            Box(modifier = Modifier.padding(top = 10.dp)) { content() }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReaderDockDefaults.rowHeight)
                .padding(start = 9.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = row,
        )
    }
}

/**
 * A 42 dp round button on a bar. One that opens a panel is [active] (filled) while the panel is
 * open; pressing it again closes the panel.
 */
@Composable
fun ReaderBarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = readerOverlayTokens()
    AppIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(ReaderChromeDefaults.buttonSize)
            .background(if (active) tokens.colors.selectedContainer else Color.Transparent, CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else tokens.colors.primaryText,
        )
    }
}

/** Where a reader's panels open: the space between the bars, and how tall a phone sheet may get. */
class ReaderPanelSlot internal constructor(
    /** The bottom sheet's height on phones (70% of the screen); unspecified on tablets. */
    val sheetMaxHeight: Dp,
)

/**
 * The bars and the panels, laid out the 8.7 way. [topBar] sits at the top and [dock] at the
 * bottom, inside the gesture insets. While [panelOpen], a scrim dims the page (only the page:
 * the bars stay above it and keep working) and a tap on it calls [onDismissPanel]. [panels] open
 * in the space between the bars, 10 dp clear of each, so they never cover them; on phones they
 * are bottom sheets under the top bar, and the dock steps aside while one is open.
 */
@Composable
fun ReaderChromeFrame(
    panelOpen: Boolean,
    onDismissPanel: () -> Unit,
    topBar: @Composable () -> Unit,
    dock: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    panels: @Composable BoxScope.(ReaderPanelSlot) -> Unit,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    val inset = if (compact) ReaderChromeDefaults.compactInset else ReaderChromeDefaults.inset
    val density = LocalDensity.current
    // The bars' edges in root pixels, measured as they are placed.
    var frameTop by remember { mutableFloatStateOf(0f) }
    var frameBottom by remember { mutableFloatStateOf(0f) }
    var topBarBottom by remember { mutableFloatStateOf(0f) }
    var dockTop by remember { mutableFloatStateOf(Float.NaN) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot()
                frameTop = bounds.top
                frameBottom = bounds.bottom
            },
    ) {
        AppFloatingPanelScrim(visible = panelOpen, onDismissRequest = onDismissPanel)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(start = inset, end = inset, top = inset)
                .onGloballyPositioned { coordinates -> topBarBottom = coordinates.boundsInRoot().bottom },
        ) {
            topBar()
        }
        AnimatedVisibility(
            visible = !(compact && panelOpen),
            enter = slideInVertically { height -> height } + fadeIn(),
            exit = slideOutVertically { height -> height } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(start = inset, end = inset, bottom = inset)
                    .onGloballyPositioned { coordinates -> dockTop = coordinates.boundsInRoot().top },
            ) {
                dock()
            }
        }
        val sheetMaxHeight = if (compact) maxHeight * ReaderChromeDefaults.sheetHeightFraction else Dp.Unspecified
        val gap = ReaderChromeDefaults.panelGap
        val top = with(density) { (topBarBottom - frameTop).coerceAtLeast(0f).toDp() } + gap
        val bottom = if (compact || dockTop.isNaN()) {
            0.dp
        } else {
            with(density) { (frameBottom - dockTop).coerceAtLeast(0f).toDp() } + gap
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = top, bottom = bottom)
                .then(
                    if (compact) {
                        Modifier
                    } else {
                        Modifier
                            .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = inset)
                    },
                ),
        ) {
            panels(ReaderPanelSlot(sheetMaxHeight = sheetMaxHeight))
        }
    }
}

/**
 * A reader panel in a [ReaderChromeFrame] slot: the kit's floating panel without its own scrim
 * (the frame dims the page) or margins (the slot already keeps it clear of the bars and edges).
 */
@Composable
fun ReaderPanel(
    visible: Boolean,
    placement: AppFloatingPanelPlacement,
    width: Dp,
    slot: ReaderPanelSlot,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppFloatingPanel(
        visible = visible,
        placement = placement,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        width = width,
        maxHeight = slot.sheetMaxHeight,
        contentPadding = PaddingValues(0.dp),
        containerColor = readerOverlayTokens().colors.container,
        scrimColor = Color.Transparent,
        testTag = testTag,
        content = content,
    )
}

object ReaderChromeDefaults {
    val barHeight: Dp = 56.dp
    val buttonSize: Dp = 42.dp
    val inset: Dp = 12.dp
    val compactInset: Dp = 8.dp
    val cornerRadius: Dp = 28.dp

    /** Between a bar and a panel. */
    val panelGap: Dp = 10.dp

    /** A phone sheet's height, as a share of the screen. */
    const val sheetHeightFraction: Float = 0.7f

    /** Panel widths on tablets. */
    val contentsWidth: Dp = 360.dp
    val pagesWidth: Dp = 380.dp
    val settingsWidth: Dp = 400.dp

    val barShape: Shape = RoundedCornerShape(cornerRadius)

    /** A page strip's thumbnails in the dock: 110 dp tall in portrait, 96 in landscape, 70 on phones. */
    fun stripThumbnailHeight(screenWidthDp: Int, screenHeightDp: Int): Dp = when {
        AppWindowWidth.of(screenWidthDp) == AppWindowWidth.Compact -> 70.dp
        screenWidthDp > screenHeightDp -> 96.dp
        else -> 110.dp
    }
}

/** The bars' glass, on the 28 dp bar shape. */
@Composable
private fun Modifier.readerChromeSurface(): Modifier = appGlass(
    shape = ReaderChromeDefaults.barShape,
    containerColor = readerOverlayTokens().colors.container,
    tintAlpha = AppGlassDefaults.tintAlpha,
)
