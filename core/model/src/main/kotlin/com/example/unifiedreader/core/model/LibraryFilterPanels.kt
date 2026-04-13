package com.example.unifiedreader.core.model

enum class LibraryFilterPanel(
    val label: String,
) {
    Format("Format"),
    Library("Library"),
    Location("Location"),
    Author("Author"),
    Series("Series"),
    Publisher("Publisher"),
    Genre("Genre"),
    Tag("Tag"),
    Language("Language"),
}

fun availableLibraryFilterPanels(
    sourceType: SourceType,
): List<LibraryFilterPanel> = when (sourceType) {
    SourceType.KAVITA -> listOf(
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Language,
    )

    SourceType.STUMP -> listOf(
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Language,
    )

    SourceType.GRIMMORY -> listOf(
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Language,
    )

    SourceType.LOCAL -> listOf(
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
    )
}

fun defaultVisibleLibraryFilterPanels(
    sourceType: SourceType,
): List<LibraryFilterPanel> = availableLibraryFilterPanels(sourceType)

fun resolveLibraryFilterPanels(
    sourceType: SourceType,
    preferredPanels: List<LibraryFilterPanel>?,
): List<LibraryFilterPanel> {
    val availablePanels = availableLibraryFilterPanels(sourceType).toSet()
    val resolvedPanels = preferredPanels
        .orEmpty()
        .filter { panel -> panel in availablePanels }
        .distinct()
    return resolvedPanels.ifEmpty {
        defaultVisibleLibraryFilterPanels(sourceType)
    }
}
