# Third redesign pass

Section 9 of [`UI_REDESIGN_PLAN.md`](../../../UI_REDESIGN_PLAN.md) is the plan; this folder keeps the
boards it was decided on. The second pass's rules still hold (see
[`../second-pass/README.md`](../second-pass/README.md)): Manrope for UI text, pill controls, 6 dp
covers, everything through `core:ui-components` with tokens in `core:ui-theme`, mock sizes in dp,
drawn at 800 dp portrait tablet and a 412 dp phone in the Graphite palette.

## Review, 2026-10-24

`pages/review.html` (live board with the picks: https://claude.ai/artifact/EeiAPfFXXD8mFUfENQMGHM)
is a review of 0.17.1 on the `yabai_tablet_35` emulator and in code. Each card sets today's
screenshot (`pages/img/`) beside a proposal. Serve the folder over http to open it; picks there save
only to that browser.

| Card | Pick | Step |
|------|------|------|
| finished: the Finished screen | Do | 9.1 |
| opening: opening a book, reader errors | Maybe | 9.7 |
| toasts: one toast for the whole app | Do | 9.2 |
| splash: splash and window background | Do | 9.3 |
| shelf-browser: "See all" on the home tile | Do | 9.4 |
| footnote: footnotes in the book's look | Do | 9.6 |
| journal-plus: remove the journal's "+" | Do | 9.5 |
| book-page-reading: "Your reading" on the book page | Do | 9.5 |
| tile-meta: one baseline, one progress format | Do | 9.4 |
| comic-labels: issue names, Local series | Don't | |
| page-colours: page colours, settings units | Do | 9.6 |
| page-frame: margins, chapter line | Do | 9.6 |
| kit-drift: places that bypass the kit | Do | 9.8 |
| identity: wordmark, icon | Don't | |
| notifications: download notifications | Don't | |
| widget-shortcuts: shortcuts, widget | Don't | |
| haptics: a haptic vocabulary | Don't | |

No notes were left. Sub-points a card marked Maybe are settled on each step's own mock.

## 9.1 The Finished screen, 2026-10-24

`pages/finished.html` (live board: https://claude.ai/artifact/2Kdx3suRVqewsTWQ2zgqWL, picks in its
store, collection `picks`) mocks the Finished screen at real dp sizes with the emulator's covers
(`pages/img/cover-*.jpg`, taken from the debug build's imported-cover cache) and the colours
`coverSwatches` + `appCoverColors` give them in Graphite and Paper. Cards: `layout` (A card over the
page, B an end page), `numbers`, `rating-note`, `next`, `phone`, `light`, `motion`, `trigger` (A as
today, B on turning past the end). Picked: layout A, numbers Don't, rating-note, next, phone, light
and motion Do, trigger A. Built as plan 9.1 records.

## 9.2 Toasts, 2026-10-24

`pages/toasts.html` (live board: https://claude.ai/artifact/KmWDzyBYWgTbE1urzEGSbT, picks in its store,
collection `picks`) draws the toast at dp size over emulator screenshots (`pages/img/bg-*.jpg`, cropped
above the system bars, 800 dp wide; the phone one 412 dp). Cards: `style` (A raised pill, B glass),
`placement`, `downloads`, `read-status` (A with Undo, B without), `removals`, `sync`, `update`, `import`.
Picked: style B, read-status B, update Don't, the rest Do. Built as plan 9.2 records.

## 9.3 Splash and window background, 2026-10-24

`pages/splash.html` (live board: https://claude.ai/artifact/NVtBG1PaX5uZJWzUHAd5bL, picks in its store,
collection `picks`) shows today's cold start on the emulator (`pages/img/splash/today-*.jpg`, from
`screenrecord` of the 0.17.1 debug build: Graphite on a light and a dark device, Paper on a dark one) and
draws the splash at dp size in every theme from `UnifiedReaderTheme.kt`'s `background` and `primary`, with
the launcher icon's own layers (`img/splash/mark.png`, `mark-mono.png`, cropped from
`ic_launcher_foreground.png` and `ic_launcher_monochrome.png`) and the library as end frame
(`img/splash/lib-graphite.jpg`, `lib-paper.jpg`). Cards: `mark` (A monochrome in the accent, B on the
cream disc, C bare), `colour`, `handoff` (A Android's exit, B the mark steps back; both play in the
page), `hold`. Picked: mark A, colour Do (stored as Don't at first, a mis-pick corrected in chat), hand-off A,
hold Do. Built as plan 9.3 records.

## 9.4 Tiles and See all, 2026-10-24

`pages/tiles.html` (live board: https://claude.ai/artifact/7m7UaCdjeJaVekarw6dHqR, picks in its store,
collection `picks`) sets today's See all, its list view, the home strip, a grid row and an empty search
(`pages/img/tiles/today-*.jpg`, 0.17.1 debug build on `yabai_tablet_35`, the Local library) beside tiles
drawn at dp size in Graphite with the emulator's covers (`pages/img/tiles/covers/`, from the second pass's
cover set). Cards: `browser-tile`, `browser-tabs`, `browser-empty`, `baseline`, `progress-format`,
`first-page`, `in-progress-1`. Picked: all seven Do, no notes. Built as plan 9.4 records.

## 9.5 The book page's "Your reading", 2026-10-24

`pages/bookpage.html` (live board: https://claude.ai/artifact/XbFW4XEbLT15Q7Mfn9WjKk, picks in its store,
collection `picks`) sets today's book pages and the journal's "+" (`pages/img/bookpage/today-*.jpg`, the
emulator after 9.4) beside the page drawn at 440 dp with Glass Meridian's cover colours and its real journal
record (rated 3.5, started 21 Oct, finished 24 Oct, 9 sessions, 8 min). Cards: `reading-place` (A/B),
`reading-content`, `file-facts`, `remove-row`, `journal-plus`. Picked: reading-place B, reading-content
Don't, file-facts Do, remove-row Maybe, journal-plus Do; answered in chat: no "Your reading" section, and both
removals move to the end. Built as plan 9.5 records.
