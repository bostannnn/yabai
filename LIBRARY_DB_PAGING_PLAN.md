# Library DB Paging — Handoff & Plan

Single source of truth for the library-performance effort on branch `perf/library-db-paging`.
Read this top-to-bottom to resume in a fresh session.

---

## ✅ CURRENT STATUS (2026-07-10) — read this first

HEAD `7639498e` on `perf/library-db-paging`, working tree clean. Both recent fixes are **verified on
the device** (`9279b171`, adb at `~/Library/Android/sdk_new/platform-tools/adb`):

- **Unfiltered grid pages from Room — instant, even cold start** (M1 + M2). The heavy in-memory
  `LibraryDerivedStateComputer` recompute (~660 ms cold) runs async and no longer blocks the grid.
- **Shelf cold-start seed (`2749f082`) — verified.** Kavita shelves compute from the bounded DB set
  (`shelves.compute [items=889]: 27 ms` at ~340 ms after launch, *before* the ~1.7 s catalog
  hydration), so shelves render immediately instead of the old ~1.8 s wait. Kavita shelves now never
  read `AppUiState.items` (via `LibraryShelvesInputResolver`).
- **Filtered paging (`9d3e695d` + `aa00371f`) — verified.** A **content** filter (format / library /
  author / series / publisher / genre / tag / language) on a Last-read/Date-added sort pages the grid
  from Room (facet `EXISTS` query) — instant, correct multi-filter AND. Runtime filters
  (reading-status, downloaded/local) and rail sorts (Title/Author/Series) stay on the in-memory path by
  design. `pagedGridActive = isPageable && filters.isEmpty` (filtered → grid from Room, `visibleItems`
  stays filtered in-memory, async).
- **`LibraryPerf` timing stays ON** in the tree by user decision (`adb logcat -s LibraryPerf`). It's
  documented in `ARCHITECTURE_RULES.md` (Runtime performance boundaries). *No longer* required to flip
  off before shipping unless the user changes their mind.

### ▶ NEXT (do in this order) — see §7 for detail

1. **Status jump (highest priority, user-flagged).** The paged grid paints instantly, but each card's
   **read badge, progress bar, rating, and download status pop in ~1 s later** — because those come
   from the in-memory recompute's maps (`displayProgressByItemId`, `displayRatingByItemId`,
   `remoteDownloadActionAvailability`). Rebuild them for the **visible paged window** from bounded
   sources (reading_progress via the reader-session store like the shelf seed; ratings via journal;
   download availability via the downloaded-id set + `library_root_members`) so badges commit *with*
   the grid, not after. More involved than the shelf seed because the grid scrolls (per-visible-item
   resolution, not a one-shot). Violates the presentation-coherence rule in `ARCHITECTURE_RULES.md`.
2. **Rail-sort paging** (Title/Author/Series) — needs an SQL-computed alphabet rail (per-letter offsets)
   to page; currently in-memory.
3. **M3 — drop `AppUiState.items` / `itemsState`** (the ~120 MB heap win). Remaining: route facets (C2)
   through the coordinator; migrate by-id props / reactive scans / availability-scope consumers (§6);
   other sources page or keep a bounded path; snapshot assembly; then delete `items`. Kavita shelves
   are already off `items`.
4. First-launch derived-table rebuild lighter/off critical path.

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

### Why it matters (the user-facing outcomes the heap number stands in for)

The heap/GC metrics are proxies. What the user actually gets:

1. **Return-without-reload after backgrounding.** Holding the whole catalog (~84 MB on device, more
   with a bigger library) makes the app a prime target when Android reclaims memory — switch to another
   app, come back, and it cold-starts and loses your place. A bounded resident set makes the process far
   less likely to be killed. *Case: read a comic → browser for 10 min → return → library is exactly where
   you left it, instantly, instead of reloading.*
2. **Instant, jank-free back-from-reader on a huge library.** Every progress change re-sorts the entire
   catalog in memory for LAST_READ (the ~236 MB churn / GC hitch). SQL ordering removes that sort.
   *Case: finish issue #40, press back → the series jumps to the top of "Last read" with no stutter,
   whether you have 500 or 50,000 issues.*
3. **Fast time-to-content regardless of catalog size.** The catalog is read/mapped several times at
   launch (the CursorWindow bursts + GC storm), so big libraries show placeholders for seconds. Paging
   the first screen makes time-to-content roughly constant. *Case: open a 30k-issue Kavita library → grid
   fills in ~1 s instead of spinners.*
4. **Constant memory as the library grows.** Today the heap grows with the catalog; after paging it's
   bounded by what's on screen. *Case: the library doubles over a year → the app doesn't get slower or
   more crash-prone.*
5. **Less battery/heat while browsing.** Fewer GC cycles = less CPU. Minor, but real on long sessions.

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
| `87e55ad6` | E4 | **Phase 1, slice 1 — cold-start cache off `visibleItems`.** Capture branches per landing: the paged Kavita landing records ordered root ids from SQL (`RemoteCatalogDao.getGridRootIds{ByTitle,ByDateAdded,ByLastRead}` → `LibraryGridPager.orderedGridRootIds`, bounded to `COLD_START_LIBRARY_ORDER_LIMIT`); every other landing still snapshots `visibleItems`. Same ids as before (shared grouping), so cold-start behaviour is unchanged. Re-captures on progress for LAST_READ; once-per-landing for DATE_ADDED. `restore()` untouched. Unblocks emptying `visibleItems` for the paged path (Phase 2). |
| `06f3bb27` | E5 | **Phase 1, slice 2 — batch download selection off `visibleItems`.** `LibraryScreen` derives its selection set from a single `renderedItems` val (paging snapshot when paged, else in-memory list; also reused by cover prefetch) and reports the selectable download **items** (not ids) up. `UnifiedReaderScaffold` `selectAll`/`downloadSelected` resolve from the screen-reported items (`effectiveSelectableDownloadItems`) instead of `uiState.visibleItems`. Synthetic container roots aren't in `readable_items`, so the paged snapshot (which holds the derived containers) is the correct source — SQL-by-id can't rebuild them; `onDownloadRemoteItems`/queue controller unchanged. Non-paged path byte-identical. Only remaining `visibleItems` read is the pre-report fallback (in-memory paths). |
| `34e8ff35` | D2a | **Phase 1, slice 3 (foundation) — bounded shelf-input SQL source.** `RemoteCatalogDao.getShelvesInputItems(source)` returns *progressed items + their series siblings* (via `library_root_members` ∩ `reading_progress`), the exact set the shelves computer keeps from the full catalog — Continue Reading / Recently Finished need the progressed items, Next-In-Series needs each active series' full ordered issue list. Provably equivalent to running the unchanged computer over all of `items`. Exposed as `LibraryShelvesInputSource.shelvesInputItems` (DI-bound). DAO-tested; no consumer yet. **Routing deferred to M3** — see §7. |
| `eeb3a6e8` | C2 | **Phase 1, slice 4 (foundation) — assemble paged facets from SQL.** Finding: the paged path is gated to empty filters and the filter dialog applies each toggle immediately, so selecting any value exits the paged path → the paged filter panel only ever shows facets for an *unfiltered* view, where contextual == non-contextual. So no `buildContextualLibraryFilterFacets` (~10-pass) work is needed for paging; that stays on the in-memory path. New reusable piece: core:model `assembleLibraryFilterFacets` turns C1's per-dimension `getDistinctFacetValues` into a `LibraryFilterFacets` with identical ordering/typing, order-independent; `fileLocations` stays caller-provided runtime. Equivalence-tested vs `buildLibraryFilterFacets`. **Routing deferred to M3** — see §7. |
| `7f4232e1` | perf | Opt-in `LibraryPerf` timing (`adb logcat -s LibraryPerf`) around the recompute passes — used to find the real costs on device. Flip ENABLED off before shipping. |
| `243f4850` | D2b | **Shelves Next-In-Series bounded to active series** — on-device it was 1236 ms over 17k items (verified → ~85 ms). `LibrarySeriesContinuationResolver.nextUnstartedAfterForItems` groups/sorts only the active comics' series. Behaviour-preserving. |
| `2749f082` | D2c | **Shelves cold-start seed from DB (unverified on device).** `LibraryShelvesInputResolver` sources Kavita shelves from the bounded DB query + reader-session progress; coordinator feeds shelves through it (Kavita → DB, else in-memory; `computeShelvesState` suspend); `seedColdStartShelves` publishes them pre-hydration so shelves don't wait the ~1.8 s catalog load. Kavita shelves now never read `AppUiState.items` — a big chunk of M3's shelves work. |
| `9d3e695d`+`aa00371f` | Bf | **Filtered paging (unverified on device).** `buildFilteredGridRootsQuery` + `pagingGridRootsFiltered` (`@RawQuery`, `EXISTS` over `library_root_facets` per content dim); `isPageable` allows content filters (`isPageableFilter`); `pagedGridActive` gated to `isPageable && filters.isEmpty` so a filtered paged view pages the grid from Room but keeps `visibleItems` filtered in-memory. Content-filtered Last-read/Date-added views now page. |
| `4f81da41` | M2 | **Skip the redundant in-memory grid sort when paged.** When the grid is served by Room paging, the in-memory last-read/date-added sort over the whole source set is redundant (Room sorts the window) yet re-ran on every progress update — the ~236 MB back-nav floor. Prep: `LibraryScreen`'s sort menu + date-added scroll anchor read `renderedItems` (paging snapshot) not the in-memory `items`. Core: `LibraryDerivedStateInput.pagedGridActive` (from `isPageable`); when true the computer leaves `visibleItems = sourceItems` (same membership) and skips `buildVisibleLibraryItems` + the last-read map. Added to the derived cache key. **Re-scoped: M2 is only the sort skip** — shelves/facets keep using `items` until M3, so their routing (D2a/C2) moved to M3. Computer test proves membership kept + sort skipped. Measurable heap/GC effect needs on-device A/B. |

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
- **Facets: SQL foundation done (C1 + C2), routing deferred to M3.** C1 stores distinct facet values;
  C2 (`assembleLibraryFilterFacets`, `eeb3a6e8`) turns them into a `LibraryFilterFacets`. The paged
  filter panel needs only these **non-contextual** facets (toggling a filter exits the paged path), so
  contextual facets (`buildContextualLibraryFilterFacets`, the ~10-pass logic) are **not needed for
  paging** and stay on the in-memory path. Remaining: repository glue (`getDistinctFacetValues` ×8 +
  runtime `fileLocations`) wired into the paged panel → M3 (facets still read `items` until it's dropped).
- **Shelves: SQL foundation done (D2a), routing deferred to M3.** The bounded-input source
  (`LibraryShelvesInputSource.shelvesInputItems`, `34e8ff35`) exists and is tested, but the recompute
  coordinator still feeds the computer `AppUiState.items` (fine until `items` is dropped). Wiring it in
  means routing Kavita shelves through SQL inside `AppLibraryStateRecomputeCoordinator` — the app's most
  delicate state machine (sync+async paths, identity-keyed signatures, retained-shelf caching, loading)
  whose ~15-test suite defaults to Kavita and asserts shelves from in-memory `items`. Do it at M3
  alongside retiring `items` (the only point it's actually required), reworking those tests once.
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

- ~~**Ordered visible set → paged order:** `AppColdStartLibraryViewCoordinator` persists
  `state.visibleItems` order per source for instant cold-start restore.~~ **DONE (E4).** Capture now
  reads the paged landing's order from SQL (`LibraryGridPager.orderedGridRootIds`); other landings
  still snapshot `visibleItems`. `restore()` never read `visibleItems`, so it's untouched.
- ~~**Batch download "select all" → bounded IDs query:** `UnifiedReaderScaffold` reads
  `uiState.visibleItems` to select all downloadable items.~~ **DONE (E5).** Served from the screen's
  rendered items (paged snapshot / in-memory), reported up as selectable items; scaffold no longer
  scans `visibleItems` (only a pre-report fallback remains).
- **By-id synchronous props → `ReadableItemLookup` (E1) resolved into state at selection time:**
  `AppUiState.selectedItem`, `selectedKavitaSeriesTitle`, `selectedStumpSeriesTitle`.
- **Reactive full scans → bounded/semantic:** `AppComicReadingDirectionController` /
  `AppComicReaderModeController` reconcile over `observeItems()` (reconcile only items with
  overrides/sessions); `AppCoverPreloadController.preloadCovers` (use `getItemsForSourceType`).
- **Derived presentation → SQL:** facets (C1 + C2 `assembleLibraryFilterFacets` **done**, routing → M3;
  contextual facets not needed for paging), shelves (D2 — bounded-input source `shelvesInputItems`
  **done (D2a)**, routing → M3), series progress/rating rollup (via members).
- **`remoteItemLocalState*` scope computations** in `AppUiState` (which items to check availability
  for) → bounded/source-scoped queries.
- **Snapshot assembly (last):** `AppCatalogStateCoordinator` / `ObserveAppCatalogSnapshotsUseCase`
  build `items = catalogItems + imported`. Once everything above is migrated, stop materializing the
  full list here and delete `AppUiState.items` + `itemsState`.
- **Other sources:** `items` is all-sources, so Grimmory/Stump/Local must either page too or keep a
  bounded path before `items` can go.

Already migrated: reader next-item (E2), download enqueue (E3), item-by-id lookup foundation (E1),
cold-start capture (E4), batch download-selection (E5).

---

## 7. Plan for the next session (do in this order)

**Phase 1 — Migrate the load-bearing consumers (prerequisite; no memory win yet, keeps everything
working).** One small, tested slice each, in roughly this order (safest/highest-leverage first):
1. ~~Cold-start cache: decouple from `visibleItems`.~~ **DONE (E4, `87e55ad6`)** — capture reads the
   paged landing's order from SQL; `restore()` never touched `visibleItems`. *Unblocked.*
2. ~~Batch download-selection: serve "select all" from `visibleItems`.~~ **DONE (E5, `06f3bb27`)** —
   the screen reports its rendered selectable items (paged snapshot / in-memory); scaffold resolves
   select-all/download from those. *Unblocked.*
3. Shelves → SQL (D2). **Foundation DONE (D2a, `34e8ff35`)** — `LibraryShelvesInputSource` bounded
   input, tested. **Routing → M3** (shelves keep reading `items` until it's dropped; wiring the
   coordinator only earns its keep then).
4. Facets → SQL. **Foundation DONE (C2, `eeb3a6e8`)** — `assembleLibraryFilterFacets` + the finding
   that the paged panel needs only non-contextual facets (contextual stays in-memory). **Routing → M3**
   (facets keep reading `items` until it's dropped).
5. **← NEXT.** By-id props (`selectedItem`, series titles) → resolve into state via `ReadableItemLookup`.
   *A direct consumer with real Phase-1 payoff, but needs state-assembly restructuring — these are
   synchronous computed props read in composition (see §6 "By-id props").*
6. Reactive scans (comic direction/mode reconciliation, cover preload) → bounded queries.
7. `remoteItemLocalState*` scope computations → bounded queries.

**Phase 2 — M2 DONE (`4f81da41`): skip the redundant in-memory grid sort when paged.** Re-scoped to
*only* the sort skip: `LibraryDerivedStateInput.pagedGridActive` (from `isPageable`) makes the computer
leave `visibleItems = sourceItems` (same membership) and skip `buildVisibleLibraryItems` + the
last-read map; `LibraryScreen` sort-menu/scroll-anchor read `renderedItems`. **Measurable:** back-nav
GC/churn drop (needs on-device A/B — see §8). Shelves/facets were NOT part of M2: they still read
`items` and keep working, so their SQL routing moved to M3 (below), where it's actually required.

**Phase 3 — M3: retire the in-memory catalog.** The headline win (startup Java heap ~84 MB drops).
Steps:
- **Route shelves through `LibraryShelvesInputSource` (D2a)** and **facets through
  `assembleLibraryFilterFacets` (C2)** for the paged path inside `AppLibraryStateRecomputeCoordinator`
  — the async Kavita recompute feeds `shelvesInputItems(KAVITA)` / `getDistinctFacetValues×8` instead
  of `AppUiState.items`; adjust the shelves/derived signatures so they recompute on progress/catalog
  change once `items` is empty; rework the coordinator's Kavita shelf tests to feed a fake source.
- Migrate the remaining by-id / reactive-scan / availability consumers (slices 5–7) + series
  progress/rating rollup.
- Extend paging to the other sources (or keep a bounded path), migrate the snapshot assembly, then
  delete `AppUiState.items` / `RoomRemoteCatalogRepository.itemsState`.
- Also fold in: make the first-launch derived-table rebuild lighter/off the critical path.

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
- **Paging/M2 only engage on `LAST_READ`/`DATE_ADDED`** (rail-less). Title/Author/Series keep the
  in-memory path (they render the A–Z alphabet rail, which needs the full ordered set + per-letter
  offsets — incompatible with a windowed PagingSource). To observe any paging effect on device, be on
  the **Last read** or **Date added** sort. (2026-07-09: a "no change" report turned out to be testing
  on Title; also, opening the reader then backing without reading does no recompute → always instant,
  regardless of paging.)
- **`LibraryPerf` timing (`7f4232e1`, opt-in via `LibraryPerfLog.ENABLED`):** `adb logcat -s LibraryPerf`
  logs `derived.compute` HIT vs MISS (+ per-pass breakdown: sourceIndex, downloadAvailability[roots=N],
  visibleItems.sort, facets), `shelves.compute` HIT/MISS, and `recompute.async total`. Use it to tell
  whether a felt back-nav delay is a cache **HIT** (→ the cost is covers re-decoding / grid relayout,
  fix = image cache, not the recompute) or a **MISS** (→ which pass dominates). The recompute runs
  async, so it doesn't show as main-thread jank in `gfxinfo` — measure it here, not via frame stats.
  Flip `ENABLED=false` before shipping.

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

1. `git checkout perf/library-db-paging` (HEAD = `4f81da41`, M2). Working tree clean.
2. Read this file + the memory note `stage3-paging-handoff`.
3. **On-device: measure M2** — A/B `LibraryPagingFeature.ENABLED` (or check the paged Kavita LAST_READ
   view), read a few items, back out, and compare back-nav GC churn / `dumpsys meminfo` (§8). M2 is
   unverified on device; this is the measurable payoff.
4. Then continue **Phase 1, slice 5** (by-id props: `selectedItem`, series titles → `ReadableItemLookup`).
   Done: cold-start (E4), batch download (E5), shelves foundation (D2a), facets foundation (C2), M2
   sort-skip. Shelves/facets **routing → M3**. Keep the branch green; commit per slice.
