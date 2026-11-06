package com.example.unifiedreader.feature.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
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
    account: DeepLAccount,
    hasKey: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
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
                val failure = account.saveKey(key)
                checking = false
                if (failure == null) onDismissRequest() else problem = failure
            }
        }
    }
    AppDialog(
        onDismissRequest = { if (!checking) onDismissRequest() },
        modifier = modifier,
        title = { Text(TranslationCopy.DeepLDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(TranslationCopy.DeepLDialogBody, style = MaterialTheme.typography.bodyMedium)
                DeepLKeyField(
                    key = key,
                    onKeyChange = { value ->
                        key = value
                        problem = null
                    },
                    problem = problem,
                    enabled = !checking,
                    onDone = { save() },
                )
                Text(
                    TranslationCopy.DeepLPrivacy,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasKey) {
                    AppTextButton(
                        onClick = {
                            account.removeKey()
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

/** The key, with where to find it underneath, or why DeepL refused it. */
@Composable
private fun DeepLKeyField(
    key: String,
    onKeyChange: (String) -> Unit,
    problem: DeepLFailure?,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    AppTextField(
        value = key,
        onValueChange = onKeyChange,
        label = { Text(TranslationCopy.ApiKey) },
        singleLine = true,
        isError = problem != null,
        enabled = enabled,
        supportingText = { Text(problem?.let(TranslationCopy::keyProblem) ?: TranslationCopy.DeepLKeyWhere) },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
