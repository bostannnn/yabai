package com.example.unifiedreader.data.kavita

import kotlin.coroutines.cancellation.CancellationException

internal suspend fun KavitaSeriesDetailDto.resolveSupplementalIssueTitles(
    maxRequests: Int = Int.MAX_VALUE,
    fetchChapterInfo: suspend (chapterId: String) -> KavitaChapterInfoDto,
): Map<String, String> {
    return resolveSupplementalChapterInfo(
        maxRequests = maxRequests,
        fetchChapterInfo = fetchChapterInfo,
    )
        .mapNotNull { (chapterId, info) ->
            val resolved = resolvedChapters().firstOrNull { chapter ->
                chapter.chapter.id?.toString() == chapterId
            } ?: return@mapNotNull null
            info.preferredKavitaIssueTitleFallback(
                volumeName = resolved.volumeName,
            )?.let { title -> chapterId to title }
        }
        .toMap()
}

internal suspend fun KavitaSeriesDetailDto.resolveSupplementalChapterInfo(
    maxRequests: Int = Int.MAX_VALUE,
    fetchChapterInfo: suspend (chapterId: String) -> KavitaChapterInfoDto,
): Map<String, KavitaChapterInfoDto> {
    val chapterInfoById = linkedMapOf<String, KavitaChapterInfoDto>()
    resolvedChapters()
        .filter { resolved -> resolved.chapter.preferredIssueTitle(resolved.volumeName) == null }
        .distinctBy { resolved -> resolved.chapter.id?.toString() }
        .take(maxRequests.coerceAtLeast(0))
        .forEach { resolved ->
            val chapterId = resolved.chapter.id?.toString() ?: return@forEach
            val chapterInfo = try {
                fetchChapterInfo(chapterId)
            } catch (throwable: Throwable) {
                when (throwable) {
                    is CancellationException,
                    is KavitaUnauthorizedException -> throw throwable
                    else -> null
                }
            }
            chapterInfo?.let { chapterInfoById[chapterId] = it }
        }
    return chapterInfoById
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
