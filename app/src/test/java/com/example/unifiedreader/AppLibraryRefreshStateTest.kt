package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.settings.GrimmoryConnectionUiState
import com.example.unifiedreader.feature.settings.KavitaConnectionUiState
import com.example.unifiedreader.feature.settings.ServerConnectionAction
import com.example.unifiedreader.feature.settings.StumpConnectionUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLibraryRefreshStateTest {
    @Test
    fun `refresh library shows on every connected server and not on local`() {
        val connected = AppUiState(
            kavitaConnection = KavitaConnectionUiState(hasStoredSession = true),
            stumpConnection = StumpConnectionUiState(hasStoredSession = true),
            grimmoryConnection = GrimmoryConnectionUiState(hasStoredSession = true),
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
            grimmoryConnection = GrimmoryConnectionUiState(
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
