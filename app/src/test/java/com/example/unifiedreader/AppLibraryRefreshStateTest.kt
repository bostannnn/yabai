package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.RemoteSourceConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLibraryRefreshStateTest {
    @Test
    fun `refresh library shows on every connected server and not on local`() {
        val connected = AppUiState(
            kavitaConnection = RemoteSourceConnectionUiState(source = SourceType.KAVITA, hasStoredSession = true),
            stumpConnection = RemoteSourceConnectionUiState(source = SourceType.STUMP, hasStoredSession = true),
            grimmoryConnection = RemoteSourceConnectionUiState(source = SourceType.GRIMMORY, hasStoredSession = true),
        )
        listOf(SourceType.KAVITA, SourceType.STUMP, SourceType.GRIMMORY).forEach { source ->
            assertEquals(
                LibraryRefreshTopBarState(isVisible = true, isEnabled = true, isRefreshing = false),
                connected.copy(selectedSource = source).libraryRefreshTopBarState(),
            )
        }
        assertEquals(LibraryRefreshTopBarState(), connected.copy(selectedSource = SourceType.LOCAL).libraryRefreshTopBarState())
        assertEquals(LibraryRefreshTopBarState(), AppUiState(selectedSource = SourceType.GRIMMORY).libraryRefreshTopBarState())
    }

    @Test
    fun `the ring shows while any sync of the library runs, not while it connects`() {
        fun grimmoryDoing(action: ServerConnectionAction) = AppUiState(
            selectedSource = SourceType.GRIMMORY,
            grimmoryConnection = RemoteSourceConnectionUiState(
                source = SourceType.GRIMMORY,
                hasStoredSession = true,
                isConnecting = true,
                activeAction = action,
            ),
        ).libraryRefreshTopBarState()

        listOf(ServerConnectionAction.FastSync, ServerConnectionAction.Sync, ServerConnectionAction.FullSync).forEach { action ->
            assertEquals(
                LibraryRefreshTopBarState(isVisible = true, isEnabled = false, isRefreshing = true),
                grimmoryDoing(action),
            )
        }
        assertEquals(
            LibraryRefreshTopBarState(isVisible = true, isEnabled = false, isRefreshing = false),
            grimmoryDoing(ServerConnectionAction.Connect),
        )
    }
}
