package com.example.unifiedreader.core.database

import com.example.unifiedreader.core.database.entity.toDomain
import com.example.unifiedreader.core.database.entity.toEntity
import com.example.unifiedreader.core.model.BookMetadata
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LibraryContentType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.ReadableItemKind
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.remoteItemId
import com.example.unifiedreader.core.model.remoteLibraryId
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCatalogEntityMappersTest {
    @Test
    fun `maps library domain model through entity`() {
        val library = Library(
            id = SourceType.GRIMMORY.remoteLibraryId("g-books"),
            sourceType = SourceType.GRIMMORY,
            title = "Books",
            remoteId = "g-books",
            contentType = LibraryContentType.BOOKS,
        )

        assertEquals(library, library.toEntity().toDomain())
    }

    @Test
    fun `maps readable item domain model through entity`() {
        val item = ReadableItem(
            id = SourceType.GRIMMORY.remoteItemId("book-1"),
            sourceType = SourceType.GRIMMORY,
            libraryId = SourceType.GRIMMORY.remoteLibraryId("g-books"),
            remoteId = "book-1",
            title = "Book 1",
            format = FormatType.EPUB,
            kind = ReadableItemKind.BOOK,
            pageCount = 300,
            coverUrl = "https://grimmory.example/api/v1/media/book/1/thumbnail",
            description = "Description",
            addedAtEpochMillis = 1775046600000L,
            bookMetadata = BookMetadata(author = "Author"),
        )

        assertEquals(item, item.toEntity(updatedAtEpochMillis = 1775046600000L).toDomain())
    }
}
