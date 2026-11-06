package com.example.unifiedreader

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.unifiedreader.feature.translation.TranslationService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Translation in the reader and its settings; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object AppTranslationModule {
    @Provides
    @Singleton
    fun provideTranslationService(
        @ApplicationContext context: Context,
    ): TranslationService = TranslationService.create(context)
}

/** Hands the singleton translation service to the composition (Settings › Translation). */
@HiltViewModel
class AppTranslationViewModel @Inject constructor(
    val service: TranslationService,
) : ViewModel()
