package com.example.unifiedreader.feature.settings

data class CoverPreloadUiState(
    val isRunning: Boolean = false,
    val isClearingCache: Boolean = false,
    val progressFraction: Float? = null,
    val progressLabel: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val failedItems: List<CoverPreloadFailureUiItem> = emptyList(),
) {
    val canStart: Boolean
        get() = !isRunning && !isClearingCache

    val canRetryFailed: Boolean
        get() = !isRunning && !isClearingCache && failedItems.isNotEmpty()

    val canClearCache: Boolean
        get() = !isRunning && !isClearingCache
}

data class CoverPreloadFailureUiItem(
    val title: String,
    val variant: String,
    val reason: String,
)
