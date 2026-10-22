# Second redesign pass: build spec

Approved by the user on 2026-10-22. This folder says exactly what to build for section 8 of
[`UI_REDESIGN_PLAN.md`](../../../UI_REDESIGN_PLAN.md): the order, the mock for every change, and the
sizes and behaviour the mocks only show. Build from here; record progress in the plan.

## How to use this folder

- **`mocks/`** has one image per approved change, named by plan step (`8.3-reading-card.jpg`). Each
  image is a decision card: the change, today's app (an emulator screenshot, or redrawn from the
  code where marked), the proposed mock, and the user's decision (green Do, amber Maybe, red
  Don't) with a note. Open them with the Read tool.
- **`pages/`** has the same cards as working HTML pages. Open them in a desktop browser for the
  motion items (Play buttons), the tab and slider mocks, and the reader prototype in
  `reader-menus.html`, which switches between book and comic and between tablet portrait, tablet
  landscape and phone. To render one card or prototype state headlessly, add `?only=<card id>` or
  `?proto=1&r=book|comic&z=tp|tl|ph&p=off|on|left|right` to the page URL.
- **Precedence:** where a mock and this README disagree, this README wins (the notes on the cards
  say where). Mock sizes are in dp (1 px in a mock is 1 dp), drawn at 800 dp portrait and 1280 dp
  landscape tablet and a 412 dp phone, in the Graphite palette. Every theme uses the same roles.
- **Rules that hold everywhere:** UI text is Manrope (no serif display face; book text keeps its
  reading fonts). Controls are pills. Covers are 6 dp. Everything goes through
  `core:ui-components`, with tokens in `core:ui-theme` (`ARCHITECTURE_RULES.md`). Anything that
  changes runtime work needs `LibraryPerf` numbers from the tablet. Never run adb against the
  tablet without asking; use an emulator (`yabai_review_35` on port 5560 has a 21-book test
  library with illustrated covers).

## Build order

Build top to bottom. Each step lands, gets judged on the tablet, and only then does the next one
start. A screen that has no mock here gets a quick mock and the user's OK before it is built.

| # | Step | Needs first | Can run alongside | Size |
|---|------|-------------|-------------------|------|
| 1 | **8.0 Prep**: Compose BOM, material3 and Haze upgrade (done: Compose 1.12.1, material3 1.4.0, Haze 1.7.3, AGP 9.2.1, compileSdk 37); tablet `LibraryPerf` baselines | | 8.1 tokens and transitions | M |
| 2 | **8.1 Motion**: tokens, route transitions, predictive back, press feedback, grid reorder, skeleton pulse | 8.0 baselines (the press shape morph needs the 8.0 upgrade) | 8.2 | M |
| 3 | **8.2 Kit**: pills, segmented, slider, dialogs, sheets, menus, download ring | | 8.1 | M |
| 4 | **8.3 Library home**: header, reading card, shelf tabs, tile progress bar, filter row, series stack, empty states | 8.2 | | L |
| 5 | **8.4 Filters, sort and top bar** | 8.2 | 8.5 (different files) | M |
| 6 | **8.5 Book page**, with the Room v29 description column | 8.2, and the reading card from 8.3 for Details | 8.4 | M |
| 7 | **8.6 Colour from the cover** | 8.3, 8.5 | 8.7 | M |
| 8 | **8.7 Reader menus** | 8.2 | 8.6 | L |
| 9 | **8.8 Navigation rail** and one window-size helper | 8.1 | 8.9 | M |
| 10 | **8.9 Screen passes**: journal, settings, then downloads, Kavita landing, first run (mock first) | 8.2 | 8.8 | L |

"Can run alongside" means separate sessions on separate files, never two sessions on the same files.

## Shared tokens

**Shape.** Cover 6 dp. Controls are pills (`CircleShape`): buttons, chips, segmented rows and their
segments, tabs, the search field, highlighted icon buttons, list rows inside panels. Menus, text
fields and settings rows keep 12 dp. Cards and the reading card are 24 dp; floating panels, side
sheets and the book page are 26 dp; phone sheets have 28 dp top corners. The reader bars are 56 dp
tall with 28 dp corners, so they read as pills.

**Type, Manrope only** (new roles in `core:ui-theme`):

| Role | Size / line | Weight | Used for |
|------|-------------|--------|----------|
| screenTitle | 40 / 44, -0.02 em | Bold | library header, journal, settings hub (32 on phones) |
| cardTitle | 30 / 34 | Bold | reading card |
| sheetTitle | 26 / 30 (24 filter sheet, 20 panels, 22 facet pages) | Bold | book page, filter sheet, reader panels |
| sectionTitle | 17 / 24 | SemiBold | journal sections, sheet sections |
| tileTitle | today's style | | grid tiles do not change |
| meta | 12.5 / 16, tabular | Medium | counts, progress, page numbers |
| overline | 11.5, +0.08 em, caps | Bold | "Continue reading" on the card only |

**Motion** (`AppMotion` in `core:ui-theme`; mapped onto Material's `MotionScheme` once material3 1.5
is adopted, since it is internal in 1.4.0):
spatial `spring(dampingRatio = 0.8f, stiffness = 380f)`, spatialFast `spring(0.6f, 800f)`,
effects `spring(1f, 1600f)`. All of it is off when the system animator scale is 0.

**Colour roles.** Selected = `surfaceSelected` with `onSurfaceSelected` (primaryContainer and
onPrimaryContainer). Accent = `primary`. Panel surface = `surfaceContainer` at 96% opacity, or the
glass at `AppGlassDefaults.paneTintAlpha`. Control backgrounds: segmented track `surfaceContainerLow`,
tonal buttons and the search field `surfaceContainerHigh`. Scrim: black at 45% (dark) or 32% (light).

## 8.1 Motion

Mocks: `8.1-screen-transitions.jpg`, `8.1-predictive-back.jpg`, `8.1-press-feedback.jpg`,
`8.1-grid-reorder.jpg`, `8.1-skeleton-pulse.jpg`. They are still frames; press Play on the same cards
in `pages/library-and-app.html`.

- **Fade-through** between Library, Journal and Settings, and for opening and leaving the reader:
  the outgoing screen fades out over 90 ms (linear); the incoming one starts at scale 0.96 and
  opacity 0, fades in over 210 ms and scales to 1 on spatial. The reader gets nothing more: the
  cover-zoom open-book animation was rejected.
- **Shared axis** for the series view, shelf browser, Kavita landing and settings pages: the
  incoming screen slides 30 dp from the end edge and fades in; the outgoing one slides 30 dp toward
  the start and fades out; back reverses it.
- **Predictive back** (`android:enableOnBackInvokedCallback="true"`, `PredictiveBackHandler`): at
  progress p the current screen moves `translationX = p * 60 dp`, scales `1 - p * 0.12` and rounds
  its corners to `p * 28 dp`; the screen underneath scales from 0.94 to 1. The system decides
  between finishing and cancelling (as built, 2026-10-23): letting go finishes it, dragging back to
  the edge springs it back. The screen stays opaque while dragged and fades only as it finishes. It covers the drawer, `AppFloatingPanel`,
  `AppSideSheet`, settings pages and leaving the reader. The existing `BackHandler` order (footnote
  popup, preferences) stays.
- **Press**: covers, chips and buttons scale to 0.96 on spatialFast while pressed. A pressed
  button's corners morph from pill to 14 dp, animated in the kit (material3 1.4.0 has no press
  morph; `ButtonDefaults.shapes()` is 1.5 only). "More actions" gets a bounded ripple
  (`LibraryItemMenuButton.kt` sets `indication = null` today).
- **Grid reorder**: `Modifier.animateItem(placementSpec = spatial)` on library grid items, so sort
  and filter changes move covers. Check it against paging placeholders and `libraryScrollRestoreStep`.
- **Skeleton pulse**: placeholder blocks go 1 → 0.45 → 1 alpha over 1.4 s, ease in-out, repeating.
- **`AppSideSheet` exit** mirrors its enter: it slides a third of its width toward its edge and fades.
- Not built: a cover fade-in while covers load (rejected).

## 8.2 Kit

Mocks: `8.2-pill-controls.jpg`, `8.2-segmented.jpg`, `8.2-slider.jpg`, `8.2-dialogs.jpg`,
`8.2-download-ring.jpg`; the menu is `8.4-sort-menu.jpg`.

- **Chips**: 36 dp, pill, 14 SemiBold, 1 dp `outlineVariant` edge; selected is filled
  `surfaceSelected` with no edge; an optional trailing count is 12 SemiBold at 75%.
- **Segmented rows**: a pill track (`surfaceContainerLow`, 3 dp inner padding) holding 34 dp pill
  segments; selected is filled `surfaceSelected`; no checkmark icon and no outline; unselected text
  is `onSurfaceVariant`.
- **Slider**: 28 dp touch height; an 18 dp pill track (`surfaceContainerHighest`) filled with the
  accent up to the value; a 4 × 28 dp handle in `onSurface`, 3 dp corners; the value sits at the
  end of the label row.
- **Dialogs** (`AppDialog`): 380 dp wide, `surfaceContainerHigh`, 28 dp corners, 24 dp padding;
  optional 52 dp cover thumbnail before the title; title 24 Bold; body 14 `onSurfaceVariant`;
  buttons are 42 dp pills, Cancel tonal and a destructive action filled with the error container.
- **Sheets** (`AppSheet`): our own handle (36 × 4 dp, 2 dp corners, `outline`), 28 dp top corners.
- **Menus** (`AppMenu`): `surfaceContainerHigh`, 16 dp corners, 8 dp vertical padding. Section
  headings are 12.5 Bold `onSurfaceVariant` text, not disabled items. Rows are 44 dp with 18 dp side
  padding and a 20 dp check column (an accent check and Bold text on the current choice). Booleans
  use `AppSwitch`, not checkboxes.
- **Download ring**: a 2 dp ring around the 40 dp Downloads button, track `surfaceContainerHighest`,
  progress in the accent from 12 o'clock; it replaces the error-red `Badge`.
- `FeatureMaterialImportRuleTest` also forbids `ButtonDefaults.shape` and the stock segmented-button
  icons in feature code.

## 8.3 Library home

Mocks: `8.3-header.jpg`, `8.3-reading-card.jpg`, `8.3-shelf-tabs.jpg` (tablet landscape: card and
tabs side by side), `8.3-tile-progress-bar.jpg`, `8.3-filter-row.jpg`, `8.3-series-stack.jpg`,
`8.3-empty-state.jpg`. **Mock first:** the phone layout and the series view header.

- **Header**: the source name in screenTitle, then a 14 Medium `onSurfaceVariant` summary 8 dp
  below ("12 books · 9 comics · 7 in progress"). At 840 dp and wider, the end of the header holds the
  search field (340 × 44 dp pill, `surfaceContainerHigh`), Downloads and "•••" (40 dp icon buttons).
  Once the title has scrolled away, today's glass `AppTopBar` slides in (spatial) with the name at
  20 SemiBold.
- **Reading card**: the last-read book still in progress; hidden when there is none. It spans three
  of six grid columns in landscape (grid gap 24 dp) and the full width in portrait and on phones.
  24 dp corners, 22 dp padding, 22 dp between the cover and the text. The cover is exactly the grid
  cell width, with a 0 12 30 dp black 35% shadow. Text column: overline "CONTINUE READING" at 72%
  alpha; cardTitle, two lines; the author at 14.5 Medium, 74% alpha; at the bottom "Chapter 3 of 6"
  and "42%" at 13 SemiBold, a 6 dp progress bar (track: white 18% on dark, ink 14% on light), then
  44 dp pills Continue (filled with the card accent) and Details (tonal, track colour). Until 8.6 the
  card uses `primaryContainer` and `primary`; from 8.6 it is tinted from the cover.
- **Shelf tabs**: 34 dp pill tabs "In progress 6", "Up next 3", "Finished 4" (counts 12 SemiBold at
  80%), selected filled `surfaceContainerHigh`; "See all" in the accent at the end of the row. Under
  them, one horizontal strip of today's shelf cards (cover, title, subtitle, accent line; their
  text does not change) at the grid's cell width and gap. The panel containers go. A tab with no
  items is hidden; with none at all, the band is hidden. Switching tabs fades the strip and slides
  it 12 dp (spatial). The band's height comes from the cover height, so switching never moves the
  grid (presentation coherence).
- **Tile progress bar**: inset 7 dp from the cover's left, right and bottom edges, 4 dp tall, pill,
  track black 42%, fill accent; it replaces `LibraryCoverProgressBar`'s edge-to-edge bar.
  **Nothing else on the grid tile changes**: the two-line titles, meta line and quieter "•••" in the
  mock were rejected.
- **Filter row**: a "Filters" chip with the filter icon and a 20 dp accent count badge; one chip per
  applied value naming it ("EPUB", "In progress", an author), whose × removes that value (today it
  reads "Format (1)"); the sort chip ("Last read ▾") at the end.
- **Series stack**: two hairlined strips behind the first cover, inset 7 and 14 dp and raised 6 and
  12 dp, in `surfaceContainerHigh` and `surfaceContainerHighest`; the meta line reads "3 issues" or
  "3 volumes"; the count badge goes.
- **Empty states**: a centred 170 × 118 dp line drawing (three book outlines, 2.4 dp
  `onSurfaceVariant` strokes), a 28 Bold title, a 14.5 `onSurfaceVariant` body up to 360 dp wide, and
  actions. Empty library: "Nothing here yet", Import a file (filled) and Connect a server (tonal).
  Filters with no matches: "No books match these filters" and Clear filters. An unreachable source:
  "Can't reach Kavita" and Try again.

## 8.4 Filters, sort and the top bar

Mocks: `8.4-filter-sheet.jpg`, `8.4-filter-list.jpg`, `8.4-sort-menu.jpg`, `8.4-top-bar-search.jpg`
(search field), `8.4-top-bar-menu.jpg` ("•••" without Select), `8.4-selection.jpg`.

- **Filter sheet** (`AppSideSheet` at 600 dp and wider: 420 dp wide, 26 dp corners, 12 dp margins;
  a bottom sheet on phones). Header: "Filters" (24 Bold), "Showing 14 of 21" (13.5 SemiBold
  `onSurfaceVariant`), "Clear all" in the accent only while something is applied, and a close
  button. Body: Reading status, Format and Location stay open as chips with counts, under 13 Bold
  `onSurfaceVariant` headings, separated by hairlines. The other facets (Author, Series, Genre,
  Tag, Language, Publisher, Library) are 52 dp rows: label 15 SemiBold, the selection summary at the
  end ("Any" when empty, accent when set), and a chevron. Footer: a full-width 46 dp pill "Show 14
  books" that closes the sheet. Filters still apply as they are tapped, and counts reflect the
  other active filters. Which facets show still follows `availableLibraryFilterPanels`; the Kavita
  reading-list scope keeps its dialog.
- **Facet page**, shown inside the sheet with the shared-axis slide: back arrow, the facet name
  (22 Bold) and "Clear"; an always-visible 42 dp search pill (today it appears only past eight
  values); a "Selected" group pinned on top; then A-Z groups under 12.5 Bold letters. Rows are
  52 dp: name, count, and a 22 dp round check (accent fill with a check, or a 2 dp ring). Footer
  "Show N books".
- **Sort and view menu** (`AppMenu`, 300 dp, anchored under the sort chip): "Sort by" with a check
  on the current sort; a divider; "View" with a "Fade read books" switch; "Cover size" as a
  full-width segmented row: Small (140 dp cells), Medium (180, the default) and Large (240). The
  160 and 200 settings snap to Medium. This replaces "180dp cards · 6 cols".
- **Top bar**: on 840 dp and wider, search is a field in the bar; on phones it stays an icon. The
  Select (checklist) button goes: a long-press already starts selection. "•••" holds "Import a
  file" and, on Kavita, "Refresh library". On server sources it also shows the tip "Long-press a
  cover to select books for download." under the items until the user has selected once.
- **Selection bar** (after a long-press): replaces the top bar; 56 dp, `surfaceSelected`; close,
  "3 selected" (18 Bold), "Select all", and a filled 40 dp pill "Download 3". Selected covers scale
  to 0.94 with a 3 dp accent ring and a 22 dp check at the top end.

## 8.5 Book page

Mock: `8.5-book-page.jpg` (a comic; a book shows author and chapters instead of issue and pages).

- Opens from "•••" under a cover and from Details on the reading card; long-press stays selection.
  It replaces the `LibraryItemMenu` dropdown and `LibraryItemReaderModeMenu`. Side sheet at 600 dp
  and wider (440 dp, 26 dp corners), bottom sheet on phones.
- **Top**, on a tint (the cover colour from 8.6, `surfaceContainerHigh` before that): close; the
  cover 118 dp wide with a shadow; title in sheetTitle; series line "Neon Ronin · 2 of 3" at 14
  SemiBold; creators at 13.5. Then a full-width 46 dp accent pill "Continue · page 3 of 8"
  ("Start reading" or "Read again" when not started or finished); two 42 dp tonal pills, "Mark
  finished" and "Remove file" (or "Download" for a server book not on the device); a 6 dp progress
  bar with "38% · read yesterday" and "3 / 8".
- **Reading**: Direction (comics only) and Open as (Paged / Scroll) as segmented rows, moved from
  the dropdown and saved where they are today.
- **About**: the description (four lines, then "More"); genre and tag chips (30 dp; a tap applies
  that filter to the library and closes the page); facts: format and pages, size, language,
  publisher.
- **More in <series>** (with See all), **More by <author>**, and **Similar** (books sharing at least
  two genres or tags, up to eight), each a row of covers four across with 12 dp gaps.
- **Data**: the description is new. The Kavita and Stump DTOs already parse `summary` and Grimmory's
  parse `description`, but the domain model drops it. Add it to the item model and Room (schema v29,
  with a migration; there is no destructive fallback), plus EPUB `dc:description` and ComicInfo
  `Summary` for local files. Everything else on the page already exists.

## 8.6 Colour from the cover

Mock: `8.6-cover-colour.jpg` (tap the small covers on the same card in `pages/library-and-app.html`).

- Extract a palette per cover once, off the main thread, from a small decode (about 60 × 90 px),
  and cache it with the cover file. Accent = the most saturated swatch with lightness between 0.22
  and 0.78, weighted by population. Ambient = the dominant swatch plus the next one.
- On dark themes the card and book-page tint is the ambient colours darkened to about L 0.25-0.30
  in two radial layers, with text `#f6eef1`; the accent is lightened to about L 0.75 with dark text
  on it. On light themes the tints are pale (about L 0.90) and the accent is darkened to about
  L 0.35 with white text. Button text keeps at least 4.5:1 contrast; otherwise fall back to the
  theme primary.
- No live blur: the tint is gradients, or a 16 × 24 downscale of the cover drawn stretched.
- Used by the reading card, the book page and the reader's accent (slider fill, selected states).
  The grid and lists stay neutral.

## 8.7 Reader menus

Mocks: the prototype states `8.7-book-…` and `8.7-comic-…` for tablet portrait, tablet landscape
and phone (`reading` = bars hidden, `bars`, `contents` or `pages`, `settings`); the panel contents
are `8.7-epub-contents.jpg`, `8.7-epub-settings.jpg` and `8.7-comics-settings.jpg`. Try them live in
`pages/reader-menus.html`. The state images:

| | Tablet portrait | Tablet landscape | Phone |
|---|---|---|---|
| Book | `8.7-book-tablet-portrait-reading.jpg`, `…-bars.jpg`, `…-contents.jpg`, `…-settings.jpg` | `8.7-book-tablet-landscape-contents.jpg`, `…-settings.jpg` | `8.7-book-phone-bars.jpg`, `…-contents.jpg`, `…-settings.jpg` |
| Comic | `8.7-comic-tablet-portrait-bars.jpg`, `…-pages.jpg`, `…-settings.jpg` | `8.7-comic-tablet-landscape-bars.jpg`, `…-pages.jpg`, `…-settings.jpg` | `8.7-comic-phone-pages.jpg`, `…-settings.jpg` |

- **Bars**: inset 12 dp (8 on phones). The top bar is 56 dp with 28 dp corners: Back (42 dp), a
  centred title (15 SemiBold) over a second line (12 SemiBold `onSurfaceVariant`), and at the end Aa
  (EPUB) or the settings icon (comics). The second line names where you are, never the position:
  "Глава 3 · Анна Лесная" for books, "Neon Ronin · issue 2 of 3" for comics. The bottom dock has
  the same inset and corners. Its 60 dp row holds the bottom-left button (Contents for EPUB, Pages
  for comics), the percentage (13 SemiBold), an 8 dp slider track with chapter ticks (2 × 14 dp) and
  a 4 × 20 dp knob, and the position ("p. 37 / 412", "2–3 / 8").
- **Comics**: the page strip moves inside the dock, above the row. Thumbnails are 110 dp tall in
  portrait, 96 in landscape and 70 on phones; spreads are paired; the current one has a 2 dp accent
  ring and its label under it.
- **Panels never cover the bars.** Top edge: 12 + 56 + 10 dp from the screen top. Bottom edge: the
  dock height + 12 + 10 dp above the screen bottom. Left: Contents 360 dp or Pages 380 dp. Right:
  settings 400 dp; the comics settings panel takes its content's height, top-aligned. 26 dp
  corners; slide 28 dp from their edge and fade (spatial). A 45% scrim covers the page only; the
  bars stay above it. One panel at a time. The button that opened it is filled while it's open, and
  that button, the ×, the scrim, a page tap or Back closes it.
- **Phones**: panels are bottom sheets, full width and 70% tall, with 28 dp top corners and a
  handle. The dock slides away while one is open and comes back when it closes; the top bar stays.
- **Contents** (EPUB): header "Contents" (20 Bold) and "<book> · 6 chapters". 48 dp pill rows; the
  current chapter is filled `surfaceSelected` with "40% through"; read chapters are
  `onSurfaceVariant` with a 16 dp check; page numbers at the end (12.5, tabular); sub-chapters
  indented 22 dp. Footer: "Chapter 3 of 6 · 42%" and a 6 dp bar. It still opens scrolled to two
  rows above the current chapter.
- **Pages** (comics, new): header "Pages" and "<issue> · 8 pages"; a three-column grid of 2:3
  thumbnails with 12 dp gaps, the current page or spread ringed in the accent (2.5 dp), page numbers
  under them; footer "Pages 2–3 of 8 · 38%".
- **Settings** content is today's, restyled with the 8.2 rows. EPUB: Text (Typography, Font ›, Text
  size stepper with 42 dp round − and + buttons, Line height, Alignment), Page (Page colour, Page
  number), Layout (Pages, Page turn, Margins, paragraph spacing and indent). Comics: Layout,
  Direction, Pages, Page turn, Fit, Page number. Section headings are sentence case.
- The page number from 0.11.0 is unchanged: it shows while the bars are hidden and fades when they
  appear. The PDF reader uses the same frame.

## 8.8 Navigation rail

Mock: `8.8-navigation-rail.jpg`.

- At 840 dp and wider: an 88 dp rail. Items are 80 dp wide: a 56 × 32 dp pill indicator around a
  24 dp icon, and a 12 SemiBold label; the selected indicator is `surfaceSelected`. Sources in the
  drawer-order setting, a spacer, then Journal and Settings. No app mark (the mock's italic "y" was
  a serif). Kavita's Home, Collections and Reading Lists open from a small menu on its rail item.
  Phones keep the drawer.
- One window-size helper in `core:ui-components` (Compact < 600, Medium 600-839, Expanded ≥ 840 dp)
  replaces the ten breakpoint values in use today (360, 520, 560, 600, 640, 680, 700, 760, 840, 860).

## 8.9 Screen passes

Mocks: `8.9-journal.jpg` (top of the journal), `8.9-settings.jpg` (hub). **Mock first:** the rest of
the journal, the settings pages, downloads, Kavita landing and first run.

- **Journal**: the Month / Year segmented row and the month as a 30 Bold centred title between
  arrows; four figures at 46 Bold, tabular (hours read, pages, finished, active days), each with a
  13 SemiBold label; "Pages per day" (17 SemiBold) with a one-line summary; an area chart with a
  2.5 dp accent line, a 16% accent fill and day labels every seven days; "Books and comics" as a 12 dp
  split bar. No uppercase captions; book titles from metadata, not file names; empty sections fold
  away.
- **Settings hub**: screenTitle "Settings"; group headings at 15 SemiBold in sentence case; the
  Theme row shows three covers drawn in the current theme's colours.

## Not built (rejected 2026-10-22)

- The cover-zoom open-book animation (the reader uses the fade-through), and a cover fade-in while
  covers load.
- A serif display face for UI titles.
- Quieter placeholder covers (the 3c designs stay).
- Trimming the theme list (all twelve stay).
- Grid tile changes other than the progress bar: no two-line titles, meta line or quieter "•••".
- Cover size as separate icon buttons outside the sort menu.

## Sources

The live decision pages, with the user's saved picks: library and app
https://claude.ai/artifact/JPHMSHf3WMJab1fihdKJgu, filters and menus
https://claude.ai/artifact/FciEVVZo2pYSM3NjKQZBpB, reader menus
https://claude.ai/artifact/Tnrj9TXe5YoVjux3Hkf8sn, and the review that started it
https://claude.ai/artifact/8TSpyrGAszggJMK4vUnrNX. The copies in `pages/` have the final decisions
baked in and UI titles switched to Manrope.
