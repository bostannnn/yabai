package com.example.unifiedreader

import android.app.Application
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.Library
import com.example.unifiedreader.core.model.LocatorType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.libraryDisplayLabel
import com.example.unifiedreader.data.repository.LibraryRepository
import com.example.unifiedreader.data.repository.ReadableItemRepository
import com.example.unifiedreader.feature.library.ItemProgressSummary
import com.example.unifiedreader.feature.reader.comics.ComicPageSource
import com.example.unifiedreader.feature.reader.comics.ComicReaderPreferencesStore
import com.example.unifiedreader.feature.reader.comics.ComicReaderProgressStore
import com.example.unifiedreader.feature.reader.core.ReaderScreenFactory
import com.example.unifiedreader.feature.reader.core.ReaderSessionStore
import com.example.unifiedreader.feature.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.jvm.JvmSuppressWildcards

@HiltViewModel
class AppViewModel @Inject constructor(
    application: Application,
    private val appPreferencesStore: AppPreferencesStore,
    private val libraryRepository: LibraryRepository,
    private val readableItemRepository: ReadableItemRepository,
    private val localDocumentRepository: LocalDocumentDataSource,
    private val importedItemDeletionService: ImportedItemDeletionService,
    val comicPageSource: ComicPageSource,
    val comicReaderProgressStore: ComicReaderProgressStore,
    val comicReaderPreferencesStore: ComicReaderPreferencesStore,
    val readerSessionStore: ReaderSessionStore,
    val readerFactories: List<@JvmSuppressWildcards ReaderScreenFactory>,
) : AndroidViewModel(application) {
    private var catalogLibraries: List<Library> = libraryRepository.getLibraries()
    private var catalogItems: List<ReadableItem> = readableItemRepository.getItems()

    var uiState by mutableStateOf(AppUiState())
        private set

    init {
        uiState = uiState.copy(themeMode = appPreferencesStore.preferencesFlow.value.themeMode)
        refreshLibraryState()
        observeAppState()
    }

    fun onTopLevelSelected(destination: TopLevelDestination) {
        uiState = uiState.copy(
            topLevel = destination,
        )
        refreshProgress()
    }

    fun onSourceSelected(sourceType: SourceType) {
        uiState = uiState.copy(
            selectedSource = sourceType,
            selectedItemId = null,
            activeReader = null,
        )
        refreshProgress()
    }

    fun onItemSelected(item: ReadableItem) {
        uiState = uiState.copy(
            selectedItemId = item.id,
            activeReader = item.format,
        )
        refreshProgress()
    }

    fun onBackPressed() {
        uiState = when {
            uiState.activeReader != null -> uiState.copy(
                activeReader = null,
                selectedItemId = null,
            )
            uiState.selectedItemId != null -> uiState.copy(selectedItemId = null)
            else -> uiState
        }
        refreshProgress()
    }

    fun refreshProgress() {
        uiState = uiState.copy(
            progressByItemId = loadProgressSummaries(
                items = uiState.items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
            ),
        )
    }

    fun onDocumentImported(uri: Uri) {
        viewModelScope.launch {
            importDocument(uri)
        }
    }

    fun onDeleteImportedItem(item: ReadableItem) {
        viewModelScope.launch {
            deleteImportedItem(item)
        }
    }

    fun onThemeModeSelected(themeMode: ThemeMode) {
        if (uiState.themeMode == themeMode) return
        uiState = uiState.copy(themeMode = themeMode)
        viewModelScope.launch {
            appPreferencesStore.setThemeMode(themeMode)
        }
    }

    internal suspend fun importDocument(uri: Uri) {
        val importedItem = localDocumentRepository.import(uri)
        refreshLibraryState(
            topLevel = TopLevelDestination.Library,
            selectedSource = importedItem?.sourceType ?: uiState.selectedSource,
            selectedItemId = importedItem?.id,
            activeReader = importedItem?.format,
            libraryMessage = if (importedItem != null) {
                "Imported ${importedItem.libraryDisplayLabel()} (${importedItem.format.name})"
            } else {
                "Unable to import that file as a supported local document."
            },
        )
    }

    internal suspend fun deleteImportedItem(item: ReadableItem) {
        val outcome = importedItemDeletionService.delete(item)
        val removed = outcome == ImportedItemDeletionOutcome.Removed
        val isDeletedSelection = removed && uiState.selectedItemId == item.id
        refreshLibraryState(
            selectedSource = uiState.selectedSource,
            selectedItemId = if (isDeletedSelection) null else uiState.selectedItemId,
            activeReader = if (isDeletedSelection) null else uiState.activeReader,
            libraryMessage = deletionMessage(item, outcome),
        )
    }

    fun clearLibraryMessage() {
        if (uiState.libraryMessage != null) {
            uiState = uiState.copy(libraryMessage = null)
        }
    }

    private fun refreshLibraryState(
        topLevel: TopLevelDestination = uiState.topLevel,
        selectedSource: SourceType = uiState.selectedSource,
        selectedItemId: String? = uiState.selectedItemId,
        activeReader: FormatType? = uiState.activeReader,
        libraryMessage: String? = uiState.libraryMessage,
        themeMode: ThemeMode = uiState.themeMode,
    ) {
        val items = catalogItems + localDocumentRepository.getImportedItems()
        uiState = uiState.copy(
            topLevel = topLevel,
            selectedSource = selectedSource,
            libraries = catalogLibraries,
            items = items,
            selectedItemId = selectedItemId,
            activeReader = activeReader,
            progressByItemId = loadProgressSummaries(
                items = items,
                progressByItemId = readerSessionStore.progressByItemIdFlow.value,
                sessionByItemId = readerSessionStore.sessionByItemIdFlow.value,
            ),
            libraryMessage = libraryMessage,
            themeMode = themeMode,
        )
    }

    private fun observeAppState() {
        viewModelScope.launch {
            combine(
                appPreferencesStore.preferencesFlow,
                combine(
                    libraryRepository.observeLibraries(),
                    readableItemRepository.observeItems(),
                    localDocumentRepository.importedItemsFlow,
                    readerSessionStore.progressByItemIdFlow,
                    readerSessionStore.sessionByItemIdFlow,
                ) { libraries, catalogItems, importedItems, progressByItemId, sessionByItemId ->
                    val items = catalogItems + importedItems
                    AppCatalogSnapshot(
                        libraries = libraries,
                        catalogItems = catalogItems,
                        items = items,
                        progressByItemId = loadProgressSummaries(
                            items = items,
                            progressByItemId = progressByItemId,
                            sessionByItemId = sessionByItemId,
                        ),
                        themeMode = uiState.themeMode,
                    )
                },
            ) { preferences, snapshot ->
                snapshot.copy(themeMode = preferences.themeMode)
            }.collect { snapshot ->
                catalogLibraries = snapshot.libraries
                catalogItems = snapshot.catalogItems
                uiState = uiState.copy(
                    libraries = snapshot.libraries,
                    items = snapshot.items,
                    progressByItemId = snapshot.progressByItemId,
                    themeMode = snapshot.themeMode,
                )
            }
        }
    }

    private fun loadProgressSummaries(
        items: List<ReadableItem>,
        progressByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderProgressSnapshot>,
        sessionByItemId: Map<String, com.example.unifiedreader.feature.reader.core.ReaderSessionSnapshot>,
    ): Map<String, ItemProgressSummary> = items.associateNotNull { item ->
        val progress = progressByItemId[item.id] ?: return@associateNotNull null
        val sessionPageCount = sessionByItemId[item.id]?.pageCount?.takeIf { it > 0 }
        val summary = when (progress.locatorType) {
            LocatorType.PAGE -> {
                val pageIndex = progress.page ?: return@associateNotNull null
                val pageCount = sessionPageCount ?: when (item.format) {
                    FormatType.CBZ,
                    FormatType.CBR -> localDocumentRepository.getPageCount(item)
                    FormatType.EPUB -> 0
                    FormatType.PDF -> return@associateNotNull null
                }
                buildItemProgressSummary(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                )
            }

            LocatorType.PERCENT -> buildPercentProgressSummary(
                percent = progress.percent,
                pageCount = sessionPageCount,
            )
            LocatorType.CFI -> buildSavedLocationProgressSummary(
                percent = progress.percent,
                pageCount = sessionPageCount,
            )
        } ?: return@associateNotNull null
        item.id to summary
    }
}

private data class AppCatalogSnapshot(
    val libraries: List<Library>,
    val catalogItems: List<ReadableItem>,
    val items: List<ReadableItem>,
    val progressByItemId: Map<String, ItemProgressSummary>,
    val themeMode: ThemeMode,
)

data class AppUiState(
    val topLevel: TopLevelDestination = TopLevelDestination.Library,
    val selectedSource: SourceType = SourceType.KAVITA,
    val selectedItemId: String? = null,
    val activeReader: FormatType? = null,
    val libraries: List<Library> = emptyList(),
    val items: List<ReadableItem> = emptyList(),
    val progressByItemId: Map<String, ItemProgressSummary> = emptyMap(),
    val libraryMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
) {
    val visibleItems: List<ReadableItem>
        get() = items.filter { it.sourceType == selectedSource }

    val selectedItem: ReadableItem?
        get() = items.firstOrNull { it.id == selectedItemId }
}

private fun deletionMessage(
    item: ReadableItem,
    outcome: ImportedItemDeletionOutcome,
): String = when (outcome) {
    ImportedItemDeletionOutcome.Removed -> "Removed ${item.libraryDisplayLabel()}."
    ImportedItemDeletionOutcome.NotImportedItem -> "Only imported files can be removed from the library."
    ImportedItemDeletionOutcome.NotFound -> "Unable to remove ${item.libraryDisplayLabel()}."
}

private inline fun <T, R> Iterable<T>.associateNotNull(
    transform: (T) -> Pair<String, R>?,
): Map<String, R> = buildMap {
    for (element in this@associateNotNull) {
        val pair = transform(element) ?: continue
        put(pair.first, pair.second)
    }
}

enum class TopLevelDestination {
    Library,
    Settings,
}

private fun buildItemProgressSummary(
    pageIndex: Int,
    pageCount: Int,
): ItemProgressSummary? {
    if (pageCount <= 0) return null
    val clampedPageIndex = pageIndex.coerceIn(0, pageCount - 1)
    return ItemProgressSummary(
        label = "Resume on page ${clampedPageIndex + 1} of $pageCount",
        fraction = (clampedPageIndex + 1) / pageCount.toFloat(),
        pageCount = pageCount,
    )
}

private fun buildPercentProgressSummary(
    percent: Double?,
    pageCount: Int? = null,
): ItemProgressSummary? {
    val normalizedPercent = percent?.coerceIn(0.0, 1.0) ?: return null
    val percentLabel = (normalizedPercent * 100).toInt()
    return ItemProgressSummary(
        label = "Resume at $percentLabel%",
        fraction = normalizedPercent.toFloat(),
        pageCount = pageCount,
    )
}

private fun buildSavedLocationProgressSummary(
    percent: Double?,
    pageCount: Int? = null,
): ItemProgressSummary = ItemProgressSummary(
    label = "Resume from saved location",
    fraction = (percent ?: 0.0).coerceIn(0.0, 1.0).toFloat(),
    pageCount = pageCount,
)

@VisibleForTesting(otherwise = VisibleForTesting.NONE)
internal object AppViewModelTestAccess {
    fun buildItemProgressSummary(
        pageIndex: Int,
        pageCount: Int,
    ): ItemProgressSummary? = com.example.unifiedreader.buildItemProgressSummary(
        pageIndex = pageIndex,
        pageCount = pageCount,
    )
}
