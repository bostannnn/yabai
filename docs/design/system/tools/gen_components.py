# Writes components/<Comp>/README.md + preview.html for the yabai design system.
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'project', 'components')
HYDRATE = "<script>window.Yabai && Yabai.hydrate();</script>"


def ic(name, size=None):
    s = f' data-size="{size}" style="width:{size}px;height:{size}px"' if size else ""
    return f'<span data-icon="{name}"{s}></span>'


def comp(name, group, height, readme, body, width=None, page=False, subtitle=None, script=""):
    d = os.path.join(ROOT, name)
    os.makedirs(d, exist_ok=True)
    attrs = f'group="{group}" height={height}'
    if width:
        attrs += f" width={width}"
    if page:
        attrs += " page"
    if subtitle:
        attrs += f' subtitle="{subtitle}"'
    html = f"<!-- @dsCard {attrs} -->\n{body}\n{HYDRATE if not script else script}\n"
    assert "<iframe" not in html
    open(os.path.join(d, "preview.html"), "w").write(html)
    open(os.path.join(d, "README.md"), "w").write(readme.strip() + "\n")


# Covers: fixed artwork colours (covers are images, not theme colours)
COVERS = [
    ("The Lantern Keepers", "Oona Fitch", "#1d2a44", "#e7dcc2"),
    ("Дом у реки", "Илья Горский", "#7a2a2e", "#f1d9c8"),
    ("Quiet Engines", "M. Aster", "#efe8da", "#1b1b1b"),
    ("Moths of Lisbon", "Paulo Serra", "#23232b", "#e9c46a"),
    ("Glass Meridian", "J. R. Okafor", "#3b2350", "#f4c7a1"),
    ("Star Courier #2", "", "#0f4c5c", "#f6f1e7"),
    ("The Salt Orchard", "Ines Marlow", "#2f4a3a", "#efe6cf"),
    ("Paper Cranes", "Hana Moriya", "#e9e4dc", "#b3261e"),
]


def cover(i, width=None, font=None):
    t, a, bg, fg = COVERS[i % len(COVERS)]
    w = f"width:{width}px;" if width else ""
    f = f"font-size:{font}px;" if font else ""
    return f'<div class="yb-cover" style="{w}{f}background:{bg};color:{fg}"><b>{t}</b><small>{a}</small></div>'


def row(title, sup=None, icon=None, trailing="chev", selected=False, dense=False):
    cls = "yb-row" + (" is-selected" if selected else "") + (" is-dense" if dense else "")
    lead = f'<span class="yb-lead">{ic(icon)}</span>' if icon else ""
    sup_html = f'<span class="yb-supporting">{sup}</span>' if sup else ""
    if trailing == "chev":
        tr = f'<span class="yb-chev">{ic("navigate_next")}</span>'
    elif trailing == "switch":
        tr = '<span class="yb-switch"></span>'
    elif trailing == "switch-on":
        tr = '<span class="yb-switch is-on"></span>'
    elif trailing == "drag":
        tr = f'<span class="yb-chev">{ic("drag_handle")}</span>'
    elif trailing is None:
        tr = ""
    else:
        tr = trailing
    return f'<div class="{cls}">{lead}<span class="yb-row-text"><span class="yb-row-title">{title}</span>{sup_html}</span>{tr}</div>'


DIV = '<div class="yb-divider"></div>'


def group(label, rows):
    lab = f'<h3 class="yb-group-label">{label}</h3>' if label else ""
    return f'<section class="yb-group">{lab}<div class="yb-panel">{DIV.join(rows)}</div></section>'


def theme_preview():
    return ('<span style="display:inline-flex;gap:3px;padding:4px;border:1px solid var(--outline-variant);border-radius:8px;margin-right:4px">'
            '<i style="width:12px;height:18px;border-radius:2px;background:var(--surface-container-highest)"></i>'
            '<i style="width:12px;height:18px;border-radius:2px;background:var(--primary)"></i>'
            '<i style="width:12px;height:18px;border-radius:2px;background:var(--tertiary)"></i></span>')


def chev():
    return f'<span class="yb-chev">{ic("navigate_next")}</span>'


# The Pages filter: the slider's stops (the last is "1,000+") and a library's spread over them, one bar per step.
PAGE_STOPS = [0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1000]
PAGE_BARS = [30, 120, 610, 240, 40, 35, 28, 29, 70, 62, 48, 30, 10, 6, 2]


def page_range(start, end, unknown=114):
    """The Pages section's body for a range between stop indexes start and end."""
    steps = len(PAGE_STOPS) - 1
    top = max(PAGE_BARS) ** 0.5
    bars = "".join(
        f'<i class="{"is-in" if start <= i < end else ""}" style="height:{max(2, round(56 * c ** 0.5 / top))}px"></i>'
        for i, c in enumerate(PAGE_BARS)
    )
    left, right = start / steps * 100, end / steps * 100
    low = "" if start == 0 else f"{PAGE_STOPS[start]:,}"
    high = "" if end == steps else f"{PAGE_STOPS[end]:,}"
    return f"""<div class="yb-hist">{bars}</div>
<div class="yb-range"><span class="yb-range-track"><b style="left:{left:.2f}%;right:{100 - right:.2f}%"></b></span><i style="left:{left:.2f}%"></i><i style="left:{right:.2f}%"></i></div>
<div class="yb-range-axis"><span style="left:0">0</span><span style="left:{7 / steps * 100:.2f}%;transform:translateX(-50%)">100</span><span style="left:{11 / steps * 100:.2f}%;transform:translateX(-50%)">300</span><span style="right:0">1,000+</span></div>
<div style="display:flex;gap:12px;margin-top:4px"><span class="yb-field"><span class="yb-field-label">From</span>{low}</span><span class="yb-field"><span class="yb-field-label">To</span>{high}</span></div>
<div style="display:flex;align-items:center;gap:16px;min-height:52px"><span class="yb-row-text"><span class="yb-row-title" style="font-size:15px">Include unknown length</span><span class="yb-caption">{unknown} books have no page count</span></span><span class="yb-switch"></span></div>"""


def page_label(value):
    return f'<div style="display:flex;justify-content:space-between;align-items:baseline"><h3 class="yb-sheet-label">Pages</h3><span class="yb-range-value">{value}</span></div>'


# ---------------------------------------------------------------- Navigation
comp("AppTopBar", "Navigation", 300, """
# AppTopBar

The floating top bar every screen shares: 56 tall, 12 below the status bar, its glass edge on the gutter line.

- Kit: `AppTopBar(surfaceVisible, onDrawerClick, ...)` in `AppTopBar.kt`; `AppTopBarDefaults.contentInset` (76) is the inset content starts under, plus `content-top` 8.
- **At rest it has no glass.** `surfaceVisible` turns true only once content has scrolled under the bar, on every screen: settings and journal report it with `onScrolledChanged`, the shelf browser through `LibraryShelfBrowserViewState.scrolled`.
- **Its sides follow the gutter** (`appGutter()`): 16 on Compact, 24 from Medium.
- Contents by screen: a source's library has the menu, the search field ("Search Kavita"), Downloads and •••. A top-level screen without search (journal, settings hub) has only the menu, and only while the drawer is the navigation. A page you opened has Back and its name. See Names and titles.
- Icon buttons are the kit's default size; do not force 36.
""", f"""
<div class="yb yb-ground" style="padding:16px;display:flex;flex-direction:column;gap:20px">
  <div style="position:relative;height:80px;--yb-gutter:24px">
    <div class="yb-bar"><button class="yb-icon-btn">{ic("menu")}</button><div class="yb-search">{ic("search")}<span>Search Kavita</span></div><span class="yb-spacer"></span><button class="yb-icon-btn">{ic("download")}</button><button class="yb-icon-btn">{ic("more_horiz")}</button></div>
  </div>
  <div style="position:relative;height:80px;--yb-gutter:24px">
    <div class="yb-bar is-glass"><button class="yb-icon-btn">{ic("menu")}</button><div class="yb-search">{ic("search")}<span>Search Kavita</span></div><span class="yb-spacer"></span><button class="yb-icon-btn">{ic("download")}</button><button class="yb-icon-btn">{ic("more_horiz")}</button></div>
  </div>
  <div style="position:relative;height:80px;--yb-gutter:24px">
    <div class="yb-bar"><button class="yb-icon-btn">{ic("arrow_back")}</button><span class="yb-bar-title">Theme</span></div>
  </div>
  <div class="yb-caption">At rest · scrolled (glass) · a page you opened</div>
</div>""")

comp("AppNavigationRail", "Navigation", 420, """
# AppNavigationRail

The rail on Expanded windows whose height is not Compact: 88 wide, a hairline at its edge, sources at the top and Journal and Settings at the bottom.

- Kit: `AppNavigationRail` + `AppNavigationRailItem`; `SourceNavigationRail` in feature:navigation chooses it (`sourceNavigationUsesRail()`).
- The selected item's indicator is `primary-container`; labels are 12 Medium.
- Content beside the rail starts `gutter` after its hairline.
- The rail is the screen's name: nothing on the screen repeats it.
""", f"""
<div class="yb yb-ground" style="display:flex;height:400px;border:1px solid var(--outline-variant);border-radius:12px;overflow:hidden;width:260px">
  <nav class="yb-rail" style="justify-content:space-between">
    <div style="display:flex;flex-direction:column;gap:12px;align-items:center">
      <div class="yb-rail-item"><span>{ic("collections_bookmark")}</span><span>Kavita</span></div>
      <div class="yb-rail-item"><span>{ic("folder")}</span><span>Local</span></div>
      <div class="yb-rail-item"><span>{ic("add")}</span><span>Add</span></div>
    </div>
    <div style="display:flex;flex-direction:column;gap:12px;align-items:center">
      <div class="yb-rail-item"><span>{ic("history")}</span><span>Journal</span></div>
      <div class="yb-rail-item is-selected"><span>{ic("settings")}</span><span>Settings</span></div>
    </div>
  </nav>
  <div style="flex:1;padding:84px 24px 0" class="yb-caption">content starts 24 after the hairline</div>
</div>""")

# ---------------------------------------------------------------- Lists
comp("AppSettingsGroup", "Lists", 560, """
# AppSettingsGroup

A labelled group of rows on a panel: the settings hub and every settings page, the source page's groups, About.

- Kit: `AppSettingsGroup(label)` holding `AppSettingsRow`s separated by `AppSettingsRowDivider()`.
- The label is `AppGroupLabel` (`group-label`: 15 SemiBold, `on-surface`), **flush with the panel's edge**, `heading-gap` 8 above the panel. The theme page's Device / Light / Dark use the same label over their tiles.
- Rows: `AppSettingsRow(title, icon, supporting, selected, onClick, trailing)`. Inset 20, min height 64, icon 24 then 16 to the text. One trailing thing: a chevron (`navigate_next`), a switch, a value, a button or a drag handle.
- Dividers start under the text (60) and use `outline-variant` at 60%.
- The selected row (two panes) fills with `primary-container`; its supporting text and chevron drop to 72%.
- Use it instead of hand-made rows: the sync queue row, the reading-defaults row, the filter-chips row and first launch's choices are rows of this family.
- Drag handles lead the row (the filter chips page); a row with a drag handle has no chevron.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:520px">
  <div class="yb-stack">
    {group("Appearance", [row("Theme", "Graphite", "palette", theme_preview() + chev(), selected=True)])}
    {group("Sources", [row("Kavita", "Not connected", "collections_bookmark"), row("Stump", "Not connected", "menu_book"), row("Grimmory", "Connected", "cloud")])}
    {group("About", [row("Check for updates automatically", "Once a day, when yabai opens", "history", "switch-on"), row("Open-source licenses", None, "description")])}
  </div>
</div>""", width=568)

comp("AppBookRow", "Lists", 300, """
# AppBookRow

One row for a book in a list: a 40 wide cover, the title at 15 SemiBold, one line under it at 13 `on-surface-variant`, an optional trailing value or action.

- Kit: `AppBookRow(title, cover, onClick, contentPadding, supporting, trailing, leading)` and `AppBookRowLine(text, color, maxLines)` for the usual line; `AppBookRowDefaults.coverWidth` 40.
- The journal's What you read, a journal day's books, the downloads list and a list page all use it.
- A list in an order you set numbers its rows: `AppBookRowNumber(n)` in `leading`, 15 Bold `on-surface-variant`, right-aligned in 24, 14 before the cover. A book that left the library keeps its row at 55% from the copy saved with the list.
- 14 from the cover to the text; the caller pads the row to its surface's inset. Progress, when shown, is an `AppProgressBar` under the line.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:460px">
  <div class="yb-panel">
    <div class="yb-book-row">{cover(0)}<div style="flex:1;min-width:0"><div class="yb-book-title">The Lantern Keepers</div><div class="yb-book-line">Chapter 1 of 6 · 4%</div><div class="yb-progress" style="margin-top:8px"><i style="width:4%"></i></div></div><span class="yb-supporting">9 h 43 min</span></div>
    {DIV}
    <div class="yb-book-row">{cover(3)}<div style="flex:1;min-width:0"><div class="yb-book-title">Moths of Lisbon</div><div class="yb-book-line">Finished 24 Oct · Book</div></div><button class="yb-btn is-tonal">Rate</button></div>
    {DIV}
    <div class="yb-book-row"><span style="width:24px;text-align:right;font:700 15px/20px var(--font-sans);color:var(--on-surface-variant)">7</span>{cover(5)}<div style="flex:1;min-width:0"><div class="yb-book-title">Star Courier #2</div><div class="yb-book-line">Star Courier · Kavita</div></div></div>
  </div>
</div>""", width=508)

# ---------------------------------------------------------------- Text
comp("AppSectionHeading", "Text", 200, """
# AppSectionHeading

The heading on the page ground above a grid or a list: `section-heading` 20 Bold (18 on phones) with an optional count in 15 SemiBold `on-surface-variant`, and optional actions at the end of its row.

- Kit: `AppSectionHeading(text, count)`; the caller puts actions (Filters, sort, Hide finished) after it in the same row.
- It sits **on the gutter line**, flush with the grid under it, `heading-gap` 8 above it.
- Use it for the library's All books, a search's Results, the shelf browser, and the Lists screen's sections (Your lists, Kavita reading lists) and a list page's books.
- Not inside panels (a panel's title is its own) and not in sheets (`AppPanelHeader`, then `sheet-label`s).
""", f"""
<div class="yb yb-ground" style="padding:24px;width:760px">
  <div style="display:flex;align-items:center;gap:8px">
    <h2 class="yb-section-heading" style="flex:1">All books <span class="yb-count">1,474</span></h2>
    <span class="yb-chip">{ic("filter_list")}Filters</span><span class="yb-chip">{ic("tune")}Last read</span>
  </div>
  <div style="display:grid;grid-template-columns:repeat(5,1fr);gap:16px;margin-top:8px">{''.join(cover(i) for i in range(5))}</div>
</div>""", width=808)


def list_cover(indices, width=None):
    w = f"width:{width}px;" if width else ""
    cells = "".join(f'<span style="background:{COVERS[i % len(COVERS)][2]}"></span>' for i in indices)
    cells += "".join('<span></span>' for _ in range(4 - len(indices)))
    return (f'<div style="{w}aspect-ratio:2/3;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:2px;'
            f'border-radius:var(--radius-cover);overflow:hidden;background:var(--surface-container-high)">{cells}</div>')


comp("AppListCover", "Lists", 330, """
# AppListCover

A list's cover: its first four books' covers in a 2×2 mosaic, at a cover's 2:3 and `radius-cover`, 2 apart. A list reads apart from a book (one cover) and a series (one cover with a stack behind it).

- Kit: `AppListCover(count, cell = { index, modifier -> ... })`; the caller draws each cover (the library's cover composable) filling the modifier it gets. Fewer than four books leave the other cells in `surface-container-high`; an empty list is all four.
- Used for list tiles on the Lists screen (the grid's cover width) and the list page's header (132).
- A list that updates itself (Stalled) adds a 32 round badge with the `tune` icon at its top end, on `surface-container-high`.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:520px;display:grid;grid-template-columns:repeat(3,1fr);gap:16px">
  <div>{list_cover([0, 3, 5, 6])}<div class="yb-book-title" style="margin-top:6px">The Long Eclipse</div><div class="yb-caption">Your order · 6 of 14</div></div>
  <div>{list_cover([1, 2])}<div class="yb-book-title" style="margin-top:6px">Want to read</div><div class="yb-caption">2 books</div></div>
  <div style="position:relative">{list_cover([4, 7, 2, 0])}<span style="position:absolute;right:8px;top:8px;width:32px;height:32px;border-radius:50%;background:var(--surface-container-high);display:flex;align-items:center;justify-content:center">{ic("tune", 18)}</span><div class="yb-book-title" style="margin-top:6px">Stalled</div><div class="yb-caption">Updates itself · 4</div></div>
</div>""", width=568)

# ---------------------------------------------------------------- Surfaces
comp("AppPanelHeader", "Surfaces", 300, """
# AppPanelHeader

The one header for every sheet and panel: an optional Back, the title in `panel-title` (20 Bold), an optional subtitle (`label-medium`, `on-surface-variant`), actions, Close.

- Kit: `AppPanelHeader(title, subtitle, onBack, onClose, backContentDescription, closeContentDescription, actions)`. Padding 20 at the start (8 when Back is there), 8 at the end, 14 above, 6 below.
- **Every sheet uses it**: filters and a filter's facet page, downloads, a journal day, the book page and the reader panels, so Back and Close sit in the same place on every sheet.
- A sheet whose content starts with its own title (the book page, a journal day) passes no `title`: the header is just Back and Close. On the book page it is the first row of the cover-tinted top, in the tint's content colour.
- A subtitle says something new ("Showing all 35"); it never repeats the book title the reader bar shows.
""", f"""
<div class="yb yb-ground" style="padding:16px;display:flex;gap:16px;flex-wrap:wrap">
  <div class="yb-sheet" style="width:360px"><div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Filters</h2><span class="yb-panel-sub">Showing all 35</span></div><button class="yb-icon-btn">{ic("close")}</button></div></div>
  <div class="yb-sheet" style="width:360px"><div class="yb-panel-header has-back"><button class="yb-icon-btn">{ic("arrow_back")}</button><div class="yb-panel-header-text"><h2 class="yb-panel-title">Author</h2></div><button class="yb-btn is-text">Clear</button><button class="yb-icon-btn">{ic("close")}</button></div></div>
  <div class="yb-sheet" style="width:360px"><div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Downloads</h2><span class="yb-panel-sub">1 downloading · 2 waiting</span></div><button class="yb-icon-btn">{ic("close")}</button></div></div>
</div>""")

comp("AppSideSheet", "Surfaces", 1080, """
# AppSideSheet

The sheet for anything opened over a screen: the book page, filters, downloads, a journal day. 400 wide, 12 from the window, `radius-sheet` 28, on `surface`, shadow 16. Under 600 it becomes `AppSheet` (bottom sheet) with the same header and body.

- Kit: `AppSideSheet(onDismissRequest)`; **one width**, `sheet-width` 400, for every sheet (it takes no width). Corners 28 like dialogs and bottom sheets.
- Header: `AppPanelHeader`. Body inset 20. Section labels inside: `AppSheetLabel` (`sheet-label`, 13 Bold, `on-surface-variant`), 14 above, 8 to their content. A sheet with two levels (downloads: a section over its days) puts `AppGroupLabel` above `AppSheetLabel`.
- Hairlines between a sheet's sections: `AppSheetDivider`, the sheet's width less the inset on both sides, `outline-variant` at `AppDividerDefaults.alpha` (60%).
- A footer action is a full-width filled button with 20 around it.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;justify-content:flex-end;width:480px">
  <div class="yb-sheet">
    <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Filters</h2><span class="yb-panel-sub">Showing all 35</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
    <div class="yb-sheet-body">
      <div class="yb-sheet-section"><h3 class="yb-sheet-label">Format</h3><div class="yb-chip-row"><span class="yb-chip is-selected">CBZ <span class="yb-count">20</span></span><span class="yb-chip">PDF <span class="yb-count">1</span></span><span class="yb-chip">EPUB <span class="yb-count">14</span></span></div></div>
      <div class="yb-divider is-full" style="margin:12px 0 0"></div>
      <div class="yb-sheet-section"><h3 class="yb-sheet-label">Reading status</h3><div class="yb-chip-row"><span class="yb-chip">Not started <span class="yb-count">7</span></span><span class="yb-chip">In progress <span class="yb-count">16</span></span><span class="yb-chip">Finished <span class="yb-count">12</span></span></div></div>
      <div class="yb-divider is-full" style="margin:12px 0 0"></div>
      <div class="yb-sheet-section">{page_label("150–300")}{page_range(8, 11)}</div>
      <div class="yb-divider is-full" style="margin:12px 0 0"></div>
    </div>
    <div style="margin-top:-8px">
      <div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">Library</span></span><span class="yb-supporting">Any</span>{chev()}</div>
      <div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">Author</span></span><span class="yb-supporting">Any</span>{chev()}</div>
      <div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">Series</span></span><span class="yb-supporting">Any</span>{chev()}</div>
    </div>
    <div style="height:40px"></div>
    <div style="padding:20px"><button class="yb-btn is-filled" style="width:100%">Show 20 books</button></div>
  </div>
</div>""", width=528)

comp("AppDialog", "Surfaces", 320, """
# AppDialog

A question or a short form over the screen: `AppDialogCard` up to 380 wide, `radius-sheet` 28, `inset-dialog` 24, on `surface-container-high`; title 24 Bold, body 14; pill actions at the end.

- Kit: `AppDialog`, `AppDialogCard`, `AppListDialog` (a searchable list), `AppDialogConfirmButton` / `AppDialogDismissButton`.
- A destructive confirm is `error` / `on-error`.
- A search field inside a dialog uses `AppSearchFieldDefaults.dialogContainerColor`.
- The Finished screen keeps its own 440 card, but takes its padding from `AppDialogCardDefaults`.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:440px">
  <div class="yb-dialog">
    <h2 class="yb-dialog-title">Remove from library?</h2>
    <div class="yb-supporting" style="color:var(--on-surface-variant)">This removes The Lantern Keepers from your library and clears local reading state.</div>
    <div class="yb-dialog-actions"><button class="yb-btn is-text">Cancel</button><button class="yb-btn" style="background:var(--error);color:var(--on-error)">Remove</button></div>
  </div>
</div>""", width=488)

comp("AppMenu", "Surfaces", 470, """
# AppMenu

A dropdown anchored to its button: the sort menu, •••, a tile's actions. `surface-container-high`, 44 tall items.

- Kit: `AppMenu`, `AppMenuHeading`, `AppMenuChoiceItem`, `AppMenuSwitchItem`.
- **One inset, `inset-menu` 16** (`AppMenuDefaults.horizontalPadding`), for items, headings and controls: the cover-size segmented row lines up with the labels.
- Corners `radius-control` 12, as the 2026-10-22 decision says for menus.
- A menu with a single item (••• on a Local library) is fine; do not pad it with disabled entries.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:340px">
  <div class="yb-menu">
    <div class="yb-menu-heading">Sort by</div>
    <div class="yb-menu-item"><span class="yb-check"></span>Date added</div>
    <div class="yb-menu-item"><span class="yb-check">{ic("check")}</span>Last read</div>
    <div class="yb-menu-item"><span class="yb-check"></span>Author</div>
    <div class="yb-menu-item"><span class="yb-check"></span>Title</div>
    <div class="yb-divider is-full" style="margin:6px 0"></div>
    <div class="yb-menu-heading">View</div>
    <div class="yb-menu-item" style="justify-content:space-between">Fade read books<span class="yb-switch"></span></div>
    <div class="yb-menu-heading">Cover size</div>
    <div class="yb-seg"><span>Small</span><span class="is-selected">Medium</span><span>Large</span></div>
  </div>
</div>""", width=388)

# ---------------------------------------------------------------- Actions
comp("AppButton", "Actions", 220, """
# AppButton

Pill buttons, 40 tall, `button` text. Pressed, they shrink a little and their corners move toward square.

- Kit: `AppButton` (filled: `primary`), `AppTonalButton` (`surface-container-highest`), `AppOutlinedButton`, `AppTextButton`, `AppIconButton`, `AppTonalIconButton`; colours from `AppButtonDefaults` only (`ButtonDefaults` is forbidden outside the kit by a test).
- At most one filled button per surface: the main action (Continue, Connect, Show 20 books).
- Destructive actions are text in `error`, or a filled `error` button inside a confirm dialog.
- A button's spinner is one size (18), from the kit.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:16px">
  <div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center">
    <button class="yb-btn is-filled">{ic("menu_book")}Continue</button>
    <button class="yb-btn is-tonal">Details</button>
    <button class="yb-btn is-outlined">Check for updates</button>
    <button class="yb-btn is-text">Clear</button>
    <button class="yb-btn is-destructive">{ic("delete")}Remove from library</button>
  </div>
  <div style="display:flex;gap:10px;align-items:center">
    <button class="yb-icon-btn">{ic("more_horiz")}</button>
    <button class="yb-icon-btn is-tonal">{ic("close")}</button>
    <button class="yb-icon-btn">{ic("download")}</button>
  </div>
</div>""")

comp("AppChip", "Actions", 180, """
# AppChip

A selectable pill for filters and sort: 36 tall, a hairline `outline-variant` edge, `on-surface-variant` text in SemiBold; selected fills with `primary-container` and drops the edge. An optional count follows the label at 70%.

- Kit: `AppChip(selected, onClick, label, count, leadingIcon)`.
- The library's Filters and sort pills, the filter sheet's values, Kavita lists.
- Counts are numbers only ("20", not "(20)").
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;gap:8px;flex-wrap:wrap">
  <span class="yb-chip">{ic("filter_list")}Filters</span>
  <span class="yb-chip is-selected">{ic("filter_list")}Filters <span class="yb-count">2</span></span>
  <span class="yb-chip">{ic("tune")}Last read</span>
  <span class="yb-chip">EPUB <span class="yb-count">14</span></span>
  <span class="yb-chip is-selected">CBZ <span class="yb-count">20</span></span>
</div>""")

comp("AppSegmentedRow", "Actions", 200, """
# AppSegmentedRow

Two or three choices in one pill: a `surface-container-low` track, 3 padding, 34 tall segments; the selected one is `primary-container`.

- Kit: `AppSegmentedRow` + `AppSegment`.
- Any setting with two or three values is one row of these (label + segmented row); lists are only for fonts and chapters.
- Month / Year in the journal, cover size in the sort menu, reader settings.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:16px;width:420px">
  <div class="yb-seg" style="width:240px"><span class="is-selected">Month</span><span>Year</span></div>
  <div class="yb-seg"><span>Small</span><span class="is-selected">Medium</span><span>Large</span></div>
</div>""", width=468)

comp("AppRangeSlider", "Actions", 640, """
# AppRangeSlider

A range on one track: AppSlider's 18 tall pill on `surface-container-highest`, `primary` between two slim 4 x 28 handles in `on-surface`. Above it, `AppHistogram` can show how the values spread: one bar per step, 56 tall at most, 2 apart, top corners 3, each as tall as the square root of its count so a few long books still show beside many short ones. Bars inside the range are `primary`, the rest `outline-variant`.

- Kit: `AppRangeSlider(values, onValuesChange, valueRange, onValueChangeFinished)` and `AppHistogram(counts, highlighted)`. The histogram's bars are the slider's steps, so the two span the same width.
- The caller snaps the handles to its stops and applies the range on release (`onValueChangeFinished`), not on every step.
- The library filter sheet's Pages section: the label with the range at its end in `primary` ("150–300", "150+", "Up to 300", "Any"), the histogram, the slider, From and To fields (`AppTextField`, numbers; an empty field is no bound) and, while some books have no page count, an "Include unknown length" switch row saying how many (dimmed while the range is Any, when nothing is left out). Under the slider, 0, 100, 300 and 1,000+ mark the uneven scale. Stops: 0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1,000+ (the last has no upper bound).
- What counts as pages is what the book page shows: a comic's or a PDF's page count, an EPUB's printed pages (only Grimmory knows them). A series tile fits when one of its issues does.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:400px">
  <div class="yb-sheet" style="width:360px;padding:0 20px 12px">
    <div class="yb-sheet-section" style="padding-top:14px">{page_label("150–300")}{page_range(8, 11)}</div>
  </div>
  <div class="yb-sheet" style="width:360px;padding:0 20px 12px;margin-top:16px">
    <div class="yb-sheet-section" style="padding-top:14px">{page_label("Any")}{page_range(0, 15).replace('class=""', 'class="is-in"').replace('min-height:52px"', 'min-height:52px;opacity:.38"')}</div>
  </div>
</div>""", width=448)

comp("AppSearchField", "Actions", 200, """
# AppSearchField

The search pill: 40 tall, `surface-container-high`, a search icon, a placeholder that names what it searches, Clear once there is text, and an optional trailing slot (the connection badge while a source is offline).

- Kit: `AppSearchField(query, onQueryChange, placeholder, containerColor, trailingContent)`.
- In the library bar it fills the middle up to 640. The placeholder says the place: "Search Kavita", "Search Neon Ronin", "Search shelves".
- On a dialog, pass `AppSearchFieldDefaults.dialogContainerColor`.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:12px;width:480px">
  <div class="yb-search">{ic("search")}<span>Search Kavita</span></div>
  <div class="yb-search" style="color:var(--on-surface)">{ic("search")}<span style="flex:1">lantern</span>{ic("close")}</div>
</div>""", width=528)

# ---------------------------------------------------------------- Feedback
comp("AppEmptyState", "Feedback", 360, """
# AppEmptyState

An empty or failed place: an illustration or icon, a 28 Bold title, a 14.5 line, up to two actions. `inset-dialog` 24 at the sides, 40 above and below.

- Kit: `AppEmptyState`. Every empty state uses it (an empty source, no downloads, no matches, a reader error).
- "No matches" inside a filter facet or a list dialog is a short `AppEmptyState` too, not local text.
""", f"""
<div class="yb yb-ground" style="width:420px">
  <div class="yb-empty"><span style="color:var(--on-surface-variant)">{ic("download_done", 56)}</span><h2 class="yb-empty-title">Nothing downloaded</h2><p class="yb-empty-body">Downloaded books open without the server. Use Download on a book's page, or long-press covers to pick several.</p></div>
</div>""", width=468)

comp("AppStatusPill", "Feedback", 160, """
# AppStatusPill

A source's or a queue's state as a pill: 28 tall, `label-large` Bold, an optional 16 icon.

- Kit: `AppStatusPill(label, containerColor, contentColor, icon)`. The caller picks the colours for the state: the sync accent container for Connected, `surface-container-highest` for neutral (Checking, Waiting), `error-container` for a problem.
- The source page and the sync page use it.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;gap:10px;flex-wrap:wrap">
  <span class="yb-pill is-good">{ic("check")}Connected</span>
  <span class="yb-pill">Checking</span>
  <span class="yb-pill is-bad">{ic("error")}Offline</span>
</div>""")

comp("AppProgressBar", "Feedback", 160, """
# AppProgressBar

A thin bar: 4 tall, `primary` on `surface-container-highest`, pill ends.

- Kit: `AppProgressBar(fraction, height, progressColor, trackColor)`; a null fraction is Material's moving bar on the same track. The update download (and its row in About), first launch's sync, downloads, the journal's What you read and the EPUB contents footer use it.
- The reading card's chapter-split bar and the cover's progress strip stay their own: they draw on a cover tint and a cover.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:14px;width:420px">
  <div class="yb-progress"><i style="width:4%"></i></div>
  <div class="yb-progress"><i style="width:58%"></i></div>
  <div class="yb-progress"><i style="width:96%"></i></div>
</div>""", width=468)

# ---------------------------------------------------------------- Foundations
comp("Icons", "Foundations", 520, """
# Icons

Material Symbols Rounded, 24, unfilled, from `core:ui-components/src/main/res/drawable/ic_*.xml` through `AppIcons`. Icons take the colour of the text beside them.

- Sources: Kavita `collections_bookmark`, Stump `menu_book`, Grimmory `cloud`, Local `folder`.
- Chevrons in rows are `navigate_next` everywhere; `keyboard_arrow_*` is for the journal's period arrows and its expand arrow.
- Add an icon by copying its Rounded drawable into the kit and naming it in `AppIcons`; never use the Material Icons artifacts.
""", """
<div class="yb yb-ground" id="icons" style="padding:20px;display:grid;grid-template-columns:repeat(auto-fill,minmax(112px,1fr));gap:10px 8px"></div>
""", script="""<script>
(function(){var root=document.getElementById("icons");var Y=window.Yabai;if(!Y)return;Y.icons.forEach(function(n){var d=document.createElement("div");d.style.cssText="display:flex;flex-direction:column;align-items:center;gap:6px;padding:8px 4px;color:var(--on-surface)";d.innerHTML=Y.icon(n,24)+'<span style="font:500 11px/14px var(--font-sans);color:var(--on-surface-variant);text-align:center;word-break:break-word">'+n+'</span>';root.appendChild(d);});})();
</script>""")

print("components written")
