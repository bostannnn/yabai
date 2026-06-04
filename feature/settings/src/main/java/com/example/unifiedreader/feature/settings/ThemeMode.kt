package com.example.unifiedreader.feature.settings

data class ThemePreviewSwatches(
    val surface: Long,
    val reader: Long,
    val sync: Long,
    val source: Long,
)

enum class ThemeMode(
    val storageValue: String,
    val label: String,
    val description: String,
    val previewSwatches: ThemePreviewSwatches,
    val usesDynamicColor: Boolean = false,
    val isDark: Boolean = false,
    val isAvailableInSettings: Boolean = true,
) {
    SystemDynamic(
        storageValue = "system",
        label = "System Dynamic",
        description = "Follow the device appearance and use Android dynamic color when available.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFFE8EEF7,
            reader = 0xFF4667A5,
            sync = 0xFF5E6F49,
            source = 0xFF7A5570,
        ),
        usesDynamicColor = true,
    ),
    Paper(
        storageValue = "paper",
        label = "Paper",
        description = "Use warm paper surfaces with clearer separation between cards, sheets, and navigation.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFFFFF8F0,
            reader = 0xFF405F6B,
            sync = 0xFF476A53,
            source = 0xFF86504E,
        ),
    ),
    Graphite(
        storageValue = "graphite",
        label = "Graphite",
        description = "Use neutral dark surfaces with stronger layer contrast for repeated browsing.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF191C20,
            reader = 0xFF9FCBDB,
            sync = 0xFFB2D3BC,
            source = 0xFFFFB4AB,
        ),
        isDark = true,
    ),
    HighContrast(
        storageValue = "high_contrast",
        label = "High Contrast",
        description = "Use bright text, firm outlines, and stronger selected states.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF000000,
            reader = 0xFFFFFFFF,
            sync = 0xFFFFFF00,
            source = 0xFF00E5FF,
        ),
        isDark = true,
    ),
    OledReader(
        storageValue = "oled_reader",
        label = "OLED Reader",
        description = "Use near-black app chrome with restrained raised surfaces for low-light reading.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF000000,
            reader = 0xFF8FA7FF,
            sync = 0xFF75D09A,
            source = 0xFFE9B4FF,
        ),
        isDark = true,
    ),
    SepiaReading(
        storageValue = "sepia_reading",
        label = "Sepia Reading",
        description = "Use amber paper surfaces and muted chrome for a softer reading-adjacent app shell.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFFFFF3DE,
            reader = 0xFF7B5B31,
            sync = 0xFF60764A,
            source = 0xFF8A574A,
        ),
    ),
    Terminal95(
        storageValue = "terminal_95",
        label = "Terminal 95",
        description = "Use phosphor green, amber, and cyan accents over deep terminal-black browsing surfaces.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF07110A,
            reader = 0xFF7CFF8A,
            sync = 0xFFFFD166,
            source = 0xFF6DE7FF,
        ),
        isDark = true,
    ),
    CrimsonCourt(
        storageValue = "crimson_court",
        label = "Crimson Court",
        description = "Use blood-red chrome, antique gold accents, and candlelit gothic surfaces.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF191016,
            reader = 0xFFFF6B86,
            sync = 0xFFE8C276,
            source = 0xFFC8B6FF,
        ),
        isDark = true,
    ),
    PanelPop(
        storageValue = "panel_pop",
        label = "Panel Pop",
        description = "Use loud comic-shop color with ink-heavy text, cyan panels, pink accents, and lemon highlights.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFFFFFDF6,
            reader = 0xFF006E8F,
            sync = 0xFFC8176B,
            source = 0xFF8A6500,
        ),
    ),
    Blueprint(
        storageValue = "blueprint",
        label = "Blueprint",
        description = "Use drafting-blue surfaces, precise cyan linework, and technical diagram accents.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF071A2E,
            reader = 0xFF9BE7FF,
            sync = 0xFFFFC857,
            source = 0xFFB8D8FF,
        ),
        isDark = true,
    ),
    VhsNight(
        storageValue = "vhs_night",
        label = "VHS Night",
        description = "Use washed analog blacks, faded cyan, tracking red, and tape-label surfaces.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF12131B,
            reader = 0xFF78D8E8,
            sync = 0xFFFF5C7A,
            source = 0xFFE1D7BA,
        ),
        isDark = true,
    ),
    DetectiveFile(
        storageValue = "detective_file",
        label = "Detective File",
        description = "Use manila folder surfaces, typewriter ink, evidence red, and case-file blue.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFFF3E4C2,
            reader = 0xFF233D5A,
            sync = 0xFFB3362D,
            source = 0xFF8A6B2F,
        ),
    ),
    Light(
        storageValue = "light",
        label = "Light",
        description = "Legacy light theme. New selections use Paper.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFFFFF8F0,
            reader = 0xFF405F6B,
            sync = 0xFF476A53,
            source = 0xFF86504E,
        ),
        isAvailableInSettings = false,
    ),
    Dark(
        storageValue = "dark",
        label = "Dark",
        description = "Legacy dark theme. New selections use Graphite.",
        previewSwatches = ThemePreviewSwatches(
            surface = 0xFF191C20,
            reader = 0xFF9FCBDB,
            sync = 0xFFB2D3BC,
            source = 0xFFFFB4AB,
        ),
        isDark = true,
        isAvailableInSettings = false,
    );

    companion object {
        val settingsEntries: List<ThemeMode> = entries.filter { it.isAvailableInSettings }

        fun fromStorageValue(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SystemDynamic
    }
}
