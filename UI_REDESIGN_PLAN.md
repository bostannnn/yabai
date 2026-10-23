# UI Redesign Plan

Live tracker for making the app look modern. Update the status boxes as work lands, and record
anything that changes the plan under "Decisions" or "Open questions".

Status: `[x]` done · `[~]` done but not shipped (uncommitted, unmeasured, or not on the tablet) · `[ ]` not started

Started 2026-10-17. Sections 0-7 are the first pass; section 8 is the second pass approved 2026-10-22.

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

- **Settings redesign approved 2026-10-18: a hub of grouped rows plus one page per form.**
  Mock: https://claude.ai/artifact/QNjJtXQYKrxmJpEB64KB98 (hub, source page, theme picker,
  landscape). The old screen expanded every form and filter list at once, duplicated the title,
  and carried the library's import action. Decided with it: About page (version and the bundled
  third-party notices), drag to reorder for filter chips and drawer order (no library; a small
  reorder helper in the kit), and the LAN/Tailscale default server addresses stay.

- **Second pass approved 2026-10-22: cover-led and alive.** Review and plan:
  https://claude.ai/artifact/8TSpyrGAszggJMK4vUnrNX. The user decided each change on three boards
  (today next to a mock, Do/Maybe/Don't): library and app
  https://claude.ai/artifact/JPHMSHf3WMJab1fihdKJgu, filters and menus
  https://claude.ai/artifact/FciEVVZo2pYSM3NjKQZBpB, reader menus
  https://claude.ai/artifact/Tnrj9TXe5YoVjux3Hkf8sn. The work is section 8. It replaces four
  earlier decisions:
  - The library home gets a reading card for the last-read book (cover tint, title, chapter,
    progress, Continue and Details). Its cover stays the grid's cell size, so "one cover size per
    screen" holds; this answers the open question about Continue Reading's emphasis.
  - Shelves become tabs (In progress, Up next, Finished) over one strip of covers next to the
    reading card, replacing "shelves stay side by side in their panels". The band has the same
    height whatever the counts, which is what sank the full-width rows.
  - The control radius becomes a pill for buttons, chips, segmented rows, tabs and the search
    field; menus, text fields and list rows keep 12 dp. Replaces "control 12" from section 0 step 2.
  - Reader panels (contents, pages, settings) open between the top bar and the bottom dock and
    never cover them, replacing 7c/7d's "a sibling of the two docks so it covers the bars like the
    drawer does". The button that opened a panel stays visible and filled.

  Rejected: the open-book cover zoom (opening a book uses the plain fade of the other routes), the
  cover fade-in while loading, a serif display face (titles stay in Manrope; hierarchy comes from
  size and weight), quieter placeholder covers (3c's designs stay), and trimming the themes (all
  twelve stay). The grid tile changes only its progress bar. Cover size stays in the sort menu, as
  Small / Medium / Large. Screens without an approved mock get one when their step starts.

- **In-app updater, 2026-10-23.** Board: https://claude.ai/artifact/3zJepLSa879ARYnWfjjc9C (eight
  cards; no picks were saved and the user said "implement everything", so all eight are built, with
  the Dialog variant of the offer). Built in `feature:updates`: the Version row in Settings › About
  carries the next step as a pill (Check for updates, Update, Cancel, Install, Try again), a
  "Check for updates automatically" switch under it, the offer and its download, permission and
  failure steps as one dialog, a dot on the menu button and a version badge on Settings in the
  drawer while an update waits, and "Updated to x.y.z" with What's new on the first launch after
  an update. It brought three kit pieces ahead of 8.2: `AppDialogCard` (the 8.2 dialog layout;
  `AppDialog` should move onto it in 8.2), `AppTonalButton`, and `AppDrawerBadge` /
  `Modifier.appBadgeDot`; `AppSettingsRow` gained `supportingIcon` and `supportingContent`.

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
6. `[x]` **Settings as hub and pages** (2026-10-18). `SettingsScreen` is a hub of
   `AppSettingsGroup`/`AppSettingsRow` rows (Appearance, Sources, Library, About) and a
   `SettingsPage` per form: theme tiles split into light and dark, one page per remote source
   (server form, sync and cover rows, per-library reading defaults), filter chips as one
   drag-to-reorder checklist with a source segmented row, drawer order as a drag list, the
   pending-sync detail, and the licenses page (the module copies `THIRD_PARTY_NOTICES.md` into
   its assets at build time). The top bar shows the page title with a back arrow; the import
   action is gone from it. Under 860 dp the page replaces the hub; above it the hub stays as a
   320 dp side column. The kit gained `AppSettingsGroup`, `AppSettingsRow`,
   `AppSettingsRowDivider`, `rememberAppReorderState` with `appReorderableItem` and
   `appReorderHandle`, and five icons. The app wires one `SourceSettingsEntry` per source instead
   of fifty lambdas. Checked on the emulator in both orientations; not yet on the tablet.

## 1. Compose and Material 3 upgrade `[~]` (step 8.0 of the second pass)

Foundation for the newer components (Expressive shapes and motion, `LoadingIndicator`,
`ButtonGroup`, `FloatingToolbar`, carousels). Done 2026-10-22 on `claude/ui-redesign-step-8-8f714b`.

- [x] `composeBom` `2024.12.01` to `2026.09.00` (Compose 1.12.1, material3 1.3.1 to 1.4.0), Haze
      1.5.3 to 1.7.3 (the last 1.x; 2.0 is a rewrite). Compose 1.12 and Haze 1.7.3 require
      compileSdk 37, which AGP 9.1 does not support, so AGP is 9.2.1 (the first release that
      does) with Gradle 9.4.1, and every module compiles against 37. `targetSdk` stays 35, so
      runtime behaviour does not change. The catalog now names the versions that already resolve
      (activity 1.12.2, lifecycle 2.9.4, core-ktx 1.16.0, coroutines 1.10.2; the app's resolved
      graph is identical before and after). Haze 1.7.3 brings kotlin-stdlib 2.3.20 while the
      Kotlin compiler stays 2.2.10, which reads one version ahead; a Kotlin bump is its own step.
      Bumping Compose further needs nothing new: BOMs up to `2026.06.01` (Compose 1.11) still
      worked with compileSdk 35, everything from `2026.08.00` needs 37.
- [x] Deprecations and breaks: AGP 9.2's old-DSL `sourceSets` accessor fails with
      `android.newDsl=false` (cast to a class the source set does not implement), so
      `feature/settings` reaches the generated licenses folder through the new DSL type;
      `rememberSaveable(key = …)` is deprecated, so the reader overlay state takes the book id as
      an input (same reset per book). The instrumentation tests compile but warn that
      `createComposeRule` is deprecated for the `v2` rule, which queues coroutine work instead
      of running it at once; not migrated, because the tests are not run in CI and running
      them on a shared emulator uninstalls the app (wiping its library).
- [x] No visual change, checked on the emulator (`yabai_review_35`, Graphite, the 21-book
      library): 14 screens (library, drawer, sort menu, filter sheet, cover menu, settings, theme,
      journal, comic reader and its settings panel, EPUB reader with contents and settings) are
      pixel-identical to the pre-upgrade build. Two material3 1.4 defaults changed and are pinned
      in the kit until 8.2 restyles both: the chip edge (`outline` became `outlineVariant`) in
      `AppChip`, and the unselected segment fill (`surface` became transparent) in `AppSegment`.
      Other 1.4 behaviour changes: components animate through `MotionScheme`, and the
      indeterminate circular indicator moves differently.
- [x] Unit tests green (1301 across the Android and plain-Kotlin modules). Signed release builds
      with the release certificate and runs on the emulator; the APK is 61.1 MB against 0.11.0's
      56.9 MB, all of it dex (+4.1 MB of newer library code; release builds are not minified).
- [ ] `LibraryPerf` cold-start numbers on the tablet compared before and after (deferred, see 8.0).
- [x] Expressive APIs: wait for material3 1.5 stable (1.5.0-beta02 now). In 1.4.0 `MotionScheme`
      is `internal` (components use the standard scheme, and `MaterialTheme(motionScheme = …)`
      cannot be called), and `ButtonGroup`, `LoadingIndicator`, `FloatingToolbar` and the
      `ButtonDefaults.shapes()` press morph exist only in 1.5. So 8.1 uses `AppMotion` in our own
      components and builds the pressed-corner morph in the kit; Material's own components (switch,
      bottom sheet, drawer) move onto `AppMotion` when 1.5 is adopted.

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

## 5. Cover-derived color `[ ]` (superseded by 8.6)

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
- [~] Section 1 (Compose BOM) landed with 8.0 (tablet numbers pending); section 5 (cover-derived
      color) is superseded by 8.6.

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
      does: header "Contents" with the chapter count and a close button (chapters are the top-level
      entries, or the children of one that wraps most of the contents, as FB2 conversions have:
      `epubTocChapterIndices`, 2026-10-20), indent-only
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

### 7d. EPUB reading settings `[~]`

- [x] `End` panel, 400 dp, a sibling of the docks like the contents panel: Text, Theme and
      Layout as labelled sections in one scrolling column on tablets; in the phone sheet the
      three stay as segmented tabs and the sheet sizes to the open tab. Typography
      ("Book's own" / "Custom") and alignment ("Auto" / "Left" / "Justify") are choice rows,
      text size is a stepper row in tenths between 80% and 240%, line height and the three
      spacing values are slider rows, the theme is three page-colored swatches (Follow app,
      Light, Dark), Pages is a stacked four-way row (`AppFormRow(stacked = true)`, added for
      controls too wide to share a line), and the font picker is a second page with back in the
      header; back leaves the font page before it closes the panel. `EpubPagesChoice`,
      `stepEpubFontSize` and the label helpers are pure and unit-tested. The top dock is the bar
      only. Checked on the emulator at 800 dp (sections, font page, back) and 411 dp (tabs).
- [ ] Tablet check.

### 7e. Loose ends `[~]`

- [x] `ReaderAttachedPane` removed (nothing used it after 7d).
- [x] PDF bar: nothing to change beyond what 6c did.
- [ ] Tablet pass over 7b, 7c and 7d together (one signed release build): glass over a comic
      page and over an EPUB page with each panel open, frame times with a panel open.

## 8. Second pass: cover-led and alive `[ ]`

Approved 2026-10-22 (see Decisions). **Build spec:
[`docs/design/second-pass/README.md`](docs/design/second-pass/README.md)**: the mock image for every
change (`docs/design/second-pass/mocks/`), exact sizes and behaviour, what each step depends on, what
can run in parallel, and what was rejected. Where this section and the spec disagree, fix both.

Build in the order below (8.0 first, 8.9 last); the order puts the cheapest changes with the widest
reach first. Each step lands and is judged on the tablet before the next; a screen without an
approved mock gets one before it is built; anything that changes runtime work carries `LibraryPerf`
numbers.

### 8.0 Prep `[~]`

- [x] Section 1, the Compose BOM and material3 upgrade (Compose 1.12.1, material3 1.4.0, Haze
      1.7.3, AGP 9.2.1, compileSdk 37). No visual change: checked pixel by pixel on the emulator.
- [ ] **Deferred** (user, 2026-10-22: test performance later; 8.1 went ahead without it).
      `LibraryPerf` on the tablet: cold start, grid fling, drawer, reader open. The 0.11.0 release
      APK (GitHub release `0.11.0`) stays the "before", so the upgrade and each later step can
      still be measured against it.

### 8.1 Motion `[~]`

Built 2026-10-23 on `claude/ui-redesign-step-8-8f714b`, checked on the emulator (`yabai_review_35`,
gesture navigation, animations slowed 10x for mid-flight frames); not yet on the tablet, and its
frame times are part of the deferred `LibraryPerf` pass.

- [x] `AppMotion` tokens in `core:ui-theme`: spatial (spring, damping 0.8, stiffness 380),
      spatialFast (0.6, 800), effects (1.0, 1600), plus the screen, press and predictive-back
      constants. The 220/160 ms tweens in `AppFloatingPanel` and `AppSideSheet` are gone. Mapping
      them onto Material's `MotionScheme` waits for material3 1.5 (internal in 1.4.0), so Material's
      own components (switch, bottom sheet, drawer) still move on the standard scheme.
- [x] Screen transitions through one kit component, `AppScreenHost` (on `SeekableTransitionState`):
      fade-through (90 ms out, 210 ms in from 0.96) between Library, Journal and Settings and for
      opening and leaving a book; the 30 dp shared-axis slide by depth in the library (series view,
      shelf browser, Kavita Collections and Reading Lists landings and the lists opened from them)
      and for settings pages on narrow windows. Another source fades through, and so do sibling
      settings pages in the wide two-column layout. The leaving screen keeps drawing the state it
      was last shown with (a per-screen frame), so a sliding-out grid does not show the new
      screen's items. Top bars switch at once.
- [x] Predictive back: `android:enableOnBackInvokedCallback`. Leaving a book and leaving a settings
      page scrub the screen change: the current screen follows the finger 60 dp, shrinks to 0.88
      and rounds to 28 dp, the screen underneath (the library, the settings hub) grows from 0.94;
      the gesture covers the first 75% of the change and the fade-out plays after release, so the
      screen stays opaque while dragged. Readers keep saving their place first: their back
      handler is now `AppScreenBackHandler`, which reports the gesture to the host and runs
      `closeReader` on release. `AppFloatingPanel` and `AppSideSheet` move and shrink toward their
      own edge with the scrim fading and close from there. The drawer now uses
      `ModalDrawerSheet(drawerState = …)`, which gives Material's own predictive back and fixes Back
      with the drawer open leaving the app. Footnote and font-page handlers keep their priority.
- [x] Press feedback: kit buttons, chips and covers shrink to 0.96 on spatialFast; pill buttons
      (and the round tonal icon button) round toward 14 dp corners as they shrink, unless a caller
      gives its own shape (only the tonal cover-menu button does). The compact "More actions"
      buttons get a bounded ripple. Pressing a cover's title opens the book but does not shrink the
      cover (a shared press state would mirror the ripple).
- [x] `animateItem()` on library grid cards (placement only, no fades; paging placeholders not
      animated). The in-memory grid now keeps one `LazyGridState` per screen instead of per
      sort/filter/search key, so covers glide to their new places when a sort or filter changes
      while the grid is at the top; scrolled down, the existing scroll-to-top on sort and filter
      changes jumps as before (a jump plays no item animations). Kavita's paged grid builds a new
      paging stream for every sort or filter, so it still cuts; animating it would need the pager
      refreshed in place, a paging change of its own.
- [x] Loading skeletons pulse 1 to 0.45 alpha over 1.4 s (`Modifier.appSkeletonPulse`, one shared
      clock so a screen of them breathes together, still when the system animator scale is 0):
      `LibraryGridPlaceholderCard` and the shelf placeholders. `AppSideSheet` has an exit animation
      (it slides a third of its width toward its edge and fades); its content closes it through
      `AppSideSheetScope.dismiss()`.
- [x] Found while checking: a library composed under a back gesture before its shelves were
      computed (the app relaunched into a book) kept the shelves above the screen once they
      arrived. `LibraryScreen` now scrolls a row that appears above a grid still at its very top
      into view.
- [ ] Tablet: feel of the springs, the reader gesture over an EPUB page (WebView in a scaled
      layer), and frame times with the screen host (two screens composed during a change; the
      library composed under the reader for the length of a back gesture).

### 8.2 Kit `[~]`

Built 2026-10-23 on `claude/ui-redesign-step-8-2`, checked on the emulator (`yabai_review_35`,
Graphite, the 21-book library): library filter row and sort menu, cover menu, remove dialog,
Downloads sheet, journal, EPUB reading settings (sliders dragged); the Downloads ring through a
throwaway build with a faked queue (no remote source on that emulator). Not yet on the tablet.

- [x] Pills: `AppChip` is a 36 dp pill with SemiBold labels, a 1 dp `outlineVariant` edge and the
      selected container with no edge, plus an optional `count`; the colours moved from the
      library's `libraryAccentFilterChip*` helpers (deleted) into `AppChipDefaults`. The top-bar
      search field is a pill. Text fields, menus and settings rows keep 12 dp.
- [x] `AppSegmentedRow` is the kit's own row (no Material `SegmentedButton`): a
      `surfaceContainerLow` pill track with 3 dp padding, 34 dp pill segments in equal shares, the
      selected one filled with `surfaceSelected` (the fill fades on effects), no checkmark, no
      outline. `AppSegment` lost `index`, `count`, `colors` and `border`; `icon` is optional.
- [x] `AppSlider`: Material's slider for drag, keys and semantics, with the kit's track (18 dp
      pill, accent fill to the value) and handle (4 × 28 dp, 3 dp corners, `onSurface`). Callers
      lost the `colors` parameter. The reader's page slider is its own control and changes in 8.7.
- [x] `AppDialog` is built on the `AppDialogCard` layout (380 dp, `surfaceContainerHigh`, 28 dp,
      bold 24 sp title, 14 sp `onSurfaceVariant` body, the `icon` slot as the 52 dp leading image);
      `AppDialogConfirmButton` (filled, `destructive` in `error` / `onError`, the mock's pink) and
      `AppDialogDismissButton` (tonal) are the 42 dp pills, used by every dialog and the updater.
      `AppDialogDefaults` (custom dialogs) moved to 28 dp too. The remove dialog has no cover
      thumbnail yet: its three callers have no cover loader; the book page (8.5) is the place.
- [x] `AppSheet` draws its own handle (36 × 4 dp, `outline`, 16 dp above and below) and 28 dp top
      corners; `AppFloatingPanel`'s phone sheet uses the same handle and corners.
- [x] `AppMenu`: `surfaceContainerHigh`, 16 dp corners, 44 dp rows with 18 dp side padding;
      `AppMenuHeading`, `AppMenuChoiceItem` (20 dp check column, accent check, bold current choice)
      and `AppMenuSwitchItem`. The sort menu uses all three already (taken from 8.4: headings, the
      check and the "Fade read books" switch); the cover size labels stay for 8.4.
- [x] Downloads: `Modifier.appProgressRing` (2 dp ring, 40 dp, accent from 12 o'clock; a turning
      quarter arc while the first file of a run downloads, still with animations off) replaces the
      red `Badge`. `DownloadEntriesSnapshot.buttonState` counts the files of the run (the active
      entries plus those completed since the oldest of them was queued); a failure with nothing
      running shows an error dot. Unit-tested (`DownloadsButtonStateTest`).
- [x] The material3 1.3 pins in `AppChip` and `AppSegment` are gone with the restyle.
- [x] `FeatureMaterialImportRuleTest` also forbids references to `ButtonDefaults.shape` (and the
      outlined, text, tonal and elevated shapes), `FilterChipDefaults.shape` and the stock
      segmented-button icons.
- [ ] Tablet: the pills, the sliders over an EPUB page, the dialog, and the ring during a real
      Kavita download.

### 8.3 Library home `[~]`

Built 2026-10-23 on `claude/ui-redesign-step-8-2` (on top of 8.2), checked on the emulator
(`yabai_review_35`, Graphite, the 21-book Local library) at 800 dp portrait, 1280 dp landscape and
411 dp phone width. Not yet on the tablet; the series view was not seen on a device (the emulator
has no Kavita or Stump source), only its unit-tested parts.

- [x] Mock the phone layout and the series view header first: board
      https://claude.ai/artifact/G4aXzrCHb1o66ntsWCE3Wk (`docs/design/second-pass/pages/library-phone.html`;
      picks save to the page). Picked 2026-10-23: the phone home Do; the phone reading card A (a 96 dp
      cover with the buttons in a row under it); the series header B (the reading card for the
      issue to read next). The two data questions were left open and taken as proposed: the card
      shows the page until the reader stores chapters, and a series counts as its issues.
- [x] Header (`LibraryHeader.kt`): the first grid row is the source name (40 sp Bold, 32 on phones)
      and "13 books · 10 comics · 23 in progress" (`librarySourceSummary`: books and comics by kind,
      a series container counting its issues, in progress = the Continue Reading shelf). The summary
      line is reserved while unknown, so the grid does not move when it arrives. While the title is
      on screen the top bar is see-through (`AppTopBar(surfaceVisible = false)`) and has no name;
      once half of it has scrolled under the bar the glass fades in (effects) and the name slides up
      (spatial). Search, selection and the Kavita lists and sections keep today's bar and have no
      header. The bar keeps its row of icons above the title at every width (the tablet mock had the
      search field beside the title; that arrives with 8.4 and the rail in 8.8).
- [x] The reading card (`LibraryReadingCard.kt`): the first of Continue Reading; primaryContainer and
      primary until 8.6; 24 dp corners, 22 dp padding, the cover at the grid's cell width with a
      shadow and its progress bar; overline, 30 sp Bold title, author, "Page 1 of 73" and the
      percentage over a 6 dp bar, 44 dp Continue (filled) and Details. Details opens the item's menu
      (`LibraryItemMenuPopup`) until the book page replaces it in 8.5. Under 600 dp the phone layout
      (A). The position line reads "Chapter 4 of 6" for EPUBs: the reader saves the chapter it is in
      (the contents panel's rule, `epubTocChapterPosition`) with each session save, under the
      reader-core key `reader.chapter` in the session preferences (no Room change), and the progress
      summary carries it. Books not reopened since show the page ("Page 47 of 73") until their next
      save; comics and PDFs show the page.
- [x] Shelf tabs (`LibraryShelvesSections.kt`, rewritten): 34 dp pill tabs "In progress 22", "Up next",
      "Finished" (a tab with nothing is hidden; In progress leaves out the card's book), See all in the
      accent, and one strip of today's shelf cards at the grid's cover width and gap (up to 12, it
      scrolls sideways). Switching fades the strip in and slides it 12 dp. The panels are gone. With
      six grid columns or more (landscape) the card spans three of them with the tabs beside it;
      otherwise the card is full width above the tabs. The loading placeholder has the same shape.
- [x] Series view (B): the header is the series name and "3 issues · 1 read · 1 in progress"
      (`librarySeriesSummary`); above the issues, the reading card for the issue being read, or the
      next one to start after the last finished ("Up next in this series", Start)
      (`librarySeriesNextIssue`). Unit-tested; not seen on a device.
- [x] Tile: only the new progress bar, a 4 dp pill inset 7 dp from the cover's sides and bottom over a
      42% black track (`LibraryCoverProgressBar`, so the grid, the shelves and the shelf browser all
      change). Titles, the kebab and the text block stay.
- [x] Filter row (`LibraryFilterRow.kt`, moved out of `LibraryFilterComponents.kt`, which went from 492
      to 312 lines): the Filters chip first with a 20 dp accent badge counting the applied values, then
      one selected chip per value naming it ("EPUB", "In progress", an author, a library's title)
      whose × removes only that value, the sort chip at the end. `appliedLibraryFilters` is pure and
      unit-tested.
- [x] Series tiles (Kavita and Stump containers): two card edges behind the cover, inset 7 and 14 dp and
      raised 6 and 12 dp into the row gap (`Modifier.librarySeriesStack`), and "3 issues" on the meta
      line (with the percentage when started); the count badge that showed under the issue-count sort
      is gone. Checked with a throwaway build that drew every tile as a series (Local never groups).
- [x] Empty states on the kit's new `AppEmptyState` (drawing of three books, 28 Bold title, one
      sentence, actions): no search matches; "No books match these filters" with Clear filters;
      "Can't reach Kavita" with Try again when a server source is offline and empty; "Nothing here
      yet" with Import a file and Connect a server (opens Settings) on Local.
- [~] Presentation coherence: the summary line and the tab strip keep their height; the placeholder
      shows the card's footprint, so a source with nothing in progress still drops the card when the
      shelves arrive (as the old panel did). Shelf and library instrumentation tests updated and run on
      the emulator (new: the card's Details menu, switching tabs; the series tile's "12 issues"). The four
      that also failed on `main` were stale and are fixed, so both modules pass (10 and 14 tests):
      the shelf browser shows the series over the issue line, not the raw title; the Kavita landing's
      name moved to the top bar in 6d; the cover prefetch test now checks the current contract (a
      changed key is fetched again on the next visible pass; see the note in the commit); the
      top-content test holds the clock, so it lets one frame run after the change. Cold start is part
      of the deferred tablet `LibraryPerf` pass.

### 8.4 Filters, sort and the top bar `[ ]`

- [ ] Filter sheet: reading status, format and location as always-open chips with counts; the long
      facets (Author, Series, Genre, Tag, Language, Publisher, Library) as rows showing the
      selection, each opening a page inside the sheet with search, picks pinned on top, A-Z groups
      and counts; "Showing 14 of 21" at the top and a "Show 14 books" button that closes the sheet.
      Filters still apply as they are tapped.
- [ ] Sort and view menu: cover size as Small / Medium / Large instead of "180dp cards · 6 cols"
      (the headings, the check on the current sort and the "Fade read books" switch came with 8.2).
- [ ] Top bar: search as a field on tablets; Select removed (a long-press already starts
      selection); "•••" holds Import and, on Kavita, Refresh, with a one-time "Long-press a cover to
      select" tip on server sources; the selection bar shows close, the count, Select all and
      Download N.

### 8.5 Book page `[ ]`

- [ ] A sheet (a side sheet at 600 dp and wider) opened by "•••" and by the reading card's Details,
      replacing the `LibraryItemMenu` dropdown: cover, title, series position, Continue, Download or
      Remove, Mark finished, progress and last read; per-book direction and Paged/Scroll; About
      (description; genres and tags as chips that filter the library; format, size, language,
      publisher); More in this series, More by this author, Similar (two or more shared genres or tags).
- [ ] Keep the description: the Kavita and Stump DTOs parse `summary` and Grimmory's parse
      `description`, but the domain model drops them. Add it to the item model and Room (a v29
      migration), plus EPUB `dc:description` and ComicInfo `Summary` for local files.

### 8.6 Colour from the cover `[ ]` (replaces section 5)

- [ ] A palette per cover, extracted off the main thread and cached with the cover file; the accent
      clamped for contrast on the theme's surface, the theme primary as the fallback. Used by the
      reading card, the book page and the reader's accent; the grid and lists stay neutral.
      Extraction time and memory measured on the tablet.

### 8.7 Reader menus `[ ]`

- [ ] On tablets, panels open between the top bar and the bottom dock and never cover them; the
      button that opened one stays filled and closes it; one panel at a time; a tap on the page
      closes it.
- [ ] Same places in both readers: bottom left is Contents (EPUB) or Pages (comics), top right is
      reading settings.
- [ ] The top bar's second line names the chapter or issue; the position stays in the dock only.
- [ ] Comics: the page strip moves inside the dock above the slider; a new Pages panel shows every
      page as a grid.
- [ ] Phones: panels are bottom sheets, the top bar stays, the dock steps aside while one is open.
- [ ] Panel contents as on the menus board: segmented rows without checks, thick sliders,
      sentence-case section titles, contents rows with a done check and "% through", a progress
      footer. PDF uses the same frame.
- [ ] Frame times on the tablet with a panel open over an EPUB page and over a comic page.

### 8.8 Navigation rail `[ ]`

- [ ] A rail at 840 dp and wider (sources, Journal, Settings; Kavita's Home, Collections and Reading
      Lists open from its rail item); phones keep the drawer.
- [ ] One window-size helper instead of the ten breakpoint values in use (360 to 860 dp).

### 8.9 Screen passes `[ ]` (each mocked first)

- [ ] Journal: larger figures, the chart in the accent with a soft fill, the calendar as an
      intensity heatmap, book titles from metadata instead of file names, empty sections folded
      away, no uppercase captions.
- [ ] Settings: a title, sentence-case group headings, a theme preview on the Theme row.
- [ ] Downloads sheet, Kavita landing, first run.

## Not yet designed

Each is mocked when its step in section 8 starts: the phone library home and the series view
header (8.3), the journal below its header, the settings pages, downloads, Kavita landing and first
run (8.9). The book page is designed (8.5).


## Open questions

- Answered 2026-10-22: Continue Reading gets the reading card (8.3) instead of a wider tile.
