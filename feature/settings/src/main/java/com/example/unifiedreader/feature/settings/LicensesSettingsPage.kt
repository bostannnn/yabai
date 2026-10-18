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
import com.example.unifiedreader.ui.components.AppPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val ThirdPartyNoticesAssetName = "THIRD_PARTY_NOTICES.md"

/** The bundled third-party notices, read from the module's assets and shown as simple blocks. */
@Composable
internal fun LicensesSettingsPage(
    contentTopInset: Dp,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var blocks by remember { mutableStateOf<List<NoticeBlock>?>(null) }
    LaunchedEffect(Unit) {
        blocks = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(ThirdPartyNoticesAssetName).bufferedReader().use { it.readText() }
            }.map(::parseNoticeBlocks).getOrElse {
                listOf(NoticeBlock.Paragraph("The notices file is not bundled in this build."))
            }
        }
    }
    SettingsPageContainer(
        contentTopInset = contentTopInset,
        modifier = modifier,
        horizontalPadding = horizontalPadding,
    ) {
        AppPanel(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                blocks?.forEach { block ->
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
