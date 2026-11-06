package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.common.ApplicationScope
import com.example.unifiedreader.feature.library.DataStoreLibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.library.LibraryBrowsePreferencesStore
import com.example.unifiedreader.feature.navigation.DataStoreSourceNavigationPreferencesStore
import com.example.unifiedreader.feature.navigation.SourceNavigationPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreComicReaderModePreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreComicReadingDirectionPreferencesStore
import com.example.unifiedreader.feature.settings.DataStoreSettingsPreferencesStore
import com.example.unifiedreader.feature.settings.SettingsPreferencesStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

/**
 * The DataStore-backed preference stores. Each saves on the app scope, on IO, so a save outlives the
 * screen that asked for it. Kept out of [AppModule], which should not grow.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppPreferencesModule {
    @Provides
    @Singleton
    fun provideSourceNavigationPreferencesStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        appDispatchers: AppDispatchers,
    ): SourceNavigationPreferencesStore = DataStoreSourceNavigationPreferencesStore(context, scope = applicationScope + appDispatchers.io)

    @Provides
    @Singleton
    fun provideSettingsPreferencesStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        appDispatchers: AppDispatchers,
    ): SettingsPreferencesStore = DataStoreSettingsPreferencesStore(context, scope = applicationScope + appDispatchers.io)

    @Provides
    @Singleton
    fun provideLibraryBrowsePreferencesStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        appDispatchers: AppDispatchers,
    ): LibraryBrowsePreferencesStore = DataStoreLibraryBrowsePreferencesStore(context, scope = applicationScope + appDispatchers.io)

    @Provides
    @Singleton
    fun provideComicReadingDirectionPreferencesStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        appDispatchers: AppDispatchers,
    ): ComicReadingDirectionPreferencesStore = DataStoreComicReadingDirectionPreferencesStore(
        context,
        scope = applicationScope + appDispatchers.io,
    )

    @Provides
    @Singleton
    fun provideComicReaderModePreferencesStore(
        @ApplicationContext context: Context,
        @ApplicationScope applicationScope: CoroutineScope,
        appDispatchers: AppDispatchers,
    ): ComicReaderModePreferencesStore = DataStoreComicReaderModePreferencesStore(context, scope = applicationScope + appDispatchers.io)
}
