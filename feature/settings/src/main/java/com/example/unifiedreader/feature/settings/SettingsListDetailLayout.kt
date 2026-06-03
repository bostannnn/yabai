package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun SettingsListDetailLayout(
    sections: List<SettingsSectionDescriptor>,
    selectedSectionId: SettingsSectionId,
    onSectionSelected: (SettingsSectionId) -> Unit,
    header: @Composable () -> Unit,
    detailContent: @Composable (SettingsSectionId) -> Unit,
) {
    val presentation = UnifiedReaderThemeTokens.presentation
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = SettingsMaxContentWidth)
                .padding(horizontal = SettingsScreenHorizontalPaddingDp.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(SettingsListDetailPaneSpacingDp.dp),
        ) {
            SettingsSectionListPane(
                sections = sections,
                selectedSectionId = selectedSectionId,
                onSectionSelected = onSectionSelected,
                modifier = Modifier
                    .width(SettingsListDetailSidebarWidthDp.dp)
                    .fillMaxHeight(),
            )
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(presentation.cardCornerRadius),
                color = MaterialTheme.colorScheme.surface,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = presentation.shelfCardHorizontalPadding + 6.dp,
                        vertical = presentation.shelfCardVerticalPadding + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing + 4.dp),
                ) {
                    item {
                        header()
                    }
                    item {
                        detailContent(selectedSectionId)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionListPane(
    sections: List<SettingsSectionDescriptor>,
    selectedSectionId: SettingsSectionId,
    onSectionSelected: (SettingsSectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val semanticColors = UnifiedReaderThemeTokens.colors
    val presentation = UnifiedReaderThemeTokens.presentation
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(presentation.cardCornerRadius),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = presentation.shelfCardHorizontalPadding + 2.dp,
                vertical = presentation.shelfCardVerticalPadding + 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
        ) {
            Text(
                text = "Sections",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            sections.forEach { section ->
                val selected = section.id == selectedSectionId
                val itemColor = if (selected) {
                    semanticColors.surfaceSelected
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val textColor = if (selected) {
                    semanticColors.onSurfaceSelected
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(presentation.controlCornerRadius))
                        .background(itemColor)
                        .clickable { onSectionSelected(section.id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                    )
                }
            }
        }
    }
}
