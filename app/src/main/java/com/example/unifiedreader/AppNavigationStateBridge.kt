package com.example.unifiedreader

import com.example.unifiedreader.feature.navigation.SourceNavigationState

internal fun AppUiState.toSourceNavigationState(): SourceNavigationState = SourceNavigationState(
    sourceOrder = sourceOrder,
)

internal fun AppUiState.withSourceNavigationState(
    navigationState: SourceNavigationState,
): AppUiState = copy(
    sourceOrder = navigationState.sourceOrder,
    selectedSource = selectedSource.takeIf { source -> source in navigationState.sourceOrder }
        ?: navigationState.startupSource,
)
