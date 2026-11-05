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
- Contents by screen: a source's library has the menu, the search field ("Search Kavita"), Downloads and •••. **The library's search field is centred on the bar**, which is centred on the page, so the field shares one centre with the grid under it; it is 640 at most, and where the bar is too narrow for that it fills the space between the buttons (never closer than 4 to them). A top-level screen without search (journal, settings hub) has only the menu, and only while the drawer is the navigation. A page you opened has Back and its name. See Names and titles.
- Icon buttons are the kit's default size; do not force 36.
- The glass edge is `outline-variant` at 28%; a character theme draws it in its `bar-edge` (see Colour).
""", f"""
<div class="yb yb-ground" style="padding:16px;display:flex;flex-direction:column;gap:20px">
  <div style="position:relative;height:80px;--yb-gutter:24px">
    <div class="yb-bar is-centred"><span class="yb-bar-lead"><button class="yb-icon-btn">{ic("menu")}</button></span><div class="yb-search">{ic("search")}<span>Search Kavita</span></div><span class="yb-bar-trail"><button class="yb-icon-btn">{ic("download")}</button><button class="yb-icon-btn">{ic("more_horiz")}</button></span></div>
  </div>
  <div style="position:relative;height:80px;--yb-gutter:24px">
    <div class="yb-bar is-glass is-centred"><span class="yb-bar-lead"><button class="yb-icon-btn">{ic("menu")}</button></span><div class="yb-search">{ic("search")}<span>Search Kavita</span></div><span class="yb-bar-trail"><button class="yb-icon-btn">{ic("download")}</button><button class="yb-icon-btn">{ic("more_horiz")}</button></span></div>
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
- The selected item's indicator is `primary-container` (in a character theme `primary`, its icon `on-primary`); labels are 12 Medium.
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
- The label is `AppGroupLabel` (`group-label`: 15 SemiBold, `on-surface`, a character theme's `label-accent`), **flush with the panel's edge**, `heading-gap` 8 above the panel. A count may follow it, `heading-gap` apart, in Medium `on-surface-variant` ("Rules 1,284 match"). The theme page's Device / Light / Dark use the same label over their tiles.
- Rows: `AppSettingsRow(title, icon, supporting, selected, onClick, trailing, leading)`. Inset 20, min height 64, icon 24 then 16 to the text. One trailing thing: a chevron (`navigate_next`), a switch, a value, a button or a drag handle.
- Dividers start under the text (60) and use `outline-variant` at 60%.
- The selected row (two panes) fills with `primary-container`; its supporting text and chevron drop to 72%.
- Use it instead of hand-made rows: the sync queue row, the reading-defaults row, the filter-chips row and first launch's choices are rows of this family.
- Drag handles lead the row (the filter chips page): `leading = { AppReorderHandleIcon(state, index, label) }`, the 24 arrow on the inset line with its grab area reaching 8 past it on each side. A row with a drag handle has no chevron; its trailing switch stays.
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
- A list in an order you set numbers its rows: `AppBookRowNumber(n)` in `leading`, 15 SemiBold `on-surface-variant`, right-aligned in 24, 14 before the cover. A book that left the library keeps its row at 55% from the copy saved with the list.
- 14 from the cover to the text; the caller pads the row to its surface's inset. Progress, when shown, is an `AppProgressBar` under the line.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:460px">
  <div class="yb-panel">
    <div class="yb-book-row">{cover(0)}<div style="flex:1;min-width:0"><div class="yb-book-title">The Lantern Keepers</div><div class="yb-book-line">Chapter 1 of 6 · 4%</div><div class="yb-progress" style="margin-top:8px"><i style="width:4%"></i></div></div><span class="yb-supporting">9 h 43 min</span></div>
    {DIV}
    <div class="yb-book-row">{cover(3)}<div style="flex:1;min-width:0"><div class="yb-book-title">Moths of Lisbon</div><div class="yb-book-line">Finished 24 Oct · Book</div></div><button class="yb-btn is-tonal">Rate</button></div>
    {DIV}
    <div class="yb-book-row"><span style="width:24px;text-align:right;font:600 15px/20px var(--font-sans);color:var(--on-surface-variant)">7</span>{cover(5)}<div style="flex:1;min-width:0"><div class="yb-book-title">Star Courier #2</div><div class="yb-book-line">Star Courier · Kavita</div></div></div>
  </div>
</div>""", width=508)

# ---------------------------------------------------------------- Text
comp("AppSectionHeading", "Text", 200, """
# AppSectionHeading

The heading on the page ground above a grid or a list: `section-heading` 20 SemiBold (18 on phones), `on-surface` (a character theme's `label-accent`), with an optional count in 15 Regular `on-surface-variant`, and optional actions at the end of its row.

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
    if 0 < len(indices) < 4:
        t, a, bg, fg = COVERS[indices[0] % len(COVERS)]
        return (f'<div class="yb-cover" style="{w}background:{bg};color:{fg}"><b>{t}</b><small>{a}</small></div>')
    cells = "".join(f'<span style="background:{COVERS[i % len(COVERS)][2]}"></span>' for i in indices)
    return (f'<div style="{w}aspect-ratio:2/3;display:grid;grid-template-columns:1fr 1fr;grid-template-rows:1fr 1fr;gap:2px;'
            f'border-radius:var(--radius-cover);overflow:hidden;background:var(--surface-container-high)">{cells}</div>')


comp("AppListCover", "Lists", 330, """
# AppListCover

A list's cover: its first four books' covers in a 2×2 mosaic, at a cover's 2:3 and `radius-cover`, 2 apart. A list reads apart from a book (one cover) and a series (one cover with a stack behind it).

- Kit: `AppListCover(count, cell = { index, modifier -> ... })`; the caller draws each cover (the library's cover composable) filling the modifier it gets.
- Four books or more make the mosaic. One to three books show the first book's cover, whole: a mosaic with empty cells reads as broken. An empty list is the bare `surface-container-high` card.
- Used for list tiles on the Lists screen (the grid's cover width), `AppListRow` (40) and the list page's header (132).
- A list that updates itself (Stalled) adds a 32 round badge with the `tune` icon at its top end, on `surface-container-high`.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:520px;display:grid;grid-template-columns:repeat(3,1fr);gap:16px">
  <div>{list_cover([0, 3, 5, 6])}<div class="yb-book-title" style="margin-top:6px">The Long Eclipse</div><div class="yb-caption">6 of 14 read</div></div>
  <div>{list_cover([1, 2])}<div class="yb-book-title" style="margin-top:6px">Want to read</div><div class="yb-caption">2 books</div></div>
  <div style="position:relative">{list_cover([4, 7, 2, 0])}<span style="position:absolute;right:8px;top:8px;width:32px;height:32px;border-radius:50%;background:var(--surface-container-high);display:flex;align-items:center;justify-content:center">{ic("tune", 18)}</span><div class="yb-book-title" style="margin-top:6px">Stalled</div><div class="yb-caption">Updates itself · 4</div></div>
</div>""", width=568)

def list_row(name, line, indices, progress=None, pinned=False, smart=False, check=None, pressed=False, name_html=None):
    """An AppListRow: the list's cover 40 wide, its name and line, a progress bar for an order under way, its marks."""
    lead = ""
    style = ""
    if check is not None:
        lead = (f'<span style="width:22px;height:22px;border-radius:50%;background:var(--primary);color:var(--on-primary);display:flex;'
                f'align-items:center;justify-content:center;flex:none">{ic("check", 16)}</span>') if check else (
                '<span style="width:22px;height:22px;border-radius:50%;box-shadow:inset 0 0 0 2px var(--outline);flex:none"></span>')
        if check:
            style = "background:var(--primary-container);color:var(--on-primary-container);"
    elif pressed:
        style = "background:var(--surface-container-highest);"
    bar = (f'<div class="yb-progress" style="margin-top:6px;max-width:220px"><i style="width:{progress}%"></i></div>'
           if progress is not None else "")
    marks = (f'<span class="yb-chev">{ic("keep", 18)}</span>' if pinned else "") + (f'<span class="yb-chev">{ic("tune", 18)}</span>' if smart else "")
    tail = "" if check is not None else chev()
    return (f'<div class="yb-book-row" style="padding:8px 12px 8px 20px;{style}">{lead}{list_cover(indices, width=40)}'
            f'<div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:2px"><div class="yb-book-title">{name_html or name}</div>'
            f'<div class="yb-book-line" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{line}</div>{bar}</div>{marks}{tail}</div>')


def letter_strip(current, line=17, letters="#ABCDEFGHIJKLMNOPQRSTUVWXYZ", missing="QXZ", style="", bubble=False):
    """An AppLetterStrip: # and A to Z down the end edge, the current group's letter on primary-container."""
    out = []
    for ch in letters:
        if ch == current:
            out.append(f'<span style="width:18px;height:{line}px;display:flex;align-items:center;justify-content:center;border-radius:999px;'
                       f'background:var(--primary-container);color:var(--on-primary-container)">{ch}</span>')
        else:
            op = "opacity:.38;" if ch in missing else ""
            out.append(f'<span style="width:18px;height:{line}px;display:flex;align-items:center;justify-content:center;{op}">{ch}</span>')
    bub = ""
    if bubble:
        top = letters.index(current) * line - 22
        bub = (f'<span style="position:absolute;right:30px;top:{top}px;width:60px;height:60px;border-radius:30px 30px 6px 30px;'
               f'background:var(--primary);color:var(--on-primary);display:flex;align-items:center;justify-content:center;font:600 28px/1 var(--font-sans)">{current}</span>')
    return (f'<div style="position:absolute;display:flex;flex-direction:column;font:600 10.5px/1 var(--font-sans);'
            f'color:var(--on-surface-variant);{style}">{bub}{"".join(out)}</div>')


comp("AppListRow", "Lists", 420, """
# AppListRow

A list as a row, for a page that holds many lists: a place on Lists (Kavita, Grimmory), Lists' search, and a list's place in What Lists shows. Names come first in a row; covers of story-arc lists and genre shelves look alike.

- Kit: `AppListRow(name, line, cover, progress, pinned, updatesItself, selection, onClick, onLongClick)`; `cover` draws the list's `AppListCover` 40 wide.
- `AppBookRow`'s shape: `inset` 20 at the start, 12 at the end, 76 tall at least, the cover 40 wide and 14 from the text; the name in 15 SemiBold and one line under it in 13 `on-surface-variant`, each on one line with an ellipsis.
- The line says what the list is, how big, how far you are and, for a Kavita reading list, its years: "Reading list · 9 of 22 read · 2019–2020", "Collection · 9 series", "Magic shelf · 44 books", "Shelf · 55 books · 21 read". Under a kind's tab the kind is left out.
- A reading order with something read and something left adds an `AppProgressBar` under the line, at most 220 wide.
- At the end, `on-surface-variant`: `keep` (18) when pinned, `tune` (18) when it updates itself, then the `navigate_next` chevron.
- Long-press opens the list's menu (Pin, Hide, Select several, Edit); the row takes `surface-container-highest` while the menu is open.
- Selecting several: an `AppRoundCheck` before the cover and no chevron; a picked row takes `primary-container`.
- Rows sit in an `AppPanel` with 4 above and below, an `AppListRowDivider` between two rows: the hairline starts under the text (inset 20 + cover 40 + 14), so the covers read as one column. Two columns of rows on Expanded, each its own panel, `grid-gap` 16 apart; one below it. `AppBookRow`s in a panel take the same divider.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:520px;display:flex;flex-direction:column;gap:16px">
  <div class="yb-panel" style="padding:4px 0">
    {list_row("Eclipse War", "Reading list · 9 of 22 read · 2019–2020", [0, 4, 5, 3], progress=41, pinned=True)}
    {list_row("Short Russian classics", "Magic shelf · 44 books", [1, 1, 1, 1], smart=True)}
    {list_row("Eisner winners", "Collection · 9 series", [4, 1, 3, 0])}
    {list_row("Black Tide", "Reading list · 24 issues · 2012–2013", [3, 0, 6, 2], pressed=True)}
  </div>
  <div class="yb-panel" style="padding:4px 0">
    {list_row("Absolute Zero", "Reading list · 18 issues · 2014", [5, 3, 0, 4], check=True)}
    {list_row("Ashfall", "Reading list · 11 issues · 2017–2018", [6, 2, 4, 1], check=False)}
  </div>
</div>""", width=568)

comp("AppLetterStrip", "Lists", 520, """
# AppLetterStrip

The index down the end edge of a long list sorted by name, to jump to a letter: # (names that start with a digit or a sign) and A to Z.

- Kit: `AppLetterStrip(letters, current, onLetter)`. 18 wide, one letter per 17 (15 on Compact), 11 SemiBold `on-surface-variant`, over the gutter at the end edge, from the first letter group's top down.
- A letter with no group is drawn at 38% and moves to the next letter that has one.
- Tap or drag: the list jumps to that letter's group label, and a 60 bubble in `primary` shows the letter beside the finger while it is down. The letter of the group at the top of the window sits on `primary-container`.
- Only while the section is sorted by name and holds more than 20 lists. Under the strip the group labels (`AppGroupLabel`, `on-surface-variant`) mark the letters.
- TalkBack: "Jump to a letter", each letter an action; the group labels are headings, so heading navigation does the same.
""", f"""
<div class="yb yb-ground" style="padding:24px;width:460px;height:470px;position:relative">
  <div style="display:flex;flex-direction:column;gap:8px;width:380px">
    <h3 class="yb-group-label" style="color:var(--on-surface-variant)">B</h3>
    <div class="yb-panel" style="padding:4px 0">
      {list_row("Black Tide", "Reading list · 24 issues · 2012–2013", [3, 0, 6, 2])}
      {list_row("Blood Moon Rising", "Reading list · 2 of 18 read · 2020", [4, 7, 2, 0], progress=11)}
      {list_row("Broken Crown", "Reading list · 6 issues · 2023", [6, 1, 3, 5])}
    </div>
    <h3 class="yb-group-label" style="color:var(--on-surface-variant)">C</h3>
    <div class="yb-panel" style="padding:4px 0">{list_row("Cinder Road", "Reading list · 9 issues · 2019", [1, 5, 2, 7])}</div>
  </div>
  {letter_strip("B", style="right:6px;top:24px", bubble=True)}
</div>""", width=508)

# ---------------------------------------------------------------- Surfaces
comp("AppPanelHeader", "Surfaces", 300, """
# AppPanelHeader

The one header for every sheet and panel: an optional Back, the title in `panel-title` (20 SemiBold), an optional subtitle (`label-medium`, `on-surface-variant`), actions, Close.

- Kit: `AppPanelHeader(title, subtitle, onBack, onClose, backContentDescription, closeContentDescription, actions)`. Padding 20 at the start (8 when Back is there), 8 at the end, 16 above, 8 below, at least 64 tall.
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

The sheet for anything opened over a screen: the book page, filters, downloads, a journal day. 400 wide, 12 from the window, `radius-sheet` 28, on `surface`, shadow 16. Under 600 the same call opens as a bottom sheet (`AppSheetLayout`, opened all the way) with the same header and body.

- Kit: `AppSideSheet(onDismissRequest)`; **one width**, `sheet-width` 400, for every sheet (it takes no width). Corners 28 like dialogs and bottom sheets.
- Header: `AppPanelHeader`. Body inset 20. Section labels inside: `AppSheetLabel` (`sheet-label`, 13 SemiBold, `on-surface-variant`), 14 above, 8 to their content. A sheet with two levels (downloads: a section over its days) puts `AppGroupLabel` above `AppSheetLabel`.
- Hairlines between a sheet's sections: `AppSheetDivider`, the sheet's width less the inset on both sides, `outline-variant` at `AppDividerDefaults.alpha` (60%).
- A section's body (chips, value rows, a slider) ends with `AppSheetSectionEnd`, `grid-gap` 16, before the next divider, the same under every kind of section; nothing sits on a divider. A section that ends in a row (`AppSettingsRow`, a value row that opens a page) leaves it out: the row carries its own padding.
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

A question or a short form over the screen: `AppDialogCard` up to 380 wide, `radius-sheet` 28, `inset-dialog` 24, on `surface-container-high`; title 24 SemiBold, body 14; pill actions at the end.

- Kit: `AppDialog`, `AppDialogCard`, `AppListDialog` (a searchable list), `AppDialogConfirmButton` / `AppDialogDismissButton`.
- A destructive confirm is `error` / `on-error`.
- The content starts `grid-gap` 16 below the title (or its subtitle); the actions 24 below the content.
- A search field inside a dialog uses `AppSearchFieldDefaults.dialogContainerColor`.
- The Finished screen keeps its own card (640 with the cover beside, see FinishedScreen), with the dialog's corners, padding and window margin from `AppDialogCardDefaults`.
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

- Kit: `AppMenu`, `AppMenuItem`, `AppMenuHeading`, `AppMenuChoiceItem`, `AppMenuSwitchItem`.
- An item that needs saying what it does (Lists' New: List, Smart list, Import a reading order) takes a `supporting` line under its label and grows to 56; keep that to menus whose choices make something.
- **One inset, `inset-menu` 16** (`AppMenuDefaults.horizontalPadding`), for items, headings and controls: the cover-size slider lines up with the labels.
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
    <div class="yb-menu-item" style="justify-content:space-between">Cover size<span class="yb-range-value">Large</span></div>
    <div style="padding:0 16px 10px"><div class="yb-range"><span class="yb-range-track"><b style="left:0;right:25%"></b></span><i style="left:75%"></i></div></div>
  </div>
</div>""", width=388)

# ---------------------------------------------------------------- Actions
comp("AppButton", "Actions", 220, """
# AppButton

Pill buttons, 40 tall, `button` text. Pressed, they shrink a little and their corners move toward square.

- Kit: `AppButton` (filled: `primary`), `AppTonalButton` (`surface-container-highest`), `AppTextButton`, `AppIconButton`, `AppTonalIconButton`; colours from `AppButtonDefaults` only (`ButtonDefaults` is forbidden outside the kit by a test).
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

A selectable pill for filters and sort: 36 tall, 16 inside, a hairline `outline-variant` edge, `on-surface-variant` text in SemiBold; selected fills with `primary-container` and drops the edge. An optional count follows the label in Regular at 70%, in tabular figures.

- Kit: `AppChip(selected, onClick, label, count, leadingIcon, onLongClick)`.
- The library's Filters and sort pills, the filter sheet's values, the Lists place row and Sort chip, and a smart list's rules.
- `onLongClick` is optional: Filters' Set in, When and About values and the book page's tags open the tag's sort sheet with it (see Tags).
- Counts are digits with a separator ("1,474"; `appCount`), not in brackets.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;gap:8px;flex-wrap:wrap">
  <span class="yb-chip">{ic("filter_list")}Filters</span>
  <span class="yb-chip is-selected">{ic("filter_list")}Filters <span class="yb-count">2</span></span>
  <span class="yb-chip">{ic("tune")}Last read</span>
  <span class="yb-chip">EPUB <span class="yb-count">14</span></span>
  <span class="yb-chip is-selected">CBZ <span class="yb-count">20</span></span>
</div>""")

comp("AppTabPill", "Actions", 160, """
# AppTabPill

A tab as a pill: 36 tall, 16 inside, text only until selected, then on `surface-container-high` with `on-surface` text. An optional count follows in Regular at 80%, in tabular figures. Quieter than an `AppChip`, so a row of tabs reads as "which list" and chips under it as "which value".

- Kit: `AppTabPill(label, selected, onClick, count)`; 4 apart in a row that scrolls when it doesn't fit.
- The library home's shelf tabs (In progress, Up next, Finished) and See all's.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:16px;width:520px">
  <div class="yb-tabs"><span class="yb-tab is-selected">In progress <span class="yb-count">7</span></span><span class="yb-tab">Up next <span class="yb-count">3</span></span><span class="yb-tab">Finished <span class="yb-count">4</span></span></div>
  <div style="display:flex;flex-direction:column;gap:8px"><div class="yb-tabs"><span class="yb-tab is-selected">Set in</span><span class="yb-tab">When</span><span class="yb-tab">About</span><span class="yb-tab">Genre</span></div><div class="yb-chip-row"><span class="yb-chip is-selected">Japan <span class="yb-count">96</span></span><span class="yb-chip">Tokyo <span class="yb-count">16</span></span><span class="yb-chip">Kyoto <span class="yb-count">2</span></span></div></div>
</div>""", width=568)

comp("AppSegmentedRow", "Actions", 200, """
# AppSegmentedRow

Two or three choices on one track: a `surface-container-low` track at `radius-control` 12, 4 padding, 36 tall segments at `radius-inner` 8 (12 less the padding); the selected one is `primary-container`. A field, not a pill: pills are for things you tap to act or pick.

- Kit: `AppSegmentedRow` + `AppSegment`.
- Any setting with two or three values is one row of these (label + segmented row); lists are only for fonts and chapters. More values than three, ordered by size, take a stepped `AppSlider` with the value's name at the label's end (the sort menu's cover size).
- Month / Year in the journal, reader settings.
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
- What counts as pages is what the book page shows: a comic's or a PDF's page count, an EPUB's printed pages (only Grimmory knows them). A series tile's length is all its issues together, as the Pages sort counts it.
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

The search field: 40 tall (56 in the library bar), `radius-control` 12, `surface-container-high`, a search icon, a placeholder that names what it searches, Clear once there is text, and an optional trailing slot (the connection state while a source is offline: `AppStatusPill` in the neutral tone; a tap retries).

- Kit: `AppSearchField(query, onQueryChange, placeholder, containerColor, trailingContent, onClick, onSearch)`.
- In the library bar it is centred on the bar, up to 640 (see AppTopBar). The placeholder says the place: "Search Kavita", "Search Neon Ronin", "Search shelves".
- **The library bar's field is a door, not a field**: `onClick` makes it a button that opens the search panel (`AppSearchPanel`), where you type. While a search is applied to All books (after Show all) it shows the words and Clear.
- `onSearch` is what the keyboard's search key does; without it the key only hides the keyboard.
- On a dialog, pass `AppSearchFieldDefaults.dialogContainerColor`.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:12px;width:480px">
  <div class="yb-search">{ic("search")}<span>Search Kavita</span></div>
  <div class="yb-search" style="color:var(--on-surface)">{ic("search")}<span style="flex:1">lantern</span>{ic("close")}</div>
</div>""", width=528)

comp("AppSearchPanel", "Surfaces", 640, """
# AppSearchPanel

Search in the middle of the screen, like Spotlight: the library bar's field grows into a panel over the dimmed library, and you type there.

- Kit: `AppSearchPanel(query, onQueryChange, placeholder, onDismissRequest, anchor, onSearch, content, footer)`. It is its own window, like a sheet.
- **Where**: its top and its centre are the bar field's (`anchor`), so it opens out of the field; 680 wide at most, `gutter` from the window's sides. It ends `gutter` above the keyboard or the navigation bar; with the keyboard up it holds what fits and scrolls the rest.
- **Shape**: `surface-container`, `radius-sheet` 28, the sheet shadow. The field row is 64 (the field without its own box or Clear; Close sits at its end), then an `AppSheetDivider`, the results, and an optional footer under another divider. While the keyboard is up the footer steps aside so the results get the room.
- **Leaving**: a tap outside, Back, or Close in the field row puts the search back as it was when the panel opened. Opening a result or Show all closes it.
- **The library's results** (`LibrarySearchPanel`):
  - **The tags a search names** first: "Filter by" in `on-surface-variant`, then an `AppChip` per tag, genre or mood the words name, its section quieter and its count after it ("Set in Japan 96"), whole names first, on one line that scrolls sideways. Picking one applies it to All books as a filter and closes the panel.
  - **Books** (`AppSheetLabel` with the count), then up to 50 `AppBookRow`s. A row says why it matched when it isn't the title or a name: the sentence of the description that holds the words, marked with `primary-container`, two lines at most, with "In the description"; or, for a tag or genre, one line named as Filters names it ("Set in **Japan**"). Read state at the row's end (Finished, 12%).
  - **The first row is highlighted** (`surface-selected`, `radius-control`): the keyboard's search key opens it.
  - **Footer**: "Show all 446 in All books" with a chevron, in `primary`: closes the panel and leaves the search on All books, as covers. The bar's field then shows the words; tapping it opens the panel again.
  - Nothing typed: one quiet line saying what search looks in. No match: "No books match “…”".
""", f"""
<div class="yb yb-ground" style="padding:24px;width:560px;background:color-mix(in srgb, var(--background) 60%, black)">
  <div class="yb-search-panel">
    <div class="yb-search-panel-field">{ic("search")}<span style="flex:1;color:var(--on-surface)">war</span><button class="yb-icon-btn">{ic("close")}</button></div>
    <div class="yb-divider" style="margin:0 20px"></div>
    <div style="display:flex;gap:8px;align-items:center;padding:12px 20px 4px;overflow:hidden;white-space:nowrap"><span class="yb-sheet-label">Filter by</span><span class="yb-chip"><span style="opacity:.72">About</span> War <span class="yb-count">183</span></span><span class="yb-chip"><span style="opacity:.72">When</span> World War II <span class="yb-count">65</span></span></div>
    <div style="padding:14px 20px 8px"><span class="yb-sheet-label">Books 446</span></div>
    <div class="yb-book-row is-selected" style="margin:0 8px;padding:8px 12px">{cover(0)}<div style="flex:1;min-width:0"><div class="yb-book-title">A Drink Before the War</div><div class="yb-book-line">Dennis Lehane</div></div></div>
    <div class="yb-book-row" style="margin:0 8px;padding:8px 12px">{cover(3)}<div style="flex:1;min-width:0"><div class="yb-book-title">A Rumor of War</div><div class="yb-book-line">Philip Caputo</div></div><span class="yb-supporting">Finished</span></div>
    <div class="yb-book-row" style="margin:0 8px;padding:8px 12px">{cover(4)}<div style="flex:1;min-width:0"><div class="yb-book-title">Civil War</div><div class="yb-book-line">Peter Ackroyd</div></div><span class="yb-supporting">12%</span></div>
    <div class="yb-divider" style="margin:8px 20px 0"></div>
    <div style="display:flex;align-items:center;height:52px;padding:0 20px;font:600 14px/20px var(--font-sans);color:var(--primary)"><span style="flex:1">Show all 446 in All books</span>{ic("navigate_next")}</div>
  </div>
</div>""", width=608)

# ---------------------------------------------------------------- Feedback
comp("AppEmptyState", "Feedback", 360, """
# AppEmptyState

An empty or failed place: an illustration or icon, a 28 SemiBold title, a 14/20 line, up to two actions 12 apart. `inset-dialog` 24 at the sides, 40 above and below.

- Kit: `AppEmptyState`. Every empty state uses it (an empty source, no downloads, no matches, a reader error).
- "No matches" inside a filter facet or a list dialog is a short `AppEmptyState` too, not local text.
""", f"""
<div class="yb yb-ground" style="width:420px">
  <div class="yb-empty"><span style="color:var(--on-surface-variant)">{ic("download_done", 56)}</span><h2 class="yb-empty-title">Nothing downloaded</h2><p class="yb-empty-body">Downloaded books open without the server. Use Download on a book's page, or long-press covers to pick several.</p></div>
</div>""", width=468)

comp("AppStatusPill", "Feedback", 160, """
# AppStatusPill

A source's or a queue's state as a dot and a word, with no container: an 8 dot, 8 before the label in 14 Medium. Good is the dot in `primary` with `on-surface` text; neutral (Checking, Waiting) the dot in `outline` with `on-surface-variant` text; a problem the dot and the text in `error`. 28 tall, so it lines up with the controls beside it.

- Kit: `AppStatusPill(label, tone)` with `AppStatusTone.Good`, `Neutral` or `Problem`; the kit picks the colours.
- The source page and the sync page use it.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;gap:10px;flex-wrap:wrap">
  <span class="yb-pill is-good">Connected</span>
  <span class="yb-pill">Checking</span>
  <span class="yb-pill is-bad">Sign-in needed</span>
</div>""")

comp("AppProgressBar", "Feedback", 160, """
# AppProgressBar

A thin bar: 4 tall, `primary` on `surface-container-highest`, pill ends.

- Kit: `AppProgressBar(fraction, height, progressColor, trackColor)`; a null fraction is Material's moving bar on the same track. The update download (and its row in About), a server's Sync row in Settings, first launch's sync, downloads, the journal's What you read and the EPUB contents footer use it.
- A new fraction slides there with AppMotion's effects spring instead of jumping; it doesn't overshoot, so a filling bar never runs past the work. A bar whose fraction stays put doesn't move.
- **A sync's bar.** Moving while its length can't be known (connecting, the book list), then filled by the work it can count: saving, the uploads, each book's reading history. The fill never goes back, even when a step is retried. The step is the row's supporting text with the bar under it, at most 320 wide as in About: "Getting the book list", "Reading history · 12 of 80 books".
- The reading card's chapter-split bar and the cover's progress strip stay their own: they draw on a cover tint and a cover.
""", f"""
<div class="yb yb-ground" style="padding:24px;display:flex;flex-direction:column;gap:14px;width:420px">
  <div class="yb-progress"><i style="width:4%"></i></div>
  <div class="yb-progress"><i style="width:58%"></i></div>
  <div class="yb-progress"><i style="width:96%"></i></div>
  <div style="display:flex;flex-direction:column;gap:6px;max-width:320px;margin-top:6px"><span class="yb-supporting">Reading history · 12 of 80 books</span><div class="yb-progress"><i style="width:30%"></i></div></div>
</div>""", width=468)

# ---------------------------------------------------------------- Foundations
def tiles(values, cols, selected=(), more=None):
    cells = "".join(
        f'<div class="yb-tile{" is-selected" if name in selected else ""}"><b>{name}</b><small>{count}</small></div>'
        for name, count in values
    )
    if more:
        cells += f'<div class="yb-tile is-more"><b>{more[0]}</b><small>{more[1]}</small></div>'
    return f'<div class="yb-tiles" style="--yb-cols:{cols}">{cells}</div>'


def check_row(name, count, checked=False, share=None, indented=False):
    cls = "yb-check-row" + (" is-checked" if checked else "") + (" is-indented" if indented else "")
    bar = f'<i class="yb-share" style="width:calc((100% - 40px) * {share})"></i>' if share is not None else ""
    tick = ic("check") if checked else ""
    return (f'<div class="{cls}">{bar}<span class="yb-box" style="margin-left:{8 if share is not None else 0}px">{tick}</span>'
            f'<span>{name}</span><span class="yb-count">{count}</span></div>')


def letter_grid(letters, empty):
    return '<div class="yb-letters">' + "".join(
        f'<span class="{"is-empty" if c in empty else ""}">{c}</span>' for c in letters) + "</div>"


def stack(name, line, indices):
    art = "".join(cover(i) for i in indices[::-1])
    return f'<div class="yb-stack"><div class="yb-stack-art">{art}</div><b>{name}</b><small>{line}</small></div>'


def timeline_row(label, count, kind=""):
    cls = "yb-timeline-row" + (f" is-{kind}" if kind else "")
    return f'<div class="{cls}"><span class="yb-rail"><span class="yb-dot"></span></span><span>{label}</span><span class="yb-count">{count}</span></div>'


comp("AppTileGrid", "Selection", 260, """
# AppTileGrid

A few short choices with their counts, as tiles you tap: a library, a reading status, a mood, a length. Quieter than chips and easier to hit; the counts sit under the names, so the tiles line up.

- Kit: `AppTileGrid(tiles, onToggle)` with `AppTile(label, supporting, selected)`; `more` adds a last tile ("4 more") that opens the section's page.
- A tile: at least 56 tall, `radius-control` 12, `surface-container` (a step above the sheet's `surface`), the name in 14 Medium `on-surface`, the line under it 12 `on-surface-variant` in tabular figures. Selected: `primary-container` with a 1 `primary` edge, both lines `on-primary-container`.
- Columns: 2 for 2 or 4 tiles or when a name is longer than 11 characters (Medium-paced), 3 otherwise; `grid-gap` 8 both ways. More than six choices show five and a "N more" tile with a hairline edge and its name in `primary`.
- Filters: Library, Reading status, Mood and Length. Not for long lists of names; those are `AppCheckRow`s or chips.
""", f"""
<div class="yb yb-ground" style="padding:20px;display:flex;flex-direction:column;gap:16px;max-width:420px">
  <h3 class="yb-sheet-label">Reading status</h3>
  {tiles([("Not started", "2,097"), ("In progress", "8"), ("Finished", "4")], 3, selected=("In progress",))}
  <h3 class="yb-sheet-label">Library, nine of them</h3>
  {tiles([("Books", "1,102"), ("Russian", "200"), ("Comics", "214"), ("Manga", "118"), ("Nonfiction", "300")], 3, more=("4 more", "Magazines, Kids…"))}
</div>""")


comp("AppCheckRow", "Selection", 300, """
# AppCheckRow

One value of a list you can pick several of: a checkbox, the name, the count at the end. With `share`, a faint bar behind the row shows how big the value is next to the biggest in its list.

- Kit: `AppCheckRow(label, count, checked, onClick, share, indent)`.
- 44 tall (40 indented), the box 20 with 5 corners, `outline` edge; checked fills `primary` with an `on-primary` check. The name in 15 Regular, the count 13 `on-surface-variant` in tabular figures at the end.
- `share` (0 to 1): a `surface-container-low` bar from the box's start, `radius-inner` 8, 6 in from the row's top and bottom, as long as that share of the row less the count's room.
- `indent`: 26 in, for a city under its country.
- Filters: Language, Format, Location; Set in with its share bars, a picked country's cities indented under it.
""", f"""
<div class="yb yb-ground" style="padding:20px;display:flex;flex-direction:column;max-width:420px">
  <h3 class="yb-sheet-label" style="margin-bottom:6px">Language</h3>
  {check_row("English", "1,503")}{check_row("Russian", "605", checked=True)}{check_row("Portuguese", "1")}
  <h3 class="yb-sheet-label" style="margin:14px 0 6px">Set in</h3>
  {check_row("Russia", "402", checked=True, share=1)}{check_row("Moscow", "120", share=0.3, indented=True)}{check_row("England", "288", share=0.72)}{check_row("France", "140", share=0.35)}
</div>""")


comp("AppLetterGrid", "Selection", 240, """
# AppLetterGrid

The alphabet as keys, to open a long list of names at a letter: Author and Publisher in Filters. Cyrillic first when the library holds Russian names, then Latin.

- Kit: `AppLetterGrid(letters, available, onLetter)`.
- Nine keys to a row, 34 tall, `radius-inner` 8, `surface-container-low`, the letter in 13 Medium. A letter no name starts with is drawn at 35% and does nothing.
- A tap opens the section's page scrolled to that letter's group; what is picked shows at the end of the section's label ("Ivan Bunin", or "Any").
""", f"""
<div class="yb yb-ground" style="padding:20px;display:flex;flex-direction:column;gap:8px;max-width:420px">
  <div style="display:flex"><h3 class="yb-sheet-label" style="flex:1">Author</h3><span class="yb-supporting" style="font-size:13px">Any</span></div>
  {letter_grid("АБВГДЕЖЗИКЛМНОПРСТУФХЧШЭЯ", "ЖЗХЧШЭ")}
  {letter_grid("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "QXYZUIOV")}
</div>""")


comp("AppCoverStack", "Lists", 230, """
# AppCoverStack

A series as a thing you can see: its first three covers fanned, the name under them and how far you are ("41 · 12 read"). A row of them scrolls sideways.

- Kit: `AppCoverStack(name, line, selected, onClick, cover = { index, modifier -> ... })`; the caller draws the covers (the library's cover composable) in the modifiers it gets.
- 96 wide; the covers 64 wide, the back two turned 6° and 2° and set 20 and 10 to the end, the front one with the small cover shadow. Name 13 Medium, line 12 `on-surface-variant`. Selected: the front cover gets a 2 `primary` ring.
- Filters › Series: the series with most books, then "All 86" at the label's end opens the page.
""", f"""
<div class="yb yb-ground" style="padding:20px;display:flex;flex-direction:column;gap:12px">
  <div style="display:flex"><h3 class="yb-sheet-label" style="flex:1">Series</h3><span class="yb-supporting" style="font-size:13px;color:var(--primary);font-weight:600">All 86</span></div>
  <div class="yb-stacks">{stack("The Lantern Keepers", "6 · 2 read", [0, 4, 6])}{stack("Дом у реки", "3 · all read", [1, 2, 3])}{stack("Star Courier", "12 · 1 read", [5, 7, 0])}{stack("Paper Cranes", "4", [7, 1, 4])}</div>
</div>""")


comp("AppTimeline", "Selection", 420, """
# AppTimeline

Time as one line down the start: a dot per pile (a century or a decade) in time order, and the named periods (Russian Revolution, World War II, Cold War) placed where they fall, smaller and in italics.

- Kit: `AppTimeline(entries, onToggle)` with `AppTimelineEntry(label, count, selected, period)`.
- Rows 40 tall; the line 2 `outline-variant` in a 20 wide rail; a pile's dot 10 `outline-variant`, a period's 8 `outline`, both ringed in the ground. Selected: the dot 14 `primary`, the label SemiBold. Label 14 Medium (a period 13 italic `on-surface-variant`), the count at the end.
- Filters › When, in the panel itself. A whole century with no pile of its own (20th century) is a stop just before its first decade; a period with no known years goes after the piles.
""", f"""
<div class="yb yb-ground" style="padding:20px;display:flex;flex-direction:column;max-width:420px">
  <h3 class="yb-sheet-label" style="margin-bottom:8px">When</h3>
  <div class="yb-timeline">{timeline_row("1800s", 198)}{timeline_row("1910s", 88)}{timeline_row("Russian Revolution", 31, "period")}{timeline_row("1940s", 140, "selected")}{timeline_row("World War II", 40, "period")}{timeline_row("1960s", 40)}{timeline_row("Cold War", 22, "period")}{timeline_row("Future", 88)}</div>
</div>""")


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
