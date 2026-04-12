package com.example.unifiedreader.feature.navigation

import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceNavigationStateCoordinatorTest {
    @Test
    fun `restore state uses current preferences`() {
        val coordinator = SourceNavigationStateCoordinator(
            preferencesStore = FakeSourceNavigationPreferencesStore(
                initialPreferences = SourceNavigationPreferences(
                    sourceOrder = listOf(
                        SourceType.LOCAL,
                        SourceType.KAVITA,
                        SourceType.STUMP,
                        SourceType.GRIMMORY,
                    ),
                ),
            ),
        )

        assertEquals(
            SourceNavigationState(
                sourceOrder = listOf(
                    SourceType.LOCAL,
                    SourceType.KAVITA,
                    SourceType.STUMP,
                    SourceType.GRIMMORY,
                ),
            ),
            coordinator.restoreState(),
        )
    }

    @Test
    fun `move source returns null when source is already first`() {
        val coordinator = SourceNavigationStateCoordinator(
            preferencesStore = FakeSourceNavigationPreferencesStore(),
        )

        val selection = coordinator.moveSource(
            currentState = SourceNavigationState(),
            sourceType = SourceType.KAVITA,
            direction = SourceOrderMoveDirection.Up,
        )

        assertNull(selection)
    }

    @Test
    fun `move source swaps positions when movement is valid`() {
        val coordinator = SourceNavigationStateCoordinator(
            preferencesStore = FakeSourceNavigationPreferencesStore(),
        )

        val selection = coordinator.moveSource(
            currentState = SourceNavigationState(),
            sourceType = SourceType.GRIMMORY,
            direction = SourceOrderMoveDirection.Up,
        )

        assertEquals(
            listOf(
                SourceType.KAVITA,
                SourceType.GRIMMORY,
                SourceType.STUMP,
                SourceType.LOCAL,
            ),
            selection?.sourceOrder,
        )
        assertEquals(SourceType.KAVITA, selection?.state?.startupSource)
    }

    @Test
    fun `persist source order delegates to preferences store`() = runTest {
        val preferencesStore = FakeSourceNavigationPreferencesStore()
        val coordinator = SourceNavigationStateCoordinator(
            preferencesStore = preferencesStore,
        )

        coordinator.persistSourceOrder(
            listOf(
                SourceType.LOCAL,
                SourceType.KAVITA,
                SourceType.STUMP,
                SourceType.GRIMMORY,
            ),
        )

        assertEquals(
            listOf(
                SourceType.LOCAL,
                SourceType.KAVITA,
                SourceType.STUMP,
                SourceType.GRIMMORY,
            ),
            preferencesStore.preferencesFlow.value.sourceOrder,
        )
    }
}

private class FakeSourceNavigationPreferencesStore(
    initialPreferences: SourceNavigationPreferences = SourceNavigationPreferences(),
) : SourceNavigationPreferencesStore {
    private val mutablePreferencesFlow = MutableStateFlow(initialPreferences)

    override val preferencesFlow: StateFlow<SourceNavigationPreferences> = mutablePreferencesFlow

    override suspend fun setSourceOrder(sourceOrder: List<SourceType>) {
        mutablePreferencesFlow.value = SourceNavigationPreferences(
            sourceOrder = normalizeSourceOrder(sourceOrder),
        )
    }
}
