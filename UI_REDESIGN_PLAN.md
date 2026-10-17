# UI Redesign Plan

Live tracker for making the app look modern. Update the status boxes as work lands, and record
anything that changes the plan under "Decisions" or "Open questions".

Status: `[x]` done · `[~]` done but not shipped (uncommitted, unmeasured, or not on the tablet) · `[ ]` not started

Started 2026-10-17. Scope so far is the library home; other screens are listed at the end but not designed yet.

## Decisions

- **Direction approved 2026-10-17: glass chrome over flat content, themes as palettes only.**
  Mock: https://claude.ai/artifact/HUb2kC5y5k17dniBJgxTKa (library home, seven color-only
  themes). One translucent blurred material for the top bar, drawer, sheets, and reader bars;
  covers and lists sit flat on the background; one cover tile shared by the grid and the
  shelves; three corner radii (cover, control, container); progress and finished state drawn on
  the cover; the designed placeholder covers from 3c stay as they are.
- **Build it through an app-owned component kit, not a new UI library.** The mismatched look
  came from feature code calling raw Material `Card`/`Surface`/`Dialog`/`Button` with ad-hoc
  shapes and alphas. `core:ui-components` is the single chokepoint; Haze is the only planned
  new dependency. Kyant's Backdrop (refractive liquid glass), Miuix, Lumo, and Compose Unstyled
  were considered and rejected: none fixes the missing component layer, and Backdrop is GPU
  work over a scrolling cover grid that would need `LibraryPerf` proof.

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

## 0. Unification sequence

Pull-request-sized steps; each lands and gets judged on the tablet before the next.

1. `[x]` **Component kit, no visual change.** `core:ui-components` with `AppPanel`,
   `AppBackground`, `AppBadge`, `AppDialog`, `AppDialogHost`, `AppSheet`, `AppButton`,
   `AppOutlinedButton`, `AppTextButton`, `AppIconButton`, `AppTonalIconButton`, `AppChip`,
   `AppDivider`, `AppMenu`, `AppMenuItem`, `AppSegmentedRow`, `AppSegment`, `AppTextField`,
   `AppLinearProgress`, `AppCircularProgress`. Every raw Material call for those roles in
   `feature/*` and `app` routed through the kit (44 files); the kit mirrors Material's
   parameters and defaults so nothing moves. `FeatureMaterialImportRuleTest` forbids the raw
   imports from coming back. Still allowed until the kit covers them: `Checkbox`, `Switch`,
   `Slider`, `Scaffold`, `Snackbar`, drawer and navigation rail components.
2. `[x]` **Themes to color only.** Deleted `UnifiedReaderTreatmentModifiers.kt`, the per-theme
   `Shapes` and `Typography`, `UnifiedReaderComponentCatalog.kt`, the three personality theme
   files, and the personality/component token types. Every palette kept. One shape scale:
   cover 6, control 12, container 20 (was 18 for cards and 8 for chips in the plain themes).
   The faint accent border on shelf panels and shelf cards stays as a plain 1 dp stroke; the
   thin cover and row progress bars draw through `Modifier.appProgressFill` in the kit.
   `THEME_CUSTOMIZATION_GUIDE.md` rewritten for palette-only themes.
3. `[x]` **The look.** Haze 1.5.3 (the last line built against Compose 1.7; Haze 1.6 needs
   Compose 1.8, so the BOM bump in section 1 is still separate). The kit owns the recipe:
   `Modifier.appGlass` (translucent surface over a 22 dp blur, hairline edge, near-opaque
   fallback where blur is unavailable), `Modifier.appGlassSource` on the content it floats over,
   and `AppTopBar`, a 56 dp floating bar with `AppTopBarDefaults.contentInset` of top padding
   reserved by each screen so the library, Kavita landing, shelf browser, and journal scroll
   under it (settings is inset but does not scroll under yet). The drawer is a floating glass
   panel inset from the screen edges. Shelf covers are bare tiles like the grid's; shelf panels
   keep a title, a muted count, and See all, with no accent rail, mark, or count pill. One
   `LibraryCoverProgressBar` (4 dp, accent over a dark track) draws on covers in the grid, the
   shelves, the shelf rows, and the shelf browser; finished books get a check circle instead
   of the Read pill and no dimming. Reader bars, panes, and the preview strip use the same glass
   through `readerOverlaySurface`, with the pages as the source via `ImmersiveReaderOverlay`.
   Checked on the emulator (library, drawer, comics reader).
   Bottom sheets stay opaque (a modal sheet is its own window, so it cannot blur the screen
   behind it).
   **Measured on the tablet 2026-10-17** (release `4474011f`, Kavita, Graphite, 890 items, 120 Hz),
   against the shelves baseline above (start 420/418/417 ms, scroll p50 14 ms, p90 17-19 ms):
   cold start `am start -W` 421/401/445 ms; idle 0 frames in a 10 s window; a four-swipe fling
   over the cover grid under the glass bar rendered 1220 frames at p50 14 ms, p90 19 ms, p95
   25 ms, p99 44 ms, 1 janky frame (0.08%), GPU p50 7 ms / p90 9 ms; opening and closing the
   glass drawer: 108 frames, p50 13 ms, p90 16 ms, 0 janky. Start time and scroll frame times are
   unchanged by the blur; the GPU share of a frame is now near the 8.3 ms budget of 120 Hz, so
   any further blur (sheets, larger regions) needs its own measurement.
4. `[x]` **Font and icons.** Manrope is bundled as a variable font
   (`app/src/main/res/font/manrope.ttf`, OFL, full Cyrillic) and is the family of every style in
   the base `Typography`: display and headline at Medium, titles at SemiBold, labels at Medium,
   body at Regular. Material Symbols Rounded replaces `material-icons-extended`: the 40 symbols
   the app uses are vector drawables in `core:ui-components`, exposed through `AppIcons`, and the
   import rule now forbids `androidx.compose.material.icons`. Licenses are in
   `THIRD_PARTY_NOTICES.md`. Placeholder covers keep their own serif, as a book jacket would.
5. `[x]` **Settings and journal** in the new language. `AppPanel` now defaults to the raised
   surface (`surfaceRaised`) and the container radius, so every grouped panel in the app shares
   one look; inner rows and tiles sit on the plain surface at the control radius. Settings lost
   its developer-facing subtitle, its list and overview layouts take the top-bar inset and scroll
   under the bar, and the navigation and filter sections use the same panel pair. Journal's
   stat panels drop their 8 dp corners and tonal elevation for the defaults; its facet dialog
   is a plain surface with a shadow. There is no book detail screen in the app, so nothing to
   rebuild there. The per-cover kebab menu stays: long-press already enters download selection
   on remote sources, so moving the menu there would collide with it.

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

- [x] Manrope, bundled as a variable font (see step 4 in section 0).
- [x] Wired into the base `Typography`; themes are palettes only, so every theme inherits it.
- [x] `material-icons-extended` replaced by Material Symbols Rounded vector drawables behind
      `AppIcons` in `core:ui-components`.

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
- [x] **Measured on the tablet** (Kavita, Terminal 95, 5 cold starts per block, new/old/new):
      start time 420/418/417 ms and frame times (p50 14 ms, p90 17-19 ms) unchanged; graphics
      memory **+13 MB** (122.5-122.8 vs 109.7 MB) because shelf covers now load at the grid's image
      size (`Grid`/`GridLarge`/`GridExtraLarge`) instead of `ShelfPreview`. Kept for sharper covers.
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

- [x] All filters in one side sheet (`LibraryFilterSheet.kt`, on the kit's new `AppSideSheet`,
      which becomes the bottom sheet under 600 dp). One section per visible panel; a section
      opens to its chips or to a searchable checklist, the header shows the selected count with
      its own ×, and the sheet header has Clear all. The Filters chip opens it directly, an
      applied chip opens it at that section. The per-panel dialogs are gone, and
      `LibraryFilterComponents.kt` shrank from 915 to 492 lines. The Kavita reading-list scope
      keeps its dialog.
- [x] A × on applied-filter chips removes that filter in one tap.

### 3c. Covers and placeholders `[ ]`

- [ ] **Investigate first:** on the emulator, imported comics have cover files in
      `files/imported-cover-cache`, but both the grid and the shelves show the grey "CBZ"
      placeholder. Not caused by 3a (the grid's cover loading wasn't changed). The tablet showed the
      same for many Kavita books, so this may be a real bug.
- [x] Design chosen 2026-10-17 (mockup, no code yet). The current placeholder is one grey
      gradient with the format name; for PDF the label is empty, so a PDF without a cover is blank.
  - **EPUB, PDF: title cover.** Color from the title (stable per book); title and author set like
    a book jacket; small format tag at the bottom.
  - **CBZ, CBR: series number.** One color per series (all issues match); series name small at the
    top, issue or volume number large, issue title below, format tag.
  - A CBZ/CBR without series and number falls back to the title cover. A PDF that is really a comic
    still gets the title cover, because the rule goes by format.
- [x] Implemented in `LibraryCoverPlaceholder.kt`, drawn in one pass (no subcomposition) with
      text measured to fit a safe zone (15% left for the spine, 9% right, 8% top, 7% bottom).
      Titles shrink to fit in at most 4 lines without splitting words, then break and ellipsize.
      The safe zone moves clear of the Read, rating, count and download chips when shown. Covers
      narrower than 110dp (journal, shelf sheet rows, downloads) show one big glyph: the issue
      number or the title's first letter. Checked on the emulator.
- [x] Checked on the tablet (Local source, Terminal 95): series covers match per series, text clears the Read and rating chips.

## 4. Blur behind chrome `[~]`

- [x] Haze 1.5.3 behind the top bar, the drawer, and the reader chrome (see step 3 in section 0).
      Bottom sheets cannot blur the screen behind them (separate window), so they stay opaque.
- [x] Measured on the tablet (numbers under step 3 in section 0): scroll p50/p90 unchanged, GPU
      p90 9 ms against an 8.3 ms budget at 120 Hz.

## 5. Cover-derived color `[ ]`

- [ ] Use MaterialKolor to build a color scheme from a book's cover for the book detail view and
      reader chrome; colored placeholders (3c) can use the same palette code.
- [ ] Extract colors off the main thread and cache them per cover.

## Not yet designed

- Reader preferences panel and page slider beyond their glass surfaces (the bars themselves are
  done in step 3)
- Series view (the series browse reuses the library grid; it has no header of its own)
- Book detail (does not exist yet)

## Open questions

- Should Continue Reading get more emphasis (e.g. a tile spanning two grid columns) if the
  same-size row feels too flat on the tablet?
