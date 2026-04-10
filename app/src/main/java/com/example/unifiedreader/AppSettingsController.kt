package com.example.unifiedreader

import com.example.unifiedreader.feature.settings.SettingsStateCoordinator
import com.example.unifiedreader.feature.settings.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppSettingsController @Inject constructor(
    private val settingsStateCoordinator: SettingsStateCoordinator,
) {
    fun restoreState(
        updateState: AppUiStateUpdater,
    ) {
        val initialState = settingsStateCoordinator.restoreState()
        updateState { state -> state.withSettingsState(initialState) }
    }

    fun onThemeModeSelected(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        themeMode: ThemeMode,
    ) {
        val selection = settingsStateCoordinator.selectThemeMode(
            currentState = currentState.toSettingsState(),
            themeMode = themeMode,
        ) ?: return
        updateState { state -> state.withSettingsState(selection.state) }
        scope.launch {
            settingsStateCoordinator.persistThemeMode(selection.themeMode)
        }
    }
}
