package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

@Composable
internal fun SettingsCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val presentation = UnifiedReaderThemeTokens.presentation
    AppPanel(
        modifier = modifier,
        shape = RoundedCornerShape(presentation.cardCornerRadius),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = presentation.shelfCardHorizontalPadding + 6.dp,
                vertical = presentation.shelfCardVerticalPadding + 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(presentation.shelfItemSpacing),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
