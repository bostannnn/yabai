package com.example.unifiedreader

import com.example.unifiedreader.core.common.AppDispatchers
import com.example.unifiedreader.core.common.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** The dispatchers and the one app-wide [ApplicationScope] that outliving work runs on. */
@Module
@InstallIn(SingletonComponent::class)
object AppCoroutinesModule {
    @Provides
    @Singleton
    fun provideAppDispatchers(): AppDispatchers = AppDispatchers()

    /** Lives as long as the process. A failed child does not cancel the others (SupervisorJob). */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(appDispatchers: AppDispatchers): CoroutineScope =
        CoroutineScope(SupervisorJob() + appDispatchers.default)
}
