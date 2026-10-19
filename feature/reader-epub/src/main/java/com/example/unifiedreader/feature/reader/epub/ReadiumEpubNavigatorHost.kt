package com.example.unifiedreader.feature.reader.epub

import android.content.res.Configuration
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.lifecycleScope
import com.example.unifiedreader.core.model.ReadableItem
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
    onLocationChanged: (Locator, Int, Int?, Boolean) -> Unit,
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
    val resumePoint = remember(publication) { EpubNavigatorResumePoint() }
    val curlHost = remember(publication) { EpubPageCurlHost() }
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
        backTint = epubPageCurlBackTint(preferences.theme),
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
    }

    DisposableEffect(
        fragmentManager,
        publication,
        navigatorFactory,
        containerId,
        fragmentTag,
    ) {
        var navigatorFragment: EpubNavigatorFragment? = null
        val footnoteNavigationJobs = mutableSetOf<Job>()
        val isScrollMode = preferences.layoutMode == EpubReaderLayoutMode.Scroll
        var isAtBookEnd = false
        var bookEndCheckJob: Job? = null
        fun reportLocation(locator: Locator, pageIndex: Int, totalPages: Int?, atBookEnd: Boolean) {
            isAtBookEnd = atBookEnd
            curlHost.onPageChanged(
                isAtBookStart = pageIndex == 0 && publication.readingOrderIndexOf(locator) == 0,
                isAtBookEnd = atBookEnd,
            )
            latestOnLocationChanged.value(locator, pageIndex, totalPages, atBookEnd)
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
                    if (footnoteContext != null) {
                        latestOnFootnoteActivated.value(
                            EpubFootnoteContent(
                                title = link.title?.takeIf(String::isNotBlank) ?: "Footnote",
                                body = htmlFootnoteToPlainText(footnoteContext.noteContent),
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
                    if (!isScrollMode || !isLastResource) {
                        reportLocation(
                            locator = locator,
                            pageIndex = pageIndex,
                            totalPages = totalPages,
                            atBookEnd = isEpubBookEnd(isLastResource, isScrollMode, pageIndex, totalPages, null),
                        )
                        return
                    }
                    bookEndCheckJob = footnoteNavigationScope.launch {
                        val scrolledToEnd = navigatorFragment
                            ?.evaluateJavascript(EpubScrolledToResourceEndScript)
                            ?.trim() == "true"
                        reportLocation(
                            locator = locator,
                            pageIndex = pageIndex,
                            totalPages = totalPages,
                            atBookEnd = isEpubBookEnd(true, true, pageIndex, totalPages, scrolledToEnd),
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
                            )
                            curlHost.refreshColumns()
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
                        turnByTap(PageCurlTarget.Previous) { fragment.goBackward(animated = true) }
                        true
                    }

                    EpubTapAction.Next -> {
                        if (isAtBookEnd) {
                            latestOnEndNavigationRequested.value()
                        } else {
                            turnByTap(PageCurlTarget.Next) { fragment.goForward(animated = true) }
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
            curlHost.navigator = null
            bookEndCheckJob?.cancel()
            footnoteNavigationJobs.toList().forEach { job -> job.cancel() }
            footnoteNavigationJobs.clear()
            val navigator = fragmentManager.findFragmentByTag(fragmentTag) as? EpubNavigatorFragment
            navigator?.let {
                it.removeInputListener(tapZoneListener)
                resumePoint.onNavigatorRemoved(it.currentLocator.value)
                latestOnLocationChanged.value(it.currentLocator.value, 0, null, isAtBookEnd)
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
