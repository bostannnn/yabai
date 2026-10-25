package com.example.unifiedreader

import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.database.entity.ItemDetailsEntity
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryBookDescriptionLoader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** What a server said about a book's description: [text] is null when it has none. */
class FetchedDescription(val text: String?)

/** One server's way to the description its catalog leaves out. */
fun interface RemoteBookDescriptionSource {
    /** The server's answer, or null when it couldn't be asked (no session, unreachable). */
    suspend fun fetch(item: ReadableItem): FetchedDescription?
}

/**
 * The book page's descriptions: the whole one from the book's catalog entry, else what its server
 * says (a Grimmory description, a Kavita series summary), kept in `item_details` so the page has it
 * at once next time and offline. A server's answer, "none" included, is asked again after
 * [RefreshAfterMillis].
 */
@Singleton
class BookDescriptionRepository internal constructor(
    private val details: ItemDetailsStore,
    private val sourceFor: (SourceType) -> RemoteBookDescriptionSource?,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : LibraryBookDescriptionLoader {
    @Inject constructor(
        database: UnifiedReaderDatabase,
        grimmory: GrimmoryBookDescriptionSource,
        kavita: KavitaBookDescriptionSource,
    ) : this(
        details = RoomItemDetailsStore(database),
        sourceFor = { sourceType ->
            when (sourceType) {
                SourceType.GRIMMORY -> grimmory
                SourceType.KAVITA -> kavita
                // Stump sends summaries with its books, and local files carry their own.
                SourceType.STUMP,
                SourceType.LOCAL -> null
            }
        },
    )

    override suspend fun description(item: ReadableItem): String? = withContext(Dispatchers.IO) {
        try {
            details.catalogDescription(item.id)?.takeIf(String::isNotBlank)
                ?: serverDescription(item)
                ?: item.description
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            item.description
        }
    }

    private suspend fun serverDescription(item: ReadableItem): String? {
        val stored = details.get(item.id)
        if (stored != null && nowEpochMillis() - stored.updatedAtEpochMillis < RefreshAfterMillis) {
            return stored.description
        }
        val fetched = sourceFor(item.sourceType)?.fetch(item) ?: return stored?.description
        details.put(ItemDetailsEntity(itemId = item.id, description = fetched.text, updatedAtEpochMillis = nowEpochMillis()))
        return fetched.text
    }

    private companion object {
        const val RefreshAfterMillis = 30L * 24 * 60 * 60 * 1000
    }
}

/** Where fetched descriptions are kept, apart from the catalog the next sync rewrites. */
internal interface ItemDetailsStore {
    fun catalogDescription(itemId: String): String?

    fun get(itemId: String): ItemDetailsEntity?

    fun put(details: ItemDetailsEntity)
}

private class RoomItemDetailsStore(
    private val database: UnifiedReaderDatabase,
) : ItemDetailsStore {
    override fun catalogDescription(itemId: String): String? = database.itemDetailsDao().getCatalogDescription(itemId)

    override fun get(itemId: String): ItemDetailsEntity? = database.itemDetailsDao().getDetails(itemId)

    override fun put(details: ItemDetailsEntity) = database.itemDetailsDao().insertOrReplace(details)
}
