package com.example.unifiedreader.feature.library

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun libraryAccentFilterChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = UnifiedReaderThemeTokens.colors.surfaceSelected,
    selectedLabelColor = UnifiedReaderThemeTokens.colors.onSurfaceSelected,
    selectedLeadingIconColor = UnifiedReaderThemeTokens.colors.onSurfaceSelected,
)

@Composable
internal fun libraryAccentFilterChipShape() = RoundedCornerShape(
    UnifiedReaderThemeTokens.presentation.chipCornerRadius,
)
