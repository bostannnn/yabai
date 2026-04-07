package com.example.unifiedreader.feature.settings

enum class ThemeMode(
    val storageValue: String,
    val label: String,
    val description: String,
) {
    System(
        storageValue = "system",
        label = "System",
        description = "Follow the device appearance and use Material 3 dynamic color when Android provides it.",
    ),
    Light(
        storageValue = "light",
        label = "Light",
        description = "Keep bright surfaces and higher contrast for daytime reading.",
    ),
    Dark(
        storageValue = "dark",
        label = "Dark",
        description = "Use Material 3 dark surfaces with lower glare for low-light use.",
    );

    companion object {
        fun fromStorageValue(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: System
    }
}
