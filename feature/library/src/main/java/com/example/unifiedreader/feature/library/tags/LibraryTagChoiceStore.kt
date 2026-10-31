package com.example.unifiedreader.feature.library.tags

import com.example.unifiedreader.core.database.dao.LibraryTagChoiceDao
import com.example.unifiedreader.core.database.entity.LibraryTagChoiceEntity
import com.example.unifiedreader.core.model.LibraryPlaceRegion
import com.example.unifiedreader.core.model.LibraryTagChoice
import com.example.unifiedreader.core.model.LibraryTagField
import com.example.unifiedreader.core.model.LibraryTagKind
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryTagKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where you put each source's tags, kept on this device. */
interface LibraryTagChoiceStore {
    val sortings: Flow<Map<SourceType, LibraryTagSorting>>

    suspend fun sort(source: SourceType, changes: List<LibraryTagSortChange>)
}

class RoomLibraryTagChoiceStore(
    private val dao: LibraryTagChoiceDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : LibraryTagChoiceStore {
    override val sortings: Flow<Map<SourceType, LibraryTagSorting>> = dao.observeChoices()
        .map(List<LibraryTagChoiceEntity>::toLibraryTagSortings)
        .flowOn(ioDispatcher)

    override suspend fun sort(source: SourceType, changes: List<LibraryTagSortChange>) {
        if (changes.isEmpty()) return
        val now = clock()
        withContext(ioDispatcher) { dao.putChoices(changes.map { change -> change.toEntity(source, now) }) }
    }
}

/** A Location is always a place, in Elsewhere unless it has a region; only a place keeps one. */
internal fun LibraryTagSortChange.toEntity(source: SourceType, chosenAtEpochMillis: Long): LibraryTagChoiceEntity {
    val kind = if (field == LibraryTagField.LOCATION) LibraryTagKind.PLACE else choice.kind
    val region = when {
        kind != LibraryTagKind.PLACE -> null
        field == LibraryTagField.LOCATION -> choice.region ?: LibraryPlaceRegion.ELSEWHERE
        else -> choice.region
    }
    return LibraryTagChoiceEntity(
        sourceType = source.name,
        field = field.name,
        tagKey = libraryTagKey(name),
        kind = kind.name,
        region = region?.name,
        chosenAtEpochMillis = chosenAtEpochMillis,
    )
}

/** Rows with a name the app no longer knows (a source, a row, a region) are skipped. */
internal fun List<LibraryTagChoiceEntity>.toLibraryTagSortings(): Map<SourceType, LibraryTagSorting> =
    mapNotNull { row -> enumOrNull<SourceType>(row.sourceType)?.let { source -> source to row } }
        .groupBy(keySelector = { (source, _) -> source }, valueTransform = { (_, row) -> row })
        .mapValues { (_, rows) ->
            LibraryTagSorting(
                tags = rows.filter { it.field == LibraryTagField.TAG.name }.mapNotNull { row ->
                    val kind = enumOrNull<LibraryTagKind>(row.kind) ?: return@mapNotNull null
                    row.tagKey to LibraryTagChoice(kind, row.region?.let { enumOrNull<LibraryPlaceRegion>(it) })
                }.toMap(),
                locationRegions = rows.filter { it.field == LibraryTagField.LOCATION.name }.associate { row ->
                    row.tagKey to (row.region?.let { enumOrNull<LibraryPlaceRegion>(it) } ?: LibraryPlaceRegion.ELSEWHERE)
                },
            )
        }

private inline fun <reified T : Enum<T>> enumOrNull(name: String): T? = enumValues<T>().firstOrNull { it.name == name }
