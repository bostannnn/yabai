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
 * Where a panel sits in the filter sheet: which books (where they are kept, read or not, in what
 * language and file), what kind they are, who wrote them and where and when they are set, then their
 * length. The sheet heads each group by its [label].
 */
enum class LibraryFilterGroup(
    val label: String,
) {
    WhichBooks("Which books"),
    WhatKind("What kind"),
    WhoWhereWhen("Who, where, when"),
    Length("Length"),
}

val LibraryFilterPanel.group: LibraryFilterGroup
    get() = when (this) {
        LibraryFilterPanel.Library,
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.Language,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Location -> LibraryFilterGroup.WhichBooks
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Mood -> LibraryFilterGroup.WhatKind
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.SetIn,
        LibraryFilterPanel.When,
        LibraryFilterPanel.Publisher -> LibraryFilterGroup.WhoWhereWhen
        LibraryFilterPanel.Pages -> LibraryFilterGroup.Length
    }

/**
 * The panels a source's filter sheet offers, in their default order, group by group
 * ([LibraryFilterGroup]): the short choices picked most (library, reading status, language, format,
 * location) first, then genre, tags and moods, then people, series, places and times, then Pages, whose
 * slider is half a docked panel tall. Kavita and Stump have no moods or authors; a local library has
 * only its files' authors and series.
 */
fun availableLibraryFilterPanels(
    sourceType: SourceType,
): List<LibraryFilterPanel> = when (sourceType) {
    SourceType.KAVITA,
    SourceType.STUMP -> listOf(
        LibraryFilterPanel.Library,
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.Language,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.SetIn,
        LibraryFilterPanel.When,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Pages,
    )

    SourceType.GRIMMORY -> listOf(
        LibraryFilterPanel.Library,
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.Language,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Genre,
        LibraryFilterPanel.Tag,
        LibraryFilterPanel.Mood,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.SetIn,
        LibraryFilterPanel.When,
        LibraryFilterPanel.Publisher,
        LibraryFilterPanel.Pages,
    )

    SourceType.LOCAL -> listOf(
        LibraryFilterPanel.Library,
        LibraryFilterPanel.ReadingStatus,
        LibraryFilterPanel.Format,
        LibraryFilterPanel.Location,
        LibraryFilterPanel.Author,
        LibraryFilterPanel.Series,
        LibraryFilterPanel.Pages,
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
