package com.example.unifiedreader

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.unifiedreader.core.network.buildHttpClient
import com.example.unifiedreader.feature.updates.AppUpdateController
import com.example.unifiedreader.feature.updates.AppUpdates
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** The in-app updater; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object AppUpdatesModule {
    @Provides
    @Singleton
    fun provideAppUpdateController(
        @ApplicationContext context: Context,
    ): AppUpdateController = AppUpdates.createController(
        context = context,
        // GitHub is on the internet, so this client waits longer than the LAN one.
        httpClient = buildHttpClient(
            connectTimeoutMillis = UPDATE_CONNECT_TIMEOUT_MILLIS,
            socketTimeoutMillis = UPDATE_SOCKET_TIMEOUT_MILLIS,
        ),
    )

    private const val UPDATE_CONNECT_TIMEOUT_MILLIS = 15_000L
    private const val UPDATE_SOCKET_TIMEOUT_MILLIS = 30_000L
}

/** Hands the singleton updater to the composition. */
@HiltViewModel
class AppUpdatesViewModel @Inject constructor(
    val controller: AppUpdateController,
) : ViewModel()
