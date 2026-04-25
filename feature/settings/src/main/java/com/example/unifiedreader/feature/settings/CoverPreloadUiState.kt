package com.example.unifiedreader.feature.settings

data class CoverPreloadUiState(
    val isRunning: Boolean = false,
    val progressFraction: Float? = null,
    val progressLabel: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canStart: Boolean
        get() = !isRunning
}

