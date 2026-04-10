package com.example.unifiedreader.feature.settings

data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.System,
)

data class ThemeModeSelection(
    val state: SettingsState,
    val themeMode: ThemeMode,
)

class SettingsStateCoordinator(
    private val preferencesStore: SettingsPreferencesStore,
) {
    fun restoreState(): SettingsState = preferencesStore.preferencesFlow.value.toSettingsState()

    fun selectThemeMode(
        currentState: SettingsState,
        themeMode: ThemeMode,
    ): ThemeModeSelection? {
        if (currentState.themeMode == themeMode) return null
        return ThemeModeSelection(
            state = currentState.copy(themeMode = themeMode),
            themeMode = themeMode,
        )
    }

    suspend fun persistThemeMode(themeMode: ThemeMode) {
        preferencesStore.setThemeMode(themeMode)
    }
}

private fun SettingsPreferences.toSettingsState(): SettingsState = SettingsState(
    themeMode = themeMode,
)
