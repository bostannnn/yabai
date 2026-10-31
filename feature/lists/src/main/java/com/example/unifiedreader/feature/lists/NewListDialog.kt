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
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppLayout
import com.example.unifiedreader.ui.components.AppSegment
import com.example.unifiedreader.ui.components.AppSegmentedRow
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField

/**
 * A name for a new list, kept on this device in Your order, or, when [serverName] is given (its
 * server is connected and every book going in is from it), as a shelf on that server. [subtitle]
 * says what goes into it ("Harbor Lights #7 goes into it") when it was opened from Add to list.
 */
@Composable
fun NewListDialog(
    onCreate: (name: String, onServer: Boolean) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    serverName: String? = null,
    // Import a reading order (.cbl) instead; null where importing isn't offered.
    onImport: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var onServer by rememberSaveable { mutableStateOf(false) }
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = "New list",
        subtitle = subtitle ?: "In your order, on this tablet. You can mix books from every source.".takeIf { serverName == null },
        actions = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
            AppDialogConfirmButton(
                text = "Create",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim(), serverName != null && onServer) },
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
            if (serverName != null) {
                AppSegmentedRow(modifier = Modifier.fillMaxWidth()) {
                    AppSegment(selected = !onServer, onClick = { onServer = false }, label = { Text("On this device") })
                    AppSegment(selected = onServer, onClick = { onServer = true }, label = { Text("On $serverName") })
                }
                Text(
                    text = if (onServer) {
                        "A $serverName shelf, also in $serverName's web app. $serverName books only, in no set order."
                    } else {
                        "Books from any source, mixed, in your order. Stays on this tablet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onImport != null && !onServer) {
                AppTextButton(onClick = onImport) {
                    Text(text = "Import a reading order (.cbl)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
