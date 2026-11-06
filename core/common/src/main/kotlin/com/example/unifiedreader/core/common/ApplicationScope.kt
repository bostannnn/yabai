package com.example.unifiedreader.core.common

import javax.inject.Qualifier

/**
 * The app-wide `CoroutineScope` for work that outlives a screen (writes, prefetches, preference saves).
 * There is one, provided by Hilt; a class takes it rather than building a `CoroutineScope(SupervisorJob() + …)`
 * of its own, so tests can pass a scope they control and cancel. Add a dispatcher with `scope + dispatcher`:
 * the result shares the app scope's job.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
