@file:OptIn(ExperimentalReadiumApi::class)

package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.enumValueOrNull
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

internal const val EpubTypographyModeKey = "epub.typographyMode"
internal const val EpubFontFamilyKey = "epub.fontFamily"
internal const val EpubFontSizeKey = "epub.fontSize"
internal const val EpubLineHeightKey = "epub.lineHeight"
internal const val EpubPageMarginsKey = "epub.pageMargins"
internal const val EpubParagraphSpacingKey = "epub.paragraphSpacing"
internal const val EpubParagraphIndentKey = "epub.paragraphIndent"
internal const val EpubTextAlignKey = "epub.textAlign"
internal const val EpubThemeKey = "epub.theme"
internal const val EpubThemeOverrideKey = "epub.themeOverride"
internal const val EpubLayoutModeKey = "epub.layoutMode"
internal const val EpubLayoutOverrideKey = "epub.layoutOverride"

internal data class EpubReaderPreferences(
    val typographyMode: EpubTypographyMode = EpubTypographyMode.Custom,
    val fontFamily: EpubReaderFontFamily = EpubReaderFontFamily.Serif,
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.5,
    val pageMargins: Double = 1.0,
    val paragraphSpacing: Double = 0.0,
    val paragraphIndent: Double = 1.0,
    val textAlign: EpubReaderTextAlign = EpubReaderTextAlign.Default,
    val theme: EpubReaderTheme = EpubReaderTheme.Light,
    val themeOverride: Boolean = false,
    val layoutMode: EpubReaderLayoutMode = EpubReaderLayoutMode.Scroll,
    val layoutModeOverride: Boolean = false,
)

internal fun restoreEpubPreferences(
    session: ReaderSessionSnapshot?,
): EpubReaderPreferences {
    val preferences = session?.preferences.orEmpty()
    return EpubReaderPreferences(
        typographyMode = preferences[EpubTypographyModeKey]
            ?.enumValueOrNull<EpubTypographyMode>()
            ?: EpubTypographyMode.Custom,
        fontFamily = preferences[EpubFontFamilyKey]
            ?.enumValueOrNull<EpubReaderFontFamily>()
            ?: EpubReaderFontFamily.Serif,
        fontSize = preferences[EpubFontSizeKey]
            ?.toDoubleOrNull()
            ?.coerceIn(0.8, 2.4)
            ?: 1.0,
        lineHeight = preferences[EpubLineHeightKey]
            ?.toDoubleOrNull()
            ?.coerceIn(1.1, 2.2)
            ?: 1.5,
        pageMargins = preferences[EpubPageMarginsKey]
            ?.toDoubleOrNull()
            ?.coerceIn(0.5, 2.0)
            ?: 1.0,
        paragraphSpacing = preferences[EpubParagraphSpacingKey]
            ?.toDoubleOrNull()
            ?.coerceIn(0.0, 2.0)
            ?: 0.0,
        paragraphIndent = preferences[EpubParagraphIndentKey]
            ?.toDoubleOrNull()
            ?.coerceIn(0.0, 3.0)
            ?: 1.0,
        textAlign = preferences[EpubTextAlignKey]
            ?.enumValueOrNull<EpubReaderTextAlign>()
            ?: EpubReaderTextAlign.Default,
        theme = preferences[EpubThemeKey]
            ?.enumValueOrNull<EpubReaderTheme>()
            ?: EpubReaderTheme.Light,
        themeOverride = preferences[EpubThemeOverrideKey]
            ?.toBooleanStrictOrNull()
            ?: false,
        layoutMode = preferences[EpubLayoutModeKey]
            ?.enumValueOrNull<EpubReaderLayoutMode>()
            ?: EpubReaderLayoutMode.Scroll,
        layoutModeOverride = preferences[EpubLayoutOverrideKey]
            ?.toBooleanStrictOrNull()
            ?: false,
    )
}

internal fun buildEpubPreferencesMap(
    preferences: EpubReaderPreferences,
): Map<String, String> = mapOf(
    EpubTypographyModeKey to preferences.typographyMode.name,
    EpubFontFamilyKey to preferences.fontFamily.name,
    EpubFontSizeKey to preferences.fontSize.toString(),
    EpubLineHeightKey to preferences.lineHeight.toString(),
    EpubPageMarginsKey to preferences.pageMargins.toString(),
    EpubParagraphSpacingKey to preferences.paragraphSpacing.toString(),
    EpubParagraphIndentKey to preferences.paragraphIndent.toString(),
    EpubTextAlignKey to preferences.textAlign.name,
    EpubThemeKey to preferences.theme.name,
    EpubThemeOverrideKey to preferences.themeOverride.toString(),
    EpubLayoutModeKey to preferences.layoutMode.name,
    EpubLayoutOverrideKey to preferences.layoutModeOverride.toString(),
)

internal fun EpubReaderPreferences.toNavigatorPreferences(): EpubPreferences = EpubPreferences(
    fontFamily = if (typographyMode == EpubTypographyMode.Custom) {
        fontFamily.toReadiumFontFamily()
    } else {
        null
    },
    fontSize = fontSize.takeIf { typographyMode == EpubTypographyMode.Custom },
    lineHeight = lineHeight.takeIf { typographyMode == EpubTypographyMode.Custom },
    pageMargins = pageMargins.takeIf { typographyMode == EpubTypographyMode.Custom },
    paragraphSpacing = paragraphSpacing.takeIf { typographyMode == EpubTypographyMode.Custom },
    paragraphIndent = paragraphIndent.takeIf { typographyMode == EpubTypographyMode.Custom },
    textAlign = textAlign.toReadiumTextAlign().takeIf { typographyMode == EpubTypographyMode.Custom },
    scroll = layoutMode == EpubReaderLayoutMode.Scroll,
    columnCount = when (layoutMode) {
        EpubReaderLayoutMode.Scroll -> ColumnCount.AUTO
        EpubReaderLayoutMode.SinglePage -> ColumnCount.ONE
        EpubReaderLayoutMode.DoublePage -> ColumnCount.TWO
    },
    publisherStyles = typographyMode == EpubTypographyMode.Original,
    spread = when (layoutMode) {
        EpubReaderLayoutMode.DoublePage -> Spread.ALWAYS
        EpubReaderLayoutMode.Scroll,
        EpubReaderLayoutMode.SinglePage,
        -> Spread.NEVER
    },
    theme = when (theme) {
        EpubReaderTheme.Light -> Theme.LIGHT
        EpubReaderTheme.Dark -> Theme.DARK
    },
)

internal fun ReaderProgressSnapshot.toReadiumLocator(): Locator? {
    if (locatorType != LocatorType.CFI) return null
    val serialized = cfi ?: return null
    return deserializeLocator(serialized)
}

internal fun buildReaderLocator(
    locator: Locator,
    page: Int? = locator.locations.position,
): ReaderLocator = ReaderLocator(
    locatorType = LocatorType.CFI,
    page = page,
    cfi = locator.toJSON().toString(),
    percent = locator.locations.totalProgression ?: locator.locations.progression,
)

private fun deserializeLocator(
    serialized: String,
): Locator? = runCatching {
    Locator.Companion.fromJSON(JSONObject(serialized))
}.getOrNull()

internal enum class EpubReaderTheme(
    override val label: String,
) : EpubLabeledOption {
    Light("Light"),
    Dark("Dark"),
}

internal enum class EpubTypographyMode(
    override val label: String,
) : EpubLabeledOption {
    Original("Original"),
    Custom("Custom"),
}

internal enum class EpubReaderFontFamily(
    override val label: String,
    val readiumName: String,
    val category: EpubReaderFontCategory,
    val previewAssetPath: String? = null,
) : EpubLabeledOption {
    Serif("Serif", FontFamily.SERIF.name, EpubReaderFontCategory.System),
    Sans("Sans", FontFamily.SANS_SERIF.name, EpubReaderFontCategory.System),
    Monospace("Monospace", FontFamily.MONOSPACE.name, EpubReaderFontCategory.System),
    OpenDyslexic("OpenDyslexic", FontFamily.OPEN_DYSLEXIC.name, EpubReaderFontCategory.Accessibility),
    AccessibleDfa("AccessibleDfA", FontFamily.ACCESSIBLE_DFA.name, EpubReaderFontCategory.Accessibility),
    AtkinsonHyperlegible(
        "Atkinson Hyperlegible",
        "Atkinson Hyperlegible",
        EpubReaderFontCategory.Accessibility,
        "fonts/atkinson-hyperlegible/AtkinsonHyperlegible-Regular.ttf",
    ),
    IaWriterDuospace("iA Writer Duospace", FontFamily.IA_WRITER_DUOSPACE.name, EpubReaderFontCategory.Reader),
    Literata("Literata", "Literata", EpubReaderFontCategory.Reader, "fonts/literata/Literata-Variable.ttf"),
    SourceSerif4(
        "Source Serif 4",
        "Source Serif 4",
        EpubReaderFontCategory.Reader,
        "fonts/source-serif-4/SourceSerif4-Variable.ttf",
    ),
    LibreBaskerville(
        "Libre Baskerville",
        "Libre Baskerville",
        EpubReaderFontCategory.Reader,
        "fonts/libre-baskerville/LibreBaskerville-Variable.ttf",
    ),
    CrimsonPro("Crimson Pro", "Crimson Pro", EpubReaderFontCategory.Reader, "fonts/crimson-pro/CrimsonPro-Variable.ttf"),
    CormorantGaramond(
        "Cormorant Garamond",
        "Cormorant Garamond",
        EpubReaderFontCategory.Reader,
        "fonts/cormorant-garamond/CormorantGaramond-Variable.ttf",
    ),
    Merriweather("Merriweather", "Merriweather", EpubReaderFontCategory.Reader, "fonts/merriweather/Merriweather-Variable.ttf"),
}

internal fun EpubReaderFontFamily.toReadiumFontFamily(): FontFamily = FontFamily(readiumName)

internal enum class EpubReaderFontCategory(
    val sectionLabel: String,
) {
    System("Publisher and system"),
    Accessibility("Accessibility"),
    Reader("Reader fonts"),
}

internal enum class EpubReaderTextAlign(
    override val label: String,
) : EpubLabeledOption {
    Default("Default"),
    Start("Left"),
    Justify("Justified"),
}

internal fun EpubReaderTextAlign.toReadiumTextAlign(): TextAlign? = when (this) {
    EpubReaderTextAlign.Default -> null
    EpubReaderTextAlign.Start -> TextAlign.START
    EpubReaderTextAlign.Justify -> TextAlign.JUSTIFY
}

internal enum class EpubReaderLayoutMode(
    override val label: String,
) : EpubLabeledOption {
    Scroll("Scroll"),
    SinglePage("Page"),
    DoublePage("Spread"),
}
