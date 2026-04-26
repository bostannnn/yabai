package com.example.unifiedreader.feature.reader.epub

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.epub.css.FontWeight
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class)
internal fun epubNavigatorFragmentConfiguration(): EpubNavigatorFragment.Configuration =
    EpubNavigatorFragment.Configuration(
        shouldApplyInsetsPadding = false,
    ).apply {
        useReadiumCssFontSize = false
        servedAssets += "fonts/.*"
        addBundledReaderFontDeclarations()
    }

@OptIn(ExperimentalReadiumApi::class)
private fun EpubNavigatorFragment.Configuration.addBundledReaderFontDeclarations() {
    addVariableReaderFont(
        family = EpubReaderFontFamily.Literata,
        romanSource = "fonts/literata/Literata-Variable.ttf",
        italicSource = "fonts/literata/Literata-Italic-Variable.ttf",
        alternates = listOf(FontFamily.SERIF),
    )
    addVariableReaderFont(
        family = EpubReaderFontFamily.SourceSerif4,
        romanSource = "fonts/source-serif-4/SourceSerif4-Variable.ttf",
        italicSource = "fonts/source-serif-4/SourceSerif4-Italic-Variable.ttf",
        alternates = listOf(FontFamily.SERIF),
    )
    addVariableReaderFont(
        family = EpubReaderFontFamily.LibreBaskerville,
        romanSource = "fonts/libre-baskerville/LibreBaskerville-Variable.ttf",
        italicSource = "fonts/libre-baskerville/LibreBaskerville-Italic-Variable.ttf",
        alternates = listOf(FontFamily.SERIF),
    )
    addVariableReaderFont(
        family = EpubReaderFontFamily.CrimsonPro,
        romanSource = "fonts/crimson-pro/CrimsonPro-Variable.ttf",
        italicSource = "fonts/crimson-pro/CrimsonPro-Italic-Variable.ttf",
        alternates = listOf(FontFamily.SERIF),
    )
    addVariableReaderFont(
        family = EpubReaderFontFamily.CormorantGaramond,
        romanSource = "fonts/cormorant-garamond/CormorantGaramond-Variable.ttf",
        italicSource = "fonts/cormorant-garamond/CormorantGaramond-Italic-Variable.ttf",
        alternates = listOf(FontFamily.SERIF),
    )
    addVariableReaderFont(
        family = EpubReaderFontFamily.Merriweather,
        romanSource = "fonts/merriweather/Merriweather-Variable.ttf",
        italicSource = "fonts/merriweather/Merriweather-Italic-Variable.ttf",
        alternates = listOf(FontFamily.SERIF),
    )
    addFontFamilyDeclaration(
        fontFamily = EpubReaderFontFamily.AtkinsonHyperlegible.toReadiumFontFamily(),
        alternates = listOf(FontFamily.SANS_SERIF),
    ) {
        addFontFace {
            addSource("fonts/atkinson-hyperlegible/AtkinsonHyperlegible-Regular.ttf")
            setFontWeight(FontWeight.NORMAL)
        }
        addFontFace {
            addSource("fonts/atkinson-hyperlegible/AtkinsonHyperlegible-Italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(FontWeight.NORMAL)
        }
        addFontFace {
            addSource("fonts/atkinson-hyperlegible/AtkinsonHyperlegible-Bold.ttf")
            setFontWeight(FontWeight.BOLD)
        }
        addFontFace {
            addSource("fonts/atkinson-hyperlegible/AtkinsonHyperlegible-BoldItalic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(FontWeight.BOLD)
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private fun EpubNavigatorFragment.Configuration.addVariableReaderFont(
    family: EpubReaderFontFamily,
    romanSource: String,
    italicSource: String,
    alternates: List<FontFamily>,
) {
    addFontFamilyDeclaration(
        fontFamily = family.toReadiumFontFamily(),
        alternates = alternates,
    ) {
        addFontFace {
            addSource(romanSource)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource(italicSource)
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }
}
