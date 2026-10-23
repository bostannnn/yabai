package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.navigation.SourceNavigationState
import com.example.unifiedreader.feature.navigation.SourceNavigationStateCoordinator
import com.example.unifiedreader.feature.navigation.SourceOrderMoveDirection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AppNavigationController @Inject constructor(
    private val sourceNavigationStateCoordinator: SourceNavigationStateCoordinator,
) {
    fun initialState(): SourceNavigationState = sourceNavigationStateCoordinator.restoreState()

    fun moveSource(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        sourceType: SourceType,
        direction: SourceOrderMoveDirection,
    ) {
        val selection = sourceNavigationStateCoordinator.moveSource(
            currentState = currentState.toSourceNavigationState(),
            sourceType = sourceType,
            direction = direction,
        ) ?: return
        updateState { state -> state.withSourceNavigationState(selection.state) }
        scope.launch {
            sourceNavigationStateCoordinator.persistSourceOrder(selection.sourceOrder)
        }
    }

    fun reorderSources(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        sourceOrder: List<SourceType>,
    ) {
        val selection = sourceNavigationStateCoordinator.reorderSources(
            currentState = currentState.toSourceNavigationState(),
            sourceOrder = sourceOrder,
        ) ?: return
        updateState { state -> state.withSourceNavigationState(selection.state) }
        scope.launch {
            sourceNavigationStateCoordinator.persistSourceOrder(selection.sourceOrder)
        }
    }

    /** Records which servers are connected, for the rail and the drawer at the next launch. */
    fun recordConnectedSources(
        currentState: AppUiState,
        updateState: AppUiStateUpdater,
        connectedSources: Set<SourceType>,
    ) {
        if (currentState.lastConnectedSources == connectedSources) return
        updateState { state -> state.copy(lastConnectedSources = connectedSources) }
        sourceNavigationStateCoordinator.persistConnectedSources(connectedSources)
    }
}
