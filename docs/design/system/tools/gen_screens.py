# Screen mocks for the yabai design system (dp = px). Run after gen_components.py.
import sys
sys.path.insert(0, __import__("os").path.dirname(__import__("os").path.abspath(__file__)))
from gen_components import comp, ic, row, group, cover, theme_preview, chev, DIV, list_cover, list_row, letter_strip  # noqa: E402

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


def hub(selected_theme=True, selected=None, supporting=None):
    selected = selected or ("Theme" if selected_theme else None)
    supporting = supporting or {}
    out = []
    for label, rows in HUB_GROUPS:
        rs = []
        for title, sup, icon, extra in rows:
            tr = (theme_preview() + chev()) if extra == "theme" else "chev"
            rs.append(row(title, supporting.get(title, sup), icon, tr, selected=(title == selected)))
        out.append(group(label, rs))
    return "".join(out)


def queue_row(title, line, state, failed=False):
    colour = "var(--error)" if failed else "var(--on-surface-variant)"
    return row(title, line, None, f'<span style="font:700 14px/20px var(--font-sans);color:{colour}">{state}</span>')


def sync_page():
    """Reading progress sync with one upload waiting and two books gone from the library."""
    waiting = queue_row("The Left Hand of Darkness", "Progress · since 30 Oct", "Waiting")
    gone = [
        row("Moths of Lisbon", "Grimmory · Progress, 2 reading sessions", None, None),
        row("A Field Guide to Fog", "Grimmory · Journal entry", None, None),
    ]
    return ('<div style="display:flex;flex-direction:column;gap:14px">'
            '<div style="display:flex;gap:10px;align-items:center"><span class="yb-pill">1 waiting since 30 Oct</span>'
            '<span class="yb-supporting" style="font-size:14px">Last tried today at 09:14</span></div>'
            '<div><button class="yb-btn is-filled">Retry now</button></div></div>'
            f'{group("Grimmory", [waiting])}'
            '<div style="display:flex;flex-direction:column;gap:8px">'
            f'{group("No longer in your library", gone)}'
            '<p class="yb-supporting" style="margin:0 20px">These books left your library on the server, so what you read in them can&#8217;t upload. Keep it on this device: your journal keeps it and the app stops trying.</p>'
            '<div style="margin:6px 20px 0"><button class="yb-btn is-tonal">Keep on this device</button></div></div>')


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

settings_sync_expanded = f"""
<div class="yb-device" style="width:{W}px;height:{H}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Settings")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div style="position:absolute;top:84px;left:24px;width:320px;display:flex;flex-direction:column;gap:24px">{hub(selected="Reading progress sync", supporting={"Reading progress sync": "1 item waiting to upload"})}</div>
      <div style="position:absolute;top:84px;left:368px;right:24px;display:flex;flex-direction:column;gap:24px;max-width:800px">{sync_page()}</div>
      <div class="yb-spec">{g(24)}{g(344)}{g(368)}{h(84)}<span class="t" style="left:700px;top:62px">84 content top, both panes</span></div>
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

comp("SettingsScreen", "Screens", 2660, """
# Settings

The settings hub and its pages, on the frame in Layout and spacing.

- **No "Settings" title.** The hub starts with its first group, Appearance, at content top (84). On Compact and Medium the bar holds only the menu button; on Expanded there is no bar content at all.
- **Expanded: two panes on one top line.** The hub is the 320 list pane on the gutter; the open page follows 24 later and is at most 800 wide. Both start at content top, so the hub's first group label and the page's first line share a top edge.
- **Gutter 24** from Medium, 16 on phones; the panes are a gutter apart. Group labels sit on the panel's edge, and so does a page's one-line intro.
- A page opened on Compact or Medium shows Back and its name in the bar, and the bar has no glass until something scrolls under it.
- The open page is marked by the selected hub row (`primary-container`); the page itself carries no title.

## Reading progress sync

What waits to upload, and why. With nothing waiting, one row says "Everything is uploaded".

- **Waiting uploads.** An `AppStatusPill` with how many and since when (`error-container` once one failed), "Last tried …", then Retry now, the page's one filled button. Then a group per server, a book's uploads of one kind on one row, "Waiting" or "Failed" at the end, and the server's last error under the group.
- **Books no longer in your library.** An upload whose book is no longer in the app's copy of its server's library can't go anywhere: the server removed the book, so no retry helps. Those leave the count and the server groups for their own group, last on the page, "No longer in your library": one row per book, "Grimmory · Progress, 2 reading sessions", nothing at the end. Under it one line says why, and a tonal **Keep on this device** stops the uploads and keeps everything: the journal keeps the sessions and the entry, the progress stays, and the app stops trying. Nothing is deleted, so it needs no confirm. A server whose library the app holds no books from (signed out, never synced) proves nothing, so its uploads stay waiting.
- **The hub row** says what is waiting ("1 item waiting to upload", in `error` once one failed); with only gone books, "1 book no longer in your library"; otherwise "Up to date".
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens" style="display:flex;flex-direction:column;gap:28px">
    <div><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800: rail, hub, Theme page</div>{settings_expanded}</div>
    <div><div class="yb-caption" style="margin-bottom:8px">Expanded: Reading progress sync, one upload waiting and two books no longer in the library</div>{settings_sync_expanded}</div>
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
- **Cover size** (the sort menu's View): a slider with five stops, Tiny, Small, Medium, Large and Huge, the name at the label's end in `primary`; covers at least 70, 96, 140, 176 and 240 wide. Large is the default. The grid fits as many columns as that minimum allows and shares out the rest of the width: on a phone Tiny gives 4 columns, Small 3, Medium 2, Large 1 or 2 (360 or 411 wide), Huge 1. The grid follows the slider as it moves, and the menu stays open. Tiny drops the title under the cover (the cover says it); the line under it keeps the progress or length and the •••. The reading card and the shelves keep the default (Large) covers at every size: the slider sizes All books only, so All books' row, which the menu hangs from, stays where it is while the slider moves. See all's grid follows the same size.
- All books is `AppSectionHeading`, on the gutter line, with Filters, a chip per applied value and sort at its end. Filters narrows the library by anything its books carry, places, times, themes and moods included (see Filters); there is no separate Browse page.
- **On Expanded, Filters docks** beside the library: a 400 wide panel on the right gutter from content top to content bottom, the bar and the grid taking the width left of it. The covers keep the width they had before it opened: the grid loses columns, and the ones left come as close to that width as whole columns allow instead of stretching to fill. While it is open the reading card and the shelves step aside, so All books' row is at content top; they come back when it closes. The Filters chip shows and hides it.
- The reading card's inside inset is `inset` 20 at every width and its corners `radius-container` 20.
- No source name anywhere on the screen: the search field says it.
- **The jump rail** runs down the right edge for a sort that groups: the letters for Title, Author and Series, and for Pages the page ranges the filter's slider stops at (0, 10, 20, 30, 40, 50, 75, 100, 150, 200, 250, 300, 400, 500, 750, 1k), each where its range starts (150 holds 150 to 199), then `?` for books with no page count. Only sections with books show.
- **Sorted by Pages** (shortest first, unknown lengths last), the line under each cover gives its length instead of its progress: "212 pages", a series "212 pages · 8 issues". A series' length is all its issues together, for the sort and the Pages filter alike.
- **The card follows a reading order** when the book you read last was finished and is in the reading order under way, and that order's next book is from this library: its overline is "Next in The Long Eclipse · 7 of 14", one line under the progress names the two after it ("Then Neon Ronin #13, The Long Eclipse #3"), and its buttons are Read (or Continue) and Open list.
- **Refresh.** The bar's ••• holds Import a file and, on a server, Refresh library; while the library refreshes, the ••• button wears the progress ring. The library also refreshes by itself each time it shows (the app opening or coming back, another source picked) once its server last synced 15 minutes ago or more. That is Kavita and Grimmory, whose refresh saves only the books that changed; not Stump, whose sync loads every series again. A refresh keeps you where you are, says nothing when it ends and stays quiet offline (the search field's Offline says it). The full sync stays in Settings, under the server's Sync now.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Medium, 800 wide (the emulator's portrait)</div>{library}</div>
</div>""", width=832, page=True, script=TOGGLE_SCRIPT)

# ------------------------------------------------------------------ Filters (docked on Expanded); its helpers serve Tags too
BW, BH = 1280, 800
# The app's title-cover fills (LibraryCoverPlaceholder): the shelf's spines take these.
SPINES = ["#0F6E56", "#993C1D", "#534AB7", "#185FA5", "#993556", "#854F0B", "#3B6D11", "#5F5E5A", "#0C447C", "#3C3489", "#72243E", "#085041"]
SLOTS = [("Prehistory", 6), ("Ancient", 46), ("Middle Ages", 55), ("Renais&shy;sance", 8), ("1500s", 9), ("1600s", 22), ("1700s", 39),
         ("1800s", 152), ("1900s", 8), ("1910s", 12), ("1920s", 52), ("1930s", 70), ("1940s", 32), ("1950s", 63), ("1960s", 72),
         ("1970s", 70), ("1980s", 64), ("1990s", 104), ("2000s", 38), ("2010s", 35), ("21st c.", 50), ("Future", 70)]


def chips(values, selected=()):
    return "".join(
        f'<span class="yb-chip{" is-selected" if v in selected else ""}">{v} <span class="yb-count">{n}</span></span>'
        for v, n in values
    )


def spine_color(seed, i):
    return SPINES[(sum(map(ord, seed)) * 7 + i * 5) % len(SPINES)]


def pile(label, count, top, picked=None):
    n = max(1, round(14 * (count / top) ** 0.5))
    widths = [86, 94, 80, 91, 98, 83, 89]
    shift = [-2, 1, 3, -1, 0, 2, -3]
    books = "".join(
        f'<i style="display:block;height:7px;width:{widths[(len(label) + i) % 7]}%;margin-left:auto;margin-right:auto;'
        f'transform:translateX({shift[(len(label) + i * 3) % 7]}px);border-radius:1.5px;background:{spine_color(label, i)}"></i>'
        for i in range(n))
    fade = "opacity:.3;" if picked and label != picked else ""
    return (f'<div style="width:40px;flex:none;display:flex;flex-direction:column;align-items:center;gap:5px;{fade}">'
            f'<span style="font:700 11px/14px var(--font-sans)">{count}</span>'
            f'<div style="width:100%;display:flex;flex-direction:column-reverse;gap:1px">{books}</div></div>')


def shelf(slots, picked=None, first=0):
    shown = slots[first:]
    top = max(c for _, c in slots)
    piles = "".join(pile(label, count, top, picked) for label, count in shown)
    labels = "".join(
        f'<span style="width:40px;flex:none;text-align:center;font:{"700" if label == picked else "500"} 10px/12px var(--font-sans);height:24px;'
        + ('background:var(--primary-container);color:var(--on-primary-container);border-radius:8px;' if label == picked else 'color:var(--on-surface-variant);' + ("opacity:.55;" if picked else ""))
        + f'">{label.replace(" ", "<br>")}</span>'
        for label, _ in shown)
    return (f'<div class="yb-panel" style="padding:20px 0 14px 20px;overflow:hidden">'
            f'<div style="display:flex;gap:4px;align-items:flex-end">{piles}</div>'
            f'<div style="height:6px;border-radius:3px;margin:1px 0 6px;background:var(--outline-variant)"></div>'
            f'<div style="display:flex;gap:4px">{labels}</div></div>')


def place_card(name, count, cities, covers, picked=False):
    stage_bg = "var(--primary-container)" if picked else "var(--surface-container)"
    stack = "".join(
        f'<div style="position:absolute;top:17px;left:{16 + k * 38}px;z-index:{3 - k};width:68px;border-radius:var(--radius-cover);box-shadow:0 0 0 2px {stage_bg}">{cover(c, 68, 8)}</div>'
        for k, c in enumerate(covers))
    check = (f'<span style="position:absolute;top:10px;right:10px;z-index:5;width:28px;height:28px;border-radius:999px;background:var(--primary);'
             f'color:var(--on-primary);display:flex;align-items:center;justify-content:center">{ic("check", 18)}</span>') if picked else ""
    city_line = f'<span style="font:500 13px/18px var(--font-sans);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{cities}</span>' if cities else ""
    return (f'<div style="flex:none;width:176px;display:flex;flex-direction:column;gap:10px">'
            f'<div style="position:relative;height:136px;border-radius:var(--radius-container);background:{stage_bg};overflow:hidden">{stack}{check}</div>'
            f'<div style="display:flex;flex-direction:column;gap:1px"><span class="yb-book-title">{name}</span>'
            f'<span class="yb-caption">{count} books</span>{city_line}</div></div>')


def split(left, right):
    return (f'<div class="yb-seg"><span><b style="font-weight:600;color:var(--on-surface)">{left[0]}</b>&nbsp;{left[1]}</span>'
            f'<span><b style="font-weight:600;color:var(--on-surface)">{right[0]}</b>&nbsp;{right[1]}</span></div>')


def genre_tile(name, count, start):
    return (f'<div style="display:flex;flex-direction:column;gap:8px;min-width:0">{list_cover([start, start + 1, start + 2, start + 3])}'
            f'<div style="display:flex;flex-direction:column"><span class="yb-book-title" style="font-size:14px">{name}</span><span class="yb-caption">{count} books</span></div></div>')


def reading_panel():
    books = "".join(
        f'<div style="display:grid;grid-template-columns:36px minmax(0,1fr);gap:12px;align-items:center">{cover(i, 36, 5)}'
        f'<div style="min-width:0"><div class="yb-book-title" style="font-size:14px;line-height:18px">{t}</div><div class="yb-caption">{a}</div>'
        f'<div style="display:flex;align-items:center;gap:8px;margin-top:6px"><div class="yb-progress" style="flex:1"><i style="width:{p}%"></i></div>'
        f'<span class="yb-caption" style="font-weight:600">{p}%</span></div></div></div>'
        for i, (t, a, p) in enumerate([("Moths of Lisbon", "Paulo Serra", 62), ("Glass Meridian", "J. R. Okafor", 81), ("Paper Cranes", "Hana Moriya", 34)]))
    stat = lambda n, label, i: (
        f'<div class="yb-panel" style="flex:1;padding:16px 18px;display:flex;align-items:center;gap:14px">'
        f'<div style="position:relative;width:60px;height:54px">{"".join(f"<div style=\"position:absolute;left:{k * 12}px;z-index:{3 - k};width:36px;box-shadow:0 0 0 2px var(--surface-container);border-radius:var(--radius-cover)\">{cover(i + k, 36, 5)}</div>" for k in range(3))}</div>'
        f'<div><b style="display:block;font:700 22px/28px var(--font-sans)">{n}</b><span class="yb-caption">{label}</span></div></div>')
    return (f'<div style="display:grid;grid-template-columns:minmax(0,1.55fr) minmax(0,1fr);gap:16px">'
            f'<div class="yb-panel" style="padding:20px;display:flex;flex-direction:column;gap:16px"><h3 class="yb-group-label">In progress <span class="yb-caption" style="font-size:13px;font-weight:600">23</span></h3>{books}</div>'
            f'<div style="display:flex;flex-direction:column;gap:16px">{stat("259", "Finished", 2)}{stat("1,812", "Not started", 5)}</div></div>')


def heading(title, count, more=None):
    end = f'<button class="yb-btn is-text" style="margin-left:auto">{more}</button>' if more else ""
    return f'<div style="display:flex;align-items:center"><h2 class="yb-section-heading">{title} <span class="yb-count">{count}</span></h2>{end}</div>'


def pane_row(name, count, lead="", picked=False, indent=False):
    bg = "background:var(--primary-container);color:var(--on-primary-container);" if picked else ""
    pad = "padding-left:48px;height:34px;" if indent else ""
    tail = ic("check", 18) if picked else ""
    return (f'<div style="display:flex;align-items:center;gap:12px;height:44px;padding:0 12px;margin:0 -12px;border-radius:var(--radius-control);{bg}{pad}">'
            f'{lead}<span style="flex:1;font:500 {13.5 if indent else 14}px/20px var(--font-sans);{"color:var(--on-surface-variant)" if indent else ""}">{name}</span>'
            f'<span style="font:600 13px/18px var(--font-sans);{"" if picked else "color:var(--on-surface-variant)"}">{count}</span>{tail}</div>')


def era_row(label, count, top, picked=False):
    n = max(1, round(26 * (count / top) ** 0.5))
    books = "".join(f'<i style="display:block;width:4px;height:{11 + (i * 5 + len(label)) % 7}px;border-radius:1px;background:{spine_color(label, i)}"></i>' for i in range(n))
    bg = "background:var(--primary-container);color:var(--on-primary-container);" if picked else ""
    return (f'<div style="display:grid;grid-template-columns:92px minmax(0,1fr) 28px;align-items:center;gap:10px;height:32px;padding:0 12px;margin:0 -12px;border-radius:var(--radius-control);{bg}">'
            f'<span style="font:500 13px/18px var(--font-sans)">{label}</span><span style="display:flex;align-items:flex-end;gap:1px;height:18px">{books}</span>'
            f'<span style="text-align:right;font:600 12px/16px var(--font-sans)">{count}</span></div>')


pane_set_in = (pane_row("Russia", 72, f'<span style="width:24px">{cover(3, 24, 3)}</span>', picked=True)
               + pane_row("Saint Petersburg", 14, indent=True) + pane_row("Moscow", 6, indent=True)
               + pane_row("France", 3, f'<span style="width:24px">{cover(1, 24, 3)}</span>')
               + pane_row("Caucasus", 2, f'<span style="width:24px">{cover(5, 24, 3)}</span>'))
era_counts = [("1700s", 3), ("1800s", 72), ("1910s", 2), ("Future", 1)]
pane_when = "".join(era_row(label, count, 72, picked=label == "1800s") for label, count in era_counts)
filters_find = """<div class="yb-search">""" + ic("search") + """<span>Find a place, tag, mood or author</span></div>"""
filter_tiles = "".join(
    f'<div style="display:flex;flex-direction:column;gap:6px;min-width:0">{cover(i)}<div class="yb-book-title" style="font-size:14px">{t}</div></div>'
    for i, t in enumerate(["The Lantern Keepers", "Дом у реки", "Quiet Engines", "Moths of Lisbon", "Glass Meridian", "Star Courier #2", "The Salt Orchard", "Paper Cranes"])
)
docked = f"""
<div class="yb-device" style="width:{BW}px;height:{BH}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Grimmory")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-bar"><div class="yb-search">{ic("search")}<span>Search Grimmory</span></div><span class="yb-spacer"></span><button class="yb-icon-btn">{ic("download")}</button><button class="yb-icon-btn">{ic("more_horiz")}</button></div>
      <div style="position:absolute;left:24px;right:448px;top:84px;bottom:0;display:flex;flex-direction:column;gap:16px;overflow:hidden">
        <div style="display:flex;gap:8px;align-items:center"><h2 class="yb-section-heading" style="flex:1">All books <span class="yb-count">72</span></h2><span class="yb-chip is-selected">Russia{ic("close", 18)}</span><span class="yb-chip is-selected">1800s{ic("close", 18)}</span><span class="yb-chip">{ic("tune")}Date added</span></div>
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px">{filter_tiles}</div>
      </div>
      <div class="yb-panel" style="position:absolute;right:24px;top:84px;bottom:24px;width:400px;padding:0;display:flex;flex-direction:column;overflow:hidden">
        <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Filters</h2><span class="yb-panel-sub">Showing 72 of 1,474</span></div><button class="yb-btn is-text">Clear all</button><button class="yb-icon-btn">{ic("navigate_next")}</button></div>
        <div style="padding:0 20px;display:flex;flex-direction:column;gap:16px;overflow:hidden">
          {filters_find}
          <div style="display:flex;flex-direction:column;gap:6px"><h3 class="yb-sheet-label">Set in</h3>{pane_set_in}</div>
          <div style="display:flex;flex-direction:column;gap:6px"><h3 class="yb-sheet-label">When</h3>{pane_when}</div>
          <div style="display:flex;flex-direction:column;gap:8px"><h3 class="yb-sheet-label">About</h3><div class="yb-chip-row">{chips([("Love and relationships", 9), ("Supernatural", 8), ("Aristocracy", 7), ("Philosophical fiction", 6)])}</div></div>
          <div style="display:flex;flex-direction:column;gap:8px"><h3 class="yb-sheet-label">Mood</h3><div class="yb-chip-row">{chips([("Dark", 31), ("Reflective", 28), ("Slow-paced", 22)])}</div></div>
        </div>
      </div>
      <div class="yb-spec">{g(24)}{g(BW - 88 - 448, "grid")}{g(BW - 88 - 24, "24")}{h(84)}<span class="t" style="left:560px;top:90px">84 content top</span></div>
    </div>
  </div>
</div>"""

comp("LibraryFilters", "Screens", 880, """
# Filters

One way to narrow a source's library: by anything its books carry, the places, times, themes and moods included. There is no Browse page; Filters holds what it showed, in compact forms, and narrows All books itself.

- **Opening it.** The Filters chip in All books' row (with the number of applied values); an applied value's chip (opens Filters at its section); a tag on the book page or in the search suggestions, which applies that value to All books and shows its chip, as a tag did before Browse.
- **Sheet or docked.** Under Expanded it is an `AppSideSheet` (a bottom sheet on phones) over the library, with Save as list and "Show 72 books" in its footer. On Expanded it docks: a 400 wide panel on the right gutter, from content top to content bottom, on `surface` with `radius-container` 20; the library takes the width left of it. Every tap changes the books at once, so the docked panel has no Show button, only Save as list. The Filters chip shows and hides it; the app remembers which.
- **The books stay in view.** While the panel is docked open, the library leaves out the reading card and the shelves (as a search does): All books' row starts at content top, with the books under it. They come back when the panel closes, even with picks still applied, so a filter left on never hides what you are reading. Every change of picks (in Filters, a chip's ×, a tag on the book page) scrolls the library to All books' row, unless that combination was scrolled further down before; it opens where it was left.
- **Header:** `AppPanelHeader` "Filters" with "Showing 72 of 1,474" (or "Showing all 1,474"), Clear all once something is picked, and close (docked: hide). Docked, the Filters chip shows as selected while the panel is open.
- **Find:** an `AppSearchField` ("Find a place, tag, mood or author") at the top. While it holds text, the sections give way to the matches from every text section (a word starting with what you typed: "war" finds Cold War and Ruth Ware, not Edward), grouped under their section's `AppSheetLabel`, picked ones first, each a value row with a checkbox, the matched part bold and the count; a tap picks it. Named periods (World War II, Cold War), which have no pile under When, are reached here.
- **Sections**, in the order Settings › Filter chips gives (by default Reading status, Set in, When, About, Mood, Genre, Author, Series, Publisher, Language, Pages, Format, Library, Location: what the books are first, then their length, then where they are kept); a section no book has stays out (a source without moods has no Mood):
  - **Reading status, Format, Library, Location:** chips with counts.
  - **Set in:** the tags you sorted as places (Settings › Tags) and Kavita's Locations, most books first: rows with the name and the count, six of them, then "All 53 places", which opens the section's full list with its search and letters. Cities fold into their country: picking a country picks its cities' books, and a picked country (selected, `primary-container`, a check) shows its cities under it, indented, each pickable.
  - **When:** one 26 row per pile (a century or decade, in time order) with its books standing as 4 wide spines, as many as the square root of its count allows (14 at most), in the title-cover fills; the count at the end. A pick takes every tag in the pile. "All N times" opens the full list, named periods among them.
  - **About:** every other tag as chips, most books first, 14 of them, then "All 283 tags".
  - **Mood:** chips (Grimmory's moods, paces such as Fast-paced among them).
  - **Genre:** chips, 10, then "All 49 genres".
  - **Author, Series, Publisher, Language:** a value row (52) with its top three values and counts on the line under its name ("Ryu Murakami 5 · Yukio Mishima 4 · Edogawa Rampo 4"), what is picked ("Any") at its end, opening the section's full list.
  - **Pages:** the range slider. It comes after the sections of what the books are, because it is half a docked panel tall.
- **Counts narrow.** Each section counts the books every other section leaves. Two picks in one section mean either one (Japan or France); picks in different sections narrow (Japan and Dark).
- Save as list keeps the picks as a smart list's rules.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 × 800: Russia and 1800s picked, Filters docked</div>{docked}</div>
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
    <h3 class="yb-sheet-label" style="padding-top:14px">Filters shows it in</h3>
    {seg(["Set in", "When", "About"], "About")}
    <div class="yb-divider" style="margin:14px 0 0"></div>
    <h3 class="yb-sheet-label" style="padding-top:14px">Books with this tag</h3>
    {sort_books}
    <button class="yb-btn is-text" style="align-self:flex-start;padding:0 8px;margin-left:-8px">Filter by it · 28</button>
  </div>
</section>"""
region_sheet = f"""
<section class="yb-sheet" style="height:440px">
  <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Kosovo</h2><span class="yb-panel-sub">New tag · 1 book · Grimmory</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-body">
    <p class="yb-supporting" style="margin:0">The app doesn't know this name, so it has no guess. The tag is in About until you choose.</p>
    <h3 class="yb-sheet-label" style="padding-top:14px">Filters shows it in</h3>
    {seg(["Set in", "When", "About"], "Set in")}
    <h3 class="yb-sheet-label" style="padding-top:14px">Region</h3>
    <div class="yb-chip-row">{"".join(f'<span class="yb-chip{" is-selected" if r == "Europe" else ""}">{r}</span>' for r in ["Americas", "Europe", "Russia and around", "Asia", "Middle East and Africa", "Oceania", "Made-up places", "Elsewhere"])}</div>
  </div>
</section>"""

comp("LibraryTags", "Screens", 1560, """
# Tags

Settings › Library › Tags: where each source's tags go in Filters and on the book page. You decide; the app only suggests.

- **Three rows.** Every tag is in Set in, When or About, and a place has a region. A tag nobody has sorted is in About, so Filters never shows a guess as a fact.
- **The app's guess** (`libraryTagGuess`, core:model): about 400 place names with their regions, decades and centuries by their shape, and some named periods. It is shown beside the tag, never applied, until you accept it.
- **The page**, opened from the hub's Tags row (on Expanded it is the page pane, the row selected; elsewhere Back and "Tags" in the bar): the sources as an `AppSegmentedRow`, as on Filter chips; it opens on the library's source, or on the source Filters sent you from.
- **New** comes first: the tags nobody has sorted. Up to 10 are `AppSettingsRow`s with the guess as the supporting line ("3 books · Looks like a place · Europe") and one text button, Accept, or Keep in About when there is no guess; a tap on the row opens the tag's sheet. More than 10 (the first time) are grouped by the guess under one line saying what happens and an **Accept all N tags** filled button: Looks like a place (an `AppPanel` with an `AppSheetLabel` per region beside its chips), Looks like a time (On the timeline, then Periods), No guess: stays in About.
- **The sorted tags** follow as `AppTabPill`s, Set in · When · About with their counts, and a Find a tag `AppSearchField` beside them: Set in as region panels in two columns (one on Compact), When as the timeline's tags then the periods, About as chips. A tap on any tag opens its sheet.
- **The sort sheet** (`AppSideSheet`; `AppPanelHeader` with the tag and "28 books · Grimmory", "New tag · ..." while unsorted): one line saying the app's guess and why ("The app guessed Set in · Americas, because Hollywood is on its list of place names."); "Filters shows it in" as an `AppSegmentedRow` (Set in, When, About); for Set in, "Region" as single-choice `AppChip`s (Americas, Europe, Russia and around, Asia, Middle East and Africa, Oceania, Made-up places, Elsewhere; Elsewhere until you pick); then up to five of its books (`AppBookRow`: the author and the book's other tags) and "Filter by it · N". A choice saves at once and holds for every book with that tag.
- **A long press** on a Set in, When or About value in Filters, or on a tag on the book page, opens the same sheet over what you were looking at.
- **Kavita's Locations** (comics, from ComicInfo) are places already: they join Set in as they arrive, under Elsewhere until you give them a region, and New asks only for that ("Location from Kavita · 8 issues · Looks like Asia"); their sheet has no row choice. Grimmory, Stump and local books have tags only.
- **Per source**, like Filter chips: each source's tags are sorted on their own. Choices are kept on the device by name, so a tag that leaves every book keeps its place in case it comes back.
- **The hub row**: `sell` icon, "Tags", and "N tags to check" while nothing is sorted, "N new tags" while some wait, else "Where Filters shows each tag".
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


def check_box(on):
    if on:
        return (f'<span style="width:22px;height:22px;border-radius:6px;background:var(--primary);color:var(--on-primary);'
                f'display:flex;align-items:center;justify-content:center;flex:none">{ic("check", 18)}</span>')
    return '<span style="width:22px;height:22px;border-radius:6px;box-shadow:inset 0 0 0 2px var(--outline);flex:none"></span>'


def round_check(on):
    if on:
        return (f'<span style="width:22px;height:22px;border-radius:50%;background:var(--primary);color:var(--on-primary);'
                f'display:flex;align-items:center;justify-content:center;flex:none">{ic("check", 16)}</span>')
    return '<span style="width:22px;height:22px;border-radius:50%;box-shadow:inset 0 0 0 2px var(--outline);flex:none"></span>'


def filter_chip(label, count=None, selected=False):
    n = f' <span class="yb-count">{count}</span>' if count is not None else ""
    return f'<span class="yb-chip{" is-selected" if selected else ""}">{label}{n}</span>'


def grid(tiles, cols=6):
    return f'<div style="display:grid;grid-template-columns:repeat({cols},1fr);gap:16px">{"".join(tiles)}</div>'


LISTS_H = 2040  # All, the whole page


def place_row(selected, counts=(("All", None), ("Your lists", 5), ("Kavita", 64), ("Grimmory", 61)), style=""):
    return f'<div class="yb-chip-row" style="flex-wrap:nowrap;{style}">{"".join(filter_chip(n, c, n == selected) for n, c in counts)}</div>'


def see_all(n):
    return f'<button class="yb-btn is-text" style="height:32px;margin-right:-12px;font-weight:600">See all {n}{ic("navigate_next", 18)}</button>'


def lists_heading(title, count, more=None):
    end = see_all(more) if more else ""
    return (f'<div style="display:flex;align-items:center;justify-content:space-between;gap:16px">'
            f'<h2 class="yb-section-heading">{title} <span class="yb-count">{count}</span></h2>{end}</div>')


def strip(tiles):
    # One sideways row: tiles at the grid's cover width; the last one peeks past the gutter.
    return (f'<div style="display:grid;grid-auto-flow:column;grid-auto-columns:176px;gap:16px;margin-right:-24px;overflow:hidden">'
            f'{"".join(tiles)}</div>')


def kind_tabs(tabs, selected):
    return ('<div class="yb-tabs">' + "".join(
        f'<span class="yb-tab{" is-selected" if t == selected else ""}">{t}{f" <span class=yb-count>{c}</span>" if c is not None else ""}</span>'
        for t, c in tabs) + '</div>')


def show_as(rows=True):
    return (f'<div class="yb-seg" style="flex:none"><span class="{"is-selected" if rows else ""}" style="flex:none;padding:0 10px">{ic("view_list", 20)}</span>'
            f'<span class="{"" if rows else "is-selected"}" style="flex:none;padding:0 10px">{ic("grid_view", 20)}</span></div>')


def sort_chip(label):
    return f'<span class="yb-chip">{ic("swap_vert", 18)}{label}</span>'


def letter_group(letter, rows, cols=2):
    grid_style = f"display:grid;grid-template-columns:repeat({cols},1fr);" if cols > 1 else ""
    return (f'<div class="yb-group"><h3 class="yb-group-label" style="color:var(--on-surface-variant)">{letter}</h3>'
            f'<div class="yb-panel" style="{grid_style}padding:4px 0">{"".join(rows)}</div></div>')


def under_way_card():
    return f"""<div class="yb-panel" style="padding:20px;display:flex;gap:20px;align-items:center">
          {cover(5, width=88)}
          <div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:4px">
            <span class="yb-caption" style="font-weight:600">Reading order · book 3 of 5 · read yesterday</span>
            <span style="font:700 24px/30px var(--font-sans)">Night Run</span>
            <span class="yb-supporting">Next: Star Courier #2</span>
            <div class="yb-progress" style="margin-top:8px"><i style="width:40%"></i></div>
          </div>
          <button class="yb-btn is-filled">Continue</button>
        </div>"""


def bar(menu=False):
    lead = f'<button class="yb-icon-btn">{ic("menu")}</button>' if menu else ""
    # On a phone the search field takes the bar's room; on wider windows it stops at its maximum and a spacer follows.
    spacer = "" if menu else '<span class="yb-spacer"></span>'
    return (f'<div class="yb-bar">{lead}<div class="yb-search">{ic("search")}<span>Search lists</span></div>{spacer}'
            f'<button class="yb-icon-btn">{ic("add")}</button><button class="yb-icon-btn">{ic("more_vert")}</button></div>')


lists = f"""
<div class="yb-device" style="width:{W}px;height:{LISTS_H}px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Lists")}
    <div class="yb-pane" style="--yb-gutter:24px">
      {bar()}
      <div class="yb-scroll">
        {place_row("All", style="margin-bottom:-8px")}
        {under_way_card()}
        <div class="yb-group">
          {lists_heading("Pinned", 5)}
          {grid([
              list_tile("Eclipse War", "Kavita · 9 of 22 read", [0, 4, 5, 3]),
              list_tile("Harbor Lights, in order", "Kavita · 12 of 32 read", [5, 5, 5, 5]),
              list_tile("Short Russian classics", "Grimmory · magic shelf · 44", [1, 1, 1, 1], smart=True),
              list_tile("Russian Classics", "Grimmory · 55 books", [1, 7, 1, 2]),
              list_tile("Want to read", "This device · 9 books", [1, 2, 4, 7]),
          ])}
        </div>
        <div class="yb-group">
          {lists_heading("Your lists", 5)}
          {grid([
              list_tile("Night Run", "2 of 5 read", [0, 3, 5, 6]),
              list_tile("Stalled", "Updates itself · 4", [3, 0, 6, 2], smart=True),
              list_tile("Book club picks", "1 book", [6]),
              list_tile("Short reads", "Updates itself · 23", [7, 1, 2, 3], smart=True),
              list_tile("Crossover", "4 books", [5, 0, 5, 4]),
          ])}
        </div>
        <div class="yb-group">
          {lists_heading("Kavita", 64, more=64)}
          {strip([
              list_tile("Neon Ronin: Year One", "3 of 14 read", [5, 3, 5, 0]),
              list_tile("Eisner winners", "Collection · 9 series", [4, 1, 3, 0]),
              list_tile("Blood Moon Rising", "2 of 18 read", [4, 7, 2, 0]),
              list_tile("Clockwork Saints", "Reading list · 31 issues", [3, 3, 0, 6]),
              list_tile("Black Tide", "Reading list · 24 issues", [3, 0, 6, 2]),
              list_tile("Dead Signal", "Reading list · 8 issues", [0, 5, 6, 4]),
              list_tile("Deep Water", "Reading list · 15 issues", [5, 0, 4, 3]),
          ])}
        </div>
        <div class="yb-group">
          {lists_heading("Grimmory", 61, more=61)}
          {strip([
              list_tile("Cinema", "46 books · 5 read", [7, 3, 0, 5]),
              list_tile("Poetry", "70 books · 12 read", [2, 6, 1, 4]),
              list_tile("European Fiction", "50 books", [0, 2, 7, 6]),
              list_tile("Biographies", "49 books", [0, 7, 3, 2]),
              list_tile("American Fiction", "42 books", [6, 2, 4, 1]),
              list_tile("33 1/3", "64 books", [3, 0, 6, 2]),
              list_tile("Art", "18 books", [2, 4, 7, 1]),
          ])}
        </div>
        <span class="yb-caption" style="margin-top:-8px;font-size:13px">11 lists hidden · <span style="color:var(--primary);font-weight:600">What Lists shows</span></span>
      </div>
      <div class="yb-spec">{g(24)}<span class="t" style="left:28px;top:30px">24 gutter</span>{h(84)}<span class="t" style="left:520px;top:62px">84 content top</span></div>
    </div>
  </div>
</div>"""

kavita_place = f"""
<div class="yb-device" style="width:{W}px;height:1120px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Lists")}
    <div class="yb-pane" style="--yb-gutter:24px">
      {bar()}
      {letter_strip("B", style="right:3px;top:330px")}
      <div class="yb-scroll">
        {place_row("Kavita", style="margin-bottom:-8px")}
        <div style="display:flex;align-items:center;gap:8px;margin-top:-8px">{kind_tabs([("All", 64), ("Reading lists", 48), ("Collections", 16)], "All")}<span class="yb-spacer" style="flex:1"></span>{sort_chip("Name")}{show_as(True)}</div>
        <div class="yb-group">
          {lists_heading("Recent", 4)}
          <div class="yb-panel" style="display:grid;grid-template-columns:1fr 1fr;padding:4px 0">
            {list_row("Eclipse War", "Reading list · 9 of 22 read · 2019–2020", [0, 4, 5, 3], progress=41, pinned=True)}
            {list_row("Harbor Lights, in order", "Reading list · 12 of 32 read · 2016–2018", [5, 5, 5, 5], progress=38, pinned=True)}
            {list_row("Neon Ronin: Year One", "Reading list · 3 of 14 read · 2021", [5, 3, 5, 0], progress=21)}
            {list_row("Eisner winners", "Collection · 9 series", [4, 1, 3, 0])}
          </div>
        </div>
        <div style="display:flex;flex-direction:column;gap:16px">
          {lists_heading("A to Z", 59)}
          {letter_group("A", [list_row("Absolute Zero", "Reading list · 18 issues · 2014", [5, 3, 0, 4]), list_row("After the Flood", "Reading list · 7 issues · 2022", [2, 6, 1, 0]), list_row("Ashfall", "Reading list · 11 issues · 2017–2018", [6, 2, 4, 1])])}
          {letter_group("B", [list_row("Best of the decade", "Collection · 14 series", [1, 4, 0, 7]), list_row("Black Tide", "Reading list · 24 issues · 2012–2013", [3, 0, 6, 2]), list_row("Blood Moon Rising", "Reading list · 2 of 18 read · 2020", [4, 7, 2, 0], progress=11), list_row("Broken Crown", "Reading list · 6 issues · 2023", [6, 1, 3, 5])])}
          {letter_group("C", [list_row("Cinder Road", "Reading list · 9 issues · 2019", [1, 5, 2, 7]), list_row("Clockwork Saints", "Reading list · 31 issues · 2008–2010", [3, 3, 0, 6]), list_row("Crime and noir", "Collection · 7 series", [3, 1, 6, 0]), list_row("Crossfire", "Reading list · 12 issues · 2016", [7, 4, 0, 2])])}
        </div>
      </div>
      <div class="yb-spec">{g(24)}<span class="t" style="left:28px;top:30px">24 gutter</span>{h(84)}<span class="t" style="left:520px;top:62px">84 content top</span></div>
    </div>
  </div>
</div>"""

grimmory_phone = f"""
<div class="yb-device yb-compact" style="width:{PW}px;height:{PH}px">
  {statusbar()}
  <div class="yb-device-body" style="flex-direction:column">
    <div class="yb-pane">
      {bar(menu=True)}
      {letter_strip("A", line=15, style="right:0;top:470px")}
      <div class="yb-scroll">
        <div style="margin:0 -16px -8px;overflow:hidden">{place_row("Grimmory", style="padding:0 16px;transform:translateX(-56px)")}</div>
        <div style="display:flex;align-items:center;gap:4px;margin-top:-8px"><div style="flex:1;min-width:0;overflow:hidden">{kind_tabs([("All", None), ("Magic shelves", 12), ("Shelves", 49)], "All")}</div><button class="yb-icon-btn">{ic("swap_vert")}</button><button class="yb-icon-btn">{ic("grid_view")}</button></div>
        <div class="yb-group">
          {lists_heading("Recent", 3)}
          <div class="yb-panel" style="padding:4px 0">
            {list_row("Short Russian classics", "Magic shelf · 44 books", [1, 1, 1, 1], smart=True, pinned=True)}
            {list_row("Russian Classics", "Shelf · 55 books · 21 read", [1, 7, 1, 2], pinned=True)}
            {list_row("Cinema", "Shelf · 46 books", [7, 3, 0, 5])}
          </div>
        </div>
        <div style="display:flex;flex-direction:column;gap:16px">
          {lists_heading("A to Z", 57)}
          {letter_group("#", [list_row("33 1/3", "Shelf · 64 books", [3, 0, 6, 2])], cols=1)}
          {letter_group("A", [list_row("American Fiction", "Shelf · 42 books", [6, 2, 4, 1]), list_row("Art", "Shelf · 18 books", [2, 4, 7, 1])], cols=1)}
        </div>
      </div>
    </div>
  </div>
</div>"""

grimmory_phone_end = f"""
<div class="yb-device yb-compact" style="width:{PW}px;height:{PH}px">
  {statusbar()}
  <div class="yb-device-body" style="flex-direction:column">
    <div class="yb-pane">
      <div class="yb-bar is-glass" style="z-index:1"><button class="yb-icon-btn">{ic("menu")}</button><div class="yb-search">{ic("search")}<span>Search lists</span></div><button class="yb-icon-btn">{ic("add")}</button><button class="yb-icon-btn">{ic("more_vert")}</button></div>
      {letter_strip("W", line=15, style="right:0;top:250px")}
      <div class="yb-scroll" style="top:40px;gap:16px">
        {letter_group("T", [list_row("Translated fiction", "Shelf · 38 books", [0, 6, 2, 5]), list_row("Travel", "Shelf · 21 books", [6, 0, 7, 2])], cols=1)}
        {letter_group("U", [list_row("Unread gifts", "Shelf · 9 books", [2, 5, 1, 4])], cols=1)}
        {letter_group("W", [list_row("War", "Shelf · 17 books", [3, 6, 0, 1]), list_row("Westerns", "Shelf · 6 books", [7, 0, 2, 3]), list_row("World history", "Magic shelf · 52 books", [0, 3, 3, 1], smart=True)], cols=1)}
        {letter_group("Y", [list_row("Young adult", "Shelf · 13 books", [4, 2, 6, 7])], cols=1)}
        <div class="yb-panel" style="margin-top:8px">
          {row("Finished <span class='yb-count' style='color:var(--on-surface-variant)'>4</span>", "Every book read: Poetry 2024, Plays, Noir, Stoics", icon="check")}{DIV}
          {row("Hidden <span class='yb-count' style='color:var(--on-surface-variant)'>5</span>", "Kobo, and 4 public shelves of other people", icon="visibility_off")}
        </div>
      </div>
    </div>
  </div>
</div>"""


def lmenu_row(icon, title, line=None, muted=False):
    sub = f'<span class="yb-caption">{line}</span>' if line else ""
    op = "opacity:.45;" if muted else ""
    return (f'<div class="yb-menu-item" style="height:{56 if line else 44}px;gap:14px;{op}"><span style="display:flex;color:var(--on-surface-variant)">{ic(icon)}</span>'
            f'<span style="display:flex;flex-direction:column"><span style="font:500 15px/20px var(--font-sans)">{title}</span>{sub}</span></div>')


list_menu = f"""
<div style="position:relative;width:420px;height:440px">
  <div class="yb-panel" style="padding:4px 0">
    {list_row("Ashfall", "Reading list · 11 issues · 2017–2018", [6, 2, 4, 1])}
    {list_row("Black Tide", "Reading list · 24 issues · 2012–2013", [3, 0, 6, 2], pressed=True)}
    {list_row("Blood Moon Rising", "Reading list · 2 of 18 read · 2020", [4, 7, 2, 0], progress=11)}
    {list_row("Broken Crown", "Reading list · 6 issues · 2023", [6, 1, 3, 5])}
  </div>
  <div class="yb-menu" style="position:absolute;left:96px;top:158px;width:300px;padding:8px 0">
    {lmenu_row("keep", "Pin to the top", "First on All and on Kavita")}
    {lmenu_row("visibility_off", "Hide from Lists", "Stays on Kavita. Hidden brings it back.")}
    {lmenu_row("select_all", "Select several")}
    <div class="yb-divider" style="margin:4px 16px"></div>
    {lmenu_row("edit", "Edit", "Promoted by someone else: read-only", muted=True)}
  </div>
</div>"""

select_bar = f"""
<div style="position:relative;width:860px;display:flex;flex-direction:column;gap:16px">
  <div class="yb-bar is-glass" style="position:relative;top:0;left:0;right:0;background:var(--surface-container-high)"><button class="yb-icon-btn">{ic("close")}</button><span class="yb-bar-title">5 selected</span><button class="yb-icon-btn">{ic("select_all")}</button><button class="yb-btn is-tonal" style="padding:0 16px">{ic("keep", 18)}Pin</button><button class="yb-btn is-filled" style="padding:0 16px">{ic("visibility_off", 18)}Hide</button></div>
  <div class="yb-panel" style="display:grid;grid-template-columns:1fr 1fr;padding:4px 0">
    {list_row("Absolute Zero", "Reading list · 18 issues · 2014", [5, 3, 0, 4], check=True)}
    {list_row("Best of the decade", "Collection · 14 series", [1, 4, 0, 7], check=False)}
    {list_row("After the Flood", "Reading list · 7 issues · 2022", [2, 6, 1, 0], check=True)}
    {list_row("Broken Crown", "Reading list · 6 issues · 2023", [6, 1, 3, 5], check=True)}
    {list_row("Ashfall", "Reading list · 11 issues · 2017–2018", [6, 2, 4, 1], check=False)}
    {list_row("Cinder Road", "Reading list · 9 issues · 2019", [1, 5, 2, 7], check=True)}
  </div>
  <div style="align-self:center;display:flex;align-items:center;gap:16px;padding:0 8px 0 20px;height:52px;border-radius:var(--radius-control);background:var(--on-surface);color:var(--surface);font:500 14px/20px var(--font-sans)"><span>5 lists hidden</span><button class="yb-btn is-text" style="color:var(--primary-container);font-weight:700">Undo</button></div>
</div>"""


def shows_switch(title, line, on):
    return row(title, line, trailing="switch-on" if on else "switch", dense=True)


def hidden_row(name, line):
    return (f'<div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">{name}</span><span class="yb-supporting">{line}</span></span>'
            f'<button class="yb-btn is-text">Show</button></div>')


what_lists_shows = f"""
<div class="yb-sheet" style="height:auto;padding-bottom:12px">
  <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">What Lists shows</h2><span class="yb-panel-sub">On this device only. Nothing changes on the servers.</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label" style="padding:0 20px">Kavita</h3></div>
  {shows_switch("Lists other people share", "14 promoted by someone else", True)}
  {shows_switch("Collections", "16 groups of series", True)}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label" style="padding:0 20px">Grimmory</h3></div>
  {shows_switch("Public shelves of other people", "4 shelves", False)}
  {shows_switch("Empty shelves", "3 hold no books", False)}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label" style="padding:0 20px">Everywhere</h3></div>
  <div style="padding:4px 20px 8px;display:flex;flex-direction:column;gap:8px"><span class="yb-row-title">Finished lists</span><div class="yb-seg"><span class="is-selected">At the end</span><span>Hide</span><span>With the rest</span></div></div>
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label" style="padding:0 20px">Hidden · 11</h3></div>
  {hidden_row("Kobo", "Grimmory · shelf · 12 books")}
  {hidden_row("Absolute Zero", "Kavita · reading list · 18 issues")}
  {hidden_row("After the Flood", "Kavita · reading list · 7 issues")}
  <button class="yb-btn is-text" style="margin:4px 8px">See all 11 hidden</button>
</div>"""


def hl(text, q="Moon"):
    return text.replace(q, f'<span style="color:var(--primary)">{q}</span>')


def found_book(i, title, by, where):
    return (f'<div class="yb-book-row" style="padding:8px 12px 8px 20px">{cover(i)}<div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:2px">'
            f'<div class="yb-book-title">{hl(title)}</div><div class="yb-book-line">{by}</div>'
            f'<div class="yb-book-line" style="color:var(--on-surface);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{where}</div></div>{chev()}</div>')


lists_search = f"""
<div class="yb-device" style="width:{W}px;height:700px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Lists")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-bar"><div class="yb-search" style="color:var(--on-surface);box-shadow:inset 0 0 0 2px var(--primary)">{ic("search")}<span style="flex:1">Moon</span>{ic("close", 20)}</div><span class="yb-spacer"></span><button class="yb-icon-btn">{ic("add")}</button><button class="yb-icon-btn">{ic("more_vert")}</button></div>
      <div class="yb-scroll">
        {place_row("All", style="margin-bottom:-8px")}
        <div class="yb-group">
          {lists_heading("Lists", 3)}
          <div class="yb-panel" style="display:grid;grid-template-columns:1fr 1fr;padding:4px 0">
            {list_row("Blood Moon Rising", "Kavita · reading list · 2 of 18 read", [4, 7, 2, 0], progress=11, name_html=hl("Blood Moon Rising"))}
            {list_row("Half Moon Bay", "Kavita · reading list · 6 issues · 2023", [0, 2, 5, 6], name_html=hl("Half Moon Bay"))}
            {list_row("Moon and stars", "Grimmory · shelf · 12 books", [7, 1, 4, 2], name_html=hl("Moon and stars"))}
          </div>
        </div>
        <div class="yb-group">
          {lists_heading("Books in your lists", 4)}
          <div class="yb-panel" style="display:grid;grid-template-columns:1fr 1fr;padding:4px 0">
            {found_book(0, "The Moonstone", "Wilkie Collins", "In Want to read · Victorian mysteries")}
            {found_book(5, "Moonfall #2", "Moonfall · Kavita", "In Blood Moon Rising, 2 of 18 · Night Run")}
            {found_book(6, "The Moon and Sixpence", "W. Somerset Maugham", "In British classics · Book club picks")}
            {found_book(3, "Paper Moon", "Joe David Brown", "In Cinema")}
          </div>
        </div>
      </div>
    </div>
  </div>
</div>"""


def menu_row(icon, title, line):
    return (f'<div class="yb-menu-item" style="height:56px;gap:14px;align-items:center"><span style="display:flex;color:var(--on-surface-variant)">{ic(icon)}</span>'
            f'<span style="display:flex;flex-direction:column"><span style="font:500 15px/20px var(--font-sans)">{title}</span>'
            f'<span class="yb-caption">{line}</span></span></div>')


plus_menu = f"""
<div class="yb-menu" style="width:320px">
  {menu_row("checklist", "List", "You pick the books")}
  {menu_row("tune", "Smart list", "Picked by rules, kept up to date")}
  {menu_row("description", "Import a reading order", "From a .cbl file")}
</div>"""


def radio_row(title, sup, on):
    return (f'<div class="yb-row is-dense" style="padding:0"><span class="yb-row-text"><span class="yb-row-title">{title}</span>'
            f'<span class="yb-supporting">{sup}</span></span>{round_check(on)}</div>')


new_list = f"""
<div class="yb-dialog" style="width:400px">
  <h2 class="yb-dialog-title">New list</h2>
  <span class="yb-field" style="box-shadow:inset 0 0 0 2px var(--primary)"><span class="yb-field-label" style="color:var(--primary)">Name</span>Book club</span>
  <h3 class="yb-sheet-label" style="margin:4px 0 -8px">Keep it</h3>
  <div>
    {radio_row("On this device", "Books from every source, in your order.", True)}
    {radio_row("On Kavita", "A reading list, also in Kavita's web reader. Kavita issues only.", False)}
    {radio_row("On Grimmory", "A shelf, also in Grimmory's web app. Grimmory books only.", False)}
  </div>
  <div class="yb-dialog-actions"><button class="yb-btn is-text">Cancel</button><button class="yb-btn is-filled">Create</button></div>
</div>"""


def check_row(title, sup, on):
    return (f'<div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">{title}</span>'
            f'<span class="yb-supporting">{sup}</span></span>{check_box(on)}</div>')


add_to_list = f"""
<div class="yb-sheet" style="height:720px">
  <div class="yb-panel-header has-back"><button class="yb-icon-btn">{ic("arrow_back")}</button><div class="yb-panel-header-text"><h2 class="yb-panel-title">Add to list</h2><span class="yb-panel-sub">Отцы и дети</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">Your lists</h3></div>
  {check_row("Want to read", "9 books", False)}
  {check_row("Book club picks", "1 book", True)}
  {check_row("Night Run", "5 books", False)}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">Grimmory shelves</h3></div>
  {check_row("Russian Classics", "55 books", True)}
  {check_row("Poetry", "70 books", False)}
  <div class="yb-divider is-full"></div>
  {row("New list", icon="add", trailing=None, dense=True)}
</div>"""


def rule_row(field, value):
    return (f'<div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">{field}</span>'
            f'<span class="yb-supporting">{value}</span></span><button class="yb-icon-btn">{ic("close")}</button></div>')


smart_editor = f"""
<div class="yb-device" style="width:{W}px;height:800px">
  {statusbar()}
  <div class="yb-device-body">
    {rail("Lists")}
    <div class="yb-pane" style="--yb-gutter:24px">
      <div class="yb-bar"><button class="yb-icon-btn">{ic("close")}</button><span class="yb-bar-title">New smart list</span><span class="yb-spacer"></span><button class="yb-btn is-filled">Create</button></div>
      <div class="yb-scroll" style="flex-direction:row;gap:24px;align-items:flex-start">
        <div style="flex:1;min-width:0;display:flex;flex-direction:column;gap:24px">
          <span class="yb-field"><span class="yb-field-label">Name</span>Short Russian classics</span>
          <div style="display:flex;flex-direction:column;gap:8px">
            <h3 class="yb-group-label">Keep it</h3>
            <div class="yb-seg"><span>On this device</span><span class="is-selected">On Grimmory</span></div>
            <span class="yb-caption">A Grimmory magic shelf, also in Grimmory's web app. Grimmory books only.</span>
          </div>
          <div class="yb-group">
            <h3 class="yb-group-label">Rules</h3>
            <div class="yb-panel">
              <div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">Books that match</span></span><span class="yb-chip">all {ic("arrow_downward", 16)}</span></div>
              {DIV}{rule_row("Tag", "is all of: Russia, 19th century")}
              {DIV}{rule_row("Genre", "is any of: Classics, Poetry")}
              {DIV}{rule_row("Pages", "under 500")}
              {DIV}<div class="yb-row is-dense" style="gap:8px"><button class="yb-btn is-text">{ic("add")}Add rule</button><button class="yb-btn is-text">{ic("add")}Add a group</button></div>
            </div>
          </div>
          <div class="yb-panel">{row("Sort by", "Published, oldest first", dense=True)}{DIV}{row("How many", "Every match", dense=True)}</div>
        </div>
        <div style="width:320px;display:flex;flex-direction:column;gap:12px">
          <h2 class="yb-section-heading">Matches <span class="yb-count">44</span></h2>
          <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px">{"".join(cover(1, font=10) for _ in range(9))}</div>
          <span class="yb-caption">and 35 more</span>
        </div>
      </div>
    </div>
  </div>
</div>"""


def picker_row(title, hint, enabled=True):
    style = "" if enabled else ' style="opacity:.45"'
    return (f'<div class="yb-row is-dense"{style}><span class="yb-row-text"><span class="yb-row-title">{title}</span>'
            f'<span class="yb-supporting">{hint}</span></span></div>')


rule_picker = f"""
<div class="yb-sheet" style="height:760px">
  <div class="yb-panel-header has-back"><button class="yb-icon-btn">{ic("arrow_back")}</button><div class="yb-panel-header-text"><h2 class="yb-panel-title">Add a rule</h2></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">About the book</h3></div>
  {picker_row("Author", "Any of, none of")}{picker_row("Series", "Any of, none of")}{picker_row("Genre", "Any of, all of, none of")}{picker_row("Tag", "Set in, when, about")}
  {picker_row("Language", "Any of, none of")}{picker_row("Pages", "Under, over, between")}{picker_row("Published", "Before, after, between")}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">Your reading</h3></div>
  {picker_row("Status", "Not started, reading, finished")}{picker_row("Last read", "In the last, not for")}
  <div class="yb-divider is-full"></div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">In your library</h3></div>
  {picker_row("Added", "In the last, before")}{picker_row("Downloaded", "Lists on this device only", enabled=False)}{picker_row("Source", "Lists on this device only", enabled=False)}
</div>"""


def value_row(label, count, on):
    return (f'<div class="yb-row is-dense"><span class="yb-row-text"><span class="yb-row-title">{label}</span></span>'
            f'<span class="yb-caption" style="margin-right:12px">{count}</span>{check_box(on)}</div>')


rule_value = f"""
<div class="yb-sheet" style="height:760px">
  <div class="yb-panel-header has-back"><button class="yb-icon-btn">{ic("arrow_back")}</button><div class="yb-panel-header-text"><h2 class="yb-panel-title">Tag</h2></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div style="padding:0 20px 12px;display:flex;flex-direction:column;gap:12px">
    <div class="yb-seg"><span>Any of</span><span class="is-selected">All of</span><span>None of</span></div>
    <div class="yb-search" style="max-width:none">{ic("search")}<span>Search 264 tags</span></div>
  </div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">Set in</h3></div>
  {value_row("Russia", 246, True)}{value_row("Moscow", 61, False)}{value_row("Saint Petersburg", 58, False)}
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">When</h3></div>
  {value_row("19th century", 152, True)}{value_row("20th century", 690, False)}
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">About</h3></div>
  {value_row("Family", 210, False)}
  <div style="margin-top:auto;padding:12px 20px 20px;display:flex;align-items:center;gap:12px;border-top:1px solid var(--outline-variant)">
    <span class="yb-supporting" style="flex:1">Books tagged both Russia and 19th century · 72 in your library</span>
    <button class="yb-btn is-filled">Save rule</button>
  </div>
</div>"""


def rule_pill(text):
    return f'<span class="yb-chip" style="height:30px;font-weight:500">{text}</span>'


def book_row_html(i, title, line, number=None):
    num = f'<span class="yb-caption" style="width:24px;text-align:right;font-weight:600">{number}</span>' if number else ""
    return (f'<div class="yb-row" style="gap:16px">{num}{cover(i, width=44, font=6)}<span class="yb-row-text"><span class="yb-row-title">{title}</span>'
            f'<span class="yb-supporting">{line}</span></span></div>')


magic_page = f"""
<div class="yb-ground" style="width:900px;padding:24px;display:flex;flex-direction:column;gap:24px">
  <div style="display:flex;gap:24px;align-items:flex-end">
    <div style="position:relative;width:132px">{list_cover([1, 1, 1, 1])}<span style="position:absolute;right:8px;top:8px;width:32px;height:32px;border-radius:50%;background:var(--surface-container-high);display:flex;align-items:center;justify-content:center">{ic("tune", 18)}</span></div>
    <div style="flex:1;display:flex;flex-direction:column;gap:8px">
      <span class="yb-caption" style="font-weight:600">Grimmory magic shelf · updates itself</span>
      <span class="yb-display" style="font-size:32px;line-height:40px">Short Russian classics</span>
      <div class="yb-chip-row">{rule_pill("Tag: Russia and 19th century")}{rule_pill("Genre: Classics or Poetry")}{rule_pill("Pages under 500")}</div>
      <span class="yb-supporting" style="color:var(--on-surface)">44 books · 3 read</span>
      <div style="display:flex;gap:8px"><button class="yb-btn is-filled">Read · Кавказский пленник</button><button class="yb-btn is-tonal">{ic("edit", 18)}Edit rules</button></div>
    </div>
  </div>
  <div style="display:flex;flex-direction:column;gap:12px">
    <div style="display:flex;align-items:center;gap:8px"><h2 class="yb-section-heading" style="flex:1">Books <span class="yb-count">44</span></h2><span class="yb-chip">{ic("swap_vert")}Published</span><span class="yb-chip">Hide finished</span></div>
    <div class="yb-panel">{book_row_html(1, "Кавказский пленник", "Лев Толстой · 1872")}{DIV}{book_row_html(1, "Шинель", "Николай Гоголь · 1842")}{DIV}{book_row_html(1, "Отцы и дети", "Иван Тургенев · Finished")}</div>
  </div>
</div>"""

collection_page = f"""
<div class="yb-ground" style="width:900px;padding:24px;display:flex;flex-direction:column;gap:24px">
  <div style="display:flex;gap:24px;align-items:flex-end">
    <div style="width:132px">{list_cover([4, 1, 3, 0])}</div>
    <div style="flex:1;display:flex;flex-direction:column;gap:8px">
      <span class="yb-caption" style="font-weight:600">Kavita collection · on the server</span>
      <span class="yb-display" style="font-size:32px;line-height:40px">Eisner winners</span>
      <span class="yb-supporting" style="color:var(--on-surface)">9 series · 212 issues · 41 read</span>
      <div style="display:flex;gap:8px"><button class="yb-btn is-tonal">{ic("edit", 18)}Edit</button></div>
    </div>
  </div>
  <div style="display:flex;flex-direction:column;gap:12px">
    <h2 class="yb-section-heading">Series <span class="yb-count">9</span></h2>
    <div class="yb-panel">
      <div class="yb-row" style="gap:16px">{cover(4, width=44, font=6)}<span class="yb-row-text"><span class="yb-row-title">Glass Meridian</span><span class="yb-supporting">24 issues · 6 read</span></span><span class="yb-chev">{ic("navigate_next")}</span></div>{DIV}
      <div class="yb-row" style="gap:16px">{cover(0, width=44, font=6)}<span class="yb-row-text"><span class="yb-row-title">The Lantern Keepers</span><span class="yb-supporting">12 issues</span></span><span class="yb-chev">{ic("navigate_next")}</span></div>
    </div>
  </div>
</div>"""

in_your_lists = f"""
<div class="yb-sheet" style="height:auto;padding-bottom:12px">
  <div class="yb-panel-header"><div class="yb-panel-header-text"><h2 class="yb-panel-title">Отцы и дети</h2><span class="yb-panel-sub">Иван Тургенев</span></div><button class="yb-icon-btn">{ic("close")}</button></div>
  <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding:0 20px 8px">
    {"".join(f'<button class="yb-btn is-tonal" style="height:64px;flex-direction:column;gap:4px;padding:0 8px;font-size:13px">{ic(i, 20)}{t}</button>' for i, t in [("check", "Mark finished"), ("download", "Download"), ("checklist", "Add to list")])}
  </div>
  <div class="yb-sheet-section"><h3 class="yb-sheet-label">In your lists</h3></div>
  {row("Book club picks", "Your list · 1 book", dense=True)}
  {row("Short Russian classics", "Grimmory magic shelf · 44 books", dense=True)}
  {row("Russian Classics", "Grimmory shelf · 55 books", dense=True)}
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


comp("ListsScreen", "Screens", 11500, """
# Lists

Every list in one place, beside the sources in the rail and the drawer. All is a short overview that stays the same length however many lists the servers hold; a place's chip shows all its lists, as rows you can sort, narrow and jump through.

- **No screen title.** The rail says Lists; the bar holds "Search lists", New (`add`) and a menu (`more_vert`: What Lists shows, Select several).
- **New** opens an `AppMenu` of three: List ("You pick the books"), Smart list ("Picked by rules, kept up to date") and Import a reading order ("From a .cbl file"), each with its icon and line (`AppMenuItem` with `supporting`).
- **The place row** sits at the content top: `AppChip`s All, Your lists, then one per connected server that has lists (Kavita, Grimmory), each with the count of lists it shows (hidden ones aside); one is picked at a time and the pick is kept in the route's saved state. There is no Smart chip: smart lists and magic shelves are a kind inside their place. Back from a place returns to All; Back on All leaves Lists.
- **All**, the overview, `section-gap` 24 between its parts:
  - **The card**: the reading order opened most recently that still has an unread book: "Reading order · book 3 of 5", its name, what is next, an `AppProgressBar`, and Continue. An `AppPanel` with `inset` 20; not shown when no order is under way, nor under a place.
  - **Pinned**, while something is pinned: every pinned list from every place, in the order they were pinned, as tiles in the grid; the caption names the place ("Kavita · 9 of 22 read", "Grimmory · magic shelf · 44", "This device · 9 books"). A pinned list shows here and not again in its place's row.
  - **Your lists**: Want to read first, then Stalled while it holds a book (books started and not opened for 21 days), then your lists and smart lists, most recently opened first. One row of tiles at most; with more, the heading ends with See all N.
  - **One row per server** (Kavita, then Grimmory): an `AppSectionHeading` with the count and See all N at its end (a text button with `navigate_next` that picks the place's chip), over one row of tiles that scrolls sideways: the place's lists opened most recently first, then the rest by name, twelve at most. Tiles are the grid's cover width and the last one in view runs past the gutter, so the row reads as one that scrolls.
  - While lists are hidden, a caption closes the page: "11 lists hidden · What Lists shows" (the link opens the sheet).
- **A place** (its chip, or See all):
  - **The tool row** under the chips: the kinds as `AppTabPill`s with counts (Your lists: All, Smart while one exists; Kavita: All, Reading lists, Collections; Grimmory: All, Magic shelves, Shelves), then at the end the Sort chip (`swap_vert` and the sort: Name, Recently opened, Most books, and Year for Kavita, from the reading lists' first year) and Show as, an `AppSegmentedRow` of two icons (`view_list` rows, `grid_view` covers). On Compact, Sort and Show as are icon buttons and the kinds scroll.
  - Kavita and Grimmory show rows to start with, Your lists covers. Each place keeps its kind, sort and Show as on this device.
  - **Recent** first while the sort is not Recently opened: up to six of the place's lists you opened lately, newest first.
  - Then every list that is not finished, in the sort's order, under a heading that names it ("A to Z 59", "Most books", "By year"). By name, the lists sit under a group label per first letter (# for a digit or sign) and the `AppLetterStrip` runs down the end edge while the section holds more than 20 lists.
  - Rows are `AppListRow`s in an `AppPanel`, two columns on Expanded and one below it; covers are tiles in the grid.
  - **Finished and Hidden** fold at the end, in one `AppPanel` of two `AppSettingsRow`s (`check`, `visibility_off`): "Finished 4" with the first names, "Hidden 5" with what they are; a tap opens their rows in place.
- **A list's menu** (`AppMenu`, from a long-press on a tile or a row, or ⋯ on its page): Pin to the top ("First on All and on Kavita"; Unpin with `keep_off` once pinned), Hide from Lists ("Stays on Kavita. Hidden brings it back."; Show again once hidden), Select several, then Edit, greyed with its reason when the list is someone else's ("Promoted by someone else: read-only"). The row or tile takes `surface-container-highest` while its menu is open.
- **Select several** turns the bar into the selection bar (`surface-container-high`): Close, "5 selected", Select all, then Pin (tonal) and Hide (filled). Rows and tiles get an `AppRoundCheck` at their start and a picked one takes `primary-container`. Hide or Pin ends the selection; Hide's toast says "5 lists hidden" with Undo.
- **Pinned, hidden, Recent and each place's choices live on this device only.** Nothing changes on Kavita or Grimmory, and a list hidden here still shows in their web apps.
- **What Lists shows** (`AppSideSheet` from the bar's menu, `AppPanelHeader` with the line "On this device only. Nothing changes on the servers."): Kavita: Lists other people share (promoted by someone else), Collections. Grimmory: Public shelves of other people, Empty shelves. Everywhere: Finished lists (`AppSegmentedRow` At the end, Hide, With the rest). Hidden · N: a row per hidden list (its place, kind and size) with Show, the first five, then See all N hidden. A switch that is off takes that whole group out of All, the place and its chip's count.
- **Search** narrows the picked place in two sections: Lists (names that match, as `AppListRow`s with the match in `primary`), then Books in your lists (`AppBookRow`s: the book, its author or series, and the lists holding it: "In Blood Moon Rising, 2 of 18 · Night Run"). A book's row opens its book page, where In your lists opens a list. Hidden lists are left out. "No list or book in a list matches “Moon”." when both are empty.
- The servers' lists show while that server is connected; the app reads them again when Lists opens (at most every five minutes) and keeps the last copy for offline. Captions name the kind on All and under a place's All: "Reading list · 32 issues", "Collection · 9 series", "Magic shelf · 44 books", "Shelf · 55 books".
- **Tiles** are `AppListCover` in the library grid's columns and gap, the name at 14 SemiBold and one caption line: "9 books"; "2 of 5 read" for a reading order with something read; "Updates itself · 23" with the `tune` badge for a list that works itself out.
- **A list's page**: `AppListCover` 132, a caption saying what it is and where it lives ("Your list · on this device", "Grimmory shelf · on the server"), the name in `display-title`, its description and "14 books · 6 read", Continue (or Read) with the next book's title, Edit, the Order chip and Hide finished; the books are `AppBookRow`s in an `AppPanel`, numbered in Your order.
- **A shelf's page** is the same page. A Grimmory shelf has no order of its own, so the Order chip offers Date added (to Grimmory) and Title, and a shelf is never a reading order. Edit renames it, removes books and deletes the shelf, each saved on Grimmory with Done; there is no description and no dragging. Someone else's public shelf has no Edit.
- **Kavita reading lists** open as a list page, numbered in Kavita's order (Read next follows it). Yours can be renamed, reordered (drag or the row's menu), have books removed and be deleted, each saved on Kavita with Done; one someone else promoted is read-only. Add to list shows your Kavita reading lists for a Kavita issue.
- **A Kavita collection's page** holds series, not issues: "Kavita collection · on the server", "9 series · 212 issues · 41 read", and one row per series (its cover, "24 issues · 6 read", a chevron) that opens the series. Edit renames it, removes series and deletes it, each saved on Kavita with Done. One someone else promoted, or one Kavita+ keeps in step with another site, is read-only. Collections are never reading orders.
- **Smart lists** are built from rules (below). On this device a smart list looks at every source; kept on Grimmory it is a **magic shelf**, which Grimmory's web app shows too and which holds only Grimmory books. Its page: the caption "Smart list · updates itself" or "Grimmory magic shelf · updates itself", the rules as small `AppChip`s under the name ("Tag: Russia and 19th century", "Pages under 500"), Read and Edit rules, then the books with the Order chip (Date added, Title, Published, Last read) and Hide finished. Never a reading order. Filters › Save as list makes one from the library's filters.
- **The smart list editor** is a page (the bar: Close, "New smart list" or "Edit smart list", Create or Done). The name; Keep it (`AppSegmentedRow` On this device / On Grimmory, while Grimmory is connected, with a caption saying what that means; fixed once made); the rules in an `AppPanel`: "Books that match all/any of these", one row per rule (its field and what it asks: "is all of: Russia, 19th century", a remove button), Add rule and Add a group (a group is a nested panel with its own all/any, one level deep); then Sort by and How many (Every match, 10, 25, 50). The matches show beside the form on Expanded and under it otherwise: "Matches 44", the first nine covers and "and 35 more", worked out from what this device knows as you edit. Create is off until there is a name and a rule.
- **Add a rule** (`AppSideSheet`): the fields in three groups under `AppSheetLabel`s, each row with a line saying what it asks. About the book: Author, Series, Genre, Tag, Mood, Language, Publisher, Pages, Published. Your reading: Status, Last read. In your library: Added, Format, Grimmory shelf, Downloaded, Source. A field a magic shelf can't use (Downloaded, Source) is greyed with "Lists on this device only"; Grimmory shelf only shows while Grimmory is connected.
- **A rule's values** (the same sheet, one page further): for names (author, genre, tag…) an `AppSegmentedRow` Any of / All of / None of (All of only where a book has several), a search field, then `AppCheckbox` rows with how many books have each, tags under Set in, When and About as Filters groups them; for numbers and years Under / Over / Between and the values; for dates In the last / Not in the last and a number of days, weeks or months; for Status the three states. The sheet's foot says what the rule matches in words and how many books ("Books tagged both Russia and 19th century · 72 in your library") beside Save rule.
- **Add to list** (`AppSideSheet`, `AppPanelHeader` "Add to list" and the book): Your lists under an `AppSheetLabel` (Want to read first), then a server's lists under its own label ("Grimmory shelves", "Kavita reading lists", yours only) when every book being added is that server's, each an `AppSettingsRow` with `AppCheckbox`; an `AppSheetDivider` between sections, and New list last on its own row. A label shows only over rows. Smart lists and magic shelves are not here: their rules pick their books. A tap saves at once; for a server's list on the server first, and a failure says so in a toast and leaves the box as it was.
- **New list** (`AppDialogCard`, from New › List or Add to list): the name first, then, while a server is connected, "Keep it" (`AppSheetLabel`) over one row per place, each an `AppSettingsRow` with an `AppRoundCheck` and its line always shown: On this device ("Books from every source, in your order."), On Kavita ("A reading list, also in Kavita's web reader. Kavita issues only."), On Grimmory ("A shelf, also in Grimmory's web app. Grimmory books only."). From Add to list a server's row shows only when every book going in is that server's. Copy says "this device", never "tablet".
- **On the book page**, Mark finished, Download and Add to list are three equal `AppTonalButton` tiles under Start reading, 64 tall, the icon over the label, so the three always fit; a book with fewer actions keeps each tile a third of the row. **In your lists** follows Reading: an `AppSheetLabel` and one `AppSettingsRow` with a chevron per list holding the book (Want to read, your lists and smart lists, shelves, magic shelves, Kavita reading lists; never Stalled). Its line says where the book is: "Number 6 of 14 · Read next follows this list" in an order, else what the list is and its size. A tap closes the page and opens the list.
- **Want to read on Kavita**: ticking a Kavita book into Want to read also puts its series on Kavita's Want to read; unticking takes the series off once no other book of it is left in Want to read. The row's line says "Also on Kavita's Want to read" for a Kavita book. The list on this device changes either way; a failure on Kavita says so in a toast.
- **Keep the next 3 downloaded**: on the page of one of your lists, an `AppPanel` holding one `AppSettingsRow` (`download` icon, a switch). On, the next three unread server books in the list's order are queued for download whenever the list or your reading changes; its line says which are ready and which are on their way. Finished books are not removed: removing downloads stays your call.
- **A list finished**: reading the last unread book of a list in Your order opens the list-finished card over Finished (`AppDialogCard`): the mosaic, "List finished", the name, "Started 19 Oct · finished today", four figures (books, series, time read, days) in `AppPanel` tiles, the covers in a row, "It goes in your journal for October", then Delete the list and Done. The journal's month shows it as a card among the books.
- **Import a reading order (.cbl)**: the books are matched to comics in your library by series and number, from any source; one that isn't there keeps a greyed row ("Not in your library") so the order stays whole. A toast says how many were found.
- **The library home has no lists chip**: Kavita's reading lists and collections live on Lists.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens"><div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 wide: All, the whole page</div>{lists}
  <div class="yb-caption" style="margin:24px 0 8px">Expanded: Kavita picked (or See all 64), rows, Recent, then A to Z with the letter strip</div>{kavita_place}
  <div class="yb-caption" style="margin:24px 0 8px">Compact, 412: Grimmory picked, and the end of it with Finished and Hidden folded</div>
  <div style="display:flex;gap:40px;align-items:flex-start">{grimmory_phone}{grimmory_phone_end}</div></div>
  <div class="yb-caption" style="margin:24px 0 8px">A list's menu, Select several, and What Lists shows</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;flex-wrap:wrap;padding:24px">{list_menu}{what_lists_shows}{select_bar}</div>
  <div class="yb-caption" style="margin:24px 0 8px">Search: lists by name, then the books in your lists</div>
  {lists_search}
  <div class="yb-caption" style="margin:24px 0 8px">New, New list, and Add to list for a Grimmory book</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;flex-wrap:wrap;padding:24px">{plus_menu}{new_list}{add_to_list}</div>
  <div class="yb-caption" style="margin:24px 0 8px">The smart list editor, kept on Grimmory as a magic shelf</div>
  {smart_editor}
  <div class="yb-caption" style="margin:24px 0 8px">Add a rule, and a rule's values</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;padding:24px">{rule_picker}{rule_value}</div>
  <div class="yb-caption" style="margin:24px 0 8px">A magic shelf's page, and a Kavita collection's</div>
  <div style="display:flex;flex-direction:column;gap:24px">{magic_page}{collection_page}</div>
  <div class="yb-caption" style="margin:24px 0 8px">The book page's actions and In your lists, Keep the next 3 downloaded, and a list finished</div>
  <div class="yb-ground" style="display:flex;gap:40px;align-items:flex-start;flex-wrap:wrap;padding:24px">{in_your_lists}<div style="display:flex;flex-direction:column;gap:24px">{keep_row}{list_finished}</div></div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)


# ------------------------------------------------------------------ Finished (the end of a book)
# Cover colours as appCoverColors gives them for two of the emulator's covers (dark themes).
GLASS = {"title": "Glass Meridian", "sub": "J. R. Okafor", "bg": "#3b2350", "ink": "#f4c7a1",
         "ts": "#302858", "te": "#61385d", "fg": "#f6eef1", "acc": "#dba4ba", "on": "#331421"}
COURIER = {"title": "Star Courier #1", "sub": "Issue 1", "bg": "#0f4c5c", "ink": "#f6f1e7",
           "ts": "#23415c", "te": "#6f622a", "fg": "#f6eef1", "acc": "#f5e289", "on": "#423705"}


def rgba(hex_colour, alpha):
    n = int(hex_colour[1:], 16)
    return f"rgb({n >> 16 & 255} {n >> 8 & 255} {n & 255} / {alpha})"


def fin_cover(b, width, font, title=None, shadow="0 16px 36px rgb(0 0 0 / .5)"):
    return (f'<div class="yb-cover" style="width:{width}px;font-size:{font}px;background:{b["bg"]};color:{b["ink"]};flex:none;box-shadow:{shadow}">'
            f'<b>{title or b["title"]}</b><small>{b["sub"] if not title else ""}</small></div>')


def fin_stars(b, rating, spread):
    stars = []
    for k in range(5):
        part = max(0, min(2, rating - 2 * k))
        name, colour = ("star_filled", b["acc"]) if part == 2 else ("star_half", b["acc"]) if part == 1 else ("star", rgba(b["fg"], .28))
        stars.append(f'<span style="width:52px;height:52px;display:flex;align-items:center;justify-content:center;color:{colour}">{ic(name, 48)}</span>')
    layout = "justify-content:space-between;margin:4px -2px 0" if spread else "justify-content:center;gap:4px;margin-top:20px"
    return f'<div style="display:flex;align-items:center;height:56px;{layout}">{"".join(stars)}</div>'


def fin_hint(rating):
    return f"Rated {rating / 2:.1f}" if rating else "Tap to rate"


def fin_pill(b):
    return (f'<span style="display:inline-flex;align-items:center;gap:8px;height:40px;padding:0 16px;border-radius:var(--radius-pill);'
            f'background:{rgba(b["fg"], .10)};font:600 14px/20px var(--font-sans)">{ic("edit", 18)}Add a private note</span>')


def fin_note(b):
    return (f'<div style="margin-top:16px;padding:10px 16px 12px;border-radius:var(--radius-control);box-shadow:inset 0 0 0 1px {rgba(b["fg"], .38)}">'
            f'<div style="font:600 12px/16px var(--font-sans);color:{b["acc"]}">Private note</div>'
            f'<div style="margin-top:4px;font:400 15px/22px var(--font-sans);min-height:66px">The ending earns the slow start. Lend it to Ira.</div></div>')


def fin_next(b, top):
    nxt = dict(b, title="Star Courier #2")
    return (f'<div style="display:flex;align-items:center;gap:14px;margin-top:{top}px;padding:12px;border-radius:var(--radius-container);background:rgb(11 13 15 / .3)">'
            f'{fin_cover(nxt, 48, 6, shadow="none")}<div style="display:flex;flex-direction:column;gap:2px">'
            f'<span style="font:500 12px/16px var(--font-sans);color:{rgba(b["fg"], .74)}">Up next in Star Courier</span>'
            f'<span style="font:600 15px/20px var(--font-sans)">Star Courier #2</span></div></div>')


def fin_buttons(b, has_next):
    back = rgba(b["fg"], .12) if has_next else b["acc"]
    back_ink = b["fg"] if has_next else b["on"]
    btn = "flex:1 1 0;height:48px;border:0;border-radius:var(--radius-pill);display:flex;align-items:center;justify-content:center;gap:8px;font:600 15px/20px var(--font-sans)"
    read = f'<span style="{btn};background:{b["acc"]};color:{b["on"]}">{ic("menu_book", 18)}Read next</span>' if has_next else ""
    return f'<span style="{btn};background:{back};color:{back_ink}">Back to library</span>{read}'


def fin_tint(b, reach):
    return f'background:radial-gradient(circle {reach}px at 100% 100%,{b["te"]},{rgba(b["te"], 0)}),{b["ts"]};color:{b["fg"]}'


def finished_card(b, rating, note=False, has_next=False):
    head = (f'<div style="font:800 11.5px/16px var(--font-sans);letter-spacing:.08em;color:{b["acc"]}">FINISHED</div>'
            f'<div style="margin-top:6px;padding-right:40px;font:700 26px/30px var(--font-sans);letter-spacing:-.01em">{b["title"]}</div>'
            f'<div style="margin-top:4px;font:500 14px/20px var(--font-sans);color:{rgba(b["fg"], .74)}">{b["sub"]}</div>')
    rating_row = (f'<div style="display:flex;margin-top:24px;font:13px/18px var(--font-sans)"><span style="flex:1;font-weight:600">Your rating</span>'
                  f'<span style="font-weight:500;color:{rgba(b["fg"], .74)}">{fin_hint(rating)}</span></div>{fin_stars(b, rating, True)}')
    note_html = fin_note(b) if note else f'<div style="display:flex;margin-top:16px">{fin_pill(b)}</div>'
    return (f'<div style="position:relative;width:640px;display:flex;border-radius:var(--radius-sheet);overflow:hidden;{fin_tint(b, 610)};box-shadow:0 24px 64px rgb(0 0 0 / .5)">'
            f'<div style="flex:none;width:232px;display:flex;align-items:center;justify-content:center;padding:36px 0;background:rgb(0 0 0 / .22)">{fin_cover(b, 152, 19)}</div>'
            f'<div style="flex:1;min-width:0;display:flex;flex-direction:column;padding:24px">{head}{rating_row}{note_html}'
            f'{fin_next(b, 20) if has_next else ""}<div style="display:flex;gap:10px;margin-top:auto;padding-top:24px">{fin_buttons(b, has_next)}</div></div>'
            f'<span style="position:absolute;top:16px;right:16px;width:40px;height:40px;border-radius:50%;background:{rgba(b["fg"], .10)};display:flex;align-items:center;justify-content:center">{ic("close", 22)}</span>'
            f'</div>')


def reader_page(w, h, pad, lines):
    bars = "".join(f'<i style="display:block;height:10px;margin:0 0 17px;border-radius:5px;background:#b9bec4;opacity:.55;width:{92 - (i * 37) % 23}%"></i>' for i in range(lines))
    return (f'<div style="position:absolute;inset:0;background:#0b0d0f;padding:{pad}">{bars}</div>'
            f'<div style="position:absolute;inset:0;background:rgb(0 0 0 / .6)"></div>')


finished_tablet = f"""
<div class="yb-device" style="width:{W}px;height:{H}px">
  {reader_page(W, H, "64px 120px", 28)}
  <div style="position:absolute;left:{(W - 640) // 2}px;top:50%;transform:translateY(-50%)">{finished_card(GLASS, 0)}</div>
  <div class="yb-spec">{g((W - 640) // 2, "640 card")}{g((W - 640) // 2 + 232, "232 band")}{g((W - 640) // 2 + 256, "24 inset", top=60)}{g((W + 640) // 2 - 24)}</div>
</div>"""

PW, PH = 412, 892
fb = COURIER
finished_phone = f"""
<div class="yb-device" style="width:{PW}px;height:{PH}px">
  {reader_page(PW, PH, "48px 22px", 34)}
  <div style="position:absolute;left:0;right:0;bottom:0;border-radius:var(--radius-sheet) var(--radius-sheet) 0 0;{fin_tint(fb, 660)};padding:0 20px 36px">
    <div style="display:flex;justify-content:center;padding:22px 0"><i style="width:32px;height:4px;border-radius:2px;background:{rgba(fb["fg"], .4)}"></i></div>
    <div style="display:flex;flex-direction:column;align-items:center;text-align:center">
      {fin_cover(fb, 96, 12)}
      <div style="margin-top:20px;font:800 11.5px/16px var(--font-sans);letter-spacing:.08em;color:{fb["acc"]}">FINISHED</div>
      <div style="margin-top:6px;font:700 24px/30px var(--font-sans);letter-spacing:-.01em">{fb["title"]}</div>
      <div style="margin-top:4px;font:500 14px/20px var(--font-sans);color:{rgba(fb["fg"], .74)}">{fb["sub"]}</div>
      {fin_stars(fb, 8, False)}
      <div style="margin-top:4px;font:500 13px/18px var(--font-sans);color:{rgba(fb["fg"], .74)}">{fin_hint(8)}</div>
      <div style="margin-top:16px">{fin_pill(fb)}</div>
    </div>
    {fin_next(fb, 24)}
    <div style="display:flex;gap:10px;margin-top:24px">{fin_buttons(fb, True)}</div>
  </div>
  <div class="yb-spec">{g(20, "20 inset")}{g(PW - 20)}</div>
</div>"""

comp("FinishedScreen", "Screens", 1900, """
# Finished

The screen at the end of a book (`JournalFinishedScreen`, feature:journal; app `ReaderFinishedHost`): one surface in the cover's colours (`coverTint`, the book page's `AppCoverColors`) over the last page. It opens when the last page is reached; ×, Back or a tap outside leaves the reader on that page.

- **Tablets (Medium and Expanded, when 640 + two 24 window margins fit): the cover beside.** A 640 card at `radius-sheet` 28 in a dialog window. On the left a 232 band, the tint deepened (black at 22% on a dark tint, the text colour at 6% on a light one), with the cover at 152 centred in it and a 36 margin above and below. On the right a column at `inset-dialog` 24: × (40, at 16 from the top and end), FINISHED (11.5 ExtraBold, 0.08 em, the cover's accent), the title (26/30 Bold, 40 clear of ×), the author (14 Medium, the text colour at 74%).
- **Rating**: 24 below, "Your rating" (13 SemiBold) with the hint at the end ("Tap to rate", the value while picking, "Rated 3.5"), then five 48 stars in 52 slots spread across the column, the first and last on its edges. Empty stars are the text colour at 28%, rated ones the accent. A tap saves; the left half of a star is a half; tapping the rating again clears it.
- **Note**: 16 below, "Add a private note" as a pill that hugs its label (40 tall, `edit` 18, 14 SemiBold, the text colour at 10% behind it, 8% on a light tint), which opens into the field ("Private note", "Only you can see this", `radius-control`) across the column. It saves when the screen closes, whichever way.
- **Next**: 20 below, when something follows (the series, or the list the book was opened from): a strip at `radius-container` (the theme's lowest surface at 30% on dark tints, 55% on light) with the next cover at 48, "Up next in Star Courier" (12 Medium, 74%) over its title (15 SemiBold). No button in it.
- **Buttons** at the foot of the column, 24 clear of what is above: Back to library (Back to the list when opened from one) and Read next, equal halves, 48 tall; Back to library is tonal (the text colour at 12%, 10% on a light tint) beside Read next and the accent when it is the only one, across the column.
- **Phones (Compact), and windows too narrow for the 640 card**: everything centred, in a bottom sheet in the tint (the card at 440 with × on a narrow window): the cover at 96 (120 in the card), FINISHED, the title at 24/30, the author; the stars centred with 4 between them and the hint under them; the note pill centred; then the next strip and the two buttons across. Sides `inset` 20 in the sheet, `inset-dialog` 24 in the card.
- **Motion**: the card fades in from 0.96 (the sheet slides up by itself), the cover rises 12 after 60 ms, the stars pop in from 0.6 one after another. All at once with animations off.
""", f"""
<div class="yb" style="padding:16px">
  {TOGGLE}
  <div id="screens">
    <div class="yb-caption" style="margin-bottom:8px">Expanded, 1280 &#215; 800: just finished, not rated, nothing next</div>{finished_tablet}
    <div class="yb-caption" style="margin:24px 0 8px">Rated 3.5, a note, and the next issue &#183; a phone, 412 wide</div>
    <div style="display:flex;gap:40px;align-items:flex-start">
      <div class="yb-ground" style="padding:40px;border-radius:18px;background:#0b0d0f">{finished_card(COURIER, 7, note=True, has_next=True)}</div>
      {finished_phone}
    </div>
  </div>
</div>""", width=1312, page=True, script=TOGGLE_SCRIPT)


print("screens written")
