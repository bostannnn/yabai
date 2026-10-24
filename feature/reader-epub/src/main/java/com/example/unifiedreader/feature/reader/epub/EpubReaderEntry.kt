package com.example.unifiedreader.feature.reader.epub

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.primaryAuthor
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderActiveSessionTracker
import com.example.unifiedreader.feature.reader.core.ReaderChapterPosition
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.findContextAncestor
import com.example.unifiedreader.feature.reader.core.readerSessionChapterPreference
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import com.example.unifiedreader.feature.reader.preferences.ReaderDisplayPreferences
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import com.example.unifiedreader.ui.components.AppCircularProgress
import com.example.unifiedreader.ui.components.AppScreenBackHandler
import com.example.unifiedreader.ui.components.AppTextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
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
    journalReadingSessionRecorder: JournalReadingSessionRecorder? = null,
    documentUri: Uri? = null,
    documentUriProvider: (suspend (ReadableItem) -> Uri?)? = null,
    displayPreferences: ReaderDisplayPreferences = remember { ReaderDisplayPreferences() },
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
    val appBackgroundIsLight = MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val viewportSpacing = currentEpubViewportSpacing(isLandscape)
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
    val pageColorPick by displayPreferences.pageColor.pickFlow.collectAsState()
    if (!hydrated || !pageColorPick.isLoaded) {
        EpubReaderMessage(
            title = item.title,
            body = "Restoring EPUB state…",
            onBack = onBack,
            isLoading = true,
        )
        return
    }
    var initialRestoreLocked by rememberSaveable(item.id) { mutableStateOf(false) }
    // Locations reported before the page is touched or navigated are the book opening and settling
    // where it was. Saving them would stamp it as read now, over newer progress from another device.
    var readerTouched by remember(item.id) { mutableStateOf(false) }
    var positionMovedSinceOpen by rememberSaveable(item.id) { mutableStateOf(false) }
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
    val defaultPageColor = defaultEpubPageColor(pageColorPick.name, appBackgroundIsLight)
    val restoredThemeSelection = remember(restoreSessionSnapshot, restoredPreferences, defaultPageColor) {
        resolveRestoredPageColor(
            restoredPreferences = restoredPreferences,
            rawPreferences = restoreSessionSnapshot?.preferences.orEmpty(),
            defaultColor = defaultPageColor,
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
    var pageTurnStyle by rememberSaveable(item.id) { mutableStateOf(restoredPreferences.pageTurnStyle) }
    var theme by rememberSaveable(item.id) { mutableStateOf(restoredThemeSelection.color) }
    var layoutMode by rememberSaveable(item.id) {
        mutableStateOf(
            if (restoredPreferences.layoutModeOverride) {
                restoredPreferences.layoutMode
            } else {
                defaultEpubLayoutMode(isLandscape)
            },
        )
    }
    var pageColorFollowsDefault by rememberSaveable(item.id) { mutableStateOf(!restoredThemeSelection.isBookOwn) }
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
    // The chapter the reader is in, saved with the session so the library can name it.
    var currentChapter by remember(item.id) { mutableStateOf<ReaderChapterPosition?>(null) }
    var secondSpreadPage by remember(item.id) { mutableStateOf<Int?>(null) }
    // The navigator's page inside its file; 0 is a chapter's first page.
    var resourcePageIndex by remember(item.id) { mutableIntStateOf(-1) }
    val pageNumbersVisible by displayPreferences.pageNumbers.isVisibleFlow.collectAsState()
    var hasAutoRequestedCompletion by rememberSaveable(item.id) {
        mutableStateOf(false)
    }
    var seekRequestId by remember { mutableIntStateOf(0) }
    var navigationRequest by remember { mutableStateOf<EpubNavigationRequest?>(null) }
    var isTocSheetOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var isPreferencesPaneOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var footnoteContent by remember(item.id) { mutableStateOf<EpubFootnoteContent?>(null) }
    var noteReturn by remember(item.id) { mutableStateOf<EpubNoteReturn?>(null) }
    val readerPreferences = EpubReaderPreferences(
        typographyMode = typographyMode,
        fontFamily = fontFamily,
        fontSize = fontSize,
        lineHeight = lineHeight,
        pageMargins = pageMargins,
        paragraphSpacing = paragraphSpacing,
        paragraphIndent = paragraphIndent,
        textAlign = textAlign,
        theme = theme,
        themeOverride = !pageColorFollowsDefault,
        layoutMode = layoutMode,
        layoutModeOverride = !useOrientationLayoutDefault,
        pageTurnStyle = pageTurnStyle,
    )
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

    LaunchedEffect(hydrated, restoredPreferences, defaultPageColor, isLandscape, useOrientationLayoutDefault) {
        if (!hydrated || restoredSessionState) return@LaunchedEffect
        val restoredThemeUsesDefault = !restoredThemeSelection.isBookOwn
        val restoredLayoutUsesDefault = !restoredPreferences.layoutModeOverride
        typographyMode = restoredPreferences.typographyMode
        fontFamily = restoredPreferences.fontFamily
        fontSize = restoredPreferences.fontSize
        lineHeight = restoredPreferences.lineHeight
        pageMargins = restoredPreferences.pageMargins
        paragraphSpacing = restoredPreferences.paragraphSpacing
        paragraphIndent = restoredPreferences.paragraphIndent
        textAlign = restoredPreferences.textAlign
        pageTurnStyle = restoredPreferences.pageTurnStyle
        pageColorFollowsDefault = restoredThemeUsesDefault
        useOrientationLayoutDefault = restoredLayoutUsesDefault
        theme = restoredThemeSelection.color
        layoutMode = if (restoredLayoutUsesDefault) {
            defaultEpubLayoutMode(isLandscape)
        } else {
            restoredPreferences.layoutMode
        }
        restoredSessionState = true
    }
    LaunchedEffect(hydrated, restoredSessionState, defaultPageColor, pageColorFollowsDefault) {
        if (!hydrated || !restoredSessionState || !pageColorFollowsDefault) return@LaunchedEffect
        theme = defaultPageColor
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
            // At a note opened with Go to note, the place to keep is the page it was opened from.
            val savedLocator = noteReturn?.locator ?: currentLocator
            val savedPage = noteReturn?.page ?: currentPage
            val savedProgress = noteReturn?.progress ?: currentProgress
            if (hydrated &&
                restoredSessionState &&
                shouldFlushEpubReaderProgress(
                    navigatorShowedBook = initialRestoreLocked,
                    restoreProgressSnapshot = restoreProgressSnapshot,
                    currentLocator = savedLocator,
                    currentPage = savedPage,
                    currentProgress = savedProgress,
                )
            ) {
                if (positionMovedSinceOpen) sessionStore.saveProgress(
                    item = item,
                    locator = buildCurrentReaderLocator(
                        locator = savedLocator.takeUnless { navigationRequest != null },
                        page = savedPage.takeIf { it > 0 },
                        percent = savedProgress,
                    ),
                    isFinished = pageCount > 0 && savedPage >= pageCount,
                    syncState = SyncState.DIRTY,
                    pageCount = pageCount.takeIf { it > 0 },
                )
                sessionStore.updateSession(
                    item = item,
                    pageCount = pageCount.takeIf { it > 0 },
                    preferences = buildEpubPreferencesMap(readerPreferences) + readerSessionChapterPreference(currentChapter),
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
    AppScreenBackHandler(onBack = closeReader)

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
            val restoredLocator = remember(state.publication, serializedInitialLocator) {
                serializedInitialLocator?.let(state.publication::resolveEpubRestoredLocator)
            }
            val initialLocatorState by produceState<EpubInitialLocatorState>(
                initialValue = if (restoreProgressSnapshot.needsEpubProgressionLocator(restoredLocator)) {
                    EpubInitialLocatorState.Loading
                } else {
                    EpubInitialLocatorState.Ready(restoredLocator)
                },
                key1 = state.publication,
                key2 = restoreProgressSnapshot,
                key3 = restoredLocator,
            ) {
                value = EpubInitialLocatorState.Ready(
                    restoredLocator
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
            // The same chapter the contents panel marks; until the contents load it stays unknown.
            LaunchedEffect(bookProgress.tocEntries, currentHref, currentProgress) {
                val entries = bookProgress.tocEntries
                if (entries.isEmpty()) return@LaunchedEffect
                currentChapter = entries
                    .epubTocChapterPosition(entries.activeEpubTocEntry(currentProgress, currentHref))
                    ?.let { ReaderChapterPosition(index = it.index, count = it.count) }
            }

            LaunchedEffect(item.id, hydrated, restoredSessionState, readerPreferences, pageCount) {
                if (!hydrated || !restoredSessionState) return@LaunchedEffect
                sessionStore.updateSession(
                    item = item,
                    pageCount = pageCount.takeIf { it > 0 },
                    preferences = buildEpubPreferencesMap(readerPreferences),
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
                pageIndicator = if (pageNumbersVisible) {
                    {
                        EpubPageFrameLabels(
                            publication = state.publication,
                            bookTitle = item.title,
                            tocEntries = bookProgress.tocEntries,
                            location = EpubFrameLocation(currentHref, currentProgress, currentPage, pageCount, secondSpreadPage, resourcePageIndex),
                            layoutMode = layoutMode,
                            pageColor = theme,
                            stripHeight = viewportSpacing.containerInset,
                        )
                    }
                } else {
                    null
                },
                sheet = {
                    EpubFootnoteCard(
                        footnote = footnoteContent,
                        pageColor = theme,
                        type = rememberEpubFootnoteType(typographyMode, fontFamily, fontSize, lineHeight),
                        onDismissRequest = { footnoteContent = null },
                        onGoToNote = { link ->
                            footnoteContent = null
                            noteReturn = currentLocator?.let { EpubNoteReturn(it, currentPage, currentProgress, link.url().removeFragment().toString()) }
                            readerTouched = true
                            navigationRequest = EpubNoteNavigationRequest(id = seekRequestId++, link = link)
                        },
                    )
                },
                content = {
                    ReadiumEpubNavigatorHost(
                        activity = activity,
                        item = item,
                        publication = state.publication,
                        initialLocator = resolvedInitialLocator,
                        preferences = readerPreferences,
                        onLocationChanged = { reportedLocator, resourcePage, isAtBookEnd ->
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
                                ?: resourcePage.pageIndex + 1
                            if (reportedPage > 0) {
                                currentPage = reportedPage
                            }
                            resourcePageIndex = resourcePage.pageIndex
                            if (readerTouched) positionMovedSinceOpen = true
                            if (resourcePage.totalPages != null) {
                                secondSpreadPage = state.publication
                                    .epubSecondSpreadPage(locator, resourcePage, bookProgress.resourcePositions)
                            }
                            currentProgress = (
                                locator.locations.totalProgression
                                    ?: locator.locations.progression
                                    ?: bookPageCount?.let { pages ->
                                        reportedPage.toDouble() / pages.toDouble()
                                    }
                                    ?: currentProgress
                                ).coerceIn(0.0, 1.0)
                            // A note opened with Go to note is a visit: not progress, and not the book's end.
                            if (shouldPersistProgress && noteReturn == null) {
                                if (positionMovedSinceOpen) sessionStore.saveProgress(
                                    item = item,
                                    locator = buildReaderLocator(
                                        locator = locator,
                                        page = reportedPage.takeIf { it > 0 },
                                    ),
                                    isFinished = isAtBookEnd,
                                    syncState = SyncState.DIRTY,
                                    pageCount = bookPageCount ?: pageCount.takeIf { it > 0 },
                                )
                                if (isAtBookEnd && !hasAutoRequestedCompletion) {
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
                            overlayState.hide()
                            footnoteContent = footnote
                        },
                        navigationRequest = navigationRequest,
                        onNavigationRequestConsumed = {
                            navigationRequest = null
                        },
                        modifier = Modifier.fillMaxSize().onEveryTouchDown { readerTouched = true },
                    )
                },
                overlay = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EpubReaderChrome(
                            title = item.title,
                            author = item.bookMetadata?.primaryAuthor(),
                            tocEntries = bookProgress.tocEntries,
                            currentHref = currentHref,
                            currentProgress = currentProgress,
                            currentPage = currentPage,
                            pageCount = pageCount,
                            chapterMarks = bookProgress.chapterMarks,
                            isTocOpen = isTocSheetOpen,
                            isPreferencesOpen = isPreferencesPaneOpen,
                            onBack = closeReader,
                            onTocClick = {
                                overlayState.show()
                                isPreferencesPaneOpen = false
                                isTocSheetOpen = !isTocSheetOpen
                            },
                            onPreferencesClick = {
                                overlayState.show()
                                isTocSheetOpen = false
                                isPreferencesPaneOpen = !isPreferencesPaneOpen
                            },
                            onDismissPanels = {
                                isTocSheetOpen = false
                                isPreferencesPaneOpen = false
                            },
                            onProgressSelected = { selectedProgress ->
                                currentProgress = selectedProgress.toDouble()
                                readerTouched = true
                                navigationRequest = EpubProgressSeekRequest(
                                    id = seekRequestId++,
                                    progress = selectedProgress,
                                )
                            },
                            modifier = Modifier.testTag(EpubReaderOverlayTag),
                        ) { slot ->
                            EpubTableOfContentsSheet(
                                entries = bookProgress.tocEntries,
                                bookTitle = item.title,
                                currentHref = currentHref,
                                currentProgress = currentProgress,
                                isOpen = isTocSheetOpen,
                                slot = slot,
                                onDismissRequest = {
                                    isTocSheetOpen = false
                                },
                                onEntrySelected = { entry ->
                                    isTocSheetOpen = false
                                    readerTouched = true
                                    navigationRequest = EpubTocNavigationRequest(
                                        id = seekRequestId++,
                                        link = entry.link,
                                    )
                                },
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
                                layoutMode = layoutMode,
                                useOrientationLayoutDefault = useOrientationLayoutDefault,
                                pageTurnStyle = pageTurnStyle,
                                pageNumbersVisible = pageNumbersVisible,
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
                                    pageColorFollowsDefault = false
                                    theme = it
                                    displayPreferences.pageColor.setLastPicked(it.name)
                                },
                                onLayoutModeChanged = {
                                    useOrientationLayoutDefault = false
                                    layoutMode = it
                                },
                                onUseOrientationLayoutDefault = {
                                    useOrientationLayoutDefault = true
                                    layoutMode = defaultEpubLayoutMode(isLandscape)
                                },
                                onPageTurnStyleChanged = {
                                    pageTurnStyle = it
                                },
                                onPageNumbersVisibleChanged = displayPreferences.pageNumbers::setVisible,
                                slot = slot,
                            )
                        }
                    }
                },
            )
            EpubNoteReturnEffects(
                noteReturn = noteReturn,
                currentHref = currentHref,
                onReturn = {
                    noteReturn?.let { jump -> navigationRequest = EpubLocatorNavigationRequest(seekRequestId++, jump.locator) }
                    noteReturn = null
                },
                onLeftNote = { noteReturn = null },
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

internal fun Publication.readingOrderIndexOf(
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

internal sealed interface EpubNavigationRequest {
    val id: Int
}

internal data class EpubProgressSeekRequest(
    override val id: Int,
    val progress: Float,
) : EpubNavigationRequest

internal data class EpubTocNavigationRequest(
    override val id: Int,
    val link: Link,
) : EpubNavigationRequest

private const val EpubNavigationZoneFraction = 0.25f
internal const val EpubDocumentUnavailableMessage =
    "This book's file isn't available. Check the connection to its server, then open it again."

private fun defaultEpubLayoutMode(
    isLandscape: Boolean,
): EpubReaderLayoutMode = if (isLandscape) {
    EpubReaderLayoutMode.DoublePage
} else {
    EpubReaderLayoutMode.SinglePage
}
