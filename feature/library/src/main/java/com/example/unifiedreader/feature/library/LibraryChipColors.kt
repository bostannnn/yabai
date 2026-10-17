package com.example.unifiedreader.feature.library

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun libraryAccentFilterChipColors() = with(UnifiedReaderThemeTokens) {
    FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = colors.surfaceSelected,
        selectedLabelColor = colors.onSurfaceSelected,
        selectedLeadingIconColor = colors.onSurfaceSelected,
    )
}

@Composable
internal fun libraryAccentFilterChipShape() = RoundedCornerShape(
    UnifiedReaderThemeTokens.presentation.chipCornerRadius,
)

@Composable
internal fun libraryAccentTextFieldShape() = RoundedCornerShape(
    UnifiedReaderThemeTokens.presentation.controlCornerRadius,
)
