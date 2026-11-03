package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppRoundCheck
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSheetLabel
import com.example.unifiedreader.ui.components.AppTextField
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * A name for a new list, then where to keep it: on this device in your order, or on one of
 * [servers] (each connected and, from Add to list, holding every book going in) as a Kavita
 * reading list or a Grimmory shelf. Each place is a row with its line always shown, so the
 * choice reads before it is made. [subtitle] says what goes into it ("Harbor Lights #7 goes
 * into it") when it was opened from Add to list.
 */
@Composable
fun NewListDialog(
    onCreate: (name: String, server: NewListServer?) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    servers: List<NewListServer> = emptyList(),
    title: String = "New list",
    // What keeping it on this device means; a smart list's says something else than "in your order".
    onDeviceLine: String = OnThisDeviceLine,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val server = servers.firstOrNull { it.name == chosen }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = title,
        subtitle = subtitle ?: onDeviceLine.takeIf { servers.isEmpty() },
        actions = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
            AppDialogConfirmButton(
                text = "Create",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), server) },
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppLayout.gridGap)) {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (servers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(KeepItTileGap)) {
                    AppSheetLabel(text = "Keep it", horizontalInset = 0.dp)
                    KeepItRow(title = "On this device", line = onDeviceLine, selected = server == null, onClick = { chosen = null })
                    servers.forEach { option ->
                        KeepItRow(
                            title = "On ${option.name}",
                            line = option.description,
                            selected = server == option,
                            onClick = { chosen = option.name },
                        )
                    }
                }
            }
        }
    }
}

/** A place to keep the list, as a tile: the check first, the picked one filled in the selected colour. */
@Composable
private fun KeepItRow(title: String, line: String, selected: Boolean, onClick: () -> Unit) {
    AppSettingsRow(
        title = title,
        supporting = line,
        onClick = onClick,
        selected = selected,
        showChevron = false,
        leading = { AppRoundCheck(checked = selected) },
        horizontalInset = KeepItTileInset,
        modifier = Modifier
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.controlCornerRadius))
            .semantics { this.selected = selected },
    )
}

private val KeepItTileGap = 4.dp

// The check's start sits 16 in from the tile's edge (the row pulls a leading control back by its handle overhang).
private val KeepItTileInset = 24.dp

private const val OnThisDeviceLine = "Books from every source, in your order."

/** A server a new list can be kept on, and what that means: "A Grimmory shelf, also in Grimmory's web app…". */
data class NewListServer(
    val sourceType: SourceType,
    val description: String,
) {
    val name: String get() = sourceType.displayName()

    companion object {
        fun shelf(sourceType: SourceType): NewListServer = sourceType.displayName().let { name ->
            NewListServer(sourceType, "A shelf, also in $name's web app. $name books only.")
        }

        fun readingList(sourceType: SourceType): NewListServer = sourceType.displayName().let { name ->
            NewListServer(sourceType, "A reading list, also in $name's web reader. $name issues only.")
        }
    }
}
