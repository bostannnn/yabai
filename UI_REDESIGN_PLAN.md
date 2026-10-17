# UI Redesign Plan

Live tracker for making the app look modern. Update the status boxes as work lands, and record
anything that changes the plan under "Decisions" or "Open questions".

Status: `[x]` done · `[~]` done but not shipped (uncommitted, unmeasured, or not on the tablet) · `[ ]` not started

Started 2026-10-17. Scope so far is the library home; other screens are listed at the end but not designed yet.

## Decisions

- **Stay on Material 3** and build on it. The theme system (`ThemeMode`, `core:ui-theme` tokens,
  personality treatments) is built on the M3 `ColorScheme`, so swapping design systems (Miuix,
  Compose Unstyled, Lumo UI) would mean rewriting it. Revisit only if M3 Expressive still looks generic.
- **One cover size per screen.** Shelf covers use the grid's column width and 2:3 shape, so cover
  heights match across the shelves and the grid. Nothing on the library home gets a bigger "hero"
  cover.
- **Shelves stay side by side in their panels** (2026-10-17). Full-width flat rows were built and
  tried on the tablet (`f1956814`) and rejected: with a few books per shelf the rows were mostly
  empty and pushed the grid below the fold. The "no boxes inside boxes" idea is dropped for shelves.
- Shelf labels keep the app's existing Title Case ("Continue Reading", "Up Next").

## Constraints (from `ARCHITECTURE_RULES.md`)

- New shared visual tokens go in `core:ui-theme`; feature UI stays in its feature module; `app` only wires.
- Don't grow hotspot files (e.g. `LibraryFilterComponents.kt`, `AppViewModel.kt`); extract first.
- Anything that changes runtime work (cover sizes, blur, extra loads) needs `LibraryPerf` numbers
  from the tablet before it ships.
- Library presentation must commit coherently: shelves, grid, and filter summary must not jump or
  resize after the grid has shown.

## 1. Compose and Material 3 upgrade `[ ]`

Foundation for the newer components (Expressive shapes and motion, `LoadingIndicator`,
`ButtonGroup`, `FloatingToolbar`, carousels).

- [ ] Bump `composeBom` (currently `2024.12.01`, material3 ~1.3) to the current stable BOM
      (Compose 1.12, material3 1.4.0 as of 2026-10). Separate branch, no visual changes.
- [ ] Fix deprecations and compile errors across all modules.
- [ ] `./gradlew testDebugUnitTest` green; library and reader smoke-tested on the emulator.
- [ ] `LibraryPerf` cold-start numbers on the tablet compared before and after.
- [ ] Decide whether to opt into the experimental Expressive APIs now or wait for material3 1.5
      stable (1.5.0-beta01 was out on 2026-10-07).

## 2. Typography and icons `[ ]`

The app screens use the system font: base typography is `FontFamily.Default`
(`UnifiedReaderDesignTokens.kt`); Terminal 95 uses system monospace, Crimson Court and Detective
File use system serif. The bundled fonts (Atkinson Hyperlegible, Libre Baskerville, Crimson Pro,
Cormorant Garamond) are only for EPUB book text.

- [ ] Pick a UI typeface (and optionally a display face for headings); bundle it rather than
      depend on downloadable fonts.
- [ ] Wire it into the base `Typography` so all themes inherit it unless they override.
- [ ] Replace `material-icons-extended` (used in 10 modules, mostly `feature:library`) with
      Material Symbols Rounded.

## 3. Library home

### 3a. Shelves `[~]`

On branch `claude/distracted-feynman-c0cc8c`, not merged to main. Final layout is `cec77761`.

- [x] Shelf covers use the grid's cell width, 2:3 shape, and cover image variant
      (`LibraryGridMetrics` and `libraryGridColumns()` in `LibraryGridConfiguration.kt`, which
      mirrors `GridCells.Adaptive`). `LibraryScreen.topContent` receives the grid metrics.
- [x] Panels side by side as before; each `ShelfPreviewCard` is the cover width plus its padding.
- [x] Loading placeholder uses the same cover width and shape.
- [x] Unit tests: grid column math checked against `GridCells.Adaptive`; prefetch targets.
- [x] Commit.
- [ ] **Measure on the tablet.** Shelf covers now load at the grid's image size (`Grid`/`GridLarge`/
      `GridExtraLarge`) instead of `ShelfPreview`. Compare 5 cold starts against `adf843ff` **on the
      same source**; the first attempt was invalid because the source changed from Grimmory to
      Kavita between the two builds.
- [ ] Run or update the shelf instrumentation tests (`LibraryShelvesTopSlotTest`,
      `LibraryShelvesZoneActionsTest`). They compile; they haven't been run.

Measured for the rejected full-width version (`f1956814` vs `adf843ff`, Grimmory, Terminal 95,
5 cold starts each, baseline measured twice): start time unchanged (~426 vs 406/433 ms), frame time
p50 9 vs 13 ms and p99 ~71 vs ~110 ms, graphics memory 85 vs 106 MB. Either way the app draws
~1000 frames in 12 s while idle (continuous redraw, probably scrolling long titles); filed as a
separate task.

### 3b. Filter row `[x]` (mostly already done before this plan)

Since 2026-05-13 the row is one "Filters" button with a dropdown, chips for applied filters, and a
sort button. Optional differences from the 2026-10-17 mockup:

- [ ] All filters in one side sheet (bottom sheet on phones) instead of a dropdown that opens a
      dialog per filter. Must go in a new file, not `LibraryFilterComponents.kt`.
- [ ] A × on applied-filter chips to remove a filter in one tap.

### 3c. Covers and placeholders `[ ]`

- [ ] **Investigate first:** on the emulator, imported comics have cover files in
      `files/imported-cover-cache`, but both the grid and the shelves show the grey "CBZ"
      placeholder. Not caused by 3a (the grid's cover loading wasn't changed). The tablet showed the
      same for many Kavita books, so this may be a real bug.
- [ ] Replace the grey "CBZ" placeholder with a colored one: color derived from the title (stable
      per book), title and format printed on it.

## 4. Blur behind chrome `[ ]`

- [ ] Add Haze (`dev.chrisbanes.haze`, 1.x stable) behind the top bar and bottom sheets so content
      scrolls under frosted chrome.
- [ ] Measure scroll smoothness on the tablet; blur over a cover grid costs GPU time.

## 5. Cover-derived color `[ ]`

- [ ] Use MaterialKolor to build a color scheme from a book's cover for the book detail view and
      reader chrome; colored placeholders (3c) can use the same palette code.
- [ ] Extract colors off the main thread and cache them per cover.

## Not yet designed

- Reader chrome (top bar, page slider, preferences panel)
- Settings
- Navigation drawer
- Book detail / series view
- Journal

## Open questions

- Should Continue Reading get more emphasis (e.g. a tile spanning two grid columns) if the
  same-size row feels too flat on the tablet?
