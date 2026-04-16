package com.example.unifiedreader.data.kavita

import kotlin.coroutines.cancellation.CancellationException

internal suspend fun KavitaSeriesDetailDto.resolveSupplementalIssueTitles(
    fetchChapterInfo: suspend (chapterId: String) -> KavitaChapterInfoDto,
): Map<String, String> {
    val overrides = linkedMapOf<String, String>()
    resolvedChapters().forEach { resolved ->
        val chapterId = resolved.chapter.id?.toString() ?: return@forEach
        if (resolved.chapter.preferredIssueTitle(resolved.volumeName) != null) {
            return@forEach
        }
        val fallbackTitle = try {
            fetchChapterInfo(chapterId).preferredKavitaIssueTitleFallback(
                volumeName = resolved.volumeName,
            )
        } catch (throwable: Throwable) {
            when (throwable) {
                is CancellationException,
                is KavitaUnauthorizedException -> throw throwable
                else -> null
            }
        }
        fallbackTitle?.let { overrides[chapterId] = it }
    }
    return overrides
}

internal data class KavitaResolvedSeriesChapter(
    val chapter: KavitaChapterDto,
    val volumeName: String?,
)

internal fun KavitaSeriesDetailDto.resolvedChapters(): List<KavitaResolvedSeriesChapter> {
    val volumeNamesById = volumes.orEmpty()
        .associate { volume -> volume.id?.toString() to volume.name }
        .filterKeys { it != null }
        .mapKeys { (key, _) -> key!! }
    return sequenceOf(
        chapters.orEmpty(),
        specials.orEmpty(),
        storylineChapters.orEmpty(),
        volumes.orEmpty().flatMap { it.chapters.orEmpty() },
    ).flatten()
        .map { chapter ->
            KavitaResolvedSeriesChapter(
                chapter = chapter,
                volumeName = chapter.volumeTitle?.takeIf(String::isNotBlank)
                    ?: chapter.volumeId?.toString()?.let(volumeNamesById::get),
            )
        }
        .toList()
}
