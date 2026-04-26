package com.example.unifiedreader.feature.settings

enum class ThemeMode(
    val storageValue: String,
    val label: String,
    val description: String,
    val usesDynamicColor: Boolean = false,
    val isDark: Boolean = false,
    val isAvailableInSettings: Boolean = true,
) {
    SystemDynamic(
        storageValue = "system",
        label = "System Dynamic",
        description = "Follow the device appearance and use Android dynamic color when available.",
        usesDynamicColor = true,
    ),
    Paper(
        storageValue = "paper",
        label = "Paper",
        description = "Use warm paper surfaces with clearer separation between cards, sheets, and navigation.",
    ),
    Graphite(
        storageValue = "graphite",
        label = "Graphite",
        description = "Use neutral dark surfaces with stronger layer contrast for repeated browsing.",
        isDark = true,
    ),
    HighContrast(
        storageValue = "high_contrast",
        label = "High Contrast",
        description = "Use bright text, firm outlines, and stronger selected states.",
        isDark = true,
    ),
    OledReader(
        storageValue = "oled_reader",
        label = "OLED Reader",
        description = "Use near-black app chrome with restrained raised surfaces for low-light reading.",
        isDark = true,
    ),
    SepiaReading(
        storageValue = "sepia_reading",
        label = "Sepia Reading",
        description = "Use amber paper surfaces and muted chrome for a softer reading-adjacent app shell.",
    ),
    Light(
        storageValue = "light",
        label = "Light",
        description = "Legacy light theme. New selections use Paper.",
        isAvailableInSettings = false,
    ),
    Dark(
        storageValue = "dark",
        label = "Dark",
        description = "Legacy dark theme. New selections use Graphite.",
        isDark = true,
        isAvailableInSettings = false,
    );

    companion object {
        val settingsEntries: List<ThemeMode> = entries.filter { it.isAvailableInSettings }

        fun fromStorageValue(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SystemDynamic
    }
}
