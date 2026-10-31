package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField

/**
 * A name for a new list, kept on this device in Your order, or on one of [servers] (each connected
 * and, from Add to list, holding every book going in): a Kavita reading list or a Grimmory shelf.
 * [subtitle] says what goes into it ("Harbor Lights #7 goes into it") when it was opened from Add to list.
 */
@Composable
fun NewListDialog(
    onCreate: (name: String, server: NewListServer?) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    servers: List<NewListServer> = emptyList(),
    // Import a reading order (.cbl) instead; null where importing isn't offered.
    onImport: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val server = servers.firstOrNull { it.name == chosen }
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = "New list",
        subtitle = subtitle ?: "In your order, on this tablet. You can mix books from every source.".takeIf { servers.isEmpty() },
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
                modifier = Modifier.fillMaxWidth(),
            )
            if (servers.isNotEmpty()) {
                AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
                    AppSegment(selected = server == null, onClick = { chosen = null }, label = { Text("On this device") })
                    servers.forEach { option ->
                        AppSegment(selected = server == option, onClick = { chosen = option.name }, label = { Text("On ${option.name}") })
                    }
                }
                Text(
                    text = server?.description ?: "Books from any source, mixed, in your order. Stays on this tablet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onImport != null && server == null) {
                AppTextButton(onClick = onImport) {
                    Text(text = "Import a reading order (.cbl)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A server a new list can be kept on, and what that means: "A Grimmory shelf, also in Grimmory's web app…". */
data class NewListServer(
    val sourceType: SourceType,
    val description: String,
) {
    val name: String get() = sourceType.displayName()

    companion object {
        fun shelf(sourceType: SourceType): NewListServer = sourceType.displayName().let { name ->
            NewListServer(sourceType, "A $name shelf, also in $name's web app. $name books only, in no set order.")
        }

        fun readingList(sourceType: SourceType): NewListServer = sourceType.displayName().let { name ->
            NewListServer(sourceType, "A $name reading list, in its order, also in $name's web reader. $name issues only.")
        }
    }
}
