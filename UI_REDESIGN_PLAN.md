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
- **Two glass tints** (2026-10-18). Bars and the drawer keep the 0.86 tint (thin strips of text,
  legibility wins). The large reader panes (preferences, table of contents) use 0.72
  (`AppGlassDefaults.paneTintAlpha`) so the page shows through; at 0.86 they read as opaque
  panels. Tint does not change blur cost. A paper-colored tint for EPUB panes was considered and
  dropped: the reading theme and the app theme can differ, and the pane text is app-colored. An
  EPUB page is blank paper, so its panes look tinted whatever the alpha.
- **EPUB glass needs the WebView in a hardware layer** (2026-10-18, tablet, Android 16,
  WebView 150). Readium draws the page in a WebView that renders through its own SurfaceControl
  overlay ("WebView overlays are enabled" in logcat), so `hazeSource` captured nothing and the
  chrome re-recorded an empty layer on every page update while the overlay swapped underneath:
  no glass, visible flicker. `setLayerType(LAYER_TYPE_HARDWARE)` on the Readium
  `FragmentContainerView` keeps the page inside our layer; the glass then samples it (page text
  visibly blurs through the preferences pane on the tablet) and a page turn renders 190 frames at
  p50 8 ms / p90 10 ms with one janky frame. `ImmersiveReaderOverlay(glass = false)` remains as
  the escape hatch. The glass fallback surface (no blur available) is now fully opaque: at 0.96
  unblurred page text bled through the bars. The emulator's WebView does not use overlays, which
  is why it looked fine there.
- **Reader menus: one panel component, three placements** (2026-10-18). Proposal and mocks:
  https://claude.ai/artifact/MMcLhoAZ312eJugJ1H2bsZ. Placement follows content size: a
  content-sized menu anchors under its button (comics quick settings), a content-heavy one docks
  to a screen edge as a full-height panel shaped like the drawer (EPUB contents at the start
  edge, 360 dp; EPUB reading settings at the end edge, 400 dp), and under 600 dp each of them is
  a bottom sheet. Panels keep the glass at the pane tint; each one is measured on the tablet when
  it lands, and opaque `surfaceRaised` is the fallback if the blur region costs too much. Any
  setting with two or three values is one row (label + `AppSegmentedRow`); lists are only for
  fonts and chapters. An open panel means a scrim over the page, a highlighted button, and a
  close control in docked panels. Direction reads "Left to right" / "Right to left". The EPUB
  settings become sections in the tall tablet panel and keep their tabs in the phone sheet.
  Comics settings keep their current stores (per item or per series as today). All of this is
  built in the kit (`AppFloatingPanel`, `AppPanelHeader`, the `App*Row` form rows) and replaces
  `ReaderAttachedPane`; the segmented control is the existing `AppSegmentedRow`, so restyling it
  later changes settings, the cover menu, and the reader together.

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
   of the Read pill. (The opt-in "Fade read books" toggle in the sort menu stays: it is off by
   default and some readers want it.) Reader bars, panes, and the preview strip use the same glass
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

Merged to main (final layout `cec77761`).

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
- [x] **Side sheet fit and feel** (2026-10-18, tablet). The dialog's content was laid out at the
      full screen height while the window manager fitted the window between the system bars, so
      the panel ran 126 px past the bottom (no margin, under the gesture bar). `AppSideSheet` now
      clamps its content to the window's visible frame (`getWindowVisibleDisplayFrame`), which
      is a no-op where the two agree; margins are 12 dp above and below like the drawer. It slides
      in from the end edge instead of popping with the window fade. Opening a section re-sorted the
      facet values on every recomposition with the author key recomputed per comparison; the sort
      now runs once per input change with precomputed keys and the rows animate in. Section open
      on the tablet: worst frame 18 to 21 ms (was 60 to 200 ms as reported; most of that was
      post-idle vsync accounting, the real work was about 20 ms).

### 3c. Covers and placeholders `[ ]`

- [x] **Fixed 2026-10-18.** Imported comics had cover files in `files/imported-cover-cache` but
      the grid and shelves showed the placeholder because every cover request goes through
      `RemoteCoverController.coverImageUrlFor`, which had no branch for `SourceType.LOCAL`: no
      cover data source, never "reachable", and the remote fallback returned null. Since the
      persistent cover store landed (`f8869ed7`, April) local imports never showed a cover. The
      controller now returns a local item's own `coverUrl` (regression test in
      `RemoteCoverControllerTest`); checked on the emulator. The Kavita placeholders on the tablet
      are a different path: Kavita covers wait for the cached file instead of using the remote URL,
      so they appear once the cache warms.
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

## 6. Second pass: everything the first pass left on the old language `[x]`

Audit of 2026-10-17 (code read). Everything below landed on 2026-10-18 in one commit per
group; checked on the emulator, then on the tablet (release build, Android 16): flat covers,
local covers, the drawer, and the EPUB reader with its glass panes, where the WebView overlay
problem was found and fixed (see Decisions).

### 6a. Kit gaps `[x]`

- [x] Menus and the snackbar drew at 6 dp because `MenuDefaults.shape` and `SnackbarDefaults.shape`
      resolve to `Shapes.extraSmall`, which the theme maps to the cover radius. `AppMenu`,
      `AppSnackbar`, `AppDrawerItem`, and `AppSegment` end caps are on the 12 dp control radius.
- [x] `AppCheckbox`, `AppSwitch`, `AppSlider`, `AppSnackbar`, `AppDrawerItem`; the import rule now
      forbids the raw Material controls, `Snackbar`, `NavigationDrawerItem`, and their `*Defaults`.
      `Scaffold` and `ModalNavigationDrawer` stay raw (structural, no styling of their own). The
      drawer's accent-pill selection lives in `AppDrawerItemDefaults`; the drawer lost its extra
      shadow.
- [x] The app snackbar clears the navigation bar.
- [x] `AppDialogDefaults` (shape, container color) so custom dialogs built from `AppDialogHost` +
      `AppPanel` sit on the same surface as `AppDialog`.

### 6b. Covers sit flat `[x]`

- [x] `LibraryCover` is the raised surface under the image with a hairline edge: no shadow, no
      gradient backdrop, no `CoverSurfaceTreatment`.
- [x] Count, rating, read, and downloaded badges have no tonal or shadow elevation and no alpha.
- [x] One loading skeleton: `LibraryGridPlaceholderCard` (2:3, cover radius, raised surface);
      the first-load skeleton tiles it.

### 6c. Reader chrome inside the glass `[x]`

- [x] `ReaderOverlayTokens` shapes come from `MaterialTheme.shapes` (control for bars, buttons,
      selected rows; container for panes and the preview strip; cover for thumbnails); colors are
      plain theme roles; the dead hardcoded palette is gone.
- [x] `readerOverlaySurface` is the kit glass only; the attached pane lost its 18 dp shadow and
      uses the lighter pane tint (see Decisions).
- [x] Preferences panes: rows follow the tokens, text size and margins use `AppSlider`, the EPUB
      tabs are plain `AppSegment`s.
- [x] Preview strips: no compounded container alpha; the selected thumbnail has a 2 dp accent edge
      at the cover radius; PDF thumbnails are clipped.
- [x] EPUB table of contents selects by container color; the depth dot is a token color.
- [x] EPUB footnote popup is an `AppPanel` on the container radius.
- [x] Comics and EPUB loading states show `AppCircularProgress`; the fallback `ReaderTopBar`
      uses `AppIconButton`.
- [x] The page slider follows a drag and seeks on release (tap still seeks directly).

### 6d. Library surfaces outside the home grid `[x]`

- [x] Shelf browser: bare tiles, the controls strip is the first row of the grid or list so the
      whole browser scrolls under the bar, deprecated `LibraryShelfItemsSheet` removed.
- [x] Item kebab menu: reading direction and reader mode are `AppSegmentedRow`s; the menu
      buttons use the control and cover radii on the raised surface.
- [x] Alphabet rail and back-to-top on the opaque raised surface; the rail clears the top bar.
- [x] Top-bar search field: no alpha fill, accent edge only while a query is typed; connectivity
      badge without alpha fills or borders.
- [x] Kavita landing: the title is only in the top bar.
- [x] Kavita reading-list dialog on `AppDialogDefaults`.
- [x] `EmptyLibraryState` matches the other empty states.
- [x] Shelf placeholders use neutral fills at the cover radius.

### 6e. Smaller screens `[x]`

- [x] Downloads sheet: flat rows with the ripple clipped to the control radius, no extra cover
      clip, status tints from the semantic accents.
- [x] Journal: details modal on the dialog surface; chart corners at the cover radius or a circle;
      the comics mix color is the theme's tertiary.
- [x] Settings: theme swatches on the cover radius without the alpha border; filter visibility on
      `AppCheckbox`.
- [x] Dead code removed: `ProgressSummary`, `LibraryBrowseSearchField`, `LibraryShelfItemsSheet`,
      `EpubFontSection`, `ReaderOverlayDropdownMenu`/`Item`, `ReaderOverlayMenuAction`,
      `rememberReaderOverlayPanelState`, `ReadingDirectionButton`.

### 6f. Still open

- [x] Checked on the tablet 2026-10-18 (covers, drawer, EPUB reader panes).
- [ ] `LibraryPerf` scroll run on the tablet: the cover change removes a shadow and a gradient per
      tile, so the numbers should only improve, but they are not recorded yet.
- [ ] 3a: run the shelf instrumentation tests.
- [ ] Section 1 (Compose BOM) and section 5 (cover-derived color) are untouched.

## 7. Reader menus `[ ]`

Decided 2026-10-18 (see Decisions). After the second pass the reader menus are on the right
radii and material, but they are still radio lists in free-floating cards: the comics pane needs
scrolling for five two- or three-way settings and clips its last header; the EPUB contents card
is 312 dp wide at the bottom left and does not scroll to the current chapter when it opens; no
pane has a scrim or a close control; the preferences icon in comics is a carousel glyph. Each
step below lands and is judged on the emulator, then the tablet, before the next.

### 7a. Kit `[~]`

- [x] `AppFloatingPanel`: an in-tree panel (not a dialog window, so it animates with the bars,
      respects the reader's insets, and can be glass). Placements `Start`, `End`, `TopEnd`;
      under 600 dp every placement becomes a bottom sheet. Scrim, back handling, slide-in, the
      container radius, glass at `paneTintAlpha` with the opaque raised surface as the no-blur
      fallback. `resolveAppFloatingPanelLayout` is the pure placement rule, unit-tested.
- [x] `AppPanelHeader`: title, subtitle, optional back and close.
- [x] Form rows: `AppChoiceRow` (label + `AppSegmentedRow`, stacks below 360 dp),
      `AppSliderRow`, `AppStepperRow`.
- [x] Checked on the emulator through 7b: anchored card at 800 dp, bottom sheet at 411 dp
      (`wm size 616x1300`), scrim, back and tap-outside dismiss.

### 7b. Comics quick settings `[~]`

- [x] `TopEnd` panel under the settings button, 400 dp: Layout, Direction, Pages, Page turn,
      Fit as choice rows; rows that do not apply in scroll mode are disabled, not removed, so the
      panel keeps its height. Icon is `Tune` (the carousel glyph is gone from `AppIcons`).
      Direction reads "Left to right" / "Right to left" and fit reads "Screen / Width / Height"
      inside the panel only; the enum labels the cover menu uses are unchanged. Stores unchanged;
      no "remembered for" footer because direction and layout are per series while pages, page
      turn and fit live in the reader session. Checked on the emulator in both layouts and both
      widths. The glass still shows a comic page's colors through the card at the pane tint
      (the mocks assumed opaque); judge that on the tablet.
- [ ] Tablet frame measurement with the panel open over a comic page (glass vs opaque).

### 7c. EPUB contents `[~]`

- [x] `Start` panel, 360 dp, a sibling of the two docks so it covers the bars like the drawer
      does: header "Contents" with the top-level chapter count and a close button, indent-only
      hierarchy (no depth dots), current entry on the selected container, entries already read
      muted, footer "Chapter k of n" with percent and position, list scrolled to two rows above
      the current entry on open. `epubTocChapterPosition` and `isEpubTocEntryFinished` are pure
      and unit-tested. The bottom dock keeps only the button and the slider. Checked on the
      emulator at 800 dp and 411 dp with a generated 14-entry EPUB (nested sections, two parts;
      `Toc-Test-Novel.epub` is left in the emulator's Downloads and library for later checks).
- [ ] Tablet check (glass over an EPUB page, panel open).
- Found while checking: recreating the activity with an EPUB open (a `wm size` change on the
  emulator; a rotation may do the same since `MainActivity` declares no `configChanges`)
  crashes on restore with `Unable to instantiate fragment
  org.readium.r2.navigator.epub.EpubNavigatorFragment`. Not caused by this work; filed as its
  own task.

### 7d. EPUB reading settings `[ ]`

- [ ] `End` panel, 400 dp: Text, Theme, Layout as sections on tablets; the segmented tabs stay
      in the phone sheet. Typography and alignment as choice rows, text size as a stepper row,
      the sliders as slider rows, the theme as three swatches, the font picker as a second page
      with back in the header.

### 7e. Loose ends `[ ]`

- [ ] `ReaderAttachedPane` removed once nothing uses it.
- [ ] PDF bar: nothing to change beyond what 6c did.

## Not yet designed

- Series view (the series browse reuses the library grid; it has no header of its own)
- Book detail (does not exist yet)


## Open questions

- Should Continue Reading get more emphasis (e.g. a tile spanning two grid columns) if the
  same-size row feels too flat on the tablet?
