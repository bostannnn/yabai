package com.example.unifiedreader.feature.reader.epub

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderActiveSessionTracker
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.findContextAncestor
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppTextButton
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
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
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import org.w3c.dom.Element
import org.w3c.dom.Node

@Composable
fun EpubReaderScreen(
    item: ReadableItem,
    sessionStore: ReaderSessionStore,
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    documentUri: Uri? = null,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)? = null,
    onBack: () -> Unit,
    onCompletionRequested: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
            isLoading = true,
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
    var typographyMode by rememberSaveable(item.id) { mutableStateOf(restoredPreferences.typographyMode) }
    var fontFamily by rememberSaveable(item.id) { mutableStateOf(restoredPreferences.fontFamily) }
    var fontSize by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.fontSize) }
    var lineHeight by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.lineHeight) }
    var pageMargins by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.pageMargins) }
    var paragraphSpacing by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.paragraphSpacing) }
    var paragraphIndent by rememberSaveable(item.id) { mutableDoubleStateOf(restoredPreferences.paragraphIndent) }
    var textAlign by rememberSaveable(item.id) { mutableStateOf(restoredPreferences.textAlign) }
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
    var currentLocator by remember(item.id) {
        mutableStateOf(serializedInitialLocator)
    }
    var hasAutoRequestedCompletion by rememberSaveable(item.id) {
        mutableStateOf(false)
    }
    var seekRequestId by remember { mutableIntStateOf(0) }
    var navigationRequest by remember { mutableStateOf<EpubNavigationRequest?>(null) }
    var isTocSheetOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var isPreferencesPaneOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var footnoteContent by remember(item.id) { mutableStateOf<EpubFootnoteContent?>(null) }
    val overlayState = rememberReaderOverlayState(
        key = "epub-overlay-${item.id}",
        initiallyVisible = true,
    )
    val readingSessionTracker = remember(item.id, journalReadingSessionRecorder, lifecycleOwner) {
        journalReadingSessionRecorder?.let { recorder ->
            ReaderActiveSessionTracker(
                item = item,
                recorder = recorder,
                scope = lifecycleOwner.lifecycleScope,
            )
        }
    }
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
        typographyMode = restoredPreferences.typographyMode
        fontFamily = restoredPreferences.fontFamily
        fontSize = restoredPreferences.fontSize
        lineHeight = restoredPreferences.lineHeight
        pageMargins = restoredPreferences.pageMargins
        paragraphSpacing = restoredPreferences.paragraphSpacing
        paragraphIndent = restoredPreferences.paragraphIndent
        textAlign = restoredPreferences.textAlign
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
    LaunchedEffect(overlayState.isVisible) {
        if (!overlayState.isVisible) {
            isTocSheetOpen = false
            isPreferencesPaneOpen = false
        }
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
        currentLocator = serializedInitialLocator
    }
    val persistReaderState by rememberUpdatedState(
        newValue = {
            if (hydrated &&
                restoredSessionState &&
                shouldFlushEpubReaderProgress(
                    navigatorShowedBook = initialRestoreLocked,
                    restoreProgressSnapshot = restoreProgressSnapshot,
                    currentLocator = currentLocator,
                    currentPage = currentPage,
                    currentProgress = currentProgress,
                )
            ) {
                sessionStore.saveProgress(
                    item = item,
                    locator = buildCurrentReaderLocator(
                        locator = currentLocator.takeUnless { navigationRequest != null },
                        page = currentPage.takeIf { it > 0 },
                        percent = currentProgress,
                    ),
                    isFinished = pageCount > 0 && currentPage >= pageCount,
                    syncState = SyncState.DIRTY,
                    pageCount = pageCount.takeIf { it > 0 },
                )
                sessionStore.updateSession(
                    item = item,
                    pageCount = pageCount.takeIf { it > 0 },
                    preferences = buildEpubPreferencesMap(
                        EpubReaderPreferences(
                            typographyMode = typographyMode,
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            pageMargins = pageMargins,
                            paragraphSpacing = paragraphSpacing,
                            paragraphIndent = paragraphIndent,
                            textAlign = textAlign,
                            theme = theme,
                            themeOverride = !useAppThemeDefault,
                            layoutMode = layoutMode,
                            layoutModeOverride = !useOrientationLayoutDefault,
                        ),
                    ),
                )
            }
        },
    )
    val flushSessionStoreWrites by rememberUpdatedState(
        newValue = {
            sessionStore.requestFlushPendingWrites()
        },
    )
    val closeReader by rememberUpdatedState(
        newValue = {
            readingSessionTracker?.flushAndReset(
                page = currentPage.takeIf { it > 0 },
                percent = currentProgress,
                pageCount = pageCount.takeIf { it > 0 },
            )
            persistReaderState()
            flushSessionStoreWrites()
            onBack()
        },
    )
    val persistFinishedProgress by rememberUpdatedState(
        newValue = {
            val terminalPage = pageCount.takeIf { it > 0 } ?: currentPage.takeIf { it > 0 }
            sessionStore.saveProgress(
                item = item,
                locator = buildCurrentReaderLocator(
                    locator = currentLocator.takeUnless { navigationRequest != null },
                    page = terminalPage,
                    percent = 1.0,
                ),
                isFinished = true,
                syncState = SyncState.DIRTY,
                pageCount = pageCount.takeIf { it > 0 },
            )
        },
    )
    val flushReadingSession by rememberUpdatedState(
        newValue = {
            readingSessionTracker?.flushAndReset(
                page = currentPage.takeIf { it > 0 },
                percent = currentProgress,
                pageCount = pageCount.takeIf { it > 0 },
            )
        },
    )
    DisposableEffect(lifecycleOwner, item.id) {
        readingSessionTracker?.start(
            page = currentPage.takeIf { it > 0 },
            percent = currentProgress,
        )
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    readingSessionTracker?.start(
                        page = currentPage.takeIf { it > 0 },
                        percent = currentProgress,
                    )
                }
                Lifecycle.Event.ON_STOP -> {
                    flushReadingSession()
                    persistReaderState()
                    flushSessionStoreWrites()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushReadingSession()
            persistReaderState()
            flushSessionStoreWrites()
        }
    }
    BackHandler(onBack = closeReader)

    val publicationState by produceState<EpubPublicationState>(
        initialValue = EpubPublicationState.Loading,
        key1 = item.id,
        key2 = documentUriState.cacheKey,
    ) {
        value = when (val uriState = documentUriState) {
            ReaderDocumentUriState.Loading -> EpubPublicationState.Loading
            is ReaderDocumentUriState.Error -> EpubPublicationState.Error(uriState.message)
            // No file (the source is offline or signed out, or the cache was evicted): say so
            // instead of opening a stand-in book whose position would be saved over this one's.
            is ReaderDocumentUriState.Ready -> if (uriState.uri == null) {
                EpubPublicationState.Error(EpubDocumentUnavailableMessage)
            } else withContext(Dispatchers.IO) {
            runCatching {
                val publicationFile = prepareImportedEpubFile(
                    context = context.applicationContext,
                    item = item,
                    documentUri = uriState.uri,
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
            onBack = closeReader,
            isLoading = true,
        )

        is EpubPublicationState.Error -> EpubReaderMessage(
            title = item.title,
            body = state.message,
            onBack = closeReader,
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
                    serializedInitialLocator
                        ?: restoreProgressSnapshot?.toReadingOrderStartLocator(state.publication)
                        ?: withContext(Dispatchers.IO) {
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
                typographyMode,
                fontFamily,
                fontSize,
                lineHeight,
                pageMargins,
                paragraphSpacing,
                paragraphIndent,
                textAlign,
                theme,
                layoutMode,
                pageCount,
                useAppThemeDefault,
                useOrientationLayoutDefault,
            ) {
                if (!hydrated || !restoredSessionState) return@LaunchedEffect
                val preferences = buildEpubPreferencesMap(
                        EpubReaderPreferences(
                            typographyMode = typographyMode,
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            pageMargins = pageMargins,
                            paragraphSpacing = paragraphSpacing,
                            paragraphIndent = paragraphIndent,
                            textAlign = textAlign,
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
                        onBack = closeReader,
                        isLoading = true,
                    )

                    return
                }
                is EpubInitialLocatorState.Ready -> locatorState.locator
            }

            ImmersiveReaderOverlay(
                overlayState = overlayState,
                autoHideDelayMillis = 0L,
                glass = true,
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
                            typographyMode = typographyMode,
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            pageMargins = pageMargins,
                            paragraphSpacing = paragraphSpacing,
                            paragraphIndent = paragraphIndent,
                            textAlign = textAlign,
                            theme = theme,
                            layoutMode = layoutMode,
                        ),
                        currentHref = currentHref,
                        currentProgress = currentProgress,
                        onLocationChanged = { reportedLocator, pageIndex, _ ->
                            val locator = reportedLocator.withReadingOrderIndex(
                                state.publication.readingOrderIndexOf(reportedLocator),
                            )
                            val shouldPersistProgress = initialRestoreLocked
                            if (!initialRestoreLocked) {
                                initialRestoreLocked = true
                            }
                            currentLocator = locator
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
                            if (shouldPersistProgress) {
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
                                val isAtEnd = currentProgress >= 0.999 ||
                                    bookPageCount?.let { reportedPage >= it } == true
                                if (isAtEnd && !hasAutoRequestedCompletion) {
                                    hasAutoRequestedCompletion = true
                                    persistFinishedProgress()
                                    onCompletionRequested()
                                }
                            }
                        },
                        onSurfaceTap = overlayState::toggle,
                        onEndNavigationRequested = {
                            persistFinishedProgress()
                            onCompletionRequested()
                        },
                        onFootnoteActivated = { footnote ->
                            isTocSheetOpen = false
                            isPreferencesPaneOpen = false
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
                        EpubReaderTopDock(
                            title = item.title,
                            progressLabel = buildEpubProgressLabel(
                                progress = currentProgress,
                                position = currentPage,
                                pageCount = pageCount,
                            ),
                            isPreferencesOpen = isPreferencesPaneOpen,
                            onBack = closeReader,
                            onPreferencesClick = {
                                overlayState.show()
                                isTocSheetOpen = false
                                isPreferencesPaneOpen = !isPreferencesPaneOpen
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxSize()
                                .testTag(EpubReaderOverlayTag),
                        )
                        EpubReaderBottomDock(
                            currentProgress = currentProgress,
                            currentPage = currentPage,
                            pageCount = pageCount,
                            chapterMarks = bookProgress.chapterMarks,
                            isTocOpen = isTocSheetOpen,
                            onTocClick = {
                                overlayState.show()
                                isPreferencesPaneOpen = false
                                isTocSheetOpen = !isTocSheetOpen
                            },
                            onProgressSelected = { selectedProgress ->
                                currentProgress = selectedProgress.toDouble()
                                navigationRequest = EpubProgressSeekRequest(
                                    id = seekRequestId++,
                                    progress = selectedProgress,
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxSize(),
                        )
                        EpubTableOfContentsSheet(
                            entries = bookProgress.tocEntries,
                            currentHref = currentHref,
                            currentProgress = currentProgress,
                            currentPage = currentPage,
                            pageCount = pageCount,
                            isOpen = isTocSheetOpen,
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
                            modifier = Modifier.fillMaxSize(),
                        )
                        EpubPreferencesSheet(
                            isOpen = isPreferencesPaneOpen,
                            typographyMode = typographyMode,
                            fontFamily = fontFamily,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            pageMargins = pageMargins,
                            paragraphSpacing = paragraphSpacing,
                            paragraphIndent = paragraphIndent,
                            textAlign = textAlign,
                            theme = theme,
                            useAppThemeDefault = useAppThemeDefault,
                            layoutMode = layoutMode,
                            useOrientationLayoutDefault = useOrientationLayoutDefault,
                            onDismissRequest = {
                                isPreferencesPaneOpen = false
                            },
                            onTypographyModeChanged = {
                                typographyMode = it
                            },
                            onFontFamilyChanged = {
                                fontFamily = it
                            },
                            onFontSizeChanged = {
                                fontSize = it
                            },
                            onLineHeightChanged = {
                                lineHeight = it
                            },
                            onPageMarginsChanged = {
                                pageMargins = it
                            },
                            onParagraphSpacingChanged = {
                                paragraphSpacing = it
                            },
                            onParagraphIndentChanged = {
                                paragraphIndent = it
                            },
                            onTextAlignChanged = {
                                textAlign = it
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
                            modifier = Modifier.fillMaxSize(),
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
private fun EpubReaderMessage(
    title: String,
    body: String,
    onBack: () -> Unit,
    isLoading: Boolean = false,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLoading) {
                AppCircularProgress()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            AppTextButton(onClick = onBack) {
                Text(text = "Back")
            }
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
    onEndNavigationRequested: () -> Unit,
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
    val latestOnEndNavigationRequested = rememberUpdatedState(onEndNavigationRequested)
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
                        // Readium's WebView would otherwise render through Chromium's
                        // SurfaceControl overlay on Android 15+, outside our draw: the glass
                        // chrome had nothing to blur and flickered against the overlay. A
                        // hardware layer keeps the page inside our layer, where the glass can
                        // sample it. (Tablet, Android 16: page turn p50 8 ms, p90 10 ms.)
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
            configuration = epubNavigatorFragmentConfiguration(),
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
                        if (latestCurrentProgress.value >= 0.999) {
                            latestOnEndNavigationRequested.value()
                        } else {
                            fragment.goForward(animated = true)
                        }
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

internal fun buildEpubPercentLabel(
    progress: Double,
): String = "${((progress.coerceIn(0.0, 1.0) * 100.0).roundToInt()).coerceIn(0, 100)}%"

internal fun buildEpubPageProgressLabel(
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
): Int? = readingOrderIndexOf(locator)?.plus(1)

private fun Publication.readingOrderIndexOf(
    locator: Locator,
): Int? {
    val href = locator.href.removeFragment().toString()
    return readingOrder
        .indexOfFirst { link -> link.url().removeFragment().toString() == href }
        .takeUnless { it < 0 }
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

private fun buildCurrentReaderLocator(
    locator: Locator?,
    page: Int?,
    percent: Double,
): ReaderLocator = locator?.let { currentLocator ->
    buildReaderLocator(
        locator = currentLocator,
        page = page,
    )
} ?: ReaderLocator(
    locatorType = LocatorType.CFI,
    page = page,
    percent = percent.coerceIn(0.0, 1.0),
)

// A PAGE position on an EPUB comes from Kavita, which stores the reading-order index of the
// chapter; open that chapter at its start.
private fun ReaderProgressSnapshot.toReadingOrderStartLocator(
    publication: Publication,
): Locator? {
    if (locatorType != LocatorType.PAGE || isFinished) return null
    val link = publication.readingOrder.getOrNull(page ?: return null) ?: return null
    return publication.locatorFromLink(link)
}

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

private const val EpubNavigationZoneFraction = 0.25f
internal const val EpubDocumentUnavailableMessage =
    "This book's file isn't available. Check the connection to its server, then open it again."
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
