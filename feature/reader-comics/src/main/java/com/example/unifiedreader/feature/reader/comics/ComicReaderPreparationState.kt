package com.example.unifiedreader.feature.reader.comics

internal sealed interface ComicReaderPreparationState {
    data class Preparing(
        val loadedPageCount: Int,
        val totalPageCount: Int,
    ) : ComicReaderPreparationState

    data class Ready(
        val spreadPlanState: ComicSpreadPlanState,
    ) : ComicReaderPreparationState
}
