package com.example.unifiedreader.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feature and app code builds screens from the `core:ui-components` kit, not from raw
 * Material components. This keeps every screen on one set of shapes, colors, and
 * paddings. Material primitives that the kit does not cover (`Scaffold`,
 * `ModalNavigationDrawer`, navigation rail) stay allowed: they are structural and carry no
 * styling of their own.
 */
class FeatureMaterialImportRuleTest {

    private val forbiddenMaterialSymbols = setOf(
        "Card", "ElevatedCard", "OutlinedCard", "Surface",
        "AlertDialog", "BasicAlertDialog", "ModalBottomSheet",
        "TopAppBar", "CenterAlignedTopAppBar", "MediumTopAppBar", "LargeTopAppBar",
        "FilterChip", "AssistChip", "InputChip", "SuggestionChip", "ElevatedFilterChip",
        "ElevatedAssistChip", "ElevatedSuggestionChip",
        "Button", "OutlinedButton", "TextButton", "FilledTonalButton", "ElevatedButton",
        "IconButton", "FilledIconButton", "FilledTonalIconButton", "OutlinedIconButton",
        "HorizontalDivider", "VerticalDivider",
        "DropdownMenu", "DropdownMenuItem",
        "SingleChoiceSegmentedButtonRow", "MultiChoiceSegmentedButtonRow", "SegmentedButton",
        "LinearProgressIndicator", "CircularProgressIndicator",
        "OutlinedTextField", "TextField", "ListItem",
        "FloatingActionButton", "ExtendedFloatingActionButton", "SmallFloatingActionButton",
        "LargeFloatingActionButton",
        "Checkbox", "TriStateCheckbox", "Switch", "Slider", "RangeSlider", "RadioButton",
        "Snackbar", "SnackbarHost", "NavigationDrawerItem", "NavigationDrawerItemDefaults",
        "SegmentedButtonDefaults", "SliderDefaults", "CheckboxDefaults", "SwitchDefaults",
    )


    private val forbiddenImports: Set<String> =
        forbiddenMaterialSymbols.map { "androidx.compose.material3.$it" }.toSet() +
            setOf("androidx.compose.ui.window.Dialog", "androidx.compose.material3.*")

    /**
     * Stock shapes and marks the kit replaced (pill controls, segments without a checkmark),
     * caught as references because `ButtonDefaults` itself stays importable for its paddings.
     */
    private val forbiddenReferences = listOf(
        "ButtonDefaults.shape", "ButtonDefaults.outlinedShape", "ButtonDefaults.textShape",
        "ButtonDefaults.filledTonalShape", "ButtonDefaults.elevatedShape",
        "FilterChipDefaults.shape", "SegmentedButtonDefaults.Icon", "SegmentedButtonDefaults.ActiveIcon",
    ).associateWith { reference -> Regex("""\b${Regex.escape(reference)}\b""") }

    @Test
    fun `feature and app main sources use the component kit instead of raw Material components`() {
        val root = repositoryRoot()
        val sourceRoots = listOf(root.resolve("app/src/main")) +
            (root.resolve("feature").listFiles { file -> file.isDirectory }?.map { it.resolve("src/main") }
                ?: emptyList())
        val violations = sourceRoots
            .filter { it.isDirectory }
            .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file ->
                (forbiddenImportsIn(file) + forbiddenReferencesIn(file)).map { "${file.relativeTo(root)}: $it" }
            }
            .sorted()

        assertTrue(
            "Use core:ui-components (AppPanel, AppDialog, AppButton, AppIcons, ...) instead of these:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun forbiddenImportsIn(file: File): List<String> =
        file.useLines { lines ->
            lines.map { it.trim() }
                .filter { it.startsWith("import ") }
                .map { it.removePrefix("import ").substringBefore(" as ").trim() }
                .filter { it in forbiddenImports || it.startsWith("androidx.compose.material.icons.") }
                .toList()
        }

    private fun forbiddenReferencesIn(file: File): List<String> =
        file.useLines { lines ->
            lines.flatMap { line ->
                forbiddenReferences.filterValues { it.containsMatchIn(line) }.keys
            }.toList()
        }

    private fun repositoryRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !dir.resolve("settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "settings.gradle.kts not found above ${System.getProperty("user.dir")}" }
    }
}
