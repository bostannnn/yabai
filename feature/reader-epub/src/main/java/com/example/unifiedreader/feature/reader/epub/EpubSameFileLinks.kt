package com.example.unifiedreader.feature.reader.epub

import android.webkit.JavascriptInterface
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

// Android's WebView follows a link to an anchor in the page's own file by itself and never asks
// Readium, so a note later in the same chapter file ("1" pointing at the chapter's References)
// would never reach shouldFollowInternalLink: the page just jumped to the note. This script catches
// those taps before the WebView does and hands the anchor to [EpubSameFileLinkBridge], which sends
// it down the same path as any other link with an anchor (a note card, or a turn to the target).
// Readium already shows `epub:type="noteref"` links as notes, so the script leaves them alone.

internal const val EpubSameFileLinkBridgeName = "yabaiSameFileLinks"

/** Installs the tap catcher once per page; safe to run again on every page turn. */
internal val EpubSameFileLinksScript = """
    (function() {
      if (window.__yabaiSameFileLinks || !window.$EpubSameFileLinkBridgeName) return;
      window.__yabaiSameFileLinks = true;
      document.addEventListener('click', function(event) {
        if (event.defaultPrevented || !window.getSelection().isCollapsed) return;
        var link = event.target && event.target.closest && event.target.closest('a[href]');
        if (!link || typeof link.href !== 'string') return;
        var types = link.getAttributeNS('http://www.idpf.org/2007/ops', 'type') || link.getAttribute('epub:type') || '';
        if (/(^|\s)noteref(\s|$)/.test(types)) return;
        var target, here;
        try { target = new URL(link.href); here = new URL(location.href); } catch (e) { return; }
        if (target.hash.length < 2 || target.origin !== here.origin ||
            target.pathname !== here.pathname || target.search !== here.search) return;
        event.preventDefault();
        event.stopPropagation();
        window.$EpubSameFileLinkBridgeName.onLink(target.hash);
      }, true);
    })();
""".trimIndent()

/** Receives the anchor ("#ref1") of a tapped link into [resource], the file the page shows. */
internal class EpubSameFileLinkBridge(
    private val resource: Url,
    private val onLink: (Link) -> Unit,
) {
    @JavascriptInterface
    fun onLink(anchor: String) {
        epubSameFileLink(resource, anchor)?.let(onLink)
    }
}

/** The link to [anchor] ("#ref1", as the page's URL encodes it) inside [resource], or null when there is none. */
internal fun epubSameFileLink(
    resource: Url,
    anchor: String,
): Link? = anchor
    .takeIf { value -> value.startsWith("#") }
    ?.let { value -> Url(value) }
    ?.let(resource::resolve)
    ?.takeUnless { url -> url.fragment.isNullOrEmpty() }
    ?.let { url -> Link(href = Href(url)) }
