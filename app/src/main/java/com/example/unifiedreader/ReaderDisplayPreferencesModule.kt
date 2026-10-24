package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.feature.reader.preferences.DataStoreReaderPageColorPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.DataStoreReaderPageNumberPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ReaderDisplayPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The readers' shared page settings (page number, page colour); kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object ReaderDisplayPreferencesModule {
    @Provides
    @Singleton
    fun provideReaderDisplayPreferences(
        @ApplicationContext context: Context,
    ): ReaderDisplayPreferences = ReaderDisplayPreferences(
        pageNumbers = DataStoreReaderPageNumberPreferencesStore(context),
        pageColor = DataStoreReaderPageColorPreferencesStore(context),
    )
}
