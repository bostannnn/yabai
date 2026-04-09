package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.text.Html
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import java.io.ByteArrayInputStream
import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.example.unifiedreader.feature.reader.core.ReaderOverlayIconButton
import com.example.unifiedreader.feature.reader.core.ReaderOverlayMenuAction
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.findContextAncestor
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayPanelState
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.EpubDefaults
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

@Composable
fun EpubReaderScreen(
    item: ReadableItem,
    sessionStore: ReaderSessionStore,
    documentUri: Uri? = null,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findContextAncestor<FragmentActivity>() }
    val isLandscape = remember(
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> true
            Configuration.ORIENTATION_PORTRAIT -> false
            else -> configuration.screenWidthDp > configuration.screenHeightDp
        }
    }
    val appBackground = MaterialTheme.colorScheme.background
    val appReaderTheme = remember(appBackground) {
        if (appBackground.luminance() < 0.5f) {
            EpubReaderTheme.Dark
        } else {
            EpubReaderTheme.Light
        }
    }
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
    if (!hydrated) {
        EpubReaderMessage(
            title = item.title,
            body = "Restoring EPUB state…",
            onBack = onBack,
        )
        return
    }
    var initialRestoreLocked by rememberSaveable(item.id) { mutableStateOf(false) }
    var restoreProgressSnapshot by remember(item.id) {
        mutableStateOf(progressSnapshot)
    }
    var restoreSessionSnapshot by remember(item.id) {
        mutableStateOf(sessionSnapshot)
    }
    LaunchedEffect(progressSnapshot, sessionSnapshot, initialRestoreLocked) {
        if (!initialRestoreLocked) {
            restoreProgressSnapshot = progressSnapshot
            restoreSessionSnapshot = sessionSnapshot
        }
    }
    val serializedInitialLocator = restoreProgressSnapshot?.toReadiumLocator()
    val restoredPreferences = restoreEpubPreferences(restoreSessionSnapshot)
    val restoredThemeSelection = remember(restoreSessionSnapshot, restoredPreferences, appReaderTheme) {
        resolveRestoredThemeSelection(
            restoredPreferences = restoredPreferences,
            rawPreferences = restoreSessionSnapshot?.preferences.orEmpty(),
            appReaderTheme = appReaderTheme,
        )
    }
    var fontSize by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.fontSize) }
    var lineHeight by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.lineHeight) }
    var theme by rememberSaveable(item.id) {
        mutableStateOf(
            restoredThemeSelection.theme,
        )
    }
    var layoutMode by rememberSaveable(item.id) {
        mutableStateOf(
            if (restoredPreferences.layoutModeOverride) {
                restoredPreferences.layoutMode
            } else {
                defaultEpubLayoutMode(isLandscape)
            },
        )
    }
    var useAppThemeDefault by rememberSaveable(item.id) { mutableStateOf(restoredThemeSelection.useAppThemeDefault) }
    var useOrientationLayoutDefault by rememberSaveable(item.id) {
        mutableStateOf(!restoredPreferences.layoutModeOverride)
    }
    var restoredSessionState by rememberSaveable(item.id) { mutableStateOf(false) }
    var pageCount by rememberSaveable(item.id) { mutableIntStateOf(restoreSessionSnapshot?.pageCount ?: 0) }
    var currentPage by rememberSaveable(item.id) {
        mutableIntStateOf(restoreProgressSnapshot?.page ?: serializedInitialLocator?.locations?.position ?: 0)
    }
    var currentProgress by rememberSaveable(item.id) {
        mutableDoubleStateOf(
            restoreProgressSnapshot?.percent
                ?: serializedInitialLocator?.locations?.totalProgression
                ?: serializedInitialLocator?.locations?.progression
                ?: 0.0,
        )
    }
    var currentHref by rememberSaveable(item.id) {
        mutableStateOf(serializedInitialLocator?.href?.removeFragment()?.toString())
    }
    var seekRequestId by remember { mutableIntStateOf(0) }
    var navigationRequest by remember { mutableStateOf<EpubNavigationRequest?>(null) }
    var isTocSheetOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var bottomProgressBarHeightPx by remember { mutableIntStateOf(0) }
    val tocBottomAnchorHeight = if (bottomProgressBarHeightPx > 0) {
        with(density) {
            bottomProgressBarHeightPx.toDp()
        }
    } else {
        EpubTocBottomAnchorFallbackHeight
    }
    var footnoteContent by remember(item.id) { mutableStateOf<EpubFootnoteContent?>(null) }
    val overlayState = rememberReaderOverlayState(
        key = "epub-overlay-${item.id}",
        initiallyVisible = true,
    )
    val documentUriState by produceState<ReaderDocumentUriState>(
        initialValue = if (documentUri != null || documentUriProvider == null) {
            ReaderDocumentUriState.Ready(documentUri)
        } else {
            ReaderDocumentUriState.Loading
        },
        key1 = item.id,
        key2 = documentUri?.toString(),
        key3 = documentUriProvider,
    ) {
        if (documentUri != null || documentUriProvider == null) {
            value = ReaderDocumentUriState.Ready(documentUri)
        } else {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ReaderDocumentUriState.Ready(documentUriProvider(item))
                }.getOrElse { throwable ->
                    ReaderDocumentUriState.Error(
                        throwable.message ?: "Failed to prepare EPUB content.",
                    )
                }
            }
        }
    }

    LaunchedEffect(hydrated, restoredPreferences, appReaderTheme, isLandscape, useOrientationLayoutDefault) {
        if (!hydrated || restoredSessionState) return@LaunchedEffect
        val restoredThemeUsesDefault = restoredThemeSelection.useAppThemeDefault
        val restoredLayoutUsesDefault = !restoredPreferences.layoutModeOverride
        fontSize = restoredPreferences.fontSize
        lineHeight = restoredPreferences.lineHeight
        useAppThemeDefault = restoredThemeUsesDefault
        useOrientationLayoutDefault = restoredLayoutUsesDefault
        theme = restoredThemeSelection.theme
        layoutMode = if (restoredLayoutUsesDefault) {
            defaultEpubLayoutMode(isLandscape)
        } else {
            restoredPreferences.layoutMode
        }
        restoredSessionState = true
    }
    LaunchedEffect(hydrated, restoredSessionState, appReaderTheme, useAppThemeDefault) {
        if (!hydrated || !restoredSessionState || !useAppThemeDefault) return@LaunchedEffect
        theme = appReaderTheme
    }
    LaunchedEffect(hydrated, restoredSessionState, isLandscape, useOrientationLayoutDefault) {
        if (!hydrated || !restoredSessionState || !useOrientationLayoutDefault) return@LaunchedEffect
        layoutMode = defaultEpubLayoutMode(isLandscape)
    }
    LaunchedEffect(initialRestoreLocked, restoreProgressSnapshot, restoreSessionSnapshot, serializedInitialLocator) {
        if (initialRestoreLocked) return@LaunchedEffect
        pageCount = restoreSessionSnapshot?.pageCount ?: pageCount
        currentPage = restoreProgressSnapshot?.page ?: serializedInitialLocator?.locations?.position ?: currentPage
        currentProgress = restoreProgressSnapshot?.percent
            ?: serializedInitialLocator?.locations?.totalProgression
            ?: serializedInitialLocator?.locations?.progression
            ?: currentProgress
        currentHref = serializedInitialLocator?.href?.removeFragment()?.toString()
    }

    val publicationState by produceState<EpubPublicationState>(
        initialValue = EpubPublicationState.Loading,
        key1 = item.id,
        key2 = documentUriState.cacheKey,
    ) {
        value = when (val uriState = documentUriState) {
            ReaderDocumentUriState.Loading -> EpubPublicationState.Loading
            is ReaderDocumentUriState.Error -> EpubPublicationState.Error(uriState.message)
            is ReaderDocumentUriState.Ready -> withContext(Dispatchers.IO) {
            runCatching {
                val publicationFile = uriState.uri?.let { importedUri ->
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
            val initialLocatorState by produceState<EpubInitialLocatorState>(
                initialValue = if (restoreProgressSnapshot.needsEpubProgressionLocator(serializedInitialLocator)) {
                    EpubInitialLocatorState.Loading
                } else {
                    EpubInitialLocatorState.Ready(serializedInitialLocator)
                },
                key1 = state.publication,
                key2 = restoreProgressSnapshot,
                key3 = serializedInitialLocator,
            ) {
                value = EpubInitialLocatorState.Ready(
                    serializedInitialLocator ?: withContext(Dispatchers.IO) {
                        restoreProgressSnapshot?.toProgressionFallbackLocator(state.publication)
                    },
                )
            }
            DisposableEffect(state.publication) {
                onDispose {
                    state.publication.close()
                }
            }
            val bookProgress by produceState(
                initialValue = EpubBookProgress(
                    pageCount = 0,
                    chapterMarks = emptyList(),
                    tocEntries = emptyList(),
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

            LaunchedEffect(
                item.id,
                hydrated,
                restoredSessionState,
                fontSize,
                lineHeight,
                theme,
                layoutMode,
                pageCount,
                useAppThemeDefault,
                useOrientationLayoutDefault,
            ) {
                if (!hydrated || !restoredSessionState) return@LaunchedEffect
                val preferences = buildEpubPreferencesMap(
                        EpubReaderPreferences(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            theme = theme,
                            themeOverride = !useAppThemeDefault,
                            layoutMode = layoutMode,
                            layoutModeOverride = !useOrientationLayoutDefault,
                        ),
                )
                sessionStore.updateSession(
                    item = item,
                    pageCount = pageCount.takeIf { it > 0 },
                    preferences = preferences,
                )
            }

            val resolvedInitialLocator = when (val locatorState = initialLocatorState) {
                EpubInitialLocatorState.Loading -> {
                    EpubReaderMessage(
                        title = item.title,
                        body = "Restoring EPUB position…",
                        onBack = onBack,
                    )
                    return
                }
                is EpubInitialLocatorState.Ready -> locatorState.locator
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
                        initialLocator = resolvedInitialLocator,
                        preferences = EpubReaderPreferences(
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            theme = theme,
                            layoutMode = layoutMode,
                        ),
                        currentHref = currentHref,
                        currentProgress = currentProgress,
                        onLocationChanged = { locator, pageIndex, _ ->
                            if (!initialRestoreLocked) {
                                initialRestoreLocked = true
                            }
                            currentHref = locator.href.removeFragment().toString()
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
                        onFootnoteActivated = { footnote ->
                            isTocSheetOpen = false
                            footnoteContent = footnote
                            overlayState.show()
                        },
                        navigationRequest = navigationRequest,
                        onNavigationRequestConsumed = {
                            navigationRequest = null
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
                            useAppThemeDefault = useAppThemeDefault,
                            layoutMode = layoutMode,
                            useOrientationLayoutDefault = useOrientationLayoutDefault,
                            overlayVisible = overlayState.isVisible,
                            onBack = onBack,
                            onFontSizeChanged = {
                                fontSize = it
                            },
                            onLineHeightChanged = {
                                lineHeight = it
                            },
                            onThemeChanged = {
                                useAppThemeDefault = false
                                theme = it
                            },
                            onUseAppThemeDefault = {
                                useAppThemeDefault = true
                                theme = appReaderTheme
                            },
                            onLayoutModeChanged = {
                                useOrientationLayoutDefault = false
                                layoutMode = it
                            },
                            onUseOrientationLayoutDefault = {
                                useOrientationLayoutDefault = true
                                layoutMode = defaultEpubLayoutMode(isLandscape)
                            },
                            modifier = Modifier.testTag(EpubReaderOverlayTag),
                        )
                        EpubTableOfContentsSheet(
                            title = item.title,
                            entries = bookProgress.tocEntries,
                            currentHref = currentHref,
                            currentProgress = currentProgress,
                            isOpen = isTocSheetOpen,
                            bottomAnchorHeight = tocBottomAnchorHeight,
                            onDismissRequest = {
                                isTocSheetOpen = false
                            },
                            onEntrySelected = { entry ->
                                isTocSheetOpen = false
                                navigationRequest = EpubTocNavigationRequest(
                                    id = seekRequestId++,
                                    link = entry.link,
                                )
                            },
                        )
                        ReaderBottomProgressBar(
                            progress = currentProgress.toFloat(),
                            percentLabel = buildEpubPercentLabel(currentProgress),
                            pageLabel = buildEpubPageProgressLabel(
                                position = currentPage,
                                pageCount = pageCount,
                            ),
                            chapterMarks = bookProgress.chapterMarks,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .onGloballyPositioned { coordinates ->
                                    bottomProgressBarHeightPx = coordinates.size.height
                                },
                            leadingAction = {
                                ReaderOverlayIconButton(
                                    isActive = isTocSheetOpen,
                                    onClick = {
                                        overlayState.show()
                                        isTocSheetOpen = true
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Toc,
                                        contentDescription = "Table of contents",
                                        tint = Color(0xFFF5F7FA),
                                    )
                                }
                            },
                            onProgressSelected = { selectedProgress ->
                                currentProgress = selectedProgress.toDouble()
                                navigationRequest = EpubProgressSeekRequest(
                                    id = seekRequestId++,
                                    progress = selectedProgress,
                                )
                            },
                        )
                        EpubFootnotePopup(
                            footnote = footnoteContent,
                            onDismissRequest = {
                                footnoteContent = null
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
    useAppThemeDefault: Boolean,
    layoutMode: EpubReaderLayoutMode,
    useOrientationLayoutDefault: Boolean,
    overlayVisible: Boolean,
    onBack: () -> Unit,
    onFontSizeChanged: (Double) -> Unit,
    onLineHeightChanged: (Double) -> Unit,
    onThemeChanged: (EpubReaderTheme) -> Unit,
    onUseAppThemeDefault: () -> Unit,
    onLayoutModeChanged: (EpubReaderLayoutMode) -> Unit,
    onUseOrientationLayoutDefault: () -> Unit,
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
                    Text(
                        text = formatEpubFontSizeValue(fontSize),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB7C4CE),
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
                    Text(
                        text = formatEpubLineHeightValue(lineHeight),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFB7C4CE),
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
                ReaderOverlayDropdownItem(
                    label = "Follow app theme",
                    selected = useAppThemeDefault,
                    onClick = {
                        activePanel = EpubOverlayPanel.None
                        onUseAppThemeDefault()
                    },
                )
                EpubReaderTheme.entries.forEach { entry ->
                    ReaderOverlayDropdownItem(
                        label = entry.label,
                        selected = !useAppThemeDefault && entry == theme,
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
                ReaderOverlayDropdownItem(
                    label = "Auto (portrait: Page, landscape: Spread)",
                    selected = useOrientationLayoutDefault,
                    onClick = {
                        activePanel = EpubOverlayPanel.None
                        onUseOrientationLayoutDefault()
                    },
                )
                EpubReaderLayoutMode.entries.forEach { entry ->
                    ReaderOverlayDropdownItem(
                        label = entry.label,
                        selected = !useOrientationLayoutDefault && entry == layoutMode,
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
private fun EpubFootnotePopup(
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
            val popupShape = RoundedCornerShape(8.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(popupWidth)
                    .heightIn(max = popupMaxHeight)
                    .shadow(
                        elevation = 18.dp,
                        shape = popupShape,
                    )
                    .background(
                        color = Color(0xFFF8FAFC),
                        shape = popupShape,
                    )
                    .border(
                        width = 1.dp,
                        color = Color(0xFFE2E8F0),
                        shape = popupShape,
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = "Close",
                            color = Color(0xFF2563EB),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note.body.ifBlank { "No footnote text" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF111827),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
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
    currentHref: String?,
    currentProgress: Double,
    onLocationChanged: (Locator, Int, Int?) -> Unit,
    onSurfaceTap: () -> Unit,
    onFootnoteActivated: (EpubFootnoteContent) -> Unit,
    navigationRequest: EpubNavigationRequest?,
    onNavigationRequestConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerId = remember(item.id) { View.generateViewId() }
    val fragmentTag = remember(item.id) { "readium-epub-${item.id}" }
    val configuration = LocalConfiguration.current
    val latestOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val latestOnSurfaceTap = rememberUpdatedState(onSurfaceTap)
    val latestOnFootnoteActivated = rememberUpdatedState(onFootnoteActivated)
    val latestCurrentHref = rememberUpdatedState(currentHref)
    val latestCurrentProgress = rememberUpdatedState(currentProgress)
    val footnoteNavigationScope = rememberCoroutineScope()
    val isLandscape = remember(
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> true
            Configuration.ORIENTATION_PORTRAIT -> false
            else -> configuration.screenWidthDp > configuration.screenHeightDp
        }
    }
    val viewportSpacing = remember(isLandscape) { defaultEpubViewportSpacing(isLandscape) }
    val viewportVerticalInsetPx = with(LocalDensity.current) {
        viewportSpacing.containerInset.roundToPx()
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
    ) {
        var navigatorFragment: EpubNavigatorFragment? = null
        val footnoteNavigationJobs = mutableSetOf<Job>()
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = preferences.toNavigatorPreferences(),
            listener = object : EpubNavigatorFragment.Listener {
                override fun shouldFollowInternalLink(
                    link: Link,
                    context: HyperlinkNavigator.LinkContext?,
                ): Boolean {
                    val footnoteContext = context as? HyperlinkNavigator.FootnoteContext
                    if (footnoteContext != null) {
                        latestOnFootnoteActivated.value(
                            EpubFootnoteContent(
                                title = link.title?.takeIf(String::isNotBlank) ?: "Footnote",
                                body = htmlFootnoteToPlainText(footnoteContext.noteContent),
                            ),
                        )
                        return false
                    }
                    val hasFragment = link.url().fragment != null
                    if (!hasFragment) return true
                    val forceFootnote = link.isLikelyEpubFootnoteLink(
                        publication = publication,
                        currentHref = latestCurrentHref.value,
                        currentProgress = latestCurrentProgress.value,
                    )
                    footnoteNavigationJobs.toList().forEach { job -> job.cancel() }
                    footnoteNavigationJobs.clear()
                    val job = footnoteNavigationScope.launch {
                        val footnote = withContext(Dispatchers.IO) {
                            publication.extractEpubFootnoteOrNull(
                                link = link,
                                forceFootnote = forceFootnote,
                            )
                        }
                        val activeNavigator =
                            fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
                        if (activeNavigator !== navigatorFragment || activeNavigator?.isAdded != true) {
                            return@launch
                        }
                        if (footnote != null) {
                            latestOnFootnoteActivated.value(footnote)
                        } else {
                            activeNavigator.go(link, animated = false)
                        }
                    }
                    footnoteNavigationJobs += job
                    job.invokeOnCompletion {
                        footnoteNavigationJobs -= job
                    }
                    return false
                }

                override fun onExternalLinkActivated(url: AbsoluteUrl) = Unit
            },
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
                                paddingPx = viewportSpacing.contentPaddingPx,
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
        val tapZoneListener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val viewWidth = fragment.publicationView.width.toFloat()
                if (viewWidth <= 0f) return false
                return when (
                    resolveEpubTapAction(
                        tapX = event.point.x,
                        containerWidth = viewWidth,
                        readingProgression = fragment.settings.value.readingProgression,
                    )
                ) {
                    EpubTapAction.Previous -> {
                        fragment.goBackward(animated = true)
                        true
                    }

                    EpubTapAction.Next -> {
                        fragment.goForward(animated = true)
                        true
                    }

                    EpubTapAction.ToggleOverlay -> {
                        latestOnSurfaceTap.value()
                        true
                    }
                }
            }
        }
        fragment.addInputListener(tapZoneListener)
        fragmentManager.beginTransaction()
            .replace(containerId, fragment, fragmentTag)
            .commitNowAllowingStateLoss()

        onDispose {
            footnoteNavigationJobs.toList().forEach { job -> job.cancel() }
            footnoteNavigationJobs.clear()
            val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
            navigator?.let {
                it.removeInputListener(tapZoneListener)
                latestOnLocationChanged.value(it.currentLocator.value, 0, null)
            }
            fragmentManager.findFragmentByTag(fragmentTag)?.let { existing ->
                fragmentManager.beginTransaction()
                    .remove(existing)
                    .commitNowAllowingStateLoss()
            }
        }
    }

    LaunchedEffect(fragmentTag, preferences, viewportSpacing.contentPaddingPx) {
        val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
        navigator?.submitPreferences(preferences.toNavigatorPreferences())
        navigator?.let {
            applyEpubViewportPadding(
                navigator = it,
                publication = publication,
                paddingPx = viewportSpacing.contentPaddingPx,
            )
        }
    }

    LaunchedEffect(navigationRequest, fragmentTag, publication) {
        val request = navigationRequest ?: return@LaunchedEffect
        val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
        if (navigator == null) {
            onNavigationRequestConsumed()
            return@LaunchedEffect
        }
        when (request) {
            is EpubProgressSeekRequest -> {
                val locator = withContext(Dispatchers.IO) {
                    publication.locateProgression(request.progress.toDouble())
                }
                locator?.let { navigator.go(it, animated = false) }
            }

            is EpubTocNavigationRequest -> {
                navigator.go(request.link, animated = false)
            }
        }
        onNavigationRequestConsumed()
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

internal enum class EpubTapAction {
    Previous,
    Next,
    ToggleOverlay,
}

internal fun resolveEpubTapAction(
    tapX: Float,
    containerWidth: Float,
    readingProgression: ReadingProgression,
): EpubTapAction {
    if (containerWidth <= 0f) {
        return EpubTapAction.ToggleOverlay
    }
    val isLeftZone = tapX <= containerWidth * EpubNavigationZoneFraction
    val isRightZone = tapX >= containerWidth * (1f - EpubNavigationZoneFraction)
    return when {
        isLeftZone && readingProgression == ReadingProgression.RTL -> EpubTapAction.Next
        isLeftZone -> EpubTapAction.Previous
        isRightZone && readingProgression == ReadingProgression.RTL -> EpubTapAction.Previous
        isRightZone -> EpubTapAction.Next
        else -> EpubTapAction.ToggleOverlay
    }
}

private fun Publication.readingOrderPositionFor(
    locator: Locator,
): Int? {
    val href = locator.href.removeFragment().toString()
    return readingOrder
        .indexOfFirst { link -> link.url().removeFragment().toString() == href }
        .takeUnless { it < 0 }
        ?.plus(1)
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

private sealed interface EpubInitialLocatorState {
    data object Loading : EpubInitialLocatorState

    data class Ready(
        val locator: Locator?,
    ) : EpubInitialLocatorState
}

private fun ReaderProgressSnapshot?.needsEpubProgressionLocator(
    serializedLocator: Locator?,
): Boolean = this != null &&
    serializedLocator == null &&
    percent != null

private suspend fun ReaderProgressSnapshot.toProgressionFallbackLocator(
    publication: Publication,
): Locator? {
    val progress = percent ?: return null
    return publication.locateProgression(progress.coerceIn(0.0, 1.0))
}

private sealed interface ReaderDocumentUriState {
    val cacheKey: String

    data object Loading : ReaderDocumentUriState {
        override val cacheKey: String = "loading"
    }

    data class Ready(
        val uri: Uri?,
    ) : ReaderDocumentUriState {
        override val cacheKey: String = uri?.toString() ?: "fallback"
    }

    data class Error(
        val message: String,
    ) : ReaderDocumentUriState {
        override val cacheKey: String = "error:$message"
    }
}

private enum class EpubOverlayPanel {
    None,
    Text,
    Theme,
    Layout,
}

private sealed interface EpubNavigationRequest {
    val id: Int
}

private data class EpubProgressSeekRequest(
    override val id: Int,
    val progress: Float,
) : EpubNavigationRequest

private data class EpubTocNavigationRequest(
    override val id: Int,
    val link: Link,
) : EpubNavigationRequest

private data class EpubFootnoteContent(
    val title: String,
    val body: String,
)

private const val EpubNavigationZoneFraction = 0.25f
private val EpubTocBottomAnchorFallbackHeight = 74.dp
private val EpubFootnoteLinkHints = listOf(
    "edn",
    "enote",
    "footnote",
    "endnote",
    "note",
    "noteref",
    "fn",
    "ftn",
)

private data class EpubViewportSpacing(
    val containerInset: androidx.compose.ui.unit.Dp,
    val contentPaddingPx: Int,
)

internal data class EpubResolvedThemeSelection(
    val theme: EpubReaderTheme,
    val useAppThemeDefault: Boolean,
)

private fun defaultEpubLayoutMode(
    isLandscape: Boolean,
): EpubReaderLayoutMode = if (isLandscape) {
    EpubReaderLayoutMode.DoublePage
} else {
    EpubReaderLayoutMode.SinglePage
}

private fun defaultEpubViewportSpacing(
    isLandscape: Boolean,
): EpubViewportSpacing = if (isLandscape) {
    EpubViewportSpacing(
        containerInset = 48.dp,
        contentPaddingPx = 24,
    )
} else {
    EpubViewportSpacing(
        containerInset = 32.dp,
        contentPaddingPx = 12,
    )
}

private fun formatEpubFontSizeValue(
    value: Double,
): String = String.format(Locale.getDefault(), "%.1fx", value)

private fun formatEpubLineHeightValue(
    value: Double,
): String = String.format(Locale.getDefault(), "%.2f", value)

internal fun resolveRestoredThemeSelection(
    restoredPreferences: EpubReaderPreferences,
    rawPreferences: Map<String, String>,
    appReaderTheme: EpubReaderTheme,
): EpubResolvedThemeSelection {
    val hasLegacyThemeValue = rawPreferences.containsKey(EpubThemeKey) &&
        !rawPreferences.containsKey(EpubThemeOverrideKey)
    val useAppThemeDefault = when {
        restoredPreferences.themeOverride -> false
        hasLegacyThemeValue -> false
        else -> true
    }
    return EpubResolvedThemeSelection(
        theme = if (useAppThemeDefault) appReaderTheme else restoredPreferences.theme,
        useAppThemeDefault = useAppThemeDefault,
    )
}

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

private fun htmlFootnoteToPlainText(
    html: String,
): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
    .toString()
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

private fun Link.isLikelyEpubFootnoteLink(
    publication: Publication,
    currentHref: String?,
    currentProgress: Double,
): Boolean {
    val url = url()
    val fragment = url.fragment ?: return false
    val text = listOf(
        url.toString(),
        fragment,
        title.orEmpty(),
        rels.joinToString(" "),
    ).joinToString(" ").lowercase()
    if (EpubFootnoteLinkHints.any { hint -> hint in text }) return true
    if (title.orEmpty().trim().matches(Regex("""[\[(]?\d{1,3}[\])]?\.?"""))) return true

    val targetHref = url.removeFragment().toString()
    val currentIndex = currentHref?.let { href ->
        publication.readingOrder.indexOfFirst { entry ->
            entry.url().removeFragment().toString() == href
        }.takeUnless { it < 0 }
    }
    val targetIndex = publication.readingOrder.indexOfFirst { entry ->
        entry.url().removeFragment().toString() == targetHref
    }.takeUnless { it < 0 }
    val finalBandStart = publication.readingOrder.size
        .minus((publication.readingOrder.size / 5).coerceAtLeast(1))
        .coerceAtLeast(0)
    if (
        currentIndex != null &&
        targetIndex != null &&
        targetIndex > currentIndex &&
        targetIndex >= finalBandStart
    ) {
        return true
    }

    val targetProgress = publication.locatorFromLink(this)?.locations?.totalProgression
    return targetProgress != null &&
        targetProgress > 0.80 &&
        targetProgress > currentProgress + 0.10
}

private suspend fun Publication.extractEpubFootnoteOrNull(
    link: Link,
    forceFootnote: Boolean,
): EpubFootnoteContent? {
    val url = link.url()
    val fragment = url.fragment ?: return null
    val html = get(link)?.read()
        ?.getOrNull()
        ?.toString(Charsets.UTF_8)
        .orEmpty()
    val semanticText = runCatching {
        extractFootnoteTextById(
            html = html,
            targetId = fragment,
            forceFootnote = forceFootnote,
        )
    }.getOrNull()
    val fallbackText = if (forceFootnote) {
        extractElementHtmlById(html = html, targetId = fragment)
            ?.let(::htmlFootnoteToPlainText)
            ?.takeIf(String::isNotBlank)
    } else {
        null
    }
    val body = semanticText
        ?.takeIf(String::isNotBlank)
        ?: fallbackText
        ?: return null
    return EpubFootnoteContent(
        title = link.title?.takeIf(String::isNotBlank) ?: "Footnote",
        body = body,
    )
}

private fun extractElementHtmlById(
    html: String,
    targetId: String,
): String? {
    if (html.isBlank()) return null
    val escapedTargetId = Regex.escape(targetId)
    val pattern = Regex(
        pattern = """<([A-Za-z][A-Za-z0-9:_-]*)(?=[^>]*(?:\sid|xml:id)\s*=\s*["']$escapedTargetId["'])[^>]*>.*?</\1>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    return pattern.find(html)?.value
}

private fun extractFootnoteTextById(
    html: String,
    targetId: String,
    forceFootnote: Boolean,
): String? {
    if (html.isBlank()) return null
    val document = secureDocumentBuilderFactory()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(html.toByteArray()))
    document.documentElement?.normalize()
    val element = document.documentElement?.findElementById(targetId) ?: return null
    val container = element.findEpubFootnoteContainer()
        ?: element.takeIf { forceFootnote || it.isEpubFootnoteElement() }
        ?: return null
    return container.textContent
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
}

private fun Element.findElementById(
    targetId: String,
): Element? {
    if (
        getAttribute("id") == targetId ||
        getAttribute("xml:id") == targetId
    ) {
        return this
    }
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child.nodeType != Node.ELEMENT_NODE) continue
        val match = (child as Element).findElementById(targetId)
        if (match != null) return match
    }
    return null
}

private fun Element.findEpubFootnoteContainer(): Element? {
    var current: Element? = this
    while (current != null) {
        if (current.isEpubFootnoteElement()) return current
        val parent = current.parentNode as? Element
        current = parent?.takeUnless { it.tagName.equals("body", ignoreCase = true) }
    }
    return null
}

private fun Element.isEpubFootnoteElement(): Boolean {
    val text = listOf(
        tagName,
        getAttribute("id"),
        getAttribute("xml:id"),
        getAttribute("class"),
        getAttribute("epub:type"),
        getAttribute("role"),
    ).joinToString(" ").lowercase()
    return EpubFootnoteLinkHints.any { hint -> hint in text } ||
        "doc-footnote" in text ||
        "doc-endnote" in text
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
