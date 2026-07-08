# Library DB Paging — Handoff & Plan

Single source of truth for the library-performance effort on branch `perf/library-db-paging`.
Read this top-to-bottom to resume in a fresh session.

---

## 1. The goal (why this exists)

Today the whole catalog is held and re-derived in memory:
`RoomRemoteCatalogRepository.itemsState` (all remote items as domain `ReadableItem` objects with parsed
metadata) → `AppUiState.items` → `LibraryDerivedStateComputer` builds a source index + `visibleItems`
(a materialized, filtered, sorted list the grid renders) + facets, and re-runs on navigation.

That in-memory full catalog is the residual cost prior perf work couldn't remove:
- steady Java heap ~84–101 MB (measured on device; **this is the target**),
- ~236 MB back-nav recompute floor (the last-read sort legitimately re-runs on every progress update),
- ~757 MB startup GC churn historically.

**Goal:** page the grid from Room and move filter/sort/facets/shelves into SQL so only the visible
window is resident, then retire the in-memory full list. The measurable win is the heap drop.

Prior perf work (non-blocking startup, journal split, cold-start render cache, recompute guards, facet
cache) is already merged to `main`; this branch builds on it.

---

## 2. Architecture

Persist **derived projections** of `readable_items`, rebuilt inside the catalog **write transaction**
(so navigation reads bounded pages instead of scanning/deriving the full catalog). DB is at **v27**.

- `library_grid_roots` — the grid "root" set per source: standalone items + one synthetic
  Kavita/Stump series **container** per series key. What the grid pages over.
- `library_root_facets(rootId, sourceType, libraryId, dimension, value)` — normalized facet values
  (`SELECT DISTINCT` instead of ~10 filtered in-memory passes). Non-contextual only so far.
- `library_root_members(rootId, itemId, sourceType, libraryId)` — root → its catalog item ids
  (standalone = self; container = issue ids). Powers LAST_READ (`MAX` over members) + series membership.

Series-grouping truth lives once in `core:model` (`LibrarySeriesRoots.kt`:
`deriveGridRootsWithMembers` / `deriveKavita|StumpSeriesGrouping` / `deriveGridRootItems`), reused by
both the persisted populator (`RemoteCatalogDao`) and the in-memory `LibrarySourceIndex`.

Paging is gated by `LibraryPagingFeature` (app). `ENABLED = true`. `isPageable(state)` = Kavita root +
sort in {LAST_READ, DATE_ADDED} (rail-less) + no filters/search/series/scope. Rail sorts
(Title/Author/Series), filters, search, series browse, and other sources still use the in-memory path.

---

## 3. What's done (all committed on `perf/library-db-paging`)

| Commit | Stage | What |
|---|---|---|
| `1d12b20f` | A | `library_grid_roots` + migration v24→25; populated in write txn (per-source replace, per-library delta) + `rebuildAllGridRootsIfEmpty` startup backfill. |
| `30df1390` | B1 | `androidx.paging` + `room-paging`; DAO `PagingSource` (title/date-added) over roots. |
| `ffea3ddd` | B2/B3 | `LibraryGridPager` (DI-bound) → `Flow<PagingData<ReadableItem>>`; `AppViewModel.libraryGridPagingData` (cachedIn) + startup backfill; flag + wiring. |
| `e1044fe8` | — | Flag ON by default. |
| `5eab41bf` | C1 | `library_root_facets` + migration v25→26; populated in write txn; DAO `getDistinctFacetValues`. Non-contextual only. |
| `5009a434` | D1 | `library_root_members` + migration v26→27; `deriveGridRootsWithMembers`; DAO `pagingGridRootsByLastRead` (join `reading_progress`, MAX per root, nulls last). |
| `91e42908` | E1 | DAO `getLibraryItemById`; `ReadableItemLookup` (getItemById / getItemsByIds), DI-bound. |
| `66121b9f` | E2 | Reader next-item resolved from bounded SQL (`getSeriesMemberItems` via members) instead of scanning the full catalog on the main thread. |
| `fa636a9b` | E3 | Download **enqueue** paths use `getSeriesMemberItems` (generalized for containers). Delete path intentionally left on the full list (recomputes full availability). |
| `53877eb6` | M1 | **Paged view no longer a scaffold:** `LibraryScreen` renders its grid body from a paging flow when `pagedGridItems != null` (nullable, default null → all other callers byte-for-byte unchanged), reusing shelves/filter-bar/sort/dialogs/card. Deleted the separate `LibraryPagedGrid`. Default Kavita view (LAST_READ) now pages its body from SQL with full chrome. |

Verified: `core:model` / `core:database` / `feature:library` suites green; app repository/reader/VM
tests green; Room validates the schema through v27; release APK builds. Device (pre-M1 build): cold
start ~400 ms (no regression), heap ~84 MB (unchanged — expected).

---

## 4. Key findings & corrections (don't re-derive these)

1. **`reading_progress` is in the SAME `unified-reader.db`** as `readable_items` (the "separate store"
   is only a repository-layer split). `lastReadAt == reading_progress.updatedAtEpochMillis`. So the
   LAST_READ join is pure SQL (done in D1).
2. **The `*Json` facet columns are NOT JSON** — they're ``-delimited strings
   (`RemoteCatalogEntityMappers.encodeMetadataValues`). `json_each` was never viable; normalized facet
   tables are the only correct approach (C1).
3. **Paging is inherently invisible.** The in-memory library view was already complete (shelves,
   filters, sort). The only visible effect of paging done right is that the "scaffold" is gone (M1);
   the real payoff is memory, which only drops when `items` is retired. There is no visual "new
   feature" to ship — set expectations accordingly.
4. **`visibleItems` and `items` are load-bearing far beyond the grid — DO NOT empty/drop them without
   migrating consumers first.** This is why M2 was attempted and reverted (see §6).
5. **First launch after the v24→v27 migration does a heavy ONE-TIME rebuild** (populating
   roots/facets/members from the whole catalog) → ~500 MB GC churn + a CursorWindow burst. One-time
   (no-op after), but it should be made lighter/off the critical path.
6. Can't build a data-preserving install without the release keystore — see §8.

---

## 5. Committed but incomplete / deferred

- **Paged view gating** is deliberately narrow (Kavita root, LAST_READ/DATE_ADDED, no filters/search).
  Rail sorts, filters, search, series browse, and Grimmory/Stump/Local still use the in-memory path.
- **Facets are non-contextual only** (C1). Contextual facets (each dimension's values after applying
  the *other* active filters — the ~10-pass logic in `buildContextualLibraryFilterFacets`) and wiring
  SQL facets into the paged filter panel are not done.
- **Shelves are still computed in memory** (the paged view reuses `uiState.libraryShelvesState`, which
  the in-memory recompute still produces). No SQL shelves yet.
- **Series browse & search in the paged path** are not implemented (fall back to in-memory).
- **Download delete** (`AppRemoteDownloadController.onDeleteRemoteLocalFile`) still passes the full
  list (it recomputes complete local-availability sets that replace state).
- **First-launch rebuild** not yet made lighter/off-thread.

---

## 6. THE CRITICAL CONSTRAINT — consumers of the full in-memory list

The memory win requires dropping `AppUiState.items` / `RoomRemoteCatalogRepository.itemsState`. That
cannot happen until **every** reader of the full list (or of `visibleItems`) has a paging-compatible
alternative. Attempting to short-circuit this (M2: empty `visibleItems` when paged) broke real
features and 8 tests — reverted. Consumers to migrate, grouped by approach:

- **Ordered visible set → paged order:** `AppColdStartLibraryViewCoordinator` persists
  `state.visibleItems` order per source for instant cold-start restore. Reconsider whether the
  cold-start cache is even needed once the default view pages (Room paging is already fast at cold
  start); otherwise capture the paged root order instead.
- **Batch download "select all" → bounded IDs query:** `UnifiedReaderScaffold` reads
  `uiState.visibleItems` to select all downloadable items. Serve from a SQL ids query / paged snapshot.
- **By-id synchronous props → `ReadableItemLookup` (E1) resolved into state at selection time:**
  `AppUiState.selectedItem`, `selectedKavitaSeriesTitle`, `selectedStumpSeriesTitle`.
- **Reactive full scans → bounded/semantic:** `AppComicReadingDirectionController` /
  `AppComicReaderModeController` reconcile over `observeItems()` (reconcile only items with
  overrides/sessions); `AppCoverPreloadController.preloadCovers` (use `getItemsForSourceType`).
- **Derived presentation → SQL:** facets (C2/C3 over `library_root_facets`), shelves (D2 over
  `library_root_members` + `reading_progress`), series progress/rating rollup (via members).
- **`remoteItemLocalState*` scope computations** in `AppUiState` (which items to check availability
  for) → bounded/source-scoped queries.
- **Snapshot assembly (last):** `AppCatalogStateCoordinator` / `ObserveAppCatalogSnapshotsUseCase`
  build `items = catalogItems + imported`. Once everything above is migrated, stop materializing the
  full list here and delete `AppUiState.items` + `itemsState`.
- **Other sources:** `items` is all-sources, so Grimmory/Stump/Local must either page too or keep a
  bounded path before `items` can go.

Already migrated: reader next-item (E2), download enqueue (E3), item-by-id lookup foundation (E1).

---

## 7. Plan for the next session (do in this order)

**Phase 1 — Migrate the load-bearing consumers (prerequisite; no memory win yet, keeps everything
working).** One small, tested slice each, in roughly this order (safest/highest-leverage first):
1. Cold-start cache: decouple from `visibleItems` (capture paged order, or retire it for the paged
   path). *Unblocks emptying visibleItems.*
2. Batch download-selection: serve "select all" from SQL ids, not `visibleItems`.
3. Shelves → SQL (D2): Continue Reading / Up Next / Recently Finished as bounded queries over
   `library_root_members` + `reading_progress`. *Removes the biggest remaining items-scan.*
4. Facets → SQL (C2/C3): contextual facets + assemble `LibraryFilterFacets` from SQL + wire into the
   paged filter panel.
5. By-id props (`selectedItem`, series titles) → resolve into state via `ReadableItemLookup`.
6. Reactive scans (comic direction/mode reconciliation, cover preload) → bounded queries.
7. `remoteItemLocalState*` scope computations → bounded queries.

**Phase 2 — Retry M2 (now safe): skip the in-memory recompute when paged.** With the consumers
migrated, add `LibraryDerivedStateInput.includeVisibleItems=false` when `isPageable` so the
full-catalog sort/source-index no longer runs for the paged view. **Measurable:** back-nav GC/churn
drop. (The reverted M2 diff is a starting point; it needs the Phase-1 migrations first.)

**Phase 3 — M3: retire the in-memory catalog.** Extend paging to the other sources (or keep a bounded
path), migrate the snapshot assembly, then delete `AppUiState.items` / `RoomRemoteCatalogRepository.itemsState`.
**Measurable:** startup Java heap (~84 MB) drops. This is the headline win.

Also fold in: make the first-launch derived-table rebuild lighter/off the critical path.

---

## 8. Measurement & logistics

- Device: OnePlus `9279b171`, app `com.example.unifiedreader` (release, signer `cc3c8218`). adb at
  `~/Library/Android/sdk_new/platform-tools/adb`.
- Metrics: `adb shell am start -W` (TotalTime); `adb shell dumpsys meminfo com.example.unifiedreader`
  (App Summary → Java Heap / TOTAL PSS); GC churn + CursorWindow count via `adb logcat`; Choreographer
  skipped frames on scroll. Baseline: cold start ~421 ms, Java heap ~84–101 MB, TOTAL PSS ~270–282 MB,
  CursorWindow ~48, no crashes.
- **Signing (so the assistant can build+install itself):** export `ANDROID_SIGNING_STORE_FILE`,
  `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`, `ANDROID_SIGNING_KEY_PASSWORD`
  pointing at the **same keystore as the installed app** (`cc3c8218`), else installing wipes the
  catalog DB. Then `./gradlew :app:installRelease` preserves data. Without it, the user installs.
- A/B: `LibraryPagingFeature.ENABLED` (compile-time). Flip → rebuild → install.

---

## 9. Invariants / gotchas

- **Do not empty or drop `visibleItems` / `items` before migrating §6 consumers** (M2 revert lesson).
- **Library presentation must commit coherently** (ARCHITECTURE_RULES): no mixed old/new, no layout
  jumps; covers may load independently but card sizes/positions stay stable.
- Keep `app` thin; source-specific + presentation logic in owning modules.
- Derived tables must stay coherent with `readable_items` — every catalog write rebuilds them in the
  same transaction (full replace: per-source; delta: per-affected-library).
- Series grouping is defined once in `core:model` — reuse it; don't reimplement in SQL or app.
- Existing app-suite flaky tests: `AppStumpCoordinatorTest`,
  `DownloadedRemoteComicEntryCacheCoordinatorTest` fail intermittently in the full run, pass in
  isolation — unrelated to this work.

---

## 10. How to start the next session

1. `git checkout perf/library-db-paging` (HEAD = `53877eb6`, M1). Working tree clean.
2. Read this file + the memory note `stage3-paging-handoff`.
3. Start **Phase 1, slice 1** (cold-start cache) — a small, tested slice. Keep the branch green;
   commit per slice. Only attempt Phase 2 (M2) after Phase 1.
