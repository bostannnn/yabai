package com.example.unifiedreader.feature.settings

import androidx.compose.runtime.Composable

data class SettingsSupplementarySection(
    val id: String,
    val title: String,
    val content: @Composable () -> Unit,
)

internal enum class SettingsSectionId {
    Theme,
    Supplementary,
    LibraryFilters,
    Kavita,
    Grimmory,
    Stump,
}

internal data class SettingsSectionDescriptor(
    val id: SettingsSectionId,
    val title: String,
)

internal fun buildSettingsSectionDescriptors(
    supplementarySection: SettingsSupplementarySection?,
): List<SettingsSectionDescriptor> {
    val sections = mutableListOf(
        SettingsSectionDescriptor(
            id = SettingsSectionId.Theme,
            title = "Theme",
        ),
    )
    supplementarySection?.let { section ->
        sections += SettingsSectionDescriptor(
            id = SettingsSectionId.Supplementary,
            title = section.title,
        )
    }
    sections += listOf(
        SettingsSectionDescriptor(
            id = SettingsSectionId.LibraryFilters,
            title = "Library filters",
        ),
        SettingsSectionDescriptor(
            id = SettingsSectionId.Kavita,
            title = "Kavita",
        ),
        SettingsSectionDescriptor(
            id = SettingsSectionId.Grimmory,
            title = "Grimmory",
        ),
        SettingsSectionDescriptor(
            id = SettingsSectionId.Stump,
            title = "Stump",
        ),
    )
    return sections
}
