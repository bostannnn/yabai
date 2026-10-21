package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.feature.reader.preferences.DataStoreReaderPageNumberPreferencesStore
import com.example.unifiedreader.feature.reader.preferences.ReaderPageNumberPreferencesStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The readers' shared page-number setting; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object ReaderPageNumberModule {
    @Provides
    @Singleton
    fun provideReaderPageNumberPreferencesStore(
        @ApplicationContext context: Context,
    ): ReaderPageNumberPreferencesStore = DataStoreReaderPageNumberPreferencesStore(context)
}
