package com.example.unifiedreader

import android.content.Context
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.feature.reader.comics.ComicPageSpec
import com.example.unifiedreader.feature.reader.comics.PersistentComicPagePreviewStore
import com.example.unifiedreader.feature.reader.core.ReaderWorkCoordinator
import kotlinx.coroutines.CoroutineScope

/** The pages of comics imported from this device. Their entries are read once at import and kept in the record. */
class LocalComicPageSource internal constructor(
    context: Context,
    comicArchiveCodecs: List<ComicArchiveCodec>,
    scope: CoroutineScope,
    previewStore: PersistentComicPagePreviewStore?,
    readerWorkCoordinator: ReaderWorkCoordinator,
    private val findRecord: (itemId: String) -> LocalDocumentRecord?,
) : ArchiveComicPageSource(
    context = context,
    comicArchiveCodecs = comicArchiveCodecs,
    scope = scope,
    previewStore = previewStore,
    readerWorkCoordinator = readerWorkCoordinator,
    archiveLabel = "imported",
) {
    override fun getPages(item: ReadableItem): List<ComicPageSpec> {
        val record = findRecord(item.id) ?: return emptyList()
        return record.pageEntryNames.mapIndexed { index, entryName ->
            ComicPageSpec(
                id = entryName,
                pageIndex = index,
                contentDescription = "${item.libraryDisplayLabel()} page ${index + 1}",
            )
        }
    }

    override suspend fun recordFor(item: ReadableItem): LocalDocumentRecord? = findRecord(item.id)

    override fun knownRecordFor(item: ReadableItem): LocalDocumentRecord? = findRecord(item.id)

    /** [getPages] names each page by its entry. */
    override fun entryNameFor(record: LocalDocumentRecord, page: ComicPageSpec): String = page.id
}
