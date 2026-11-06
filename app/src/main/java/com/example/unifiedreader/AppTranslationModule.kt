package com.example.unifiedreader

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.unifiedreader.core.common.ApplicationScope
import com.example.unifiedreader.core.network.buildHttpClient
import com.example.unifiedreader.feature.translation.TranslationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** Translation in the reader and its settings; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object AppTranslationModule {
    @Provides
    @Singleton
    fun provideTranslationService(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
    ): TranslationService = TranslationService.create(
        context = context,
        applicationScope = applicationScope,
        // DeepL is on the internet, so this client waits longer than the LAN one.
        httpClient = buildHttpClient(
            connectTimeoutMillis = DEEPL_CONNECT_TIMEOUT_MILLIS,
            socketTimeoutMillis = DEEPL_SOCKET_TIMEOUT_MILLIS,
        ),
    )

    private const val DEEPL_CONNECT_TIMEOUT_MILLIS = 8_000L
    private const val DEEPL_SOCKET_TIMEOUT_MILLIS = 15_000L
}

/** Hands the singleton translation service to the composition (Settings › Translation). */
@HiltViewModel
class AppTranslationViewModel @Inject constructor(
    val service: TranslationService,
) : ViewModel()
