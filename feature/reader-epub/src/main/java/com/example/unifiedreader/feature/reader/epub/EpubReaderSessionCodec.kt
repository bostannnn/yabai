package com.example.unifiedreader.feature.reader.epub

import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.feature.reader.core.ReaderLocator
import com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot
import com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot
import com.example.unifiedreader.feature.reader.core.enumValueOrNull
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.Spread
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Locator

internal const val EpubFontSizeKey = "epub.fontSize"
internal const val EpubLineHeightKey = "epub.lineHeight"
internal const val EpubThemeKey = "epub.theme"
internal const val EpubLayoutModeKey = "epub.layoutMode"

internal data class EpubReaderPreferences(
    val fontSize: Double = 1.0,
    val lineHeight: Double = 1.5,
    val theme: EpubReaderTheme = EpubReaderTheme.Light,
    val layoutMode: EpubReaderLayoutMode = EpubReaderLayoutMode.Scroll,
)

internal fun restoreEpubPreferences(
    session: ReaderSessionSnapshot?,
): EpubReaderPreferences {
    val preferences = session?.preferences.orEmpty()
    return EpubReaderPreferences(
        fontSize = preferences[EpubFontSizeKey]
            ?.toDoubleOrNull()
            ?.coerceIn(0.8, 2.4)
            ?: 1.0,
        lineHeight = preferences[EpubLineHeightKey]
            ?.toDoubleOrNull()
            ?.coerceIn(1.1, 2.2)
            ?: 1.5,
        theme = preferences[EpubThemeKey]
            ?.enumValueOrNull<EpubReaderTheme>()
            ?: EpubReaderTheme.Light,
        layoutMode = preferences[EpubLayoutModeKey]
            ?.enumValueOrNull<EpubReaderLayoutMode>()
            ?: EpubReaderLayoutMode.Scroll,
    )
}

internal fun buildEpubPreferencesMap(
    preferences: EpubReaderPreferences,
): Map<String, String> = mapOf(
    EpubFontSizeKey to preferences.fontSize.toString(),
    EpubLineHeightKey to preferences.lineHeight.toString(),
    EpubThemeKey to preferences.theme.name,
    EpubLayoutModeKey to preferences.layoutMode.name,
)

internal fun EpubReaderPreferences.toNavigatorPreferences(): EpubPreferences = EpubPreferences(
    fontSize = fontSize,
    lineHeight = lineHeight,
    scroll = layoutMode == EpubReaderLayoutMode.Scroll,
    columnCount = when (layoutMode) {
        EpubReaderLayoutMode.Scroll -> ColumnCount.AUTO
        EpubReaderLayoutMode.SinglePage -> ColumnCount.ONE
        EpubReaderLayoutMode.DoublePage -> ColumnCount.TWO
    },
    publisherStyles = false,
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
): ReaderLocator = ReaderLocator(
    locatorType = LocatorType.CFI,
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

internal enum class EpubReaderLayoutMode(
    override val label: String,
) : EpubLabeledOption {
    Scroll("Scroll"),
    SinglePage("Page"),
    DoublePage("Spread"),
}
