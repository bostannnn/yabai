# Architecture Rules

This file exists to stop future changes from collapsing back into app-shell god modules.

These rules are stricter than the broader architecture proposal. They are intended to be used as working constraints for Codex and for human review.

## Primary Goal

Keep orchestration thin and keep behavior in the module that owns it.

A change is low quality if it technically works but:

- adds new business logic to app-shell classes
- adds side effects to DI wiring
- duplicates source-specific logic across multiple coordinators
- grows an already-large file instead of extracting a focused unit

## Hard Boundaries

### `app` module

The `app` module is allowed to:

- assemble the app shell
- wire feature modules together
- host thin state reducers and navigation decisions
- provide dependency wiring

The `app` module is not allowed to become the default home for new behavior.

Do not add business logic to:

- `app/src/main/java/com/example/unifiedreader/AppViewModel.kt`
- `app/src/main/java/com/example/unifiedreader/AppModule.kt`
- `app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt`

Allowed changes in those files are limited to:

- delegating to a new owning class
- registering a new dependency
- adding a small state field required by existing UI
- removing logic by extracting it elsewhere

### DI providers

Hilt providers must be construction-only.

Providers must not:

- run bootstrap or migration work
- touch disk except to construct dependencies
- perform network work
- contain business rules

If startup work is needed, create an explicit bootstrapper or startup use case and call it intentionally from app startup code.

### Source-specific behavior

Kavita, Stump, and Grimmory logic must live with the source that owns it.

Prefer:

- source-specific connect/sync rules in per-source classes
- shared remote helpers only for logic that is truly shared
- one source implementation per concern instead of one giant coordinator with branching

Avoid:

- adding another `when (sourceType)` branch to app-shell code if the behavior can live in a per-source class
- adding parallel `onKavita...`, `onStump...`, `onGrimmory...` methods unless they are simple delegation and already part of an existing thin adapter

### App coordinators and controllers

`app` coordinators and controllers may sequence owning modules, but they must not become the owner of feature behavior.

Allowed app coordinator work:

- translating top-level app events into calls on owning modules
- merging already-computed state into `AppUiState`
- managing cancellation and lifecycle for app-scoped jobs
- delegating to source-specific, reader-specific, or feature-specific collaborators

Avoid:

- placing source policy, reader policy, cache policy, or heavy presentation computation in `app`
- adding repeated `Kavita` / `Stump` / `Grimmory` branches in app code
- creating a new large `App...Coordinator` when a feature module can own the behavior
- growing an app coordinator past 250 lines without an explicit extraction plan

If an app coordinator needs source-specific behavior, introduce a small source-owned interface or adapter and have `app` call that interface.

### Reader-specific behavior

Reader behavior belongs in the reader module for that format.

Prefer:

- reader state holders in the feature reader module
- integration adapters around Readium/PDF/comic engines
- small composables that consume state and callbacks

Avoid:

- placing reader rules in `app`
- mixing UI, file I/O, session restore, and engine integration in one file when extracting is practical

### Runtime performance boundaries

Structure is not enough if the runtime behavior still causes startup jank, reader jank, battery drain, or memory pressure.

Do not introduce:

- `runBlocking` in app startup, ViewModel initialization, Compose code, or lifecycle callbacks outside tests
- disk reads or writes on the main thread
- network calls before initial display unless the user explicitly triggered them or the work has a documented startup requirement
- automatic network work without a dedupe key, cooldown or freshness rule, cancellation path, and focused test
- polling loops for background work when the same behavior can be driven by a state-change signal
- bitmap decode, archive scans, PDF/EPUB/comic page work, or expensive sampling during composition
- unbounded in-memory caches of page bytes, bitmaps, cover images, or remote payloads

Performance-sensitive work must name the owner module and the metric that proves the change helped. Acceptable proof includes a focused unit test for fewer calls/work, a benchmark, or a documented manual profiler check.

**Library DB paging (see `LIBRARY_DB_PAGING_PLAN.md`).** The Kavita library grid, shelves, and content-filtered views page from Room over the derived projection tables (`library_grid_roots`, `library_root_facets`, `library_root_members`) so only the visible window is resident, instead of holding and re-deriving the whole catalog in memory. When extending this:

- The grid renders from the paging flow; do not block it on the in-memory `LibraryDerivedStateComputer` recompute, which runs async off the presentation path.
- Shelves and any **per-item derived state the paged cards display** — read/progress badge, download availability/status — should be sourced from bounded SQL / reader-session state (like the shelves cold-start seed and `LibraryShelvesInputResolver`) so they commit *with* the grid, not pop in a second later once the full-catalog recompute finishes. A status that appears after the grid has presented is a presentation-coherence violation (below).
- Measure recompute cost with the opt-in `LibraryPerf` logging (`LibraryPerfLog` / `LibraryShelvesPerfLog`; `adb logcat -s LibraryPerf`): it reports each `compute` HIT/MISS with a per-pass breakdown. Keep it in the tree but flip `ENABLED` to `false` for shipping builds.

### Library presentation state

The main library screen must publish coherent presentation states.

The visible grid items, shelves, filter summary, browse key, sort/filter/search state, and source loading state should commit together. Do not publish a state where one part of the screen reflects new inputs while another major part still reflects stale inputs, unless the stale content is explicitly retained and marked loading.

For Kavita landing, shelves, collections, reading lists, and filtered series views:

- keep the previous coherent presentation or show one unified loading state while membership or derived presentation is recomputing
- do not make shelves appear, disappear, or resize after the grid has already presented the new item set
- covers may load independently, but card sizes and list/grid positions must remain stable
- root shelves, selected collection/list content, and filtered series grids must not be computed by unrelated timing paths that visibly fight each other

This rule belongs at the presentation commit boundary. Do not fix presentation jumping by forcing one giant network call or by moving source-specific API behavior into `app`.

### Reuse before reinterpreting

Do not introduce a new interpretation of an existing user-facing control or browse pattern when the app already has one.

Before adding any of these, first identify the existing implementation and either reuse it or explain why it cannot be reused:

- search fields
- sort menus or sort chips
- view/grid/list selectors
- grid-size and column-count controls
- cover cards, list rows, and item action menus
- cover prefetchers and visible-item prefetch target selection
- empty, loading, and placeholder states
- browse toolbars and filter summaries
- item count labels and common metadata labels

Feature-specific semantics may stay local. For example, shelves can own shelf order, recent activity, and progress sort rules. Shared chrome, control behavior, sizing rules, and visual language should live in the owning reusable module instead of being copied into the feature.

If the reusable component is too private or too specific, prefer extracting a narrow public primitive in the owning module over duplicating the behavior elsewhere.

## Module Ownership

Use this ownership map when deciding where code goes.

- `app`
  - app shell, top-level navigation, DI wiring, thin composition
- `core:model`
  - source-agnostic domain models and pure model helpers
- `core:common`
  - generic shared utilities with no product-specific behavior
- `core:ui-theme`
  - shared visual tokens (semantic colors, presentation sizes) and theme setup. Themes are
    palettes only: no per-theme shapes, fonts, or drawing treatments.
- `core:ui-components`
  - the app's component kit (`AppPanel`, `AppDialog`, `AppSheet`, `AppButton`, `AppChip`, ...):
    the only place feature and app code gets containers, dialogs, buttons, chips, menus,
    text fields, dividers, progress indicators, and icons (`AppIcons`, Material Symbols
    Rounded as bundled vector drawables). Raw Material composables for those roles and the
    `androidx.compose.material.icons` packages are forbidden in `feature/*` and `app` main
    sources; `FeatureMaterialImportRuleTest` in this module enforces it. Material primitives the kit does not cover yet (form controls,
    drawer, navigation rail, scaffold, snackbar) stay allowed.
- `core:network`
  - HTTP client setup and generic network support
- `core:database`
  - Room database, entities, DAOs, migrations
- `data:kavita`
  - Kavita DTOs, API client, source-specific mapping
- `data:stump`
  - Stump DTOs, API client, source-specific mapping
- `data:grimmory`
  - Grimmory DTOs, API client, source-specific mapping
- `data:repository`
  - repository implementations that compose data sources
- `feature:library`
  - browse/filter/sort/presentation for the library experience
- `feature:library-covers`
  - cover image models, cover loading UI contracts, and cover-facing library helpers
- `feature:library-shelves`
  - shelf presentation, shelf item browsing UI, and shelf-specific state
- `feature:navigation`
  - source navigation state and source ordering preferences
- `feature:reader-core`
  - shared reader abstractions and cross-reader UI primitives
- `feature:reader-preferences`
  - reader settings/preferences shared across reader formats
- `feature:reader-comics`
  - comics reader behavior and UI
- `feature:reader-pdf`
  - PDF reader behavior and UI
- `feature:reader-epub`
  - EPUB reader behavior and UI
- `feature:downloads`
  - download workflows and download-facing UI
- `feature:journal`
  - journal entries, journal stats, and journal-facing UI/state
- `feature:settings`
  - settings UI and settings-facing state
- `feature:updates`
  - the in-app updater: GitHub release check, APK download and checks, the install session,
    and its dialogs and settings rows

If code does not obviously belong to one owner, stop and decide ownership before implementing.

## File Growth Rules

Large files are allowed only when there is a clear reason. They are not the default target for extension.

Default guardrails:

- avoid growing any file already over 300 lines unless the change is a narrow extraction follow-up
- if a file is over 500 lines, prefer extraction over addition
- if a change would add more than 80 lines to one existing file, justify it first
- if a change touches more than 3 modules, explain why that coupling is necessary

Current hotspot files that should shrink over time, not grow:

- `app/src/main/java/com/example/unifiedreader/AppRemoteSourcesCoordinator.kt`
- `app/src/main/java/com/example/unifiedreader/AppViewModel.kt`
- `app/src/main/java/com/example/unifiedreader/AppModule.kt`
- `app/src/main/java/com/example/unifiedreader/AppLibraryStateRecomputeCoordinator.kt`
- `app/src/main/java/com/example/unifiedreader/AppPendingProgressFlushCoordinator.kt`
- `app/src/main/java/com/example/unifiedreader/AppCoverPreloadController.kt`
- `app/src/main/java/com/example/unifiedreader/AppReaderCoordinator.kt`
- `app/src/main/java/com/example/unifiedreader/RemoteComicPageSource.kt`
- `app/src/main/java/com/example/unifiedreader/LocalComicPageLoader.kt`
- `feature/reader-epub/src/main/java/com/example/unifiedreader/feature/reader/epub/EpubReaderEntry.kt`
- `feature/reader-pdf/src/main/java/com/example/unifiedreader/feature/reader/pdf/PdfReaderEntry.kt`
- `feature/reader-comics/src/main/java/com/example/unifiedreader/feature/reader/comics/ComicsReaderSpreadLayout.kt`
- `feature/reader-comics/src/main/java/com/example/unifiedreader/feature/reader/comics/ComicPagePreviewStrip.kt`
- `feature/library-shelves/src/main/java/com/example/unifiedreader/feature/library/shelves/LibraryShelfItemsSheet.kt`
- `feature/library/src/main/java/com/example/unifiedreader/feature/library/LibraryFilterComponents.kt`
- `data/kavita/src/main/kotlin/com/example/unifiedreader/data/kavita/KavitaApi.kt`
- `data/kavita/src/main/kotlin/com/example/unifiedreader/data/kavita/KavitaDtos.kt`
- `data/grimmory/src/main/kotlin/com/example/unifiedreader/data/grimmory/GrimmoryApi.kt`
- `core/database/src/main/java/com/example/unifiedreader/core/database/UnifiedReaderDatabase.kt`

## Preferred Change Shape

For non-trivial work, prefer this sequence:

1. decide the owning module
2. list files to change
3. list files that are explicitly forbidden
4. list any existing components or helpers that overlap with the requested behavior
5. add or extract the owning abstraction
6. wire the app shell to it
7. add tests in the owning module

For performance work, also require:

8. describe the before/after runtime behavior
9. identify the metric, log, benchmark, or test that proves less work runs
10. keep the fix scoped to one owner module unless the ownership boundary requires a thin app wiring change

This is better than:

1. modify the app shell until the feature works
2. promise to refactor later

## Tests And Review Gates

Every logic-heavy change should add or update tests in the owning module.

Performance-heavy changes need proof, not just explanation. Use the smallest proof that demonstrates the intended effect:

- startup: `StartupTimingMetric`, `adb shell am start -W`, StrictMode disk read/write logs, Choreographer skipped-frame logs, or a focused startup unit test
- network: request counts during launch/resume, dedupe/cooldown unit tests, Network Inspector, or `dumpsys batterystats` radio-wakeup checks
- reader idle: CPU/Energy profiler, Perfetto scheduling slices, virtual-time coroutine tests, or a manual idle-in-reader check
- reader navigation: ANR traces, StrictMode slow-call/disk-write logs, Choreographer skipped frames, or lifecycle flush tests
- memory: `adb shell dumpsys meminfo`, allocation records, trim/GC logs, or cache-budget unit tests
- library presentation: tests or screenshots proving shelves/grid/filter summary/loading state commit coherently without layout jumps

Do not accept a performance fix that cannot say what work was removed, delayed, deduped, bounded, or moved off the main thread.

Review should reject a change when:

- app-shell files gained business logic that could live elsewhere
- DI providers gained side effects
- source-specific logic was added to a shared coordinator without a strong reason
- a large file got larger without extraction
- the final explanation cannot clearly justify ownership of each changed file
- an existing user-facing pattern was reimplemented instead of reused or extracted
- runtime work was added without a trigger, freshness rule, cancellation path, and proof
- library presentation can visibly publish mixed old/new state
- memory or cache growth is unbounded

## Codex Working Rules

When using Codex for implementation, require it to do all of the following before writing code:

1. name the owning module
2. list the files it plans to change
3. list the files it must not change
4. explain why the new logic belongs where it plans to put it
5. list existing components/helpers it checked for reuse
6. for performance or UI-smoothness work, explain the before/after behavior and how it will be verified

If the proposed plan violates this file, reject it and ask for a redesign before code is written.

## Short Review Checklist

Use this checklist for PRs or Codex outputs:

- Does the change keep `app` thin?
- Did any DI provider gain side effects?
- Is the new behavior in the module that owns it?
- Did a hotspot file grow when it should have been split?
- Are tests added in the owning module?
- Could a future source or reader be added without editing the same giant file again?
- Did the change reuse existing controls/helpers instead of duplicating a parallel interpretation?
- For performance work, is there proof that fewer calls happen or less work runs?
- For library UI work, does the screen commit coherent presentation state without jumping?
