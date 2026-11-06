package com.example.unifiedreader.feature.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButtonSpinner
import com.example.unifiedreader.ui.components.AppDialog
import com.example.unifiedreader.ui.components.AppDialogConfirmButton
import com.example.unifiedreader.ui.components.AppDialogDismissButton
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTextField
import kotlinx.coroutines.launch

/**
 * Adding or changing your DeepL key. Check and save asks DeepL first: a refused key says so
 * under the field and nothing is saved. With a key saved already, Remove key is offered too.
 */
@Composable
fun DeepLKeyDialog(
    service: TranslationService,
    hasKey: Boolean,
    onDismissRequest: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<DeepLFailure?>(null) }
    val scope = rememberCoroutineScope()
    val save = {
        if (key.isNotBlank() && !checking) {
            checking = true
            problem = null
            scope.launch {
                val failure = service.saveDeepLKey(key)
                checking = false
                if (failure == null) onDismissRequest() else problem = failure
            }
        }
    }
    AppDialog(
        onDismissRequest = { if (!checking) onDismissRequest() },
        title = { Text(TranslationCopy.DeepLDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(TranslationCopy.DeepLDialogBody, style = MaterialTheme.typography.bodyMedium)
                AppTextField(
                    value = key,
                    onValueChange = { value ->
                        key = value
                        problem = null
                    },
                    label = { Text(TranslationCopy.ApiKey) },
                    singleLine = true,
                    isError = problem != null,
                    enabled = !checking,
                    supportingText = { Text(problem?.let(TranslationCopy::keyProblem) ?: TranslationCopy.DeepLKeyWhere) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    TranslationCopy.DeepLPrivacy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasKey) {
                    AppTextButton(
                        onClick = {
                            service.removeDeepLKey()
                            onDismissRequest()
                        },
                        enabled = !checking,
                    ) { Text(TranslationCopy.RemoveKey) }
                }
            }
        },
        dismissButton = { AppDialogDismissButton(TranslationCopy.Cancel, onClick = onDismissRequest, enabled = !checking) },
        confirmButton = {
            if (checking) {
                AppButtonSpinner()
            } else {
                AppDialogConfirmButton(TranslationCopy.CheckAndSave, onClick = save, enabled = key.isNotBlank())
            }
        },
    )
}
