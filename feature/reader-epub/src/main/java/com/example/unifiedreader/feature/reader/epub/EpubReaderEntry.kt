package com.example.unifiedreader.feature.reader.epub

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SyncState
import com.example.unifiedreader.core.model.primaryAuthor
import com.example.unifiedreader.feature.reader.core.ImmersiveReaderOverlay
import com.example.unifiedreader.feature.reader.core.ReaderActiveSessionTracker
import com.example.unifiedreader.feature.reader.core.ReaderChapterPosition
import com.example.unifiedreader.feature.reader.core.ReaderDocumentState
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderOpenFailure
import com.example.unifiedreader.feature.reader.core.ReaderOpenStatus
import com.example.unifiedreader.feature.reader.core.ReaderOpeningStep
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.reader.core.ReportReaderOpening
import com.example.unifiedreader.feature.reader.core.findContextAncestor
import com.example.unifiedreader.feature.reader.core.openingStep
import com.example.unifiedreader.feature.reader.core.readerSessionChapterPreferences
import com.example.unifiedreader.feature.reader.core.rememberReaderDocument
import com.example.unifiedreader.feature.reader.core.rememberReaderOverlayState
import com.example.unifiedreader.feature.reader.preferences.ReaderDisplayPreferences
import com.example.unifiedreader.journal.JournalReadingSessionRecorder
import com.example.unifiedreader.ui.components.AppScreenBackHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.unifiedreader.feature.translation.ReaderTranslationOverlay
import com.example.unifiedreader.feature.translation.TranslationService
import com.example.unifiedreader.feature.translation.rememberTranslationSession
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

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
    translation: TranslationService? = null,
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
        ReportReaderOpening(ReaderOpenStatus.Failed(ReaderOpenFailure.Unknown))
        return
    }

    val hydrated by sessionStore.isHydratedFlow.collectAsStateWithLifecycle()
    val progressByItemId by sessionStore.progressByItemIdFlow.collectAsStateWithLifecycle()
    val sessionByItemId by sessionStore.sessionByItemIdFlow.collectAsStateWithLifecycle()
    val progressSnapshot = progressByItemId[item.id]
    val sessionSnapshot = sessionByItemId[item.id]
    val pageColorPick by displayPreferences.pageColor.pickFlow.collectAsStateWithLifecycle()
    if (!hydrated || !pageColorPick.isLoaded) {
        ReportReaderOpening(ReaderOpenStatus.Opening(ReaderOpeningStep.Opening))
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
    val pageNumbersVisible by displayPreferences.pageNumbers.isVisibleFlow.collectAsStateWithLifecycle()
    var hasAutoRequestedCompletion by rememberSaveable(item.id) {
        mutableStateOf(false)
    }
    var seekRequestId by remember { mutableIntStateOf(0) }
    var navigationRequest by remember { mutableStateOf<EpubNavigationRequest?>(null) }
    var isTocSheetOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var isPreferencesPaneOpen by rememberSaveable(item.id) { mutableStateOf(false) }
    var footnoteContent by remember(item.id) { mutableStateOf<EpubFootnoteContent?>(null) }
    val translationSession = translation?.let { service -> rememberTranslationSession(service) }
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
    // Try again asks for the file again.
    var openAttempt by remember(item.id) { mutableIntStateOf(0) }
    val documentState by rememberReaderDocument(item, documentUri, documentUriProvider, openAttempt)

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
                    preferences = buildEpubPreferencesMap(readerPreferences) + readerSessionChapterPreferences(currentChapter),
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
        key2 = documentState.cacheKey,
    ) {
        value = when (val document = documentState) {
            ReaderDocumentState.Loading -> EpubPublicationState.Loading
            is ReaderDocumentState.Failed -> EpubPublicationState.Error(document.failure)
            is ReaderDocumentState.Ready -> withContext(Dispatchers.IO) {
                openEpubPublication(context.applicationContext, item, document.uri)
            }
        }
    }

    when (val state = publicationState) {
        EpubPublicationState.Loading -> ReportReaderOpening(
            status = ReaderOpenStatus.Opening(documentState.openingStep(item)),
            colors = theme.colors,
        )

        is EpubPublicationState.Error -> ReportReaderOpening(
            status = ReaderOpenStatus.Failed(state.failure),
            colors = theme.colors,
            retry = { openAttempt++ },
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
                currentChapter = entries.epubReaderChapterPosition(currentProgress, currentHref)
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
                    ReportReaderOpening(ReaderOpenStatus.Opening(ReaderOpeningStep.FindingPage), colors = theme.colors)
                    return
                }
                is EpubInitialLocatorState.Ready -> locatorState.locator
            }
            // The opening state stays until the navigator has laid out its first page.
            ReportReaderOpening(
                status = if (initialRestoreLocked) ReaderOpenStatus.Open else ReaderOpenStatus.Opening(ReaderOpeningStep.FindingPage),
                colors = theme.colors,
            )

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
                    if (translation != null && translationSession != null) {
                        ReaderTranslationOverlay(session = translationSession, service = translation)
                    }
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
                        onTranslateSelection = translationSession?.let { session ->
                            { selection ->
                                overlayState.hide()
                                session.open(selection)
                            }
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

private fun defaultEpubLayoutMode(
    isLandscape: Boolean,
): EpubReaderLayoutMode = if (isLandscape) {
    EpubReaderLayoutMode.DoublePage
} else {
    EpubReaderLayoutMode.SinglePage
}
