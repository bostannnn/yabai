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
