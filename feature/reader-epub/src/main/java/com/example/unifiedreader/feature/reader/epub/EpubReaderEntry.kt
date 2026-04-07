package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.CompactReaderOverlayFrame
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderOverlayBackButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayDropdownItem
import com.example.unifiedreader.feature.reader.core.ReaderOverlayMenuAction
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.findContextAncestor
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayPanelState
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

@Composable
fun EpubReaderScreen(
    item: ReadableItem,
    sessionStore: ReaderSessionStore,
    documentUri: Uri? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findContextAncestor<FragmentActivity>() }
    if (activity == null) {
        EpubReaderMessage(
            title = item.title,
            body = "EPUB rendering requires a FragmentActivity host.",
            onBack = onBack,
        )
        return
    }

    val hydrated by sessionStore.isHydratedFlow.collectAsState()
    val progressByItemId by sessionStore.progressByItemIdFlow.collectAsState()
    val sessionByItemId by sessionStore.sessionByItemIdFlow.collectAsState()
    val progressSnapshot = progressByItemId[item.id]
    val sessionSnapshot = sessionByItemId[item.id]
    val initialLocator = remember(progressSnapshot) { progressSnapshot?.toReadiumLocator() }
    val restoredPreferences = remember(sessionSnapshot) { restoreEpubPreferences(sessionSnapshot) }
    var fontSize by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.fontSize) }
    var lineHeight by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.lineHeight) }
    var theme by rememberSaveable(item.id) { mutableStateOf(restoredPreferences.theme) }
    var layoutMode by rememberSaveable(item.id) { mutableStateOf(restoredPreferences.layoutMode) }
    var restoredSessionState by rememberSaveable(item.id) { mutableStateOf(false) }
    var pageCount by rememberSaveable(item.id) { mutableIntStateOf(sessionSnapshot?.pageCount ?: 0) }
    val overlayState = rememberReaderOverlayState(
        key = "epub-overlay-${item.id}",
        initiallyVisible = true,
    )

    LaunchedEffect(hydrated, restoredPreferences) {
        if (!hydrated || restoredSessionState) return@LaunchedEffect
        fontSize = restoredPreferences.fontSize
        lineHeight = restoredPreferences.lineHeight
        theme = restoredPreferences.theme
        layoutMode = restoredPreferences.layoutMode
        restoredSessionState = true
    }

    val publicationState by produceState<EpubPublicationState>(
        initialValue = EpubPublicationState.Loading,
        key1 = item.id,
        key2 = documentUri?.toString(),
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val publicationFile = documentUri?.let { importedUri ->
                    prepareImportedEpubFile(
                        context = context.applicationContext,
                        item = item,
                        documentUri = importedUri,
                    )
                } ?: ensureSampleEpubFile(
                    context = context.applicationContext,
                    item = item,
                )
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(
                    context.applicationContext.contentResolver,
                    httpClient,
                )
                val parser = DefaultPublicationParser(
                    context = context.applicationContext,
                    httpClient = httpClient,
                    assetRetriever = assetRetriever,
                    pdfFactory = null,
                )
                val asset = assetRetriever.retrieve(
                    publicationFile,
                    FormatHints(
                        mediaTypes = listOf(MediaType.EPUB),
                    ),
                ).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        throw IllegalStateException(error.toString())
                    },
                )
                val publication = PublicationOpener(
                    publicationParser = parser,
                ).open(
                    asset = asset,
                    credentials = null,
                    allowUserInteraction = true,
                    onCreatePublication = {},
                    warnings = null,
                ).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        throw IllegalStateException(error.toString())
                    },
                )
                EpubPublicationState.Ready(publication)
            }.getOrElse { throwable ->
                EpubPublicationState.Error(
                    throwable.message ?: "Failed to open EPUB publication.",
                )
            }
        }
    }

    when (val state = publicationState) {
        EpubPublicationState.Loading -> EpubReaderMessage(
            title = item.title,
            body = "Opening EPUB with Readium…",
            onBack = onBack,
        )

        is EpubPublicationState.Error -> EpubReaderMessage(
            title = item.title,
            body = state.message,
            onBack = onBack,
        )

        is EpubPublicationState.Ready -> {
            DisposableEffect(state.publication) {
                onDispose {
                    state.publication.close()
                }
            }

            LaunchedEffect(item.id, hydrated, restoredSessionState, fontSize, lineHeight, theme, layoutMode, pageCount) {
                if (!hydrated || !restoredSessionState) return@LaunchedEffect
                sessionStore.updateSession(
                    item = item,
                    pageCount = pageCount.takeIf { it > 0 },
                    preferences = buildEpubPreferencesMap(
                        EpubReaderPreferences(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            theme = theme,
                            layoutMode = layoutMode,
                        ),
                    ),
                )
            }

            ImmersiveReaderOverlay(
                overlayState = overlayState,
                autoHideDelayMillis = 0L,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(EpubReaderViewportTag),
                content = {
                    ReadiumEpubNavigatorHost(
                        activity = activity,
                        item = item,
                        publication = state.publication,
                        initialLocator = initialLocator,
                        preferences = EpubReaderPreferences(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            theme = theme,
                            layoutMode = layoutMode,
                        ),
                        onLocationChanged = { locator, totalPages ->
                            if (totalPages != null) {
                                pageCount = totalPages
                            }
                            sessionStore.saveProgress(
                                item = item,
                                locator = buildReaderLocator(locator),
                                isFinished = totalPages?.let { pages ->
                                    (locator.locations.position ?: 0) >= pages
                                } ?: false,
                                syncState = SyncState.DIRTY,
                                pageCount = totalPages ?: pageCount.takeIf { it > 0 },
                            )
                        },
                        onSurfaceTap = overlayState::toggle,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                overlay = {
                    EpubReaderOverlay(
                        itemId = item.id,
                        title = item.title,
                        progressLabel = buildEpubProgressLabel(
                            progressSnapshot = progressSnapshot,
                            pageCount = pageCount,
                        ),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        theme = theme,
                        layoutMode = layoutMode,
                        overlayVisible = overlayState.isVisible,
                        onBack = onBack,
                        onFontSizeChanged = {
                            fontSize = it
                        },
                        onLineHeightChanged = {
                            lineHeight = it
                        },
                        onThemeChanged = {
                            theme = it
                        },
                        onLayoutModeChanged = {
                            layoutMode = it
                        },
                        modifier = Modifier.testTag(EpubReaderOverlayTag),
                    )
                },
            )
        }
    }
}

@Composable
private fun EpubReaderOverlay(
    itemId: String,
    title: String,
    progressLabel: String,
    fontSize: Double,
    lineHeight: Double,
    theme: EpubReaderTheme,
    layoutMode: EpubReaderLayoutMode,
    overlayVisible: Boolean,
    onBack: () -> Unit,
    onFontSizeChanged: (Double) -> Unit,
    onLineHeightChanged: (Double) -> Unit,
    onThemeChanged: (EpubReaderTheme) -> Unit,
    onLayoutModeChanged: (EpubReaderLayoutMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePanel by rememberReaderOverlayPanelState(
        key = itemId,
        overlayVisible = overlayVisible,
        hiddenPanel = EpubOverlayPanel.None,
    )
    CompactReaderOverlayFrame(
        title = title,
        progressLabel = progressLabel,
        modifier = modifier,
        leadingActions = {
            ReaderOverlayBackButton(onClick = onBack)
        },
        trailingActions = {
            ReaderOverlayMenuAction(
                expanded = activePanel == EpubOverlayPanel.Text,
                onExpandedChange = { expanded ->
                    activePanel = if (expanded) {
                        EpubOverlayPanel.Text
                    } else {
                        EpubOverlayPanel.None
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = "Text settings",
                        tint = Color(0xFFF5F7FA),
                    )
                },
                menuModifier = Modifier.width(280.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Font size",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFD6E0E8),
                    )
                    Slider(
                        value = fontSize.toFloat(),
                        onValueChange = { onFontSizeChanged(it.toDouble()) },
                        valueRange = 0.8f..2.4f,
                    )
                    Text(
                        text = "Line height",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFD6E0E8),
                    )
                    Slider(
                        value = lineHeight.toFloat(),
                        onValueChange = { onLineHeightChanged(it.toDouble()) },
                        valueRange = 1.1f..2.2f,
                    )
                }
            }
            ReaderOverlayMenuAction(
                expanded = activePanel == EpubOverlayPanel.Theme,
                onExpandedChange = { expanded ->
                    activePanel = if (expanded) {
                        EpubOverlayPanel.Theme
                    } else {
                        EpubOverlayPanel.None
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (theme == EpubReaderTheme.Dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                        contentDescription = "Reader theme",
                        tint = Color(0xFFF5F7FA),
                    )
                },
            ) {
                EpubReaderTheme.entries.forEach { entry ->
                    ReaderOverlayDropdownItem(
                        label = entry.label,
                        selected = entry == theme,
                        onClick = {
                            activePanel = EpubOverlayPanel.None
                            onThemeChanged(entry)
                        },
                    )
                }
            }
            ReaderOverlayMenuAction(
                expanded = activePanel == EpubOverlayPanel.Layout,
                onExpandedChange = { expanded ->
                    activePanel = if (expanded) {
                        EpubOverlayPanel.Layout
                    } else {
                        EpubOverlayPanel.None
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.ViewDay,
                        contentDescription = "Page layout",
                        tint = Color(0xFFF5F7FA),
                    )
                },
            ) {
                EpubReaderLayoutMode.entries.forEach { entry ->
                    ReaderOverlayDropdownItem(
                        label = entry.label,
                        selected = entry == layoutMode,
                        onClick = {
                            activePanel = EpubOverlayPanel.None
                            onLayoutModeChanged(entry)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun EpubReaderMessage(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalReadiumApi::class)
private fun ReadiumEpubNavigatorHost(
    activity: FragmentActivity,
    item: ReadableItem,
    publication: Publication,
    initialLocator: Locator?,
    preferences: EpubReaderPreferences,
    onLocationChanged: (Locator, Int?) -> Unit,
    onSurfaceTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerId = remember(item.id) { View.generateViewId() }
    val fragmentTag = remember(item.id) { "readium-epub-${item.id}" }
    val latestOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val latestOnSurfaceTap = rememberUpdatedState(onSurfaceTap)
    val viewportVerticalPaddingPx = with(LocalDensity.current) {
        EpubViewportVerticalPadding.roundToPx()
    }
    val fragmentManager = activity.supportFragmentManager
    val surfaceBackground = remember(preferences.theme) {
        epubReaderSurfaceBackground(preferences.theme)
    }
    val navigatorFactory = remember(publication, preferences.layoutMode) {
        EpubNavigatorFactory(
            publication = publication,
            configuration = EpubNavigatorFactory.Configuration(
                defaults = EpubDefaults(
                    scroll = preferences.layoutMode == EpubReaderLayoutMode.Scroll,
                    publisherStyles = false,
                ),
            ),
        )
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag(EpubReaderSurfaceTag),
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                setBackgroundColor(surfaceBackground.toArgb())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
            }
        },
        update = { container ->
            container.setBackgroundColor(surfaceBackground.toArgb())
        },
    )

    DisposableEffect(
        fragmentManager,
        publication,
        navigatorFactory,
        containerId,
        fragmentTag,
        initialLocator,
    ) {
        var navigatorFragment: EpubNavigatorFragment? = null
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = preferences.toNavigatorPreferences(),
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(
                    pageIndex: Int,
                    totalPages: Int,
                    locator: Locator,
                ) {
                    latestOnLocationChanged.value(locator, totalPages)
                }

                override fun onPageLoaded() {
                    activity.lifecycleScope.launch {
                        navigatorFragment?.let { fragment ->
                            applyEpubViewportPadding(
                                navigator = fragment,
                                publication = publication,
                                paddingPx = viewportVerticalPaddingPx,
                            )
                        }
                    }
                }
            },
            configuration = EpubNavigatorFragment.Configuration(
                shouldApplyInsetsPadding = false,
            ),
        )
        val fragment = fragmentFactory.instantiate(
            activity.classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment
        navigatorFragment = fragment
        val centerTapListener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val viewWidth = fragment.publicationView.width.toFloat()
                if (viewWidth <= 0f) return false
                val sideZoneWidth = viewWidth * 0.25f
                val isCenterTap = event.point.x > sideZoneWidth &&
                    event.point.x < viewWidth - sideZoneWidth
                if (!isCenterTap) return false
                latestOnSurfaceTap.value()
                return true
            }
        }
        fragment.addInputListener(centerTapListener)
        fragmentManager.beginTransaction()
            .replace(containerId, fragment, fragmentTag)
            .commitNowAllowingStateLoss()

        onDispose {
            val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
            navigator?.let {
                it.removeInputListener(centerTapListener)
                latestOnLocationChanged.value(it.currentLocator.value, null)
            }
            fragmentManager.findFragmentByTag(fragmentTag)?.let { existing ->
                fragmentManager.beginTransaction()
                    .remove(existing)
                    .commitNowAllowingStateLoss()
            }
        }
    }

    LaunchedEffect(fragmentTag, preferences) {
        val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
        navigator?.submitPreferences(preferences.toNavigatorPreferences())
        navigator?.let {
            applyEpubViewportPadding(
                navigator = it,
                publication = publication,
                paddingPx = viewportVerticalPaddingPx,
            )
        }
    }
}

private fun buildEpubProgressLabel(
    progressSnapshot: ReaderProgressSnapshot?,
    pageCount: Int,
): String = when {
    progressSnapshot?.percent != null -> {
        val percent = ((progressSnapshot.percent ?: 0.0) * 100).toInt().coerceIn(0, 100)
        if (pageCount > 0) "$percent% read · $pageCount locations" else "$percent% read"
    }

    pageCount > 0 -> "$pageCount locations"
    else -> "Saved location"
}

internal const val EpubReaderSurfaceTag = "epub-reader-surface"
internal const val EpubReaderOverlayTag = "epub-reader-overlay"
internal const val EpubReaderViewportTag = "epub-reader-viewport"

internal sealed interface EpubLabeledOption {
    val label: String
}

private sealed interface EpubPublicationState {
    data object Loading : EpubPublicationState

    data class Ready(
        val publication: Publication,
    ) : EpubPublicationState

    data class Error(
        val message: String,
    ) : EpubPublicationState
}

private enum class EpubOverlayPanel {
    None,
    Text,
    Theme,
    Layout,
}

private val EpubViewportVerticalPadding = 20.dp

private fun epubReaderSurfaceBackground(
    theme: EpubReaderTheme,
): Color = when (theme) {
    EpubReaderTheme.Light -> Color(0xFFFCFCFA)
    EpubReaderTheme.Dark -> Color(0xFF121212)
}

internal suspend fun applyEpubViewportPadding(
    navigator: EpubNavigatorFragment,
    publication: Publication,
    paddingPx: Int,
) {
    if (paddingPx <= 0 || publication.metadata.presentation.layout == EpubLayout.FIXED) return
    navigator.evaluateJavascript(buildEpubViewportPaddingScript(paddingPx))
}

internal fun buildEpubViewportPaddingScript(
    paddingPx: Int,
): String {
    val clampedPaddingPx = paddingPx.coerceAtLeast(0)
    return """
        (function() {
          var padding = '${clampedPaddingPx}px';
          var root = document.documentElement;
          var body = document.body;
          if (!root || !body) return 'missing-root';
          root.style.boxSizing = 'border-box';
          body.style.boxSizing = 'border-box';
          body.style.paddingTop = padding;
          body.style.paddingBottom = padding;
          return padding;
        })();
    """.trimIndent()
}
