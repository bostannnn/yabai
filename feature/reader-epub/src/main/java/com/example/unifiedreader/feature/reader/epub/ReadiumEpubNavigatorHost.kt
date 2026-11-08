package com.example.unifiedreader.feature.reader.epub

import android.content.res.Configuration
import com.example.unifiedreader.feature.translation.ReaderTextSelection
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.feature.reader.core.ReaderTapSide
import com.example.unifiedreader.feature.reader.core.ReaderTapZoneMaxWidth
import com.example.unifiedreader.feature.reader.core.readerTapSide
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTapTurner
import com.example.unifiedreader.feature.reader.core.curl.PageCurlTarget
import com.example.unifiedreader.feature.reader.core.curl.PageTurnStyle
import com.example.unifiedreader.feature.reader.core.curl.isPageCurlShaderSupported
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
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.ReadingProgression
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.AbsoluteUrl

@Composable
@OptIn(ExperimentalReadiumApi::class)
internal fun ReadiumEpubNavigatorHost(
    activity: FragmentActivity,
    item: ReadableItem,
    publication: Publication,
    initialLocator: Locator?,
    preferences: EpubReaderPreferences,
    // The last argument: whether the reader sees the end of the book (isEpubBookEnd).
    onLocationChanged: (Locator, EpubResourcePage, Boolean) -> Unit,
    onSurfaceTap: () -> Unit,
    onEndNavigationRequested: () -> Unit,
    onFootnoteActivated: (EpubFootnoteContent) -> Unit,
    translation: EpubSelectionTranslation?,
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
    val footnoteNavigationScope = rememberCoroutineScope()
    val latestTranslation = rememberUpdatedState(translation)
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
    val viewportSpacing = currentEpubViewportSpacing(isLandscape)
    val viewportVerticalInsetPx = with(LocalDensity.current) {
        viewportSpacing.containerInset.roundToPx()
    }
    val spreadSideStrip = publication.epubSpreadSideStrip(preferences)
    val sideStripPx = with(LocalDensity.current) { spreadSideStrip.roundToPx() }
    val latestSideStripPx = rememberUpdatedState(sideStripPx)
    // Readium's own gutter is fixed when the navigator is made, so a spread gains or loses its margins with a new one.
    val usesSpreadMargins = spreadSideStrip > 0.dp
    val pageTaps = remember(publication) { EpubPageTaps() }
    val textScale = preferences.fontSize.takeIf { preferences.typographyMode == EpubTypographyMode.Custom } ?: 1.0
    val latestTextScale = rememberUpdatedState(textScale)
    val fragmentManager = activity.supportFragmentManager
    // The strips around the text and the view behind Readium's page: one colour with the page.
    val surfaceBackground = preferences.theme.colors.page
    val resumePoint = remember(publication) { EpubNavigatorResumePoint() }
    val curlHost = remember(publication) { EpubPageCurlHost() }
    val selectionFollower = remember(publication) {
        EpubSelectionFollower(
            scope = footnoteNavigationScope,
            navigator = { curlHost.navigator },
            bookLanguage = publication.metadata.languages.firstOrNull(),
            translation = { latestTranslation.value },
        )
    }
    val curlTapTurner = remember(publication) { PageCurlTapTurner() }
    val curlEnabled = preferences.pageTurnStyle == PageTurnStyle.Curl &&
        preferences.layoutMode != EpubReaderLayoutMode.Scroll &&
        publication.metadata.presentation.layout != EpubLayout.FIXED &&
        isPageCurlShaderSupported()
    val latestCurlEnabled = rememberUpdatedState(curlEnabled)
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

    EpubPageCurlLayer(
        host = curlHost,
        enabled = curlEnabled,
        twoPageSpread = preferences.layoutMode == EpubReaderLayoutMode.DoublePage,
        rightToLeft = publication.metadata.readingProgression == ReadingProgression.RTL,
        backTint = preferences.theme.curlBack(),
        tapTurner = curlTapTurner,
        modifier = modifier.fillMaxSize(),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag(EpubReaderSurfaceTag),
            factory = { context ->
                FrameLayout(context).apply {
                    curlHost.pageView = this
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
                        epubNavigatorContainerLayoutParams(viewportVerticalInsetPx, sideStripPx),
                    )
                }
            },
            update = { container ->
                container.setBackgroundColor(surfaceBackground.toArgb())
                container.updateEpubNavigatorContainer(
                    verticalInsetPx = viewportVerticalInsetPx,
                    sideInsetPx = sideStripPx,
                    backgroundColor = surfaceBackground.toArgb(),
                )
                container.updateEpubStripTapListener(onTap = pageTaps::onTap)
            },
        )
    }

    DisposableEffect(
        fragmentManager,
        publication,
        navigatorFactory,
        containerId,
        fragmentTag,
        usesSpreadMargins,
    ) {
        var navigatorFragment: EpubNavigatorFragment? = null
        val footnoteNavigationJobs = mutableSetOf<Job>()
        val isScrollMode = preferences.layoutMode == EpubReaderLayoutMode.Scroll
        val isReflowableSpread = preferences.layoutMode == EpubReaderLayoutMode.DoublePage &&
            publication.metadata.presentation.layout != EpubLayout.FIXED
        var isAtBookEnd = false
        var bookEndCheckJob: Job? = null
        fun reportLocation(locator: Locator, page: EpubResourcePage, atBookEnd: Boolean) {
            isAtBookEnd = atBookEnd
            curlHost.onPageChanged(
                isAtBookStart = page.pageIndex == 0 && publication.readingOrderIndexOf(locator) == 0,
                isAtBookEnd = atBookEnd,
            )
            latestOnLocationChanged.value(locator, page, atBookEnd)
        }
        val fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = resumePoint.startLocator(initialLocator),
            initialPreferences = preferences.toNavigatorPreferences(),
            listener = object : EpubNavigatorFragment.Listener {
                override fun shouldFollowInternalLink(
                    link: Link,
                    context: HyperlinkNavigator.LinkContext?,
                ): Boolean {
                    val footnoteContext = context as? HyperlinkNavigator.FootnoteContext
                    // A note in another file of the book (a Notes chapter) can also be opened there.
                    val endnoteLink = link.takeIf { target ->
                        val here = navigatorFragment?.currentLocator?.value?.href ?: return@takeIf false
                        !isSameEpubResource(target.url(), here)
                    }
                    if (footnoteContext != null) {
                        latestOnFootnoteActivated.value(
                            EpubFootnoteContent(
                                body = htmlFootnoteToPlainText(footnoteContext.noteContent),
                                endnoteLink = endnoteLink,
                            ),
                        )
                        return false
                    }
                    if (link.url().fragment == null) return true
                    footnoteNavigationJobs.toList().forEach { job -> job.cancel() }
                    footnoteNavigationJobs.clear()
                    val job = footnoteNavigationScope.launch {
                        val footnote = withContext(Dispatchers.IO) {
                            publication.extractEpubFootnoteOrNull(link = link)
                        }
                        val activeNavigator =
                            fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
                        if (activeNavigator !== navigatorFragment || activeNavigator?.isAdded != true) {
                            return@launch
                        }
                        if (footnote != null) {
                            latestOnFootnoteActivated.value(footnote.copy(endnoteLink = endnoteLink))
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

                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    activity.openEpubExternalLink(url.toString())
                }
            },
            paginationListener = object : EpubNavigatorFragment.PaginationListener {
                override fun onPageChanged(
                    pageIndex: Int,
                    totalPages: Int,
                    locator: Locator,
                ) {
                    bookEndCheckJob?.cancel()
                    val isLastResource = publication.readingOrderIndexOf(locator) == publication.readingOrder.lastIndex
                    val checksScrollEnd = isScrollMode && isLastResource
                    // Only a resource's last spread can end on Readium's blank column.
                    val checksBlankColumn = isReflowableSpread && pageIndex >= totalPages - 1
                    if (!checksScrollEnd && !checksBlankColumn) {
                        reportLocation(
                            locator = locator,
                            page = EpubResourcePage(pageIndex = pageIndex, totalPages = totalPages),
                            atBookEnd = isEpubBookEnd(isLastResource, isScrollMode, pageIndex, totalPages, null),
                        )
                        return
                    }
                    bookEndCheckJob = footnoteNavigationScope.launch {
                        val scrolledToEnd = if (checksScrollEnd) {
                            navigatorFragment
                                ?.evaluateJavascript(EpubScrolledToResourceEndScript)
                                ?.trim() == "true"
                        } else {
                            null
                        }
                        val isSecondColumnBlank = checksBlankColumn && navigatorFragment
                            ?.evaluateJavascript(EpubSecondColumnBlankScript)
                            ?.trim() == "true"
                        reportLocation(
                            locator = locator,
                            page = EpubResourcePage(
                                pageIndex = pageIndex,
                                totalPages = totalPages,
                                isSecondColumnBlank = isSecondColumnBlank,
                            ),
                            atBookEnd = isEpubBookEnd(isLastResource, isScrollMode, pageIndex, totalPages, scrolledToEnd),
                        )
                    }
                }

                override fun onPageLoaded() {
                    activity.lifecycleScope.launch {
                        navigatorFragment?.let { fragment ->
                            applyEpubViewportPadding(
                                navigator = fragment,
                                publication = publication,
                                paddingPx = viewportSpacing.contentPaddingPx,
                                textScale = latestTextScale.value,
                            )
                            curlHost.refreshColumns()
                        }
                    }
                }
            },
            configuration = epubNavigatorFragmentConfiguration(
                pageGutterPx = EpubSpreadPageGutterPx.takeIf { usesSpreadMargins },
                selectionActionModeCallback = EpubSelectionActionModeCallback(
                    delegate = curlHost.selectionActionModeCallback,
                    canTranslate = { latestTranslation.value?.openOnSelect == false },
                    onTranslate = selectionFollower::translate,
                    onSelectionChanged = selectionFollower::selectionChanged,
                    onSelectionCleared = selectionFollower::selectionCleared,
                    onCopy = {
                        footnoteNavigationScope.withCurrentSelection(curlHost.navigator, null) { selection ->
                            activity.copyToClipboard(selection.text)
                        }
                    },
                ),
            ),
        )
        val fragment = fragmentFactory.instantiate(
            activity.classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment
        navigatorFragment = fragment
        curlHost.navigator = fragment
        fun turnByTap(target: PageCurlTarget, slide: () -> Unit) {
            if (!latestCurlEnabled.value) {
                slide()
                return
            }
            footnoteNavigationScope.launch {
                if (!curlTapTurner.turn(target)) slide()
            }
        }
        fun handlePageTap(side: ReaderTapSide): Boolean {
            when (resolveEpubTapAction(side, fragment.settings.value.readingProgression)) {
                EpubTapAction.Previous -> turnByTap(PageCurlTarget.Previous) { fragment.goBackward(animated = true) }
                EpubTapAction.Next -> if (isAtBookEnd) {
                    latestOnEndNavigationRequested.value()
                } else {
                    turnByTap(PageCurlTarget.Next) { fragment.goForward(animated = true) }
                }
                EpubTapAction.ToggleOverlay -> latestOnSurfaceTap.value()
            }
            return true
        }
        pageTaps.handler = ::handlePageTap
        val tapZoneListener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val viewWidth = fragment.publicationView.width.toFloat()
                if (viewWidth <= 0f) return false
                // Readium's point is in its own view; the zones span the strips beside it too.
                val strip = latestSideStripPx.value
                val maxZoneWidth = ReaderTapZoneMaxWidth.value * fragment.resources.displayMetrics.density
                return handlePageTap(readerTapSide(event.point.x + strip, viewWidth + 2 * strip, maxZoneWidth))
            }
        }
        fragment.addInputListener(tapZoneListener)
        fragmentManager.beginTransaction()
            .replace(containerId, fragment, fragmentTag)
            .commitNowAllowingStateLoss()

        onDispose {
            pageTaps.handler = null
            curlHost.navigator = null
            bookEndCheckJob?.cancel()
            footnoteNavigationJobs.toList().forEach { job -> job.cancel() }
            footnoteNavigationJobs.clear()
            val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
            navigator?.let {
                it.removeInputListener(tapZoneListener)
                resumePoint.onNavigatorRemoved(it.currentLocator.value)
                latestOnLocationChanged.value(
                    it.currentLocator.value,
                    EpubResourcePage(pageIndex = 0, totalPages = null),
                    isAtBookEnd,
                )
            }
            fragmentManager.findFragmentByTag(fragmentTag)?.let { existing ->
                fragmentManager.beginTransaction()
                    .remove(existing)
                    .commitNowAllowingStateLoss()
            }
        }
    }

    // When the card or sheet closes, the words stop being selected too.
    val translationOpen = translation?.isOpen == true
    LaunchedEffect(translationOpen) { selectionFollower.translationShown(translationOpen) }

    LaunchedEffect(fragmentTag, preferences, viewportSpacing.contentPaddingPx) {
        val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
        navigator?.submitPreferences(preferences.toNavigatorPreferences())
        navigator?.let {
            applyEpubViewportPadding(
                navigator = it,
                publication = publication,
                paddingPx = viewportSpacing.contentPaddingPx,
                textScale = textScale,
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

            is EpubLocatorNavigationRequest -> {
                navigator.go(request.locator, animated = false)
            }

            is EpubNoteNavigationRequest -> {
                navigator.go(request.link, animated = false)
                val tint = preferences.theme.colors.number.toArgb()
                footnoteNavigationScope.launch { navigator.markEpubNote(request.link, tint) }
            }
        }
        onNavigationRequestConsumed()
    }
}

/** The tap handling of the current navigator, for taps on the strips around it, which Readium never sees. */
private class EpubPageTaps {
    var handler: ((ReaderTapSide) -> Boolean)? = null

    fun onTap(side: ReaderTapSide) {
        handler?.invoke(side)
    }
}
