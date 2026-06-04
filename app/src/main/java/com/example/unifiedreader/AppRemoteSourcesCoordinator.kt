package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class AppRemoteSourcesCoordinator @Inject constructor(
    private val kavitaController: AppKavitaRemoteSourceController,
    private val stumpController: AppStumpRemoteSourceController,
    private val grimmoryController: AppGrimmoryRemoteSourceController,
) {
    fun initialize(
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.initialize(updateState)
        stumpController.initialize(updateState)
        grimmoryController.initialize(updateState)
    }

    fun onKavitaDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.onDisplayNameChanged(displayName, updateState)
    }

    fun onKavitaBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.onBaseUrlChanged(baseUrl, updateState)
    }

    fun onKavitaBackupBaseUrlChanged(
        backupBaseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.onBackupBaseUrlChanged(backupBaseUrl, updateState)
    }

    fun onKavitaUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.onUsernameChanged(username, updateState)
    }

    fun onKavitaPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.onPasswordChanged(password, updateState)
    }

    fun onStumpDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        stumpController.onDisplayNameChanged(displayName, updateState)
    }

    fun onStumpBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        stumpController.onBaseUrlChanged(baseUrl, updateState)
    }

    fun onStumpBackupBaseUrlChanged(
        backupBaseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        stumpController.onBackupBaseUrlChanged(backupBaseUrl, updateState)
    }

    fun onStumpUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        stumpController.onUsernameChanged(username, updateState)
    }

    fun onStumpPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        stumpController.onPasswordChanged(password, updateState)
    }

    fun onGrimmoryDisplayNameChanged(
        displayName: String,
        updateState: AppUiStateUpdater,
    ) {
        grimmoryController.onDisplayNameChanged(displayName, updateState)
    }

    fun onGrimmoryBaseUrlChanged(
        baseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        grimmoryController.onBaseUrlChanged(baseUrl, updateState)
    }

    fun onGrimmoryBackupBaseUrlChanged(
        backupBaseUrl: String,
        updateState: AppUiStateUpdater,
    ) {
        grimmoryController.onBackupBaseUrlChanged(backupBaseUrl, updateState)
    }

    fun onGrimmoryUsernameChanged(
        username: String,
        updateState: AppUiStateUpdater,
    ) {
        grimmoryController.onUsernameChanged(username, updateState)
    }

    fun onGrimmoryPasswordChanged(
        password: String,
        updateState: AppUiStateUpdater,
    ) {
        grimmoryController.onPasswordChanged(password, updateState)
    }

    fun connectKavitaServer(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        kavitaController.connect(stateProvider, updateState, scope)
    }

    fun connectStumpServer(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        stumpController.connect(stateProvider, updateState, scope)
    }

    fun connectGrimmoryServer(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        grimmoryController.connect(stateProvider, updateState, scope)
    }

    fun refreshSelectedRemoteLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        when (stateProvider().selectedSource) {
            SourceType.KAVITA -> kavitaController.refresh(stateProvider, updateState, scope)
            SourceType.STUMP -> stumpController.refresh(stateProvider, updateState, scope)
            SourceType.GRIMMORY -> grimmoryController.refresh(stateProvider, updateState, scope)
            SourceType.LOCAL -> Unit
        }
    }

    fun observeStoredSessions(
        scope: CoroutineScope,
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
    ) {
        kavitaController.observeStoredSession(scope, stateProvider, updateState)
        stumpController.observeStoredSession(scope, stateProvider, updateState)
        grimmoryController.observeStoredSession(scope, stateProvider, updateState)
    }

    fun refreshKavitaLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        kavitaController.refresh(stateProvider, updateState, scope)
    }

    fun fastSyncKavitaLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
        preserveLibraryContext: Boolean = false,
    ) {
        kavitaController.fastRefresh(
            stateProvider = stateProvider,
            updateState = updateState,
            scope = scope,
            preserveLibraryContext = preserveLibraryContext,
        )
    }

    fun refreshStumpLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        stumpController.refresh(stateProvider, updateState, scope)
    }

    fun refreshGrimmoryLibrary(
        stateProvider: AppUiStateProvider,
        updateState: AppUiStateUpdater,
        scope: CoroutineScope,
    ) {
        grimmoryController.refresh(stateProvider, updateState, scope)
    }
}
