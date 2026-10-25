package com.example.unifiedreader.ui.components

import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame from the design system (`docs/design/system/project/layout.md`): one gutter per window
 * class, and every screen's first content at the same height under the top bar.
 */
class AppLayoutRuleTest {

    @Test
    fun `the gutter is 16 on phones and 24 from 600 dp`() {
        assertEquals(16.dp, AppLayout.gutter(AppWindowWidth.Compact))
        assertEquals(24.dp, AppLayout.gutter(AppWindowWidth.Medium))
        assertEquals(24.dp, AppLayout.gutter(AppWindowWidth.Expanded))
    }

    @Test
    fun `side sheets share one width`() {
        assertEquals(AppLayout.sheetWidth, AppSideSheetDefaults.width)
    }

    @Test
    fun `screens start their content at the kit's content top, not at a top of their own`() {
        val root = repositoryRoot()
        val ownTop = Regex("""(contentTopInset|contentInset)\s*\+\s*[0-9.]+\.dp|[0-9.]+\.dp\s*\+\s*(contentTopInset|contentInset)""")
        val sourceRoots = listOf(root.resolve("app/src/main")) +
            (root.resolve("feature").listFiles { file -> file.isDirectory }?.map { it.resolve("src/main") } ?: emptyList())
        val violations = sourceRoots
            .filter { it.isDirectory }
            .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    "${file.relativeTo(root)}:${index + 1}: ${line.trim()}".takeIf { ownTop.containsMatchIn(line) }
                }
            }
        assertTrue(
            "Start a screen's content at contentTopInset + AppLayout.contentTop:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun repositoryRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !dir.resolve("settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "settings.gradle.kts not found above ${System.getProperty("user.dir")}" }
    }
}
