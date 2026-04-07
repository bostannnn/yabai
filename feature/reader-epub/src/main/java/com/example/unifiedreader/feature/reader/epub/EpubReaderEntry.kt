package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
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
import com.example.unifiedreader.feature.reader.core.ReaderBottomProgressBar
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
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import kotlin.math.roundToInt

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
    var currentPage by rememberSaveable(item.id) {
        mutableIntStateOf(progressSnapshot?.page ?: initialLocator?.locations?.position ?: 0)
    }
    var currentProgress by rememberSaveable(item.id) {
        mutableDoubleStateOf(
            progressSnapshot?.percent
                ?: initialLocator?.locations?.totalProgression
                ?: initialLocator?.locations?.progression
                ?: 0.0,
        )
    }
    var seekRequestId by remember { mutableIntStateOf(0) }
    var seekRequest by remember { mutableStateOf<EpubProgressSeekRequest?>(null) }
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
            val bookProgress by produceState(
                initialValue = EpubBookProgress(
                    pageCount = 0,
                    chapterMarks = emptyList(),
                ),
                key1 = state.publication,
            ) {
                value = withContext(Dispatchers.IO) {
                    state.publication.buildEpubBookProgress()
                }
            }
            LaunchedEffect(bookProgress.pageCount) {
                if (bookProgress.pageCount > 0) {
                    pageCount = bookProgress.pageCount
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
                        onLocationChanged = { locator, pageIndex, _ ->
                            val bookPageCount = bookProgress.pageCount
                                .takeIf { it > 0 }
                                ?: state.publication.readingOrder.size.takeIf { it > 0 }
                            if (bookPageCount != null) {
                                pageCount = bookPageCount
                            }
                            val reportedPage = locator.locations.position
                                ?: state.publication.readingOrderPositionFor(locator)
                                ?: pageIndex + 1
                            if (reportedPage > 0) {
                                currentPage = reportedPage
                            }
                            currentProgress = (
                                locator.locations.totalProgression
                                    ?: locator.locations.progression
                                    ?: bookPageCount?.let { pages ->
                                        reportedPage.toDouble() / pages.toDouble()
                                    }
                                    ?: currentProgress
                                ).coerceIn(0.0, 1.0)
                            sessionStore.saveProgress(
                                item = item,
                                locator = buildReaderLocator(
                                    locator = locator,
                                    page = reportedPage.takeIf { it > 0 },
                                ),
                                isFinished = bookPageCount?.let { reportedPage >= it } ?: false,
                                syncState = SyncState.DIRTY,
                                pageCount = bookPageCount ?: pageCount.takeIf { it > 0 },
                            )
                        },
                        onSurfaceTap = overlayState::toggle,
                        seekRequest = seekRequest,
                        onSeekRequestConsumed = {
                            seekRequest = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                overlay = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EpubReaderOverlay(
                            itemId = item.id,
                            title = item.title,
                            progressLabel = buildEpubProgressLabel(
                                progress = currentProgress,
                                position = currentPage,
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
                        ReaderBottomProgressBar(
                            progress = currentProgress.toFloat(),
                            percentLabel = buildEpubPercentLabel(currentProgress),
                            pageLabel = buildEpubPageProgressLabel(
                                position = currentPage,
                                pageCount = pageCount,
                            ),
                            chapterMarks = bookProgress.chapterMarks,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            onProgressSelected = { selectedProgress ->
                                currentProgress = selectedProgress.toDouble()
                                seekRequest = EpubProgressSeekRequest(
                                    id = seekRequestId++,
                                    progress = selectedProgress,
                                )
                            },
                        )
                    }
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
    onLocationChanged: (Locator, Int, Int?) -> Unit,
    onSurfaceTap: () -> Unit,
    seekRequest: EpubProgressSeekRequest?,
    onSeekRequestConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerId = remember(item.id) { View.generateViewId() }
    val fragmentTag = remember(item.id) { "readium-epub-${item.id}" }
    val latestOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val latestOnSurfaceTap = rememberUpdatedState(onSurfaceTap)
    val viewportVerticalInsetPx = with(LocalDensity.current) {
        EpubViewportVerticalInset.roundToPx()
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
            FrameLayout(context).apply {
                setBackgroundColor(surfaceBackground.toArgb())
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                )
                addView(
                    FragmentContainerView(context).apply {
                        id = containerId
                        setBackgroundColor(surfaceBackground.toArgb())
                    },
                    epubNavigatorContainerLayoutParams(viewportVerticalInsetPx),
                )
            }
        },
        update = { container ->
            container.setBackgroundColor(surfaceBackground.toArgb())
            container.updateEpubNavigatorContainer(
                verticalInsetPx = viewportVerticalInsetPx,
                backgroundColor = surfaceBackground.toArgb(),
            )
            container.updateEpubGutterTapListener(
                verticalInsetPx = viewportVerticalInsetPx,
                onSurfaceTap = latestOnSurfaceTap.value,
            )
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
                    latestOnLocationChanged.value(locator, pageIndex, totalPages)
                }

                override fun onPageLoaded() {
                    activity.lifecycleScope.launch {
                        navigatorFragment?.let { fragment ->
                            applyEpubViewportPadding(
                                navigator = fragment,
                                publication = publication,
                                paddingPx = 0,
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
                latestOnLocationChanged.value(it.currentLocator.value, 0, null)
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
                paddingPx = 0,
            )
        }
    }

    LaunchedEffect(seekRequest, fragmentTag, publication) {
        val request = seekRequest ?: return@LaunchedEffect
        val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
        if (navigator == null) {
            onSeekRequestConsumed()
            return@LaunchedEffect
        }
        val locator = withContext(Dispatchers.IO) {
            publication.locateProgression(request.progress.toDouble())
        }
        locator?.let { navigator.go(it, animated = false) }
        onSeekRequestConsumed()
    }
}

private fun buildEpubProgressLabel(
    progress: Double,
    position: Int,
    pageCount: Int,
): String = when {
    position > 0 && pageCount > 0 -> {
        "${buildEpubPercentLabel(progress)} read · $position/$pageCount"
    }

    progress > 0.0 -> "${buildEpubPercentLabel(progress)} read"
    pageCount > 0 -> "$pageCount locations"
    else -> "Saved location"
}

private fun buildEpubPercentLabel(
    progress: Double,
): String = "${((progress.coerceIn(0.0, 1.0) * 100.0).roundToInt()).coerceIn(0, 100)}%"

private fun buildEpubPageProgressLabel(
    position: Int,
    pageCount: Int,
): String = when {
    position > 0 && pageCount > 0 -> "$position/$pageCount"
    pageCount > 0 -> "?/$pageCount"
    position > 0 -> "$position"
    else -> "?/?"
}

private suspend fun Publication.buildEpubBookProgress(): EpubBookProgress {
    val positionsByReadingOrder = positionsByReadingOrder()
    val positionCount = positionsByReadingOrder.flatten().size
    val pageCount = positionCount.takeIf { it > 0 } ?: readingOrder.size
    val positionProgressionByHref = positionsByReadingOrder
        .mapNotNull { positions ->
            val position = positions.firstOrNull() ?: return@mapNotNull null
            val progression = position.locations.totalProgression ?: return@mapNotNull null
            position.href.removeFragment().toString() to progression
        }
        .toMap()

    val tocMarks = tableOfContents
        .flatMap { it.flattenEpubTocLinks() }
        .mapNotNull { link ->
            val locator = locatorFromLink(link)
            val href = (locator?.href ?: link.url()).removeFragment().toString()
            positionProgressionByHref[href] ?: locator?.locations?.totalProgression
        }
        .toChapterMarks()

    val chapterMarks = tocMarks.ifEmpty {
        readingOrder
            .indices
            .map { index -> index.toDouble() / readingOrder.size.toDouble() }
            .toChapterMarks()
    }
    return EpubBookProgress(
        pageCount = pageCount,
        chapterMarks = chapterMarks,
    )
}

private fun Link.flattenEpubTocLinks(): List<Link> =
    listOf(this) + children.flatMap { it.flattenEpubTocLinks() }

private fun Publication.readingOrderPositionFor(
    locator: Locator,
): Int? {
    val href = locator.href.removeFragment().toString()
    return readingOrder
        .indexOfFirst { link -> link.url().removeFragment().toString() == href }
        .takeUnless { it < 0 }
        ?.plus(1)
}

private fun List<Double>.toChapterMarks(): List<Float> =
    map { it.coerceIn(0.0, 1.0).toFloat() }
        .filter { it > 0f && it < 1f }
        .distinct()
        .sorted()

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

private data class EpubBookProgress(
    val pageCount: Int,
    val chapterMarks: List<Float>,
)

private data class EpubProgressSeekRequest(
    val id: Int,
    val progress: Float,
)

private val EpubViewportVerticalInset = 32.dp

private fun epubNavigatorContainerLayoutParams(
    verticalInsetPx: Int,
): FrameLayout.LayoutParams = FrameLayout.LayoutParams(
    FrameLayout.LayoutParams.MATCH_PARENT,
    FrameLayout.LayoutParams.MATCH_PARENT,
    Gravity.CENTER,
).apply {
    topMargin = verticalInsetPx
    bottomMargin = verticalInsetPx
}

private fun FrameLayout.updateEpubNavigatorContainer(
    verticalInsetPx: Int,
    backgroundColor: Int,
) {
    val child = getChildAt(0) ?: return
    child.setBackgroundColor(backgroundColor)
    val current = child.layoutParams as? FrameLayout.LayoutParams
    val updated = epubNavigatorContainerLayoutParams(verticalInsetPx)
    if (
        current == null ||
        current.topMargin != updated.topMargin ||
        current.bottomMargin != updated.bottomMargin
    ) {
        child.layoutParams = updated
    }
}

private fun FrameLayout.updateEpubGutterTapListener(
    verticalInsetPx: Int,
    onSurfaceTap: () -> Unit,
) {
    var isTrackingGutterTap = false
    setOnTouchListener { view, event ->
        if (verticalInsetPx <= 0) return@setOnTouchListener false

        fun isCenterGutterTap(): Boolean {
            val isVerticalGutterTap = event.y < verticalInsetPx ||
                event.y > view.height - verticalInsetPx
            if (!isVerticalGutterTap) return false

            val sideZoneWidth = view.width * 0.25f
            return event.x > sideZoneWidth &&
                event.x < view.width - sideZoneWidth
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTrackingGutterTap = isCenterGutterTap()
                isTrackingGutterTap
            }

            MotionEvent.ACTION_UP -> {
                if (!isTrackingGutterTap) return@setOnTouchListener false
                isTrackingGutterTap = false
                onSurfaceTap()
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                isTrackingGutterTap = false
                false
            }

            else -> isTrackingGutterTap
        }
    }
}

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
    if (publication.metadata.presentation.layout == EpubLayout.FIXED) return
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
