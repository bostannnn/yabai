# Screen mocks for the yabai design system (dp = px). Run after gen_components.py.
import sys
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))
from gen_components import comp, ic, row, group, cover, theme_preview, chev, DIV  # noqa: E402

TOGGLE_SCRIPT = """<script>
(function(){
  if (window.Yabai) Yabai.hydrate();
  var box = document.getElementById("spec");
  var root = document.getElementById("screens");
  if (!box || !root) return;
  try { if (localStorage.getItem("yb-spec") === "1") { box.checked = true; root.classList.add("yb-show-spec"); } } catch (e) {}
  box.addEventListener("change", function () {
    root.classList.toggle("yb-show-spec", box.checked);
    try { localStorage.setItem("yb-spec", box.checked ? "1" : "0"); } catch (e) {}
  });
})();
</script>"""

TOGGLE = '<label class="yb-spec-toggle"><input type="checkbox" id="spec"> Show spacing</label>'


def statusbar():
    return '<div class="yb-statusbar"><span>10:48</span><span>&#9662; &#9646;</span></div>'


def g(x, label=None, top=40):
    lab = f'<span class="t" style="left:{x + 4}px;top:{top}px">{label}</span>' if label else ""
    return f'<span class="g" style="left:{x}px"></span>{lab}'


def h(y, label=None, left=8):
    lab = f'<span class="t" style="left:{left}px;top:{y - 18}px">{label}</span>' if label else ""
    return f'<span class="h" style="top:{y}px"></span>{lab}'


def rail(selected):
    def item(icon, label):
        cls = "yb-rail-item is-selected" if label == selected else "yb-rail-item"
        return f'<div class="{cls}"><span>{ic(icon)}</span><span>{label}</span></div>'
    return (f'<nav class="yb-rail" style="justify-content:space-between;height:100%">'
            f'<div style="display:flex;flex-direction:column;gap:12px;align-items:center">{item("collections_bookmark", "Kavita")}{item("cloud", "Grimmory")}{item("folder", "Local")}{item("add", "Add")}</div>'
            f'<div style="display:flex;flex-direction:column;gap:12px;align-items:center">{item("history", "Journal")}{item("settings", "Settings")}</div></nav>')


def theme_tile(name, bg, a, b, c, selected=False):
    edge = "box-shadow:inset 0 0 0 2px var(--on-surface);" if selected else ""
    check = f'<span style="color:var(--on-surface)">{ic("check", 18)}</span>' if selected else ""
    return (f'<div style="background:var(--surface-container);border-radius:var(--radius-container);padding:10px;{edge}display:flex;flex-direction:column;gap:8px">'
            f'<div style="height:96px;border-radius:var(--radius-control);background:{bg};display:flex;align-items:center;justify-content:center;gap:6px">'
            f'<i style="width:22px;height:40px;border-radius:3px;background:{a}"></i><i style="width:22px;height:40px;border-radius:3px;background:{b}"></i><i style="width:22px;height:40px;border-radius:3px;background:{c}"></i></div>'
            f'<div style="display:flex;justify-content:space-between;align-items:center;font:500 14px/20px var(--font-sans)"><span>{name}</span>{check}</div></div>')


HUB_GROUPS = [
    ("Appearance", [("Theme", "Graphite", "palette", "theme")]),
    ("Sources", [("Kavita", "Connected", "collections_bookmark", None), ("Stump", "Not connected", "menu_book", None), ("Grimmory", "Connected", "cloud", None)]),
    ("Library", [("Filter chips", "Which filters each library shows, and in what order", "filter_list", None), ("Source order", "Kavita opens on launch", "swap_vert", None), ("Reading progress sync", "Up to date", "refresh", None)]),
]


def hub(selected_theme=True):
    out = []
    for label, rows in HUB_GROUPS:
        rs = []
        for title, sup, icon, extra in rows:
            tr = (theme_preview() + chev()) if extra == "theme" else "chev"
            rs.append(row(title, sup, icon, tr, selected=(selected_theme and title == "Theme")))
        out.append(group(label, rs))
    return "".join(out)


def theme_page():
    light = [("Paper", "#fff8f0", "#405f6b", "#476a53", "#86504e"), ("Sepia Reading", "#f7ebd8", "#665037", "#5f5941", "#7b4e3f"), ("Panel Pop", "#fffdf6", "#006e8f", "#c8176b", "#8a6500")]
    dark = [("Graphite", "#101214", "#b7c7d9", "#c4cfd8", "#e4b8a7"), ("High Contrast", "#000000", "#ffffff", "#ffd166", "#82d4ff"), ("OLED Reader", "#000000", "#b9c7ff", "#bfc6d8", "#e5b8c8")]
    tiles_l = "".join(theme_tile(*t) for t in light)
    tiles_d = "".join(theme_tile(*t, selected=(t[0] == "Graphite")) for t in dark)
    return (f'<p class="yb-supporting" style="margin:0">Use neutral dark surfaces with stronger layer contrast for repeated browsing.</p>'
            f'<section class="yb-group"><h3 class="yb-group-label">Light</h3><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px">{tiles_l}</div></section>'
            f'<section class="yb-group"><h3 class="yb-group-label">Dark</h3><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px">{tiles_d}</div></section>')


# ------------------------------------------------------------------ Settings
W, H = 1280, 800
settings_expanded = f"""
<div class="yb-device" style="width:{W}px;height:{H}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Settings")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div style="position:absolute;top:84px;left:24px;width:320px;display:flex;flex-direction:column;gap:24px">{hub()}</div>
      <div style="position:absolute;top:84px;left:368px;right:24px;display:flex;flex-direction:column;gap:24px;max-width:800px">{theme_page()}</div>
      <div class="yb-spec">{g(24)}{g(344)}{g(368)}<span class="t" style="left:28px;top:30px">24 gutter</span><span class="t" style="left:140px;top:52px">320 list pane</span><span class="t" style="left:346px;top:30px">24</span>{h(84)}<span class="t" style="left:700px;top:62px">84 content top, both panes</span></div>
    </div>
  </div>
</div>"""

PW, PH = 412, 915
settings_compact = f"""
<div class="yb-device yb-compact" style="width:{PW}px;height:{PH}px">
  {statusbar()}
  <div class="yb-device-body">
    <div class="yb-pane">
      <div class="yb-bar"><button class="yb-icon-btn">{ic("menu")}</button></div>
      <div class="yb-scroll">{hub(selected_theme=False)}</div>
      <div class="yb-spec">{g(16)}{g(PW - 16)}<span class="t" style="left:20px;top:30px">16</span>{h(84)}<span class="t" style="left:300px;top:62px">84</span></div>
    </div>
  </div>
</div>"""

comp("SettingsScreen", "Screens", 1820, """
# Settings

The settings hub and its pages, on the frame in Layout and spacing.

- **No "Settings" title.** The hub starts with its first group, Appearance, at content top (84). On Compact and Medium the bar holds only the menu button; on Expanded there is no bar content at all.
- **Expanded: two panes on one top line.** The hub is the 320 list pane on the gutter; the open page follows 24 later and is at most 800 wide. Both start at content top, so the hub's first group label and the page's first line share a top edge.
- **Gutter 24** from Medium, 16 on phones; the panes are a gutter apart. Group labels sit on the panel's edge, and so does a page's one-line intro.
- A page opened on Compact or Medium shows Back and its name in the bar, and the bar has no glass until something scrolls under it.
- The open page is marked by the selected hub row (`primary-container`); the page itself carries no title.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens" style="display:flex;flex-direction:column;gap:28px">
    <div><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800: rail, hub, Theme page</div>{settings_expanded}</div>
    <div style="display:flex;gap:28px;align-items:flex-start">
      <div><div class="yb-caption" style="margin-bottom:8px">Compact, 412: the hub</div>{settings_compact}</div>
      <div class="yb-caption" style="max-width:520px;padding-top:24px;line-height:20px">
        Switch on <b>Show spacing</b> to see the gutter, the list pane and the content-top line. The rows, groups and panels are the kit's (AppSettingsGroup, AppSettingsRow); nothing here is hand-made.
      </div>
    </div>
  </div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)

# ------------------------------------------------------------------ Library home (Medium portrait)
LW, LH = 800, 1010
cols = 4
grid_tiles = "".join(
    f'<div style="display:flex;flex-direction:column;gap:6px">{cover(i)}<div style="display:flex;justify-content:space-between;align-items:flex-start"><div style="min-width:0"><div class="yb-book-title" style="font-size:14px">{t}</div><div class="yb-caption">{m}</div></div><span style="color:var(--on-surface-variant)">{ic("more_horiz", 20)}</span></div></div>'
    for i, (t, m) in enumerate([("Ferry Notes", "Just started"), ("The Lantern Keepers", "4%"), ("Quiet Engines", "Finished"), ("Moths of Lisbon", "Finished")])
)
strip_tiles = "".join(
    f'<div style="width:176px;display:flex;flex-direction:column;gap:6px">{cover(i)}<div class="yb-book-title" style="font-size:14px">{t}</div><div class="yb-caption">{m}</div></div>'
    for i, (t, m) in [(1, ("Дом у реки", "96%")), (5, ("Star Courier #2", "58%"))]
)
library = f"""
<div class="yb-device" style="width:{LW}px;height:{LH}px">
  {statusbar()}
  <div class="yb-device-body">
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-bar"><button class="yb-icon-btn">{ic("menu")}</button><div class="yb-search">{ic("search")}<span>Search Kavita</span></div><span class="yb-spacer"></span><button class="yb-icon-btn">{ic("download")}</button><button class="yb-icon-btn">{ic("more_horiz")}</button></div>
      <div class="yb-scroll">
        <div style="background:#26365a;color:#eef2fb;border-radius:var(--radius-container);padding:20px;display:flex;gap:20px">
          <div style="width:176px;flex:none">{cover(0)}</div>
          <div style="flex:1;display:flex;flex-direction:column;gap:6px;min-width:0">
            <span class="yb-overline">Continue reading · today</span>
            <span style="font:700 28px/34px var(--font-sans)">The Lantern Keepers</span>
            <span style="font-size:15px;opacity:.8">Oona Fitch</span>
            <span style="font:600 20px/26px var(--font-sans);margin-top:10px">Chapter 1 of 6</span>
            <span style="flex:1"></span>
            <span style="font-size:13px;opacity:.85">4% · Page 4 of 74</span>
            <div style="height:4px;border-radius:999px;background:rgba(255,255,255,.18);overflow:hidden"><i style="display:block;width:4%;height:100%;background:#eef2fb"></i></div>
            <div style="display:flex;gap:10px;margin-top:10px"><button class="yb-btn" style="background:#dbe4f6;color:#1b2740">{ic("menu_book")}Continue</button><button class="yb-btn" style="background:rgba(255,255,255,.14);color:#eef2fb">Details</button></div>
          </div>
        </div>
        <div style="display:flex;flex-direction:column;gap:8px">
          <div style="display:flex;align-items:center"><div class="yb-tabs" style="flex:1"><span class="yb-tab is-selected">In progress <span class="yb-count">3</span></span><span class="yb-tab">Up next <span class="yb-count">1</span></span><span class="yb-tab">Finished <span class="yb-count">12</span></span></div><button class="yb-btn is-text">See all</button></div>
          <div style="display:flex;gap:16px">{strip_tiles}</div>
        </div>
        <div style="display:flex;flex-direction:column;gap:8px">
          <div style="display:flex;align-items:center;gap:8px"><h2 class="yb-section-heading" style="flex:1">All books <span class="yb-count">1,474</span></h2><span class="yb-chip">{ic("collections_bookmark")}Browse</span><span class="yb-chip">{ic("filter_list")}Filters</span><span class="yb-chip">{ic("tune")}Last read</span></div>
          <div style="display:grid;grid-template-columns:repeat({cols},1fr);gap:16px">{grid_tiles}</div>
        </div>
      </div>
      <div class="yb-spec">{g(24)}{g(LW - 24)}<span class="t" style="left:28px;top:30px">24 gutter</span><span class="t" style="left:{LW - 46}px;top:30px">24</span>{h(84)}<span class="t" style="left:560px;top:90px">84 content top</span></div>
    </div>
  </div>
</div>"""

comp("LibraryHome", "Screens", 1100, """
# Library home

A source's library: the bar with its search field, the reading card, the shelf tabs and strip, then All books and the grid.

- **Gutter 24** from Medium (16 on phones): the bar's glass, the reading card, the tabs, the heading and the grid share both edges.
- Content top 84, bottom 24. The grid's gap is 16.
- All books is `AppSectionHeading`, on the gutter line, with Browse, Filters and sort at its end. Browse opens the Browse page (see Browse); Filters stays for the grid's own filters.
- The reading card's inside inset is `inset` 20 at every width and its corners `radius-container` 20.
- No source name anywhere on the screen: the search field says it.
- **The jump rail** runs down the right edge for a sort that groups: the letters for Title, Author and Series, and for Pages the page ranges the filter's slider stops at (0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1k), each where its range starts (150 holds 150 to 199), then `?` for books with no page count. Only sections with books show.
- **Sorted by Pages** (shortest first, unknown lengths last), the line under each cover gives its length instead of its progress: "212 pages", a series "212 pages · 8 issues". A series' length is all its issues together, for the sort and the Pages filter alike.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Medium, 800 wide (the emulator's portrait)</div>{library}</div>
</div>""", width=832, page=True, script=TOGGLE_SCRIPT)

# ------------------------------------------------------------------ Browse (Expanded landscape)
BW, BH = 1280, 800


def chips(values, selected=()):
    return "".join(
        f'<span class="yb-chip{" is-selected" if v in selected else ""}">{v} <span class="yb-count">{n}</span></span>'
        for v, n in values
    )


browse_groups = "".join(
    f'<div style="display:flex;flex-direction:column;gap:8px"><h3 class="yb-group-label">{label}</h3><div class="yb-chip-row">{chips(values, selected)}</div></div>'
    for label, values, selected in [
        ("Set in", [("United Kingdom", 24), ("London", 15), ("United States", 7), ("Germany", 6)], ()),
        ("When", [("Cold War", 29), ("21st century", 16), ("World War II", 7), ("1950s", 5)], ()),
        ("About", [("Espionage", 59), ("Conspiracy", 7), ("Terrorism", 7), ("War", 4)], ("Espionage",)),
        ("Genre", [("Thriller & Suspense", 44), ("Humor & Satire", 14), ("History", 9)], ()),
        ("Your shelves", [("Spy Fiction", 24), ("European Fiction", 2)], ()),
        ("Your reading", [("Not started", 59)], ()),
    ]
)
browse_tiles = "".join(
    f'<div style="display:flex;flex-direction:column;gap:6px;min-width:0">{cover(i)}<div class="yb-book-title" style="font-size:14px">{t}</div></div>'
    for i, t in enumerate(["The Lantern Keepers", "Дом у реки", "Quiet Engines", "Moths of Lisbon", "Glass Meridian", "Star Courier #2", "The Salt Orchard", "Paper Cranes", "The Lantern Keepers", "Дом у реки"])
)
browse = f"""
<div class="yb-device" style="width:{BW}px;height:{BH}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Grimmory")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-bar"><button class="yb-icon-btn">{ic("arrow_back")}</button><span class="yb-bar-title">Browse</span><button class="yb-icon-btn">{ic("search")}</button><button class="yb-icon-btn">{ic("more_vert")}</button></div>
      <div style="position:absolute;left:24px;top:84px;width:320px;bottom:0;display:flex;flex-direction:column;gap:18px;overflow:hidden">{browse_groups}</div>
      <div style="position:absolute;left:368px;right:24px;top:84px;bottom:0;display:flex;flex-direction:column;gap:16px;overflow:hidden">
        <div style="display:flex;gap:8px;align-items:center"><span class="yb-chip is-selected"><span style="opacity:.72;font-weight:500">About</span> Espionage{ic("close", 18)}</span><button class="yb-btn is-text">Clear</button></div>
        <div style="display:flex;align-items:center;gap:8px"><h2 class="yb-section-heading" style="flex:1">59 books</h2><button class="yb-btn is-tonal">{ic("add")}Save as shelf</button></div>
        <div style="display:grid;grid-template-columns:repeat(5,1fr);gap:16px">{browse_tiles}</div>
      </div>
      <div class="yb-spec">{g(24)}{g(344, "320 pane")}{g(368, "24")}{h(84)}<span class="t" style="left:560px;top:90px">84 content top</span></div>
    </div>
  </div>
</div>"""

comp("LibraryBrowse", "Screens", 900, """
# Browse

A source's books by what they are about: chips in rows, every chip narrowing the books, every count saying how many are left.

- **Opening it.** The Browse chip, first in the All books row before Filters; a tag on the book page (Browse opens with that chip picked, where the tag used to filter the grid); a tag in the search suggestions. Back returns to where you came from. The bar shows Back, "Browse", Find and •••, and turns to glass only once content scrolls under it.
- **Rows, in this order:** Set in (place tags), When (era tags), About (the other tags), Genre (categories), Mood (moods), Your shelves (the server's shelves), Your reading (Not started, In progress, Finished). Author, Series, Publisher and Language are there to switch on.
- **A row is a field of the books** and shows only once some book has it, so new metadata from the server (moods, say) turns up as a new row by itself. Rows (••• in the bar) is an `AppSideSheet` of `AppSettingsRow`s with switches: Shown, Off, and Waiting for metadata. It remembers what you hid, not what you showed.
- **Tags sort themselves** with `libraryTagKind` (core:model): place names the app knows, then eras by their shape (a decade, a century) or a named period; everything else is About.
- **Nothing picked: the landing page.** Set in as region panels (`AppPanel`, inset 20: Americas, Europe, Russia and around, Asia, Middle East and Africa, Oceania) in three columns on Expanded, two on Medium, one on phones, 16 apart; each column stacks its panels at their own height, so a long region leaves no gap beside a short one. When as a timeline (one bar per decade or century, at least 40 wide, as tall as the square root of its count, the count above and the label below in two lines; where the bars don't fit, the timeline scrolls sideways and fades at an edge with more; a period like World War II or the 20th century is a chip under it). Then About, Genre, Your shelves and Your reading as chip rows, each ending in an "All N" text button.
- **Something picked, Expanded:** the chip pane is the 320 list pane on the gutter, each row an `AppGroupLabel` over its chips, the picked chips selected. The results follow a gutter later: the picked chips (selected `AppChip`, the row's name dimmed, a close icon) and Clear, the count as `AppSectionHeading`, then the cover grid at the library's cover size. Medium and Compact: one column, the picked chips on top, the rows as `AppTabPill`s with the chosen row's chips under them, then the grid.
- Every chip narrows, across rows and within one. A chip that would leave nothing doesn't show. Counts are digits.
- Save as shelf keeps the chips, not the books (a Grimmory magic shelf).
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800: one chip picked</div>{browse}</div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)

# ------------------------------------------------------------------ Journal (Expanded landscape)
def stat(num, unit, label, line):
    return (f'<div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:2px;padding:0 20px;border-left:1px solid color-mix(in srgb,var(--outline-variant) 60%,transparent)">'
            f'<span style="font:700 30px/36px var(--font-sans)">{num}<span style="font:600 16px/20px var(--font-sans)"> {unit}</span></span>'
            f'<span style="font:600 15px/20px var(--font-sans)">{label}</span><span class="yb-caption">{line}</span></div>')


days = "".join(
    f'<div style="height:48px;border-radius:10px;padding:4px 6px;font:500 12px/14px var(--font-sans);{("background:var(--surface-container-highest);" if d in (17, 21, 22, 23, 24, 25) else "box-shadow:inset 0 0 0 1px color-mix(in srgb,var(--outline-variant) 60%,transparent);")}">{d}</div>'
    if d > 0 else '<div></div>' for d in [0, 0, 0] + list(range(1, 32))
)
journal = f"""
<div class="yb-device" style="width:{W}px;height:{H}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Journal")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-scroll">
        <div style="display:flex;align-items:center;gap:4px">
          <button class="yb-icon-btn" style="margin-left:-8px">{ic("navigate_before")}</button><h1 class="yb-period">October 2026</h1><button class="yb-icon-btn">{ic("navigate_next")}</button>
          <span style="flex:1"></span><div class="yb-seg" style="width:220px"><span class="is-selected">Month</span><span>Year</span></div>
        </div>
        <div class="yb-panel" style="padding:20px 0;display:flex">
          {stat("15", "h", "Reading time", "About 2 h 9 min on a day you read").replace("border-left:1px", "border-left:0px")}
          {stat("7", "days", "You read", "Every day since 21 Oct")}
          {stat("110", "", "Pages", "Books 85 · comics 25")}
          {stat("15", "", "Finished", "13 waiting for a rating")}
        </div>
        <div style="display:grid;grid-template-columns:1.25fr 1fr;gap:16px">
          <div class="yb-panel" style="padding-bottom:8px">
            <div style="padding:20px 20px 4px"><div style="font:600 17px/24px var(--font-sans)">What you read</div><div class="yb-caption">Longest first · tap one for its rating, review and sessions</div></div>
            <div class="yb-book-row">{cover(0)}<div style="flex:1;min-width:0"><div class="yb-book-title">The Lantern Keepers</div><div class="yb-book-line">Oona Fitch · Book</div></div><span class="yb-supporting">9 h 43 min</span></div>
            {DIV}
            <div class="yb-book-row">{cover(5)}<div style="flex:1;min-width:0"><div class="yb-book-title">Star Courier #2</div><div class="yb-book-line">Finished 17 Oct · Comic</div></div><button class="yb-btn is-tonal">Rate</button></div>
          </div>
          <div class="yb-panel" style="padding:20px">
            <div style="font:600 17px/24px var(--font-sans)">Reading days</div><div class="yb-caption" style="margin-bottom:12px">Minutes each day · tap a day for what you read</div>
            <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:6px">{days}</div>
          </div>
        </div>
      </div>
      <div class="yb-spec">{g(24)}<span class="t" style="left:28px;top:30px">24 gutter</span>{h(84)}<span class="t" style="left:520px;top:62px">84 content top</span></div>
    </div>
  </div>
</div>"""

comp("JournalScreen", "Screens", 900, """
# Journal

The journal's month: the period with its arrows and Month / Year, the numbers, then What you read and Reading days.

- **No "Journal" eyebrow.** The period, "October 2026", is the first content and stays as the title: it is the content, and its arrows move it.
- **Gutter 24** from Medium, 16 on phones; content top 84; bottom 24.
- Panels use `inset` 20 at every width and `grid-gap` 16 between them.
- Book rows are `AppBookRow`.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800</div>{journal}</div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)

print("screens written")
