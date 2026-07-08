# Library DB Paging — Plan, Progress & Deferred Work

Living tracker for the Stage 3/4 library-performance effort (branch `perf/library-db-paging`).
Goal: stop holding and re-deriving the **entire catalog in memory**. Page the grid from Room and move
filter/sort/facets into SQL so only the visible window is resident.

Prior perf work (non-blocking startup, journal split, cold-start render cache, recompute guards,
facet cache) is committed on `main`. This effort branches from there.

## The core idea

Persist derived projections of `readable_items`, rebuilt inside the catalog **write transaction**, so
navigation reads bounded pages instead of scanning/deriving the full catalog:

- `library_grid_roots` — the grid "root" set (standalone items + synthetic Kavita/Stump series
  containers). What the grid pages over.
- `library_root_facets(rootId, sourceType, libraryId, dimension, value)` — normalized facet values.
- `library_root_members(rootId, itemId, sourceType, libraryId)` — root → its catalog item ids
  (standalone = self, container = issue ids). Powers LAST_READ (`MAX` over members) + series browse.

Series-grouping truth lives once in `core:model` (`LibrarySeriesRoots.kt`), reused by both the
persisted populator and the in-memory `LibrarySourceIndex`.

## Done (committed)

| Commit | Stage | What |
|---|---|---|
| `1d12b20f` | A | `library_grid_roots` table + migration v24→25; populated in write txn (per-source replace, per-library delta) + startup backfill. |
| `30df1390` | B1 | `androidx.paging` + `room-paging`; DAO `PagingSource` (title / date-added) over roots. |
| `ffea3ddd` | B2/B3 | `LibraryGridPager` (DI-bound) → `Flow<PagingData<ReadableItem>>`; `LibraryPagedGrid` (Compose, reuses grid card/prefetcher); `LibraryPagingFeature` flag; wired into `LibraryRoute`/scaffold + `AppViewModel`. |
| `e1044fe8` | — | Flag ON by default (for on-device measurement). |
| `5eab41bf` | C1 | `library_root_facets` + migration v25→26; populated in write txn; DAO `getDistinctFacetValues`. Non-contextual only. |
| `5009a434` | D1 | `library_root_members` + migration v26→27; `deriveGridRootsWithMembers`; DAO `pagingGridRootsByLastRead` (join `reading_progress`, MAX per root). |

DB is at **v27**. Every migration + entity is validated by the Room round-trip tests in
`UnifiedReaderDatabaseMigrationTest`. All `core:model` / `core:database` / repository suites green.

## Deferred / intentionally skipped (don't forget these)

### The paged grid is a **measurement scaffold** — these are missing vs the in-memory `LibraryScreen`
- **Shelves** (Continue Reading / Up Next / Recently Finished) top slot. → D2
- **Filter summary + filter panel + sort control chrome** (the toolbar row).
- **Alphabet navigation rail** (needs the full ordered list or an IDs+sort-letter query).
- **Download-selection mode** (long-press select, "select all visible", selectable-id reporting).
- **Per-card action menu** (download / delete / mark read-status). `GridCoverCard` supports
  `menuContent`; `LibraryPagedGrid` passes none.
- **Sort-dependent badges** (issue-count badge only shows when sort == ISSUE_COUNT).
- **Scroll-position persistence** + scroll-to-top-on-sort/date-change behaviors.
- **Nested prefetch strategy** (paged grid uses only the loaded-window prefetch).

### Gating (in `LibraryPagingFeature.isPageable`)
- **Kavita only.** Grimmory / Stump / Local still fully in-memory.
- **Sorts: TITLE / DATE_ADDED only.** LAST_READ query exists (D1) but is **not switched on** so the
  default view keeps shelves — flip after D2.
- **No active filters / search / series-browse / Kavita scope.**

### Data / correctness deferrals
- **Contextual facets** not done (only non-contextual C1). `buildContextualLibraryFilterFacets` does
  ~10 filtered passes (each dimension's values after applying all *other* active filters). → C2
- **`LibraryFilterFacets` assembly from SQL** + reapplying orderings
  (`distinctByEnumOrder` / `distinctByAuthorOrder` / `distinctByTextOrder`) + wiring into the paged UI
  filter panel. → C3
- **`fileLocation` facet** excluded from the SQL table (runtime-dependent: `isLocalImport` + downloaded
  set). Needs query-time handling if the paged view exposes the Location filter.
- **Series browse from the paged path** — tapping a container still uses the in-memory path;
  `library_root_members` now enables SQL series browse but it isn't wired.
- **Search in the paged view** — no SQL search (FTS/LIKE) yet; gated out.
- **Delta root rebuild is per-affected-library full recompute**, not per-series incremental — correct
  but reads all of an affected library's items on delta. Optimize only if sync profiling shows cost.

## Remaining roadmap

### D2 — Shelves via SQL (finishes Stage D)
Bounded queries over `library_root_members` + `reading_progress`:
- Continue Reading (in-progress, most-recent first), Up Next (next unread issue in a started series),
  Recently Finished (completed, most-recent first).
- Then flip `isPageable` to include LAST_READ so the **default** Kavita view pages without losing
  shelves. Owner: `feature:library-shelves` (+ thin `core:database` queries). Proof: DAO tests +
  presentation-coherence.

### C2 / C3 — Contextual facets in SQL + paged filter panel
Only needed once the paged view exposes filters. Contextual facet = distinct values of a dimension
among roots matching all *other* active filter predicates (join `library_root_facets` with the active
filters). Then assemble `LibraryFilterFacets` and reapply the existing Kotlin orderings.

### Stage E — Retire in-memory `AppUiState.items` (THE memory win)
**This is where the numbers move** (startup 757 MB GC / ~101 MB steady heap / 236 MB back-nav). Nothing
before E changes steady-state memory, because `items` (and its derivations) stay fully resident.
Large + risky — must be sliced and checkpointed. Consumers of the full list to migrate (~18):

- **Grid/derived pipeline**: `LibraryDerivedStateComputer`, `LibrarySourceIndex`,
  `AppLibraryStateRecomputeCoordinator`, `AppLibraryDerivedStateBinding` — the Kavita paged path
  replaces this; other sources/sorts still need an equivalent (extend paging to them, or keep a
  bounded path).
- **Shelves**: `LibraryShelvesStateComputer` (→ D2 SQL).
- **Item-by-id lookups**: `AppUiState.selectedItem`, reader next-item resolver
  (`AppReaderNextItemResolver`), `selectedKavita/StumpSeriesTitle`, `AppCoverPreloadController`,
  `AppRemoteDownloadController`, `AppKavitaReadingListController`,
  `AppComicReadingDirection/ReaderModeController` (`readableItemRepository.getItems()`).
- **Scope computations**: `remoteItemLocalState*` in `AppUiState` (which items to check download
  availability for), `AppLibraryProgressRefreshCoordinator`, `AppProgressSyncStartupRetryCoordinator`.
- **Snapshot assembly**: `AppCatalogStateCoordinator` (`items = catalogItems + imported`),
  `ObserveAppCatalogSnapshotsUseCase`.
- **Cold-start render cache**: `coldStartOrderedItemIdsBySource` + `ColdStartCatalogSeeder`.
- **Repository residency**: `RoomRemoteCatalogRepository.itemsState` (the full mapped list) +
  `CompositeCatalogRepository`.

**Suggested E slicing:**
1. **Lookup foundation** — targeted `getItemById` / `getItemsByIds` (DAO + `ReadableItemLookup`
   interface, DI-bound). **DONE — commit `91e42908`.**
2. Migrate id-lookup consumers to the lookup.
   - **Reader next-item DONE — `66121b9f`** (`getMemberItemIds` + `getSeriesMemberItems`; the
     resolver gets the bounded series-members / reading-list set, off the main thread).
   - **Download enqueue DONE — `fa636a9b`** (`getSeriesMemberItems` generalized for containers;
     `onDownloadRemoteItem`/`onDownloadRemoteItems` pass the selected item's series members).
   - `AppKavitaReadingListController` — already fine (uses a state map, not the full list).
   Still to do, and NOT mechanical:
   - `onDeleteRemoteLocalFile` — recomputes the full local-availability sets that replace state;
     needs a **scope-aware availability recompute** before it can take a bounded set.
   - `AppCoverPreloadController.preloadCovers` — user action; needs all of a source's items. Swap
     `state.items` for a source-scoped query (`getItemsForSourceType`).
   - `selectedItem`, series titles — synchronous computed props read in composition; resolve the
     selected item into state at selection time instead of deriving from the full list.
   - `AppComicReadingDirection/ReaderModeController` — reactive reconciliation over ALL items; needs
     a semantic change (reconcile only items with overrides/sessions), higher risk.
   - **Synchronous computed props** (`selectedItem`, `selectedKavita/StumpSeriesTitle`) read during
     composition; can't become `suspend` without restructuring state assembly (resolve the selected
     item into state when selection changes, rather than deriving it from the full list on read).
   - **Reactive full-catalog scans** (`AppComicReadingDirection/ReaderModeController` reconcile over
     ALL items via `observeItems()`; `AppCoverPreloadController`) — need a semantic change to scan a
     bounded/relevant subset, not a lookup swap.
   Each is its own slice with its own correctness reasoning + test.
3. Move download-availability scope computation to a bounded query.
4. Retire the in-memory derived-state pipeline for Kavita (paged path is authoritative); keep a bounded
   path for other sources or extend paging to them.
5. Stop materializing `RoomRemoteCatalogRepository.itemsState` / `AppUiState.items`; delete the field
   and its `observeItems()` feed once no consumer remains.
6. **Measure**: `am start -W`, `dumpsys meminfo`, back-from-reader heap vs the committed baselines.

## Verification / logistics

- Device: OnePlus `9279b171`, app `com.example.unifiedreader` (release v0.3.0, signer `cc3c8218`).
  adb at `~/Library/Android/sdk_new/platform-tools/adb`.
- **Baseline (2026-07-08, flag-OFF):** cold start 421 ms (== committed 423, no regression); Java heap
  ~101 MB PSS / TOTAL 282 MB; GC 0 LOS; no CursorWindow errors. The ~101 MB heap **is** the full
  catalog in `items` — the Stage E target.
- **Signing (to let the assistant build/install releases):** export
  `ANDROID_SIGNING_STORE_FILE` / `_STORE_PASSWORD` / `_KEY_ALIAS` / `_KEY_PASSWORD` pointing at the
  **same** keystore as the installed app (`cc3c8218`), else installing wipes the catalog DB. Then
  `./gradlew :app:installRelease` preserves data.
- **A/B:** `LibraryPagingFeature.ENABLED` (compile-time). Flip → rebuild → install. Paging only engages
  on Kavita Title/Date-added with no filters/search.

## Invariants to preserve

- **Library presentation must commit coherently** (ARCHITECTURE_RULES) — no mixed old/new, no layout
  jumps, covers may load independently but card sizes/positions stay stable.
- Keep `app` thin; source-specific + presentation logic in owning modules.
- Derived tables must stay coherent with `readable_items` — every catalog write rebuilds them in the
  same transaction (full replace: per-source; delta: per-affected-library).
- Series grouping defined once in `core:model`; don't reimplement it in SQL or the app.
