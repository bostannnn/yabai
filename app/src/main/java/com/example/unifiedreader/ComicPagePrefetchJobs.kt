package com.example.unifiedreader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The page prefetches of one comic page loader, keyed by page cache key. Prefetch is best effort: when the
 * reader settles on another page, [retarget] cancels the jobs outside the new window and starts the missing
 * ones, in window order.
 */
internal class ComicPagePrefetchJobs(
    private val scope: CoroutineScope,
) {
    private val jobs = mutableMapOf<String, Job>()
    private val lock = Any()

    /**
     * Prefetches [targets] (page cache key → load; the load fills the caches itself), in order. Running jobs
     * for other keys are cancelled; a target that [isCached] or already has a job is skipped.
     */
    fun retarget(
        targets: List<Pair<String, suspend () -> Any?>>,
        isCached: (String) -> Boolean,
    ) {
        val targetKeys = targets.mapTo(HashSet()) { (cacheKey, _) -> cacheKey }
        synchronized(lock) {
            jobs.entries.removeAll { (_, job) -> job.isCancelled || job.isCompleted }
            jobs.entries
                .filter { (cacheKey, _) -> cacheKey !in targetKeys }
                .toList()
                .forEach { (cacheKey, job) ->
                    job.cancel()
                    jobs.remove(cacheKey)
                }
        }
        targets.forEach { (cacheKey, load) ->
            if (isCached(cacheKey)) return@forEach
            synchronized(lock) {
                if (jobs.containsKey(cacheKey)) return@forEach
                jobs[cacheKey] = scope.launch {
                    try {
                        load()
                    } catch (_: CancellationException) {
                        // Prefetch is best effort and should stop immediately when the plan changes.
                    } finally {
                        synchronized(lock) {
                            jobs.remove(cacheKey)
                        }
                    }
                }
            }
        }
    }

    /** Cancels the jobs whose cache key matches [predicate]. */
    fun cancelWhere(predicate: (String) -> Boolean) {
        synchronized(lock) {
            jobs.entries
                .filter { (cacheKey, _) -> predicate(cacheKey) }
                .toList()
                .forEach { (cacheKey, job) ->
                    job.cancel()
                    jobs.remove(cacheKey)
                }
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            jobs.values.forEach(Job::cancel)
            jobs.clear()
        }
    }
}
