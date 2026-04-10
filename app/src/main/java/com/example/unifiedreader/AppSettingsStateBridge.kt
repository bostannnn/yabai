package com.example.unifiedreader

import com.example.unifiedreader.feature.settings.SettingsState

internal fun AppUiState.toSettingsState(): SettingsState = SettingsState(
    themeMode = themeMode,
)

internal fun AppUiState.withSettingsState(
    settingsState: SettingsState,
): AppUiState = copy(
    themeMode = settingsState.themeMode,
)
