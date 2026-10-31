package com.example.unifiedreader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.unifiedreader.core.database.UnifiedReaderDatabase
import com.example.unifiedreader.core.model.LibraryTagSortChange
import com.example.unifiedreader.core.model.LibraryTagSorting
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.feature.library.LibraryCoverImageVariant
import com.example.unifiedreader.feature.library.tags.LibraryTagChoiceStore
import com.example.unifiedreader.feature.library.tags.LibraryTagSortingContext
import com.example.unifiedreader.feature.library.tags.LibraryTagsSettingsContent
import com.example.unifiedreader.feature.library.tags.RoomLibraryTagChoiceStore
import com.example.unifiedreader.feature.library.tags.libraryTagsSummary
import com.example.unifiedreader.feature.settings.SettingsTagsPage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where you put each source's tags; kept out of [AppModule], which should not grow. */
@Module
@InstallIn(SingletonComponent::class)
object AppLibraryTagsModule {
    @Provides
    @Singleton
    fun provideLibraryTagChoiceStore(
        database: UnifiedReaderDatabase,
    ): LibraryTagChoiceStore = RoomLibraryTagChoiceStore(dao = database.libraryTagChoiceDao())
}

/** Hands your tag sorting to the composition and stores your changes. */
@HiltViewModel
class AppLibraryTagsViewModel @Inject constructor(
    private val store: LibraryTagChoiceStore,
) : ViewModel() {
    private val shown = MutableStateFlow<Map<SourceType, LibraryTagSorting>?>(null)

    /** Every source's sorting; null until the database has answered. A change shows at once, before it is stored. */
    val sortings: StateFlow<Map<SourceType, LibraryTagSorting>?> = shown.asStateFlow()

    init {
        viewModelScope.launch { store.sortings.collect { stored -> shown.value = stored } }
    }

    fun sort(source: SourceType, changes: List<LibraryTagSortChange>) {
        if (changes.isEmpty()) return
        shown.update { current ->
            current?.let { sortings -> sortings + (source to (sortings[source] ?: LibraryTagSorting()).with(changes)) }
        }
        viewModelScope.launch { store.sort(source, changes) }
    }
}

/** The library's view of your tag sorting; empty (every tag in About) without [tagsViewModel]. */
@Composable
internal fun rememberLibraryTagSortingContext(
    tagsViewModel: AppLibraryTagsViewModel?,
    items: List<ReadableItem>,
    coverImageUrlProvider: (ReadableItem, LibraryCoverImageVariant) -> String?,
    onOpenTagSettings: (SourceType) -> Unit,
): LibraryTagSortingContext {
    if (tagsViewModel == null) return LibraryTagSortingContext.Empty
    val sortings by tagsViewModel.sortings.collectAsStateWithLifecycle()
    val latestOnOpenTagSettings by rememberUpdatedState(onOpenTagSettings)
    return remember(tagsViewModel, sortings, items, coverImageUrlProvider) {
        LibraryTagSortingContext(
            sortingBySource = sortings,
            items = items,
            onSort = tagsViewModel::sort,
            coverImageUrlProvider = coverImageUrlProvider,
            onOpenTagSettings = { source -> latestOnOpenTagSettings(source) },
        )
    }
}

/** Settings › Tags for [source]: the hub row's line, worked out off the main thread, and the page. */
@Composable
internal fun rememberSettingsTagsPage(
    context: LibraryTagSortingContext,
    source: SourceType,
    onSourceChanged: (SourceType) -> Unit,
): SettingsTagsPage {
    val sortings = context.sortingBySource
    val summary by produceState(DefaultTagsSummary, context.items, sortings) {
        value = sortings?.let { known -> withContext(Dispatchers.Default) { libraryTagsSummary(context.items, known) } }
            ?: DefaultTagsSummary
    }
    val latestOnSourceChanged by rememberUpdatedState(onSourceChanged)
    return remember(summary, source) {
        SettingsTagsPage(summary = summary) {
            LibraryTagsSettingsContent(source = source, onSourceChanged = { latestOnSourceChanged(it) })
        }
    }
}

private const val DefaultTagsSummary = "Where Browse shows each tag"
