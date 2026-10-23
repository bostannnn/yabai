package com.example.unifiedreader.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unifiedreader.ui.components.AppDivider
import com.example.unifiedreader.ui.components.AppIcons
import com.example.unifiedreader.ui.components.AppPanel
import com.example.unifiedreader.ui.components.AppSettingsGroup
import com.example.unifiedreader.ui.components.AppSettingsRow
import com.example.unifiedreader.ui.components.AppSettingsRowDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ThirdPartyNoticesAssetName = "THIRD_PARTY_NOTICES.md"

/** The bundled components, one row each; a row opens that component's notice (Settings › Licenses › one). */
@Composable
internal fun LicensesSettingsPage(
    onOpenNotice: (String) -> Unit,
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val notices = rememberThirdPartyNotices()
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        SettingsPageLine("yabai is built with these. Tap one to read its license.")
        notices?.let { loaded ->
            AppSettingsGroup {
                loaded.components.forEachIndexed { index, component ->
                    if (index > 0) AppSettingsRowDivider()
                    AppSettingsRow(
                        title = component.name,
                        icon = AppIcons.Description,
                        supporting = component.about,
                        onClick = { onOpenNotice(component.name) },
                    )
                }
            }
        }
    }
}

/** One component's notice: what it is and how it is used, then its license's full text when bundled. */
@Composable
internal fun LicenseNoticePage(
    name: String,
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val notices = rememberThirdPartyNotices()
    val component = notices?.components?.firstOrNull { it.name == name }
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        if (notices == null) return@SettingsPageContainer
        AppPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val blocks = component?.let { found ->
                    found.paragraphs.map(NoticeBlock::Paragraph) +
                        if (found.includesFullText) listOf(NoticeBlock.Rule) + notices.fullText else emptyList()
                } ?: listOf(NoticeBlock.Paragraph("This notice is not bundled in this build."))
                blocks.forEach { block ->
                    when (block) {
                        is NoticeBlock.Heading -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        is NoticeBlock.Paragraph -> Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        NoticeBlock.Rule -> AppDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The notices file, read once from the module's assets; null while it loads. */
@Composable
private fun rememberThirdPartyNotices(): ThirdPartyNotices? {
    val context = LocalContext.current
    var notices by remember { mutableStateOf<ThirdPartyNotices?>(null) }
    LaunchedEffect(Unit) {
        notices = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(ThirdPartyNoticesAssetName).bufferedReader().use { it.readText() }
            }.map(::parseThirdPartyNotices).getOrElse { ThirdPartyNotices(emptyList(), emptyList()) }
        }
    }
    return notices
}

/** One bundled component: "Manrope" about "UI typeface", with the paragraphs under its heading. */
internal data class NoticeComponent(
    val name: String,
    val about: String?,
    val paragraphs: List<String>,
    /** Its paragraph says the full license text follows the components. */
    val includesFullText: Boolean,
)

internal data class ThirdPartyNotices(
    val components: List<NoticeComponent>,
    /** Everything after the components' list: the license texts. */
    val fullText: List<NoticeBlock>,
)

/**
 * The components are the headings before the first rule ("## Manrope (UI typeface)", the part in
 * brackets saying what it is), each with its paragraphs; what follows the rule is license text.
 */
internal fun parseThirdPartyNotices(markdown: String): ThirdPartyNotices {
    val blocks = parseNoticeBlocks(markdown)
    val ruleIndex = blocks.indexOf(NoticeBlock.Rule).takeIf { it >= 0 } ?: blocks.size
    val components = mutableListOf<NoticeComponent>()
    blocks.take(ruleIndex).forEach { block ->
        when (block) {
            is NoticeBlock.Heading -> {
                val heading = block.text
                val about = heading.substringAfter("(", "").substringBefore(")").trim().takeIf(String::isNotBlank)
                components += NoticeComponent(
                    name = heading.substringBefore("(").trim(),
                    about = about?.replaceFirstChar(Char::uppercase),
                    paragraphs = emptyList(),
                    includesFullText = false,
                )
            }
            is NoticeBlock.Paragraph -> components.lastOrNull()?.let { last ->
                components[components.lastIndex] = last.copy(
                    paragraphs = last.paragraphs + block.text,
                    includesFullText = last.includesFullText || block.text.contains("full text follows", ignoreCase = true),
                )
            }
            NoticeBlock.Rule -> Unit
        }
    }
    return ThirdPartyNotices(components = components, fullText = blocks.drop(ruleIndex + 1))
}

internal sealed interface NoticeBlock {
    data class Heading(val text: String) : NoticeBlock
    data class Paragraph(val text: String) : NoticeBlock
    data object Rule : NoticeBlock
}

/**
 * The little markdown the notices file uses: `#` headings, `---` rules, and paragraphs
 * separated by blank lines. The top-level title is dropped (the page already has one), and
 * hard-wrapped lines are joined so paragraphs reflow to the panel width.
 */
internal fun parseNoticeBlocks(markdown: String): List<NoticeBlock> {
    val blocks = mutableListOf<NoticeBlock>()
    val paragraph = StringBuilder()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += NoticeBlock.Paragraph(paragraph.toString())
            paragraph.clear()
        }
    }
    markdown.lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isEmpty() -> flushParagraph()
            line.startsWith("# ") -> flushParagraph()
            line.startsWith("#") -> {
                flushParagraph()
                blocks += NoticeBlock.Heading(line.trimStart('#').trim())
            }
            line.all { it == '-' } && line.length >= 3 -> {
                flushParagraph()
                if (blocks.lastOrNull() != NoticeBlock.Rule) blocks += NoticeBlock.Rule
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line.replace('`', '\''))
            }
        }
    }
    flushParagraph()
    return blocks
}
