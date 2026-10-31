package com.example.unifiedreader.feature.lists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.unifiedreader.ui.components.AppDialogCard
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppTextField

/**
 * A name for a new list, kept on this device. It starts in Your order; [subtitle] says what goes
 * into it ("Harbor Lights #7 goes into it") when it was opened from Add to list.
 */
@Composable
fun NewListDialog(
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    var name by rememberSaveable { mutableStateOf("") }
    AppDialogCard(
        onDismissRequest = onDismiss,
        title = "New list",
        subtitle = subtitle ?: "In your order, on this tablet. You can mix books from every source.",
        actions = {
            AppDialogDismissButton(text = "Cancel", onClick = onDismiss)
            AppDialogConfirmButton(
                text = "Create",
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            )
        },
    ) {
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
