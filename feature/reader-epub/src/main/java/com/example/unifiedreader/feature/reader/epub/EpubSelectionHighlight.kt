package com.example.unifiedreader.feature.reader.epub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The fill behind selected text in an EPUB. Readium's own is an opaque pale blue on every page
 * colour, which leaves the light text of Dusk and Black unreadable (1.0:1). This one is a
 * see-through blue: the page shows through it, so one value works on every page colour, and the
 * text keeps its own colour (5.4:1 or better on all five, see the test).
 */
internal object EpubSelectionHighlight {
    val color: Color = Color(red = 0x5B, green = 0x86, blue = 0xC4, alpha = 0x73)

    /** As CSS writes it. */
    val css: String = "rgba(91, 134, 196, 0.45)"

    /** What the fill looks like on [page]: the colour the selected text sits on. */
    fun over(page: Color): Color = color.compositeOver(page)
}
