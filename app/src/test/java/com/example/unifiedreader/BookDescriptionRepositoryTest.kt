package com.example.unifiedreader

import com.example.unifiedreader.core.database.entity.ItemDetailsEntity
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookDescriptionRepositoryTest {
    private val book = ReadableItem(
        id = "grimmory:item:12",
        sourceType = SourceType.GRIMMORY,
        libraryId = "grimmory:library:4",
        remoteId = "12",
        title = "Glass Meridian",
        format = FormatType.EPUB,
    )

    @Test
    fun `the catalog's whole description wins and the server is not asked`() = runTest {
        val store = FakeItemDetailsStore(catalog = mapOf(book.id to "The whole description."))
        val source = FakeDescriptionSource(FetchedDescription("From the server"))
        val repository = BookDescriptionRepository(store, { source })

        assertEquals("The whole description.", repository.description(book.copy(description = "The whole")))
        assertEquals(0, source.calls)
    }

    @Test
    fun `a description the catalog leaves out is fetched once and kept`() = runTest {
        val store = FakeItemDetailsStore()
        val source = FakeDescriptionSource(FetchedDescription("When the great lens cracks."))
        val repository = BookDescriptionRepository(store, { source }, nowEpochMillis = { 1_000L })

        assertEquals("When the great lens cracks.", repository.description(book))
        assertEquals("When the great lens cracks.", repository.description(book))
        assertEquals(1, source.calls)
        assertEquals(ItemDetailsEntity(book.id, "When the great lens cracks.", 1_000L), store.details[book.id])
    }

    @Test
    fun `a server with no description is remembered as none`() = runTest {
        val store = FakeItemDetailsStore()
        val source = FakeDescriptionSource(FetchedDescription(null))
        val repository = BookDescriptionRepository(store, { source })

        assertNull(repository.description(book))
        assertNull(repository.description(book))
        assertEquals(1, source.calls)
    }

    @Test
    fun `an old answer is asked again, and kept when the server can't be reached`() = runTest {
        val store = FakeItemDetailsStore()
        store.details[book.id] = ItemDetailsEntity(book.id, "Kept from before.", updatedAtEpochMillis = 0L)
        val unreachable = FakeDescriptionSource(null)
        val later = 31L * 24 * 60 * 60 * 1000
        val repository = BookDescriptionRepository(store, { unreachable }, nowEpochMillis = { later })

        assertEquals("Kept from before.", repository.description(book))
        assertEquals(1, unreachable.calls)
    }

    @Test
    fun `a source without a server gives the library's description`() = runTest {
        val local = book.copy(id = "local:item:1", sourceType = SourceType.LOCAL, description = "From the file.")
        val repository = BookDescriptionRepository(FakeItemDetailsStore(), { null })

        assertEquals("From the file.", repository.description(local))
    }

    @Test
    fun `a failing store gives the library's description`() = runTest {
        val repository = BookDescriptionRepository(
            details = object : ItemDetailsStore {
                override fun catalogDescription(itemId: String): String? = error("database closed")
                override fun get(itemId: String): ItemDetailsEntity? = null
                override fun put(details: ItemDetailsEntity) = Unit
            },
            sourceFor = { null },
        )

        assertEquals("The whole", repository.description(book.copy(description = "The whole")))
    }
}

private class FakeItemDetailsStore(
    private val catalog: Map<String, String> = emptyMap(),
) : ItemDetailsStore {
    val details = mutableMapOf<String, ItemDetailsEntity>()

    override fun catalogDescription(itemId: String): String? = catalog[itemId]

    override fun get(itemId: String): ItemDetailsEntity? = details[itemId]

    override fun put(details: ItemDetailsEntity) {
        this.details[details.itemId] = details
    }
}

private class FakeDescriptionSource(
    private val answer: FetchedDescription?,
) : RemoteBookDescriptionSource {
    var calls = 0

    override suspend fun fetch(item: ReadableItem): FetchedDescription? {
        calls++
        return answer
    }
}
