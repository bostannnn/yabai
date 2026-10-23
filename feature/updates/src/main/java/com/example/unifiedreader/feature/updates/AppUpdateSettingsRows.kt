package com.example.unifiedreader.feature.updates

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSwitch
import com.example.unifiedreader.ui.components.AppTonalButton

/**
 * Settings › About › Version: the installed version, what the last check found, and the one
 * button for the next step (Check, Update, Cancel, Install, Try again). The button sits under the
 * text, not beside it: the settings list is a ~350 dp column on tablets, and a trailing button
 * left the status a few letters wide.
 */
@Composable
fun AppUpdateVersionRow(
    versionName: String,
    state: AppUpdateState,
    actions: AppUpdateActions,
    modifier: Modifier = Modifier,
) {
    val lastCheckedLabel = state.lastCheckAtMillis?.let { checkedAt ->
        remember(checkedAt, state.status) { lastCheckedLabel(checkedAt, System.currentTimeMillis()) }
    }
    val copy = versionRowCopy(
        versionName = versionName.ifEmpty { state.currentVersion },
        state = state,
        lastCheckedLabel = lastCheckedLabel,
    )
    AppSettingsRow(
        title = "Version",
        icon = AppIcons.Info,
        supporting = copy.supporting,
        supportingColor = when (copy.tone) {
            AppUpdateTone.Normal -> MaterialTheme.colorScheme.onSurfaceVariant
            AppUpdateTone.Accent -> MaterialTheme.colorScheme.primary
            AppUpdateTone.Error -> MaterialTheme.colorScheme.error
        },
        supportingIcon = if (copy.showsCheck) AppIcons.Check else null,
        supportingContent = if (copy.progress == null && copy.button == null) {
            null
        } else {
            {
                copy.progress?.let { fraction ->
                    UpdateProgressBar(
                        fraction = fraction,
                        height = 4,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .widthIn(max = 320.dp),
                    )
                }
                copy.button?.let { button ->
                    Box(modifier = Modifier.padding(top = 10.dp)) {
                        RowButton(button = button, onClick = { actions.perform(button.action, state) })
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/** Settings › About: the switch for the once-a-day check. */
@Composable
fun AppUpdateAutoCheckRow(
    state: AppUpdateState,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSettingsRow(
        title = "Check for updates automatically",
        icon = AppIcons.History,
        supporting = if (state.isDebugBuild) {
            "Off on debug builds; the button above still checks"
        } else {
            "Once a day, when yabai opens"
        },
        onClick = { onCheckedChange(!state.autoCheck) },
        enabled = !state.isDebugBuild,
        showChevron = false,
        modifier = modifier,
        trailing = {
            AppSwitch(
                checked = state.autoCheck && !state.isDebugBuild,
                onCheckedChange = null,
                enabled = !state.isDebugBuild,
            )
        },
    )
}

@Composable
private fun RowButton(
    button: AppUpdateButton,
    onClick: () -> Unit,
) {
    val modifier = Modifier.height(40.dp)
    val enabled = !button.busy
    if (button.filled) {
        AppButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(text = button.label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        AppTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            if (button.busy) ButtonSpinner()
            Text(
                text = button.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = if (button.busy) Modifier.padding(start = 8.dp) else Modifier,
            )
        }
    }
}

/** "2 hours ago", "yesterday", or "just now" inside the first minute. */
internal fun lastCheckedLabel(checkedAtMillis: Long, nowMillis: Long): String =
    if (nowMillis - checkedAtMillis < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(checkedAtMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS)
            .toString()
            .replaceFirstChar { it.lowercase() }
    }
