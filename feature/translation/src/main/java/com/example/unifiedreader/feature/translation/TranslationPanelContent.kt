package com.example.unifiedreader.feature.translation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppButtonDefaults
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLinearProgress
import com.example.unifiedreader.ui.components.AppMenuChoiceItem
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.appSkeletonPulse

/** What the card and the sheet do with your taps. */
internal class TranslationPanelActions(
    val onPickFrom: (String?) -> Unit,
    val onPickTo: (String) -> Unit,
    val onSwap: () -> Unit,
    val onDownload: () -> Unit,
    val onRetry: () -> Unit,
    val onClose: () -> Unit,
    val onOpenFull: (() -> Unit)?,
)

/** From, the swap, To, and on the card a close button: the row both surfaces start with. */
@Composable
internal fun TranslationLanguageRow(
    translation: TextSelectionTranslation,
    state: TranslationState,
    actions: TranslationPanelActions,
    showClose: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LanguagePill(
            name = TranslationLanguages.displayName(translation.from),
            menuTitle = TranslationCopy.TranslateFrom,
            selected = translation.from,
            state = state,
            onPick = actions.onPickFrom,
            extraFirst = { close ->
                AppMenuChoiceItem(
                    text = TranslationCopy.TheBooksLanguage,
                    selected = false,
                    onClick = {
                        close()
                        actions.onPickFrom(null)
                    },
                )
            },
        )
        AppIconButton(onClick = actions.onSwap, enabled = translation.to != null) {
            Icon(AppIcons.SwapHoriz, contentDescription = TranslationCopy.Swap)
        }
        LanguagePill(
            name = translation.to?.let(TranslationLanguages::displayName) ?: TranslationCopy.To,
            menuTitle = TranslationCopy.TranslateInto,
            selected = translation.to,
            state = state,
            onPick = { tag -> tag?.let(actions.onPickTo) },
            emphasised = translation.to == null,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showClose) {
            AppIconButton(onClick = actions.onClose) {
                Icon(AppIcons.Close, contentDescription = TranslationCopy.Close)
            }
        }
    }
}

@Composable
private fun LanguagePill(
    name: String,
    menuTitle: String,
    selected: String?,
    state: TranslationState,
    onPick: (String?) -> Unit,
    emphasised: Boolean = false,
    extraFirst: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        val colors = if (emphasised) {
            AppButtonDefaults.tonalColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            AppButtonDefaults.tonalColors()
        }
        AppTonalButton(onClick = { open = true }, colors = colors) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(AppIcons.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        TranslationLanguageMenu(
            expanded = open,
            title = menuTitle,
            selected = selected,
            state = state,
            onPick = { tag ->
                open = false
                onPick(tag)
            },
            onDismissRequest = { open = false },
            extraFirst = extraFirst?.let { extra -> { extra { open = false } } },
        )
    }
}

/** The middle of the card or the sheet: what this moment of the translation shows. */
@Composable
internal fun TranslationPhaseBody(
    translation: TextSelectionTranslation,
    asSheet: Boolean,
    actions: TranslationPanelActions,
) {
    val selection = translation.selection
    when (val phase = translation.phase) {
        TranslationPhase.PickLanguage -> BodyText(TranslationCopy.PickTo)

        is TranslationPhase.NeedsDownload -> {
            val names = phase.languages.map(TranslationLanguages::displayName)
            Title(TranslationCopy.downloadTitle(names))
            BodyText(TranslationCopy.downloadBody(names.size))
            ButtonRow {
                AppTextButton(onClick = actions.onClose) { Text(TranslationCopy.NotNow) }
                AppButton(onClick = actions.onDownload) { Text(TranslationCopy.Download) }
            }
        }

        is TranslationPhase.Downloading -> {
            Title(TranslationCopy.gettingReady(phase.languages.map(TranslationLanguages::displayName)))
            AppLinearProgress(modifier = Modifier.fillMaxWidth())
            BodyText(TranslationCopy.KeepReading)
            ButtonRow { AppTextButton(onClick = actions.onClose) { Text(TranslationCopy.Hide) } }
        }

        TranslationPhase.Translating -> {
            if (asSheet) SelectedText(selection.text) else Caption(selection.text.trim())
            SkeletonLine(fraction = 0.6f, height = 26)
            SkeletonLine(fraction = 0.9f, height = 14)
            SkeletonLine(fraction = 0.7f, height = 14)
        }

        is TranslationPhase.Done -> if (asSheet && !selection.isShort) {
            SelectedText(selection.text)
            AppDivider()
            Caption(TranslationLanguages.displayName(checkNotNull(translation.to)))
            Text(phase.translation, style = MaterialTheme.typography.bodyLarge)
        } else {
            Caption(selection.text.trim())
            Text(
                phase.translation,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            phase.sentence?.let { sentence ->
                AppDivider()
                Caption(TranslationCopy.InThisSentence)
                if (asSheet) Caption(selection.sentence.orEmpty())
                Text(sentence, style = MaterialTheme.typography.bodyLarge)
            }
        }

        is TranslationPhase.Failed -> {
            Title(TranslationCopy.CouldntTranslate)
            BodyText(
                if (phase.reason == TranslationFailure.Download) TranslationCopy.DownloadFailed else TranslationCopy.TranslateFailed,
            )
            ButtonRow {
                AppTextButton(onClick = actions.onClose) { Text(TranslationCopy.Close) }
                AppButton(onClick = actions.onRetry) { Text(TranslationCopy.TryAgain) }
            }
        }
    }
}

/** "Translated on this device" with Copy and, on the card, Open full. */
@Composable
internal fun TranslationCardFooter(translation: TextSelectionTranslation, actions: TranslationPanelActions) {
    val done = translation.phase as? TranslationPhase.Done ?: return
    val clipboard = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OnThisDevice(modifier = Modifier.weight(1f))
        AppTextButton(onClick = { clipboard.setText(AnnotatedString(done.translation)) }) { Text(TranslationCopy.Copy) }
        actions.onOpenFull?.let { open -> AppTextButton(onClick = open) { Text(TranslationCopy.OpenFull) } }
    }
}

@Composable
internal fun TranslationSheetFooter(translation: TextSelectionTranslation) {
    val done = translation.phase as? TranslationPhase.Done
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OnThisDevice()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                onClick = { done?.let { clipboard.setText(AnnotatedString(it.translation)) } },
                enabled = done != null,
                modifier = Modifier.weight(1f),
            ) { Text(TranslationCopy.CopyTranslation) }
            AppTonalButton(
                onClick = { context.openInAnotherApp(translation.selection.text) },
                modifier = Modifier.weight(1f),
            ) { Text(TranslationCopy.OpenIn) }
        }
    }
}

/** Hands the text to any app that translates selected text (Google Translate, the system's own). */
internal fun Context.openInAnotherApp(text: String) {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    runCatching { startActivity(Intent.createChooser(intent, TranslationCopy.OpenIn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun OnThisDevice(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconSmall(AppIcons.TabletAndroid)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            TranslationCopy.OnThisDevice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconSmall(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SelectedText(text: String) {
    Caption(TranslationCopy.Selected)
    Text(text.trim(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ButtonRow(content: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { content() }
}

@Composable
private fun SkeletonLine(fraction: Float, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height.dp)
            .clip(MaterialTheme.shapes.small)
            .appSkeletonPulse()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}
