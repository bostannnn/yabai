package com.example.unifiedreader.feature.translation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppProgressBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppButtonSpinner

/**
 * Settings › Translation: your language, who translates (DeepL with your key, or this tablet),
 * the languages on this tablet (remove one, add one).
 * The settings page puts this in its column.
 */
@Composable
fun ColumnScope.TranslationSettingsContent(service: TranslationService) {
    LaunchedEffect(service) { service.start() }
    val state by service.state.collectAsStateWithLifecycle()

    AppSettingsGroup(label = TranslationCopy.LanguagesGroup) {
        var pickingTarget by remember { mutableStateOf(false) }
        Box {
            AppSettingsRow(
                title = TranslationCopy.TranslateInto,
                supporting = TranslationCopy.YourLanguageSupporting,
                value = TranslationLanguages.displayName(state.yourLanguage),
                onClick = { pickingTarget = true },
                showChevron = false,
            )
            TranslationLanguageMenu(
                expanded = pickingTarget,
                title = TranslationCopy.TranslateInto,
                selected = state.yourLanguage,
                state = state,
                onPick = { tag ->
                    pickingTarget = false
                    service.setTargetLanguage(tag)
                    service.download(tag)
                },
                onDismissRequest = { pickingTarget = false },
            )
        }
        AppSettingsRowDivider(inset = com.example.unifiedreader.ui.components.AppSettingsDefaults.dividerInsetNoIcon)
        val openOnSelect = state.preferences.openOnSelect
        AppSettingsRow(
            title = TranslationCopy.OpenOnSelect,
            supporting = TranslationCopy.OpenOnSelectSupporting,
            onClick = { service.setOpenOnSelect(!openOnSelect) },
            showChevron = false,
            trailing = { AppSwitch(checked = openOnSelect, onCheckedChange = null) },
        )
    }

    TranslateWithGroups(service, state)

    AppSettingsGroup(label = TranslationCopy.OnThisTablet) {
        val languages = TranslationLanguages.languages(state.downloaded + state.downloading)
        languages.forEachIndexed { index, language ->
            if (index > 0) {
                AppSettingsRowDivider(inset = com.example.unifiedreader.ui.components.AppSettingsDefaults.dividerInsetNoIcon)
            }
            val downloading = language.tag in state.downloading
            AppSettingsRow(
                title = language.name,
                supporting = when {
                    language.tag == TranslationLanguages.BUILT_IN -> TranslationCopy.BuiltIn
                    downloading -> TranslationCopy.Downloading
                    else -> null
                },
                trailing = when {
                    downloading -> { { AppButtonSpinner() } }
                    language.tag == TranslationLanguages.BUILT_IN -> null
                    else -> { { AppTextButton(onClick = { service.remove(language.tag) }) { Text(TranslationCopy.Remove) } } }
                },
            )
        }
        if (languages.isNotEmpty()) AppSettingsRowDivider()
        var adding by remember { mutableStateOf(false) }
        Box {
            AppSettingsRow(
                title = TranslationCopy.AddLanguage,
                icon = AppIcons.Add,
                onClick = { adding = true },
                showChevron = false,
            )
            TranslationLanguageMenu(
                expanded = adding,
                title = TranslationCopy.AddLanguage,
                selected = null,
                state = state,
                onPick = { tag ->
                    adding = false
                    service.download(tag)
                },
                onDismissRequest = { adding = false },
            )
        }
    }
}

/** Translate with (DeepL or this tablet) and, with a key saved, DeepL's key and this month's use. */
@Composable
private fun ColumnScope.TranslateWithGroups(service: TranslationService, state: TranslationState) {
    var editingKey by remember { mutableStateOf(false) }
    val deepL = state.deepL
    LaunchedEffect(deepL.hasKey) { if (deepL.hasKey) service.refreshDeepLUsage() }
    if (editingKey) {
        DeepLKeyDialog(service = service, hasKey = deepL.hasKey, onDismissRequest = { editingKey = false })
    }

    AppSettingsGroup(label = TranslationCopy.TranslateWith) {
        val onDeepL = state.provider == TranslationProvider.DeepL
        AppSettingsRow(
            title = TranslationCopy.DeepL,
            icon = AppIcons.Public,
            supporting = if (deepL.hasKey) TranslationCopy.DeepLSupporting else TranslationCopy.DeepLNoKeySupporting,
            selected = onDeepL,
            onClick = { if (deepL.hasKey) service.setProvider(TranslationProvider.DeepL) else editingKey = true },
            showChevron = false,
            trailing = if (onDeepL) { { Icon(AppIcons.Check, contentDescription = null) } } else null,
        )
        AppSettingsRowDivider()
        AppSettingsRow(
            title = TranslationCopy.ThisTablet,
            icon = AppIcons.TabletAndroid,
            supporting = TranslationCopy.ThisTabletSupporting,
            selected = !onDeepL,
            onClick = { service.setProvider(TranslationProvider.Device) },
            showChevron = false,
            trailing = if (!onDeepL) { { Icon(AppIcons.Check, contentDescription = null) } } else null,
        )
    }

    if (deepL.hasKey) {
        AppSettingsGroup(label = TranslationCopy.DeepL) {
            AppSettingsRow(
                title = TranslationCopy.ApiKey,
                supporting = listOfNotNull(TranslationCopy.plan(deepL.isFree), deepL.keyHint).joinToString(" · "),
                trailing = { AppTextButton(onClick = { editingKey = true }) { Text(TranslationCopy.Change) } },
            )
            deepL.usage?.takeIf { it.limit > 0 }?.let { usage ->
                AppSettingsRowDivider(inset = com.example.unifiedreader.ui.components.AppSettingsDefaults.dividerInsetNoIcon)
                AppSettingsRow(
                    title = TranslationCopy.ThisMonth,
                    value = TranslationCopy.usage(usage.used, usage.limit),
                    supportingContent = {
                        AppProgressBar(
                            fraction = (usage.used.toFloat() / usage.limit).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().padding(top = AppLayout.inset / 2),
                        )
                    },
                )
            }
        }
    }
}
