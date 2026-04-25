package com.example.unifiedreader.feature.settings

data class CoverPreloadUiState(
    val isRunning: Boolean = false,
    val progressFraction: Float? = null,
    val progressLabel: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val failedItems: List<CoverPreloadFailureUiItem> = emptyList(),
) {
    val canStart: Boolean
        get() = !isRunning

    val canRetryFailed: Boolean
        get() = !isRunning && failedItems.isNotEmpty()
}

data class CoverPreloadFailureUiItem(
    val title: String,
    val variant: String,
    val reason: String,
)
