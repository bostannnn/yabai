# Screen mocks for the yabai design system (dp = px). Run after gen_components.py.
import sys
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))
from gen_components import comp, ic, row, group, cover, theme_preview, chev, DIV, list_cover  # noqa: E402

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
            f'<div style="display:flex;flex-direction:column;gap:12px;align-items:center">{item("checklist", "Lists")}{item("history", "Journal")}{item("settings", "Settings")}</div></nav>')


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
    ("Library", [("Filter chips", "Which filters each library shows, and in what order", "filter_list", None), ("Tags", "3 new tags", "sell", None), ("Source order", "Kavita opens on launch", "swap_vert", None), ("Reading progress sync", "Up to date", "refresh", None)]),
]


def hub(selected_theme=True, selected=None):
    selected = selected or ("Theme" if selected_theme else None)
    out = []
    for label, rows in HUB_GROUPS:
        rs = []
        for title, sup, icon, extra in rows:
            tr = (theme_preview() + chev()) if extra == "theme" else "chev"
            rs.append(row(title, sup, icon, tr, selected=(title == selected)))
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
- **The card follows a reading order** when the book you read last was finished and is in the reading order under way, and that order's next book is from this library: its overline is "Next in The Long Eclipse · 7 of 14", one line under the progress names the two after it ("Then Neon Ronin #13, The Long Eclipse #3"), and its buttons are Read (or Continue) and Open list.
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
- **Tags go where you put them** (Settings › Tags; see Tags): Set in holds the tags you sorted as places and Kavita's Locations, When the ones you sorted as times, About every other tag, unsorted ones included, so Browse never shows a guess as a fact. A long press on a Set in, When or About chip opens the tag's sort sheet.
- **Before any tag is sorted as a place or a time**, the landing page starts with a Places and times `AppPanel` (inset 20): an `AppGroupLabel`, one line ("The app guessed that 104 of your tags are places and 33 are times. Check its guesses once, and Browse shows your books by region under Set in and on a timeline under When."), Not now (hides it for that source) and **Check the guesses**, which opens Settings › Tags on that source. Later, tags waiting to be sorted say so in one line under About's heading ("3 new tags are in About until you sort them") with a Sort text button.
- **Nothing picked: the landing page.** Set in as region panels (`AppPanel`, inset 20: Americas, Europe, Russia and around, Asia, Middle East and Africa, Oceania, Made-up places, Elsewhere) in three columns on Expanded, two on Medium, one on phones, 16 apart; each column stacks its panels at their own height, so a long region leaves no gap beside a short one. When as a timeline (one bar per decade or century, at least 40 wide, as tall as the square root of its count, the count above and the label below in two lines; where the bars don't fit, the timeline scrolls sideways and fades at an edge with more; a period like World War II or the 20th century is a chip under it). Then About, Genre, Your shelves and Your reading as chip rows, each ending in an "All N" text button.
- **Something picked, Expanded:** the chip pane is the 320 list pane on the gutter, each row an `AppGroupLabel` over its chips, the picked chips selected. The results follow a gutter later: the picked chips (selected `AppChip`, the row's name dimmed, a close icon) and Clear, the count as `AppSectionHeading`, then the cover grid at the library's cover size. Medium and Compact: one column, the picked chips on top, the rows as `AppTabPill`s with the chosen row's chips under them, then the grid.
- Every chip narrows, across rows and within one. A chip that would leave nothing doesn't show. Counts are digits.
- Save as shelf keeps the chips, not the books (a Grimmory magic shelf).
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800: one chip picked</div>{browse}</div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)

# ------------------------------------------------------------------ Tags (Settings › Tags, Expanded landscape)


def seg(options, selected):
    return '<div class="yb-seg">' + "".join(
        f'<span class="{"is-selected" if o == selected else ""}">{o}</span>' for o in options) + "</div>"


def text_btn(label):
    return f'<button class="yb-btn is-text" style="flex:none">{label}</button>'


def region_panel(name, caption, values, more=None):
    extra = f'<button class="yb-btn is-text" style="height:36px;padding:0 10px">{more}</button>' if more else ""
    return (f'<div class="yb-panel" style="padding:20px;display:flex;flex-direction:column;gap:10px">'
            f'<h4 class="yb-group-label" style="margin:0">{name} <span class="yb-caption" style="font-size:13px;font-weight:600">{caption}</span></h4>'
            f'<div class="yb-chip-row">{chips(values)}{extra}</div></div>')


tags_new = group(None, [
    row("Iceland", "3 books · Looks like a place · Europe", None, text_btn("Accept")),
    row("Kosovo", "1 book · No guess, in About for now", None, text_btn("Keep in About")),
    row("Weimar Republic", "2 books · No guess, in About for now", None, text_btn("Keep in About")),
])
tags_page = f"""
  {seg(["Kavita", "Stump", "Grimmory", "Local"], "Grimmory")}
  <section class="yb-group"><h3 class="yb-group-label">New <span class="yb-caption" style="font-size:13px;font-weight:600">3 tags</span></h3>{tags_new.replace('<section class="yb-group">', '').replace('</section>', '')}</section>
  <div style="display:flex;flex-direction:column;gap:16px">
    <div style="display:flex;align-items:center;gap:12px"><div class="yb-tabs" style="flex:1"><span class="yb-tab is-selected">Set in <span class="yb-count">102</span></span><span class="yb-tab">When <span class="yb-count">32</span></span><span class="yb-tab">About <span class="yb-count">130</span></span></div><div class="yb-search" style="width:240px">{ic("search")}<span>Find a tag</span></div></div>
    <div style="display:flex;gap:16px;align-items:flex-start">
      <div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:16px">{region_panel("Americas", "18 places", [("United States", 480), ("New York", 75), ("Los Angeles", 40), ("Cuba", 13), ("Mexico", 12), ("San Francisco", 10)], "All 18")}{region_panel("Russia and around", "9 places", [("Russia", 246), ("Moscow", 35), ("Saint Petersburg", 26), ("Ukraine", 9)], "All 9")}</div>
      <div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:16px">{region_panel("Europe", "39 places", [("England", 103), ("France", 95), ("Germany", 60), ("Paris", 59), ("United Kingdom", 52)], "All 39")}{region_panel("Asia", "18 places", [("Japan", 96), ("China", 23), ("Tokyo", 16), ("India", 9)], "All 18")}</div>
    </div>
  </div>"""
tags = f"""
<div class="yb-device" style="width:1280px;height:800px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Settings")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div style="position:absolute;top:84px;left:24px;width:320px;display:flex;flex-direction:column;gap:24px">{hub(selected="Tags")}</div>
      <div style="position:absolute;top:84px;left:368px;right:24px;max-width:800px;display:flex;flex-direction:column;gap:24px">{tags_page}</div>
      <div class="yb-spec">{g(24)}{g(344, "320 list pane")}{g(368, "24")}{h(84)}<span class="t" style="left:700px;top:62px">84 content top, both panes</span></div>
    </div>
  </div>
</div>"""

sort_books = "".join(
    f'<div class="yb-book-row" style="padding:8px 0;min-height:68px">{cover(i, 40, 7)}<div style="min-width:0;display:flex;flex-direction:column"><span class="yb-book-title">{t}</span><span class="yb-book-line">{line}</span></div></div>'
    for i, (t, line) in enumerate([
        ("The Last Great American Picture Show", "Thomas Elsaesser · United States · 1970s"),
        ("5001 Nights at the Movies", "Pauline Kael · 20th century"),
        ("New Hollywood Cinema", "Geoff King · United States · 20th century"),
    ])
)
sort_sheet = f"""
<section class="yb-sheet" style="height:620px">
  <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Hollywood</h2><span class="yb-panel-sub">28 books · Grimmory</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-body">
    <p class="yb-supporting" style="margin:0">The app guessed Set in · Americas, because Hollywood is on its list of place names. Your choice holds for every book with this tag.</p>
    <h3 class="yb-sheet-label" style="padding-top:14px">Browse shows it in</h3>
    {seg(["Set in", "When", "About"], "About")}
    <div class="yb-divider" style="margin:14px 0 0"></div>
    <h3 class="yb-sheet-label" style="padding-top:14px">Books with this tag</h3>
    {sort_books}
    <button class="yb-btn is-text" style="align-self:flex-start;padding:0 8px;margin-left:-8px">All 28 in Browse</button>
  </div>
</section>"""
region_sheet = f"""
<section class="yb-sheet" style="height:440px">
  <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Kosovo</h2><span class="yb-panel-sub">New tag · 1 book · Grimmory</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-body">
    <p class="yb-supporting" style="margin:0">The app doesn't know this name, so it has no guess. The tag is in About until you choose.</p>
    <h3 class="yb-sheet-label" style="padding-top:14px">Browse shows it in</h3>
    {seg(["Set in", "When", "About"], "Set in")}
    <h3 class="yb-sheet-label" style="padding-top:14px">Region</h3>
    <div class="yb-chip-row">{"".join(f'<span class="yb-chip{" is-selected" if r == "Europe" else ""}">{r}</span>' for r in ["Americas", "Europe", "Russia and around", "Asia", "Middle East and Africa", "Oceania", "Made-up places", "Elsewhere"])}</div>
  </div>
</section>"""

comp("LibraryTags", "Screens", 1560, """
# Tags

Settings › Library › Tags: where each source's tags go in Browse and on the book page. You decide; the app only suggests.

- **Three rows.** Every tag is in Set in, When or About, and a place has a region. A tag nobody has sorted is in About, so Browse never shows a guess as a fact.
- **The app's guess** (`libraryTagGuess`, core:model): about 400 place names with their regions, decades and centuries by their shape, and some named periods. It is shown beside the tag, never applied, until you accept it.
- **The page**, opened from the hub's Tags row (on Expanded it is the page pane, the row selected; elsewhere Back and "Tags" in the bar): the sources as an `AppSegmentedRow`, as on Filter chips; it opens on the library's source, or on the source Browse sent you from.
- **New** comes first: the tags nobody has sorted. Up to 10 are `AppSettingsRow`s with the guess as the supporting line ("3 books · Looks like a place · Europe") and one text button, Accept, or Keep in About when there is no guess; a tap on the row opens the tag's sheet. More than 10 (the first time) are grouped by the guess under one line saying what happens and an **Accept all N tags** filled button: Looks like a place (an `AppPanel` with an `AppSheetLabel` per region beside its chips), Looks like a time (On the timeline, then Periods), No guess: stays in About.
- **The sorted tags** follow as `AppTabPill`s, Set in · When · About with their counts, and a Find a tag `AppSearchField` beside them: Set in as region panels in two columns (one on Compact), When as the timeline's tags then the periods, About as chips. A tap on any tag opens its sheet.
- **The sort sheet** (`AppSideSheet`; `AppPanelHeader` with the tag and "28 books · Grimmory", "New tag · ..." while unsorted): one line saying the app's guess and why ("The app guessed Set in · Americas, because Hollywood is on its list of place names."); "Browse shows it in" as an `AppSegmentedRow` (Set in, When, About); for Set in, "Region" as single-choice `AppChip`s (Americas, Europe, Russia and around, Asia, Middle East and Africa, Oceania, Made-up places, Elsewhere; Elsewhere until you pick); then up to five of its books (`AppBookRow`: the author and the book's other tags) and "All N in Browse". A choice saves at once and holds for every book with that tag.
- **A long press** on a Set in, When or About chip in Browse, or on a tag on the book page, opens the same sheet over what you were looking at.
- **Kavita's Locations** (comics, from ComicInfo) are places already: they join Set in as they arrive, under Elsewhere until you give them a region, and New asks only for that ("Location from Kavita · 8 issues · Looks like Asia"); their sheet has no row choice. Grimmory, Stump and local books have tags only.
- **Per source**, like Filter chips: each source's tags are sorted on their own. Choices are kept on the device by name, so a tag that leaves every book keeps its place in case it comes back.
- **The hub row**: `sell` icon, "Tags", and "N tags to check" while nothing is sorted, "N new tags" while some wait, else "Where Browse shows each tag".
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800: three new tags, the sorted ones under Set in</div>{tags}</div>
  <div class="yb-caption" style="margin:24px 0 8px">The sort sheet: a guess corrected, and a new place given its region</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;padding:24px">{sort_sheet}{region_sheet}</div>
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


# ------------------------------------------------------------------ Lists (Expanded landscape)
def list_tile(name, caption, indices, smart=False):
    badge = (f'<span style="position:absolute;right:8px;top:8px;width:32px;height:32px;border-radius:50%;background:var(--surface-container-high);'
             f'display:flex;align-items:center;justify-content:center">{ic("tune", 18)}</span>') if smart else ""
    return (f'<div><div style="position:relative">{list_cover(indices)}{badge}</div>'
            f'<div class="yb-book-title" style="margin-top:6px;font-size:14px">{name}</div><div class="yb-caption">{caption}</div></div>')


LISTS_H = 1180  # the whole page, down to the Grimmory shelves


def check_box(on):
    if on:
        return (f'<span style="width:22px;height:22px;border-radius:6px;background:var(--primary);color:var(--on-primary);'
                f'display:flex;align-items:center;justify-content:center;flex:none">{ic("check", 18)}</span>')
    return '<span style="width:22px;height:22px;border-radius:6px;box-shadow:inset 0 0 0 2px var(--outline);flex:none"></span>'


lists = f"""
<div class="yb-device" style="width:{W}px;height:{LISTS_H}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Lists")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-bar"><div class="yb-search">{ic("search")}<span>Search lists</span></div><span class="yb-spacer"></span><button class="yb-icon-btn">{ic("add")}</button><button class="yb-icon-btn">{ic("more_vert")}</button></div>
      <div class="yb-scroll">
        <div class="yb-panel" style="padding:20px;display:flex;gap:20px;align-items:center">
          {cover(5, width=88)}
          <div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:4px">
            <span class="yb-caption" style="font-weight:600">Your order · 7 of 14 · read yesterday</span>
            <span style="font:700 24px/30px var(--font-sans)">The Long Eclipse</span>
            <span class="yb-supporting">Next: Star Courier #2</span>
            <div class="yb-progress" style="margin-top:8px"><i style="width:43%"></i></div>
          </div>
          <button class="yb-btn is-filled">Continue</button>
        </div>
        <div class="yb-group">
          <h2 class="yb-section-heading">Your lists <span class="yb-count">4</span></h2>
          <div style="display:grid;grid-template-columns:repeat(6,1fr);gap:16px">
            {list_tile("Want to read", "9 books", [1, 2, 4, 7])}
            {list_tile("Stalled", "Updates itself · 4", [3, 0, 6, 2], smart=True)}
            {list_tile("The Long Eclipse", "Your order · 6 of 14", [0, 3, 5, 6])}
            {list_tile("Comfort rereads", "8 books", [7, 1, 2, 3])}
          </div>
        </div>
        <div class="yb-group">
          <h2 class="yb-section-heading">Kavita reading lists <span class="yb-count">2</span></h2>
          <div style="display:grid;grid-template-columns:repeat(6,1fr);gap:16px">
            {list_tile("Harbor Lights, in order", "32 issues", [5, 5, 5, 5])}
            {list_tile("Eclipse War", "22 issues", [0, 4, 5, 3])}
          </div>
        </div>
        <div class="yb-group">
          <h2 class="yb-section-heading">Grimmory shelves <span class="yb-count">2</span></h2>
          <div style="display:grid;grid-template-columns:repeat(6,1fr);gap:16px">
            {list_tile("Book club", "5 books · by title", [6, 2, 4, 1])}
            {list_tile("Next summer", "7 books · date added", [0, 7, 3, 2])}
          </div>
        </div>
      </div>
      <div class="yb-spec">{g(24)}<span class="t" style="left:28px;top:30px">24 gutter</span>{h(84)}<span class="t" style="left:520px;top:62px">84 content top</span></div>
    </div>
  </div>
</div>"""



def check_row(title, sup, on):
    return (f'<div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">{title}</span>'
            f'<span class="yb-supporting">{sup}</span></span>{check_box(on)}</div>')


add_to_list = f"""
<div class="yb-sheet" style="height:720px">
  <div class="yb-panel-header has-back"><button class="yb-icon-btn">{ic("arrow_back")}</button><div class="yb-panel-header-text"><h2 class="yb-panel-title">Add to list</h2><span class="yb-panel-sub">The Salt Orchard</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  {check_row("Want to read", "9 books", False)}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">Your lists</h3></div>
  {check_row("The Long Eclipse", "14 books", False)}
  {check_row("Comfort rereads", "8 books", True)}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">Grimmory shelves</h3></div>
  {check_row("Book club", "5 books", True)}
  {check_row("Next summer", "7 books", False)}
  {row("New list", icon="add", trailing=None, dense=True)}
  <p class="yb-caption" style="padding:12px 20px 0">Your lists stay on this tablet and can mix books from every source. Grimmory shelves hold only Grimmory books and are kept on the server.</p>
</div>"""

new_list = f"""
<div class="yb-dialog" style="width:380px">
  <h2 class="yb-dialog-title">New list</h2>
  <span class="yb-field"><span class="yb-field-label">Name</span>Book club</span>
  <div class="yb-seg"><span>On this device</span><span class="is-selected">On Grimmory</span></div>
  <div class="yb-supporting" style="color:var(--on-surface-variant)">A Grimmory shelf, also in Grimmory's web app. Grimmory books only, in no set order.</div>
  <div class="yb-dialog-actions"><button class="yb-btn is-text">Cancel</button><button class="yb-btn is-filled">Create</button></div>
</div>"""

in_your_lists = f"""
<div class="yb-sheet" style="height:auto;padding-bottom:12px">
  <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Neon Ronin #12</h2><span class="yb-panel-sub">Neon Ronin · 12 of 20</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">In your lists</h3></div>
  {row("The Long Eclipse", "Number 6 of 14 · Read next follows this list", dense=True)}
  {row("Want to read", "9 books", dense=True)}
</div>"""

keep_row = f"""
<div class="yb-panel" style="width:560px">{row("Keep the next 3 downloaded", "Neon Ronin #12 and Harbor Lights #7 are ready; The Long Eclipse #3 is on its way.", icon="download", trailing="switch-on")}</div>"""

list_finished = f"""
<div class="yb-dialog" style="width:400px;gap:16px">
  <div style="display:flex;gap:16px;align-items:flex-end">{list_cover([0, 5, 4, 3], width=96)}<div style="display:flex;flex-direction:column;gap:4px;min-width:0"><span class="yb-overline">List finished</span><h2 class="yb-dialog-title" style="margin:0">The Long Eclipse</h2><span class="yb-supporting">Started 19 Oct · finished today</span></div></div>
  <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px">{"".join(f'<div class="yb-panel" style="padding:12px 10px;display:flex;flex-direction:column;gap:2px"><b style="font:700 20px/24px var(--font-sans)">{v}</b><span class="yb-caption">{k}</span></div>' for v, k in [("14", "books"), ("4", "series"), ("9 h 20", "read"), ("12", "days")])}</div>
  <div style="display:grid;grid-template-columns:repeat(14,1fr);gap:3px">{"".join(f'<span style="aspect-ratio:2/3;border-radius:3px;background:{c}"></span>' for c in ["#5c3d1e", "#1f3b57", "#4a2c5e", "#5c3d1e", "#4a2c5e", "#1f3b57", "#0f4c5c", "#5c3d1e", "#1f3b57", "#2a2a2a", "#4a2c5e", "#5c3d1e", "#5c3d1e", "#0f4c5c"])}</div>
  <span class="yb-supporting" style="color:var(--on-surface-variant)">It goes in your journal for October.</span>
  <div class="yb-dialog-actions"><button class="yb-btn is-text" style="color:var(--error)">Delete the list</button><span class="yb-spacer"></span><button class="yb-btn is-filled">Done</button></div>
</div>"""


comp("ListsScreen", "Screens", 2900, """
# Lists

Every list in one place, beside the sources in the rail and the drawer: the reading order you are in the middle of, then your lists, then each server's lists.

- **No screen title.** The rail says Lists; the bar holds "Search lists", New list (`add`) and a menu.
- **The card** is the reading order opened most recently that still has an unread book: what is next, the order's position and an `AppProgressBar`, and Continue. It is an `AppPanel` with `inset` 20; it is not shown when no order is under way.
- **Your lists** (on this tablet, any source): Want to read first, then Stalled (books started and not opened for 21 days, worked out on its own: the `tune` badge), then the lists you made, most recently opened first. **Kavita reading lists** follow, holding only Kavita issues; a tap opens the list in the Kavita library.
- **Grimmory shelves** come last, while Grimmory is connected: your shelves and the public ones, holding only Grimmory books, by name. The caption says the shelf's order ("5 books · by title"). They are kept on the server; the app refreshes them when Lists opens (at most every five minutes) and keeps the last copy for offline.
- Sections are `AppSectionHeading` on the gutter line with `section-gap` 24 between them; tiles are `AppListCover` in the library grid's columns and gap, the name at 14 SemiBold and one caption line.
- A list's page: `AppListCover` 132, the name in `display-title`, its description and "14 books · 6 read", Continue, Edit, the Order chip (Your order / Date added / Title) and Hide finished; the books are `AppBookRow`s in an `AppPanel`, numbered in Your order.
- **A shelf's page** is the same page. A Grimmory shelf has no order of its own, so the Order chip offers Date added (to Grimmory) and Title, and a shelf is never a reading order. Edit renames it, removes books and deletes the shelf, each saved on Grimmory with Done; there is no description and no dragging. Someone else's public shelf has no Edit.
- **Add to list** (`AppSideSheet`, `AppPanelHeader` "Add to list" and the book): Want to read, then Your lists under an `AppSheetLabel`, then Grimmory shelves (yours only) when every book being added is a Grimmory book, each an `AppSettingsRow` with `AppCheckbox`; New list; one caption line saying where each kind lives. A tap saves at once; for a shelf that is on the server, and a failure says so in a toast and leaves the box as it was.
- **New list** (`AppDialogCard`): the name, then where to keep it, an `AppSegmentedRow` "On this device" / "On Grimmory" / "On Kavita" (each only while that server is connected and, from Add to list, every book going in is that server's) with one line under it saying what that means. Under it a text button, "Import a reading order (.cbl)", for a list on this device.
- **Kavita reading lists** open as a list page, numbered in Kavita's order (Read next follows it). Yours can be renamed, reordered (drag or the row's menu), have books removed and be deleted, each saved on Kavita with Done; one someone else promoted is read-only. Add to list shows your Kavita reading lists for a Kavita issue. They are kept on the device and read again when Lists opens, like shelves.
- **In your lists** on the book page, after Reading: an `AppSheetLabel` and one `AppSettingsRow` with a chevron per list holding the book (Want to read, your lists, shelves and Kavita reading lists; never Stalled). Its line says where the book is: "Number 6 of 14 · Read next follows this list" in an order, else the list's size. A tap closes the page and opens the list.
- **Want to read on Kavita**: ticking a Kavita book into Want to read also puts its series on Kavita's Want to read; unticking takes the series off once no other book of it is left in Want to read. The row's line says "Also on Kavita's Want to read" for a Kavita book. The list on this device changes either way; a failure on Kavita says so in a toast.
- **Keep the next 3 downloaded**: on the page of one of your lists, an `AppPanel` holding one `AppSettingsRow` (`download` icon, a switch). On, the next three unread server books in the list's order are queued for download whenever the list or your reading changes; its line says which are ready and which are on their way. Finished books are not removed: removing downloads stays your call.
- **Smart lists**: the Filters sheet gets Save as list (`AppTonalButton`) beside Show; it asks for a name and keeps that library's filters, so the list changes with the library. Its tile has the `tune` badge, "Updates itself · 23"; its page has Date added and Title, no Your order, and its editor only renames or deletes it.
- **A list finished**: reading the last unread book of a list in Your order opens the list-finished card over Finished (`AppDialogCard`): the mosaic, "List finished", the name, "Started 19 Oct · finished today", four figures (books, series, time read, days) in `AppPanel` tiles, the covers in a row, "It goes in your journal for October", then Delete the list and Done. The journal's month shows it as a card among the books.
- **Import a reading order (.cbl)**: the books are matched to comics in your library by series and number, from any source; one that isn't there keeps a greyed row ("Not in your library") so the order stays whole. A toast says how many were found.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 wide, the whole page</div>{lists}</div>
  <div class="yb-caption" style="margin:24px 0 8px">Add to list for a Grimmory book, and New list kept on Grimmory</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;padding:24px">{add_to_list}{new_list}</div>
  <div class="yb-caption" style="margin:24px 0 8px">In your lists on a book page, Keep the next 3 downloaded on a list page, and a list finished</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;flex-wrap:wrap;padding:24px">{in_your_lists}<div style="display:flex;flex-direction:column;gap:24px">{keep_row}{list_finished}</div></div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)

print("screens written")
