package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.navigation.SourceNavigationState
import com.example.unifiedreader.feature.navigation.navigationConnectedServers

internal fun AppUiState.toSourceNavigationState(): SourceNavigationState = SourceNavigationState(
    sourceOrder = sourceOrder,
    lastConnectedSources = lastConnectedSources,
)

internal fun AppUiState.withSourceNavigationState(
    navigationState: SourceNavigationState,
): AppUiState = copy(
    sourceOrder = navigationState.sourceOrder,
    lastConnectedSources = navigationState.lastConnectedSources,
    selectedSource = selectedSource.takeIf { source -> source in navigationState.sourceOrder }
        ?: navigationState.startupSource,
)

/**
 * Each server's stored session once it has loaded after launch (true or false), null before: the
 * rail and the drawer list the connected ones, falling back to [AppUiState.lastConnectedSources].
 */
internal val AppUiState.serverSessions: Map<SourceType, Boolean?>
    get() = mapOf(
        SourceType.KAVITA to kavitaConnection.hasStoredSession.takeIf { kavitaConnection.isSessionKnown },
        SourceType.STUMP to stumpConnection.hasStoredSession.takeIf { stumpConnection.isSessionKnown },
        SourceType.GRIMMORY to grimmoryConnection.hasStoredSession.takeIf { grimmoryConnection.isSessionKnown },
    )

/** The servers the rail and the drawer list (see [navigationConnectedServers]). */
internal val AppUiState.navigationConnectedServers: Set<SourceType>
    get() = navigationConnectedServers(serverSessions, lastConnectedSources)
