package com.example.unifiedreader.feature.translation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppMenu
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppMenuHeading
import com.example.unifiedreader.ui.components.AppMenuItem
import com.example.unifiedreader.ui.components.AppSearchField

/**
 * Every language, in two groups: the ones on this tablet, then the ones to download. A search
 * field narrows both. [extraFirst] is an optional row above them ("The book's language").
 */
@Composable
internal fun TranslationLanguageMenu(
    expanded: Boolean,
    title: String,
    selected: String?,
    state: TranslationState,
    onPick: (String) -> Unit,
    onDismissRequest: () -> Unit,
    extraFirst: (@Composable () -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    AppMenu(
        expanded = expanded,
        onDismissRequest = {
            query = ""
            onDismissRequest()
        },
        modifier = Modifier.width(TranslationMenuWidth),
    ) {
        AppMenuHeading(title)
        Box(modifier = Modifier.padding(horizontal = AppLayout.menuInset, vertical = AppLayout.headingGap)) {
            AppSearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = TranslationCopy.FindLanguage,
            )
        }
        extraFirst?.invoke()
        val languages = TranslationLanguages.languages(state.supported)
            .filter { language -> query.isBlank() || language.name.contains(query.trim(), ignoreCase = true) }
        val (onTablet, toDownload) = languages.partition { it.tag in state.downloaded }
        if (onTablet.isNotEmpty()) AppMenuHeading(TranslationCopy.OnThisTablet)
        onTablet.forEach { language ->
            AppMenuChoiceItem(
                text = language.name,
                selected = language.tag == selected,
                onClick = {
                    query = ""
                    onPick(language.tag)
                },
            )
        }
        if (toDownload.isNotEmpty()) AppMenuHeading(TranslationCopy.DownloadHeading)
        toDownload.forEach { language ->
            AppMenuItem(
                text = { Text(language.name) },
                supporting = if (language.tag in state.downloading) TranslationCopy.Downloading else null,
                onClick = {
                    query = ""
                    onPick(language.tag)
                },
                trailingIcon = {
                    Icon(
                        imageVector = AppIcons.Download,
                        contentDescription = TranslationCopy.downloadLanguage(language.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

private val TranslationMenuWidth = 320.dp
