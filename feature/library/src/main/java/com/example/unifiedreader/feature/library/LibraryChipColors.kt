package com.example.unifiedreader.feature.library

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.example.unifiedreader.ui.theme.UnifiedReaderSelectionTreatment
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens
import com.example.unifiedreader.ui.theme.unifiedReaderComponentTreatment

@Composable
internal fun libraryAccentFilterChipColors() = with(UnifiedReaderThemeTokens) {
    val recipe = components.control
    val selectedContainerColor: Color
    val selectedContentColor: Color
    when (recipe.selectionTreatment) {
        UnifiedReaderSelectionTreatment.Inverse -> {
            selectedContainerColor = colors.sourceAccent
            selectedContentColor = colors.onSourceAccent
        }
        UnifiedReaderSelectionTreatment.Underline,
        UnifiedReaderSelectionTreatment.SideMark -> {
            selectedContainerColor = colors.surfaceOverlay.copy(alpha = 0.78f)
            selectedContentColor = colors.readerAccent
        }
        UnifiedReaderSelectionTreatment.Stamp -> {
            selectedContainerColor = colors.syncAccentContainer
            selectedContentColor = colors.onSyncAccentContainer
        }
        UnifiedReaderSelectionTreatment.Fill -> {
            selectedContainerColor = colors.surfaceSelected
            selectedContentColor = colors.onSurfaceSelected
        }
    }
    FilterChipDefaults.filterChipColors(
        containerColor = if (recipe.containerAlpha > 0f) {
            colors.surfaceRaised.copy(alpha = recipe.containerAlpha * 0.62f)
        } else {
            Color.Transparent
        },
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = selectedContainerColor,
        selectedLabelColor = selectedContentColor,
        selectedLeadingIconColor = selectedContentColor,
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

@Composable
internal fun Modifier.libraryAccentChipTreatment(
    selected: Boolean,
): Modifier {
    val themeColors = UnifiedReaderThemeTokens.colors
    return unifiedReaderComponentTreatment(
        recipe = UnifiedReaderThemeTokens.components.control,
        accentColor = themeColors.readerAccent,
        secondaryColor = themeColors.syncAccent,
        overlayColor = MaterialTheme.colorScheme.onSurface,
        shape = libraryAccentFilterChipShape(),
        active = selected,
    )
}

@Composable
internal fun Modifier.libraryAccentTextFieldTreatment(
    selected: Boolean,
): Modifier {
    val themeColors = UnifiedReaderThemeTokens.colors
    val controlRecipe = UnifiedReaderThemeTokens.components.control
    val inputRecipe = if (controlRecipe.selectionTreatment == UnifiedReaderSelectionTreatment.Stamp) {
        controlRecipe.copy(selectionTreatment = UnifiedReaderSelectionTreatment.Underline)
    } else {
        controlRecipe
    }
    return unifiedReaderComponentTreatment(
        recipe = inputRecipe,
        accentColor = themeColors.readerAccent,
        secondaryColor = themeColors.syncAccent,
        overlayColor = MaterialTheme.colorScheme.onSurface,
        shape = libraryAccentTextFieldShape(),
        active = selected,
    )
}

@Composable
fun LibraryToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.libraryAccentChipTreatment(selected = selected),
        colors = libraryAccentFilterChipColors(),
        shape = libraryAccentFilterChipShape(),
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
