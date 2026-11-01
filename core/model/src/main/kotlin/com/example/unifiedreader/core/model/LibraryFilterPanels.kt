package com.example.unifiedreader.core.model

enum class LibraryFilterPanel(
    val label: String,
) {
    Format("Format"),
    Library("Library"),
    Location("Location"),
    ReadingStatus("Reading status"),
    Pages("Pages"),
    Author("Author"),
    Series("Series"),
    Publisher("Publisher"),
    Genre("Genre"),
    // The tags not sorted as places or times (Settings › Tags); named Tag from before Set in and When split off.
    Tag("About"),
    Language("Language"),
    SetIn("Set in"),
    When("When"),
    Mood("Mood"),
}

/**
 * The panels a source's filter sheet offers, in their default order: reading status first, then what the
 * books are (places, times, themes, moods, genre, people and series), then their length, then where and
 * how they are kept. Pages' slider is half a docked panel tall, so it waits below what the books are.
 * Kavita and Stump have no moods; a local library has only its files' authors and series.
 */
fun availableLibraryFilterPanels(
    sourceType: SourceType,
): List<LibraryFilterPanel> = when (sourceType) {
    SourceType.KAVITA,
    SourceType.STUMP -> listOf(
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.SetIn,
        LibraryFilterPanel.When,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Language,
        LibraryFilterPanel.Pages,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
    )

    SourceType.GRIMMORY -> listOf(
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.SetIn,
        LibraryFilterPanel.When,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Mood,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Language,
        LibraryFilterPanel.Pages,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
    )

    SourceType.LOCAL -> listOf(
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Pages,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Library,
        LibraryFilterPanel.Location,
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
