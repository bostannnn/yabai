package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.displayName
import com.example.unifiedreader.feature.navigation.navigationIcon
import com.example.unifiedreader.ui.components.AppButton
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIconButton
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppLinearProgress
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppScreenBack
import com.example.unifiedreader.ui.components.AppScreenBackHandler
import com.example.unifiedreader.ui.components.AppScreenHost
import com.example.unifiedreader.ui.components.AppScreenTitle
import com.example.unifiedreader.ui.components.AppScreenTransition
import com.example.unifiedreader.ui.components.AppTextButton
import com.example.unifiedreader.ui.components.AppTonalButton
import com.example.unifiedreader.ui.components.AppWindowWidth
import com.example.unifiedreader.ui.components.currentAppWindowWidth
import com.example.unifiedreader.ui.components.appProgressFill

/** The first launch (Not now under the choices) or "Add a server" (a back arrow instead). */
enum class SourceStartMode {
    FirstLaunch,
    AddSource,
}

/**
 * "Where are your books?": the servers not connected yet and the files on this device. Picking a
 * server opens its connect page in place, which signs in and then shows the first sync, with the
 * library a tap away. [initialServer] opens straight on that server's page (Connect Kavita from an
 * empty library), and Back from there closes the flow.
 */
@Composable
fun SourceStartFlow(
    mode: SourceStartMode,
    servers: List<SourceSettingsEntry>,
    onImportFile: () -> Unit,
    onServerConnected: (SourceType) -> Unit,
    onOpenLibrary: (SourceType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialServer: SourceType? = null,
) {
    var page by rememberSaveable { mutableStateOf(initialServer) }
    // A server's page opened from the choices goes back to them; one opened directly closes the flow.
    val pageBack = page?.takeIf { initialServer == null }?.let { AppScreenBack<SourceType?>(target = null) { page = null } }
    AppScreenBackHandler(enabled = pageBack == null && (mode == SourceStartMode.AddSource || page != null)) { onClose() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AppScreenHost(
            screen = page,
            back = pageBack,
            transitionFor = { _, to -> if (to != null) AppScreenTransition.Forward else AppScreenTransition.Backward },
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            val entry = shown?.let { server -> servers.firstOrNull { it.sourceType == server } }
            if (entry == null) {
                SourceStartChoices(
                    mode = mode,
                    servers = servers.filterNot { it.connection.hasStoredSession },
                    onServerSelected = { server -> page = server },
                    onImportFile = onImportFile,
                    onClose = onClose,
                )
            } else {
                SourceConnectPage(
                    entry = entry,
                    canConnectAnother = servers.any { !it.connection.hasStoredSession && it.sourceType != entry.sourceType },
                    onServerConnected = onServerConnected,
                    onOpenLibrary = { onOpenLibrary(entry.sourceType) },
                    onConnectAnother = { page = null },
                    onBack = if (initialServer == null) ({ page = null }) else onClose,
                )
            }
        }
    }
}

@Composable
private fun SourceStartChoices(
    mode: SourceStartMode,
    servers: List<SourceSettingsEntry>,
    onServerSelected: (SourceType) -> Unit,
    onImportFile: () -> Unit,
    onClose: () -> Unit,
) {
    SourceStartPage(onBack = onClose.takeIf { mode == SourceStartMode.AddSource }) {
        val scheme = MaterialTheme.colorScheme
        ThemeCovers(
            card = scheme.surfaceContainerHighest,
            accent = scheme.primary,
            third = scheme.tertiary,
            frame = scheme.onTertiary.copy(alpha = 0.6f),
            coverSize = DpSize(56.dp, 84.dp),
            gap = 12.dp,
            modifier = Modifier.padding(start = 4.dp, bottom = 30.dp),
        )
        AppScreenTitle(text = "Where are your books?")
        SourceStartLead(text = "yabai reads EPUB, PDF and comics (CBZ and CBR) from your own server or from this device.")
        Spacer(modifier = Modifier.height(30.dp))
        AppPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            servers.forEachIndexed { index, entry ->
                if (index > 0) AppDivider(modifier = Modifier.padding(start = 76.dp))
                SourceStartChoice(
                    icon = entry.sourceType.navigationIcon(),
                    title = entry.sourceType.displayName(),
                    supporting = entry.sourceType.startChoiceLine(),
                    onClick = { onServerSelected(entry.sourceType) },
                )
            }
            if (servers.isNotEmpty()) AppDivider(modifier = Modifier.padding(start = 76.dp))
            SourceStartChoice(
                icon = SourceType.LOCAL.navigationIcon(),
                title = "Files on this device",
                supporting = "Import EPUB, PDF, CBZ or CBR files",
                onClick = onImportFile,
            )
        }
        if (mode == SourceStartMode.FirstLaunch) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppTextButton(onClick = onClose) {
                    Text(text = "Not now", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "You can connect more servers later in Settings.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SourceStartChoice(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = AppIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A server's connect page. Before signing in: the form. Once signed in from here, it starts the
 * first sync and shows it filling, with Open library ready at once, since books appear as they
 * arrive.
 */
@Composable
private fun SourceConnectPage(
    entry: SourceSettingsEntry,
    canConnectAnother: Boolean,
    onServerConnected: (SourceType) -> Unit,
    onOpenLibrary: () -> Unit,
    onConnectAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val connection = entry.connection
    val name = entry.sourceType.displayName()
    var signingIn by rememberSaveable(entry.sourceType) { mutableStateOf(false) }
    LaunchedEffect(signingIn, connection.hasStoredSession, connection.isConnecting) {
        if (signingIn && connection.hasStoredSession && !connection.isConnecting) {
            signingIn = false
            onServerConnected(entry.sourceType)
            entry.actions.onSync()
        }
    }
    SourceStartPage(onBack = onBack) {
        if (!connection.hasStoredSession) {
            AppScreenTitle(text = "Connect $name")
            SourceStartLead(text = "Use the address you open $name at in a browser, and your $name account.")
            Spacer(modifier = Modifier.height(24.dp))
            AppPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                ServerConnectionForm(
                    state = connection.toFormState(),
                    onDisplayNameChanged = entry.actions.onDisplayNameChanged,
                    onBaseUrlChanged = entry.actions.onBaseUrlChanged,
                    onBackupBaseUrlChanged = entry.actions.onBackupBaseUrlChanged,
                    onUsernameChanged = entry.actions.onUsernameChanged,
                    onPasswordChanged = entry.actions.onPasswordChanged,
                    onConnect = {
                        signingIn = true
                        entry.actions.onConnect()
                    },
                    showDisplayName = false,
                    fullWidthConnect = true,
                )
            }
            Text(
                text = "The backup address is tried when the first one doesn't answer, such as a VPN address for when you are away from home.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp),
            )
            return@SourceStartPage
        }
        AppScreenTitle(text = name)
        Spacer(modifier = Modifier.height(12.dp))
        ServerStatus(entry = entry)
        Spacer(modifier = Modifier.height(28.dp))
        AppPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                val sync = firstSyncState(connection, entry.lastSyncedAtEpochMillis)
                Text(text = sync.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.SemiBold)
                if (sync.running) {
                    val fraction = connection.syncProgressFraction
                    val barModifier = Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                    if (fraction != null) {
                        Box(
                            modifier = barModifier.appProgressFill(
                                fraction = fraction,
                                progressColor = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                    } else {
                        AppLinearProgress(modifier = barModifier, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    }
                }
                sync.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = if (sync.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = "Books appear as they arrive, so you can start reading now. $name opens first when you start the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (sync.failed) {
                    AppTonalButton(onClick = entry.actions.onSync, modifier = Modifier.padding(top = 12.dp)) {
                        Text(text = "Try again", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppButton(onClick = onOpenLibrary, modifier = Modifier.weight(1f).height(46.dp)) {
                Text(text = "Open library", fontWeight = FontWeight.SemiBold)
            }
            if (canConnectAnother) {
                AppTonalButton(onClick = onConnectAnother, modifier = Modifier.height(46.dp)) {
                    Text(text = "Connect another", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** A page of the flow: a centred column (the full width on phones) under an optional back arrow. */
@Composable
private fun SourceStartPage(
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val compact = currentAppWindowWidth() == AppWindowWidth.Compact
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 16.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
            if (onBack != null) {
                AppIconButton(onClick = onBack) {
                    Icon(imageVector = AppIcons.ArrowBack, contentDescription = "Back")
                }
            }
        }
        Column(
            modifier = Modifier
                .widthIn(max = SourceStartColumnMaxWidth)
                .fillMaxWidth()
                .padding(top = if (compact) 16.dp else 56.dp, bottom = 32.dp),
            content = content,
        )
    }
}

@Composable
private fun SourceStartLead(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

private fun SourceType.startChoiceLine(): String = when (this) {
    SourceType.KAVITA -> "Manga, comics and books from a Kavita server"
    SourceType.STUMP -> "Comics, manga and books from a Stump server"
    SourceType.GRIMMORY -> "Books from a Grimmory server"
    SourceType.LOCAL -> "Import EPUB, PDF, CBZ or CBR files"
}

private val SourceStartColumnMaxWidth = 560.dp
