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
          <div style="display:flex;align-items:center;gap:8px"><h2 class="yb-section-heading" style="flex:1">All books <span class="yb-count">1,474</span></h2><span class="yb-chip">{ic("filter_list")}Filters</span><span class="yb-chip">{ic("tune")}Last read</span></div>
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
- All books is `AppSectionHeading`, on the gutter line, with Filters and sort at its end.
- The reading card's inside inset is `inset` 20 at every width and its corners `radius-container` 20.
- No source name anywhere on the screen: the search field says it.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Medium, 800 wide (the emulator's portrait)</div>{library}</div>
</div>""", width=832, page=True, script=TOGGLE_SCRIPT)

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

comp("ListsScreen", "Screens", 2100, """
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
- **New list** (`AppDialogCard`): the name, then where to keep it, an `AppSegmentedRow` "On this device" / "On Grimmory" with one line under it saying what that means. The choice shows only while Grimmory is connected and every book going in is a Grimmory book.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 wide, the whole page</div>{lists}</div>
  <div class="yb-caption" style="margin:24px 0 8px">Add to list for a Grimmory book, and New list kept on Grimmory</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;padding:24px">{add_to_list}{new_list}</div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)

print("screens written")
