package com.example.unifiedreader.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsStateCoordinatorTest {
    @Test
    fun `restore state uses current preferences`() {
        val coordinator = SettingsStateCoordinator(
            preferencesStore = FakeSettingsPreferencesStore(
                initialPreferences = SettingsPreferences(themeMode = ThemeMode.Dark),
            ),
        )

        assertEquals(
            SettingsState(themeMode = ThemeMode.Dark),
            coordinator.restoreState(),
        )
    }

    @Test
    fun `select theme mode returns null when unchanged`() {
        val coordinator = SettingsStateCoordinator(
            preferencesStore = FakeSettingsPreferencesStore(),
        )

        val selection = coordinator.selectThemeMode(
            currentState = SettingsState(themeMode = ThemeMode.Light),
            themeMode = ThemeMode.Light,
        )

        assertNull(selection)
    }

    @Test
    fun `select theme mode returns updated state when changed`() {
        val coordinator = SettingsStateCoordinator(
            preferencesStore = FakeSettingsPreferencesStore(),
        )

        val selection = coordinator.selectThemeMode(
            currentState = SettingsState(themeMode = ThemeMode.SystemDynamic),
            themeMode = ThemeMode.Graphite,
        )

        assertEquals(
            ThemeModeSelection(
                state = SettingsState(themeMode = ThemeMode.Graphite),
                themeMode = ThemeMode.Graphite,
            ),
            selection,
        )
    }

    @Test
    fun `persist theme mode delegates to preferences store`() = runTest {
        val preferencesStore = FakeSettingsPreferencesStore()
        val coordinator = SettingsStateCoordinator(
            preferencesStore = preferencesStore,
        )

        coordinator.persistThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, preferencesStore.preferencesFlow.value.themeMode)
    }
}

private class FakeSettingsPreferencesStore(
    initialPreferences: SettingsPreferences = SettingsPreferences(),
) : SettingsPreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<SettingsPreferences> = mutablePreferencesFlow

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        mutablePreferencesFlow.value = SettingsPreferences(themeMode = themeMode)
    }
}
