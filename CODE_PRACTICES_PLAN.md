# Code practices plan

The live handoff for the 2026-11-05 code-practices review: what the review found, what is already fixed, and the
next steps in order. Each step says where the problem lives, what done looks like, and how to check it. Line numbers
are from 2026-11-05 and will drift; search for the names.

`ARCHITECTURE_RULES.md` stays binding. Most steps below are that file's rules applied to code that grew before it
existed.

## Status

**Done (branch `claude/code-practices-review-17d4be`)**

- **Main thread no longer blocks on Room writes when the app stops.** `AppViewModel.onAppStopped()` called
  `runBlocking { readerSessionStore.flushPendingWrites() }`; it now calls `requestFlushPendingWrites()`. The writes
  already run on the store's own scope, which outlives the stop.
- **Coroutine cancellation survives error handling.** 40 `runCatching` blocks that wrapped suspend calls now use
  `runSuspendCatching` (`core/model/.../SuspendCatching.kt`), which rethrows `CancellationException`. The app-only
  `runSuspendCatchingPreservingCancellation` was folded into it.
  - **How they were found:** a temporary same-package `runCatching` that is not `inline` makes the compiler reject
    every block that calls a suspend function. Re-use that trick to re-check after big changes.
  - **Every `runCatching` left in main code is non-suspend.**
- **Dead code removed:**
  - `buildEpubProgressLabel` and `buildEpubPageProgressLabel`, and the four `sample_comic_page_*.xml` drawables.
  - `TextFacetRow`, `libraryFilterPileRows`, `PileMaxSpines`, `itemPrimaryMaxLines`, `itemTertiaryText`,
    `itemPageCountText` and `buildComicVolumeIssueLabel`.
  - The journal `formatDate` and `formatJournalDecimal`, the Grimmory `toGrimmoryFileSegment` and
    `LibraryTitleCoverFills`.
  - Kit: `AppOutlinedButton` and `AppSheet` (`AppSheetLayout` is the bottom sheet; `AppSideSheet` opens as one on
    Compact). The design system was updated first.
- **Unused dependencies removed.** 18 lines across 10 build files, plus three catalog entries:
  `navigation-compose`, `ui-tooling-preview` and `lifecycle-viewmodel-compose`.
- **CI runs the JVM modules' tests.** `ci.yml` and `release-apk.yml` now also run
  `:core:model:test :data:grimmory:test :data:kavita:test :data:stump:test`.

**Step 3 done (2026-11-06, branch `claude/component-refactoring-reuse-b71e0f`)**, ahead of steps 1 and 2 by the
user's choice. The shared helpers and where they live:

- `String?.toServerEpochMillisOrNull()` (`core:model` `ServerTimestamps.kt`, tested): the 5 server-time parsers.
  It takes an instant, an offset time and a zone-less local time (read as UTC) everywhere, so Grimmory's catalog
  and progress times and Stump's times now also accept offsets and local times.
- `sha256Hex`, `sha256`, `toLowerHex` (`core:model` `Hashing.kt`, output pinned by a test so cache names stay put).
- `writeFileAtomically` and `File.moveReplacing` (`core:model` `AtomicFiles.kt`): replace `writeCachedFile`,
  `writeGrimmoryCacheFile` and `writeCoverCacheFile` (`CoverFileWriters.kt` is gone); the preview store, the local
  cover cache and the archive codecs move through `moveReplacing`. The move now replaces in one step instead of
  delete-then-rename.
- `LenientJson` (`core:network`): the HTTP client, Kavita (3), Grimmory and Stump. The two in `feature/updates`
  stay: that module doesn't use `core:network`, and one of them reads stored preferences.
- `String.resolveSameOriginUrl` (`core:network`, tested): Kavita's and Stump's cover/asset downloads. Not done: an
  `HttpResponse.throwIfError`, because the three servers' error messages are built differently.
- `shortDayLabel` (`core:model`, tested): settings, the book page, downloads and shelves. **Decision taken:**
  English month names (`Locale.US`) everywhere, since the rest of the text is English; the book page and shelves
  used the device language before. The journal's `formatShortDate` never shows a year, so it stays.
- `ContentResolver.queryArchiveSignature` and `Cursor.getLongOrNull` (app `ContentArchiveSignature.kt`).
- Format checks: `isComicReaderFormat` and `toReadableFormatTypeOrNull` replace `isComicArchive` (2) and Stump's
  `toComicFormatType`.

**Step 1 done (2026-11-06)**, the user's answers, all carried out:

- **Drag handle: use the kit piece.** Filter chips, the list editor and Source order now use
  `AppReorderHandleIcon`. The list editor's handle is now named "Drag to reorder …" (it said "Drag to move …")
  and takes the kit's muted colour.
- **Test-only code: deleted with its tests**, except where a test used the dead function to check live code:
  - The grid-roots test now calls `deriveGridRootsWithMembers`, and the shelf-browser test chains
    `browserEntries`, `searchedBy` and `sortedFor`.
  - `RemoteSourceReachability` was only an alias: tests use `SourceConnectivityState` directly.
  - **Kept:** `InMemoryRemoteItemLocalStateCache`. It is a full in-memory implementation that two app tests run
    against, and it uses the cache's private scope helpers.
  - `ComicSpreadPreparation.kt` and `RemoteDownloadBatchOutcome.kt` went whole.
  - `JournalRatingClearTag` was never applied, so the androidTest's check against it proved nothing.
- **`LibraryPagingFeature.ENABLED`: removed.** Paging is always on. The in-memory path stays, because it is what
  every view `isPageable` turns down still renders (search, other servers, rail sorts with filters, and so on).
- **Performance logging: the rule changed.** `ARCHITECTURE_RULES.md` now says the logs stay on in release builds
  by choice.
- **Package name:** not asked; left as `com.example.unifiedreader` (no Play Store plans known).

**Where it stands (2026-11-06):** steps 3 and 1 are on main (`ca440f12`, `ed7a460c`), pushed and released in 0.43.0.

**Step 2 done (2026-11-06)**, not on a device (build-only changes plus the `runCatching` fixes below):

- **Convention plugins** (`build-logic/convention`): `yabai.android.library`, `yabai.android.compose`,
  `yabai.android.application` and `yabai.jvm.library` hold the SDK levels, the Java version, Compose, detekt and
  lint. The 26 build files lost about 350 lines; most now hold only a namespace and dependencies.
- **One JVM target:** every module builds for Java 17 (the plain Kotlin modules built for 21). `runSuspendCatching`
  is now `inline`. The deprecated `kotlinOptions` are gone, and the two hard-coded test libraries are in the catalog.
- **detekt 1.23.8 with the Compose rules** on all 26 modules, rules in `config/detekt/detekt.yml` (line length 140,
  matching the new `.editorconfig`). Baselines hold 1,617 old findings. Two Compose rules are off by choice:
  `ParameterNaming` (this code names callbacks `onSelected`) and `CompositionLocalAllowlist`.
- **Android lint** on the 20 Android modules, 152 old findings in baselines. The three checks that ask the network
  for newer versions are off, so findings don't change without a code change.
- **CI:** a second job runs `detekt lintDebug`. Both workflows now also run `:core:network:test`, which CI had never
  run.
- **The `runCatching` rule:** detekt's `SuspendFunSwallowedCancellation` needs type resolution, which crashes on
  the Compose modules with detekt 1.23. So `SuspendRunCatchingRuleTest` (`core:model`) reads the source instead:
  no `runCatching {` inside a `suspend fun` or a coroutine block. It found 22, now `runSuspendCatching`. Real
  cases among them: translation model downloads and translations, catalog repository reads, the CBL import and
  the update check. Revisit with detekt 2 once it is stable.
- **Room schema export:** `exportSchema = true` into `core/database/schemas/`. `36.json` was rebuilt from the
  commit before version 37. `UnifiedReaderDatabaseSchemaTest` runs 36→37 with `MigrationTestHelper`, opens a 36
  database through every migration, and checks each version from 36 has a schema file. Removing a column from
  `MIGRATION_36_37` fails two of its tests.
- **Checked:** 1,900 unit tests pass (Android plus JVM modules), and detekt, lint, `:app:assembleDebug` and the
  journal androidTest sources pass too.

**Step 4 done (2026-11-06)**, not on a device:

- **One app scope.** `@ApplicationScope` (`core:common`) is provided once by `AppCoroutinesModule`, a
  `SupervisorJob` on Default. The 21 classes that built a `CoroutineScope(SupervisorJob() + …)` of their own now
  take a scope: the page sources, catalog repository, reader session store, journal recorders, cover controller,
  backfiller, local documents, comics session adapter, the seven preference stores, translation and the updater.
  Production passes `applicationScope + <dispatcher>`, which shares the one job and keeps each class's old
  dispatcher. Tests pass `backgroundScope` (plus IO where the class used IO), or `TestScope()` outside `runTest`.
  The preference-store providers moved from `AppModule` to `AppPreferencesModule`, so `AppModule` shrank.
- **Dispatchers.** 100 hard-coded `Dispatchers.IO` → 52, of which 23 are parameter defaults. The other 48 uses
  now go through a parameter. Most classes take an `ioDispatcher` constructor parameter (default
  `Dispatchers.IO`); `@Inject` classes take the injected `AppDispatchers`, since Hilt ignores Kotlin defaults.
  29 remain, in Compose screens, top-level functions, and two classes already at detekt's parameter limit
  (the encrypted sign-in store, the download repository).
- **Lifecycle-aware collection.** All 17 `collectAsState` in the readers are now `collectAsStateWithLifecycle`,
  so a reader in the background stops collecting.
- **Disk writes.** The encrypted sign-in store now encrypts and commits on IO. The theme and the source order
  write their cold-start copy on IO.
- **Download worker.** The swallowed cancellation in `RemoteDownloadWorkerRunner` now has a comment saying why
  (the entry must be settled before WorkManager gets the outcome).
- **detekt:** `LongParameterList` now ignores `@Provides` and `@Inject`, whose parameters are the dependency graph.
  Three baseline entries were renamed for constructors whose scope lost its default.
- **Known and left:** `app`'s androidTest `LibraryTopBarSearchTest` has not compiled since the search redesign
  (`81bba708`). CI does not build androidTest.

**Step 5.1 built (2026-11-07), comic page cache and prefetch.**
- `ComicPageMemoryCaches` (app) holds the five caches (decoded pages, metadata, previews, page bytes, preview
  bytes), the five load limits, `semaphoreFor`, and the evict and memory-trim steps.
- `ComicPagePrefetchJobs` (app) holds the page-prefetch jobs: `retarget` cancels jobs outside the new window and
  starts the missing ones in window order. It has its own test.
- `LocalComicPageLoader` (1,020 → 885 lines) and `RemoteComicPageSource` (1,076 → 960) use both.
- Left in each: the in-flight byte requests (they change under the same lock as each loader's cache
  generations), the preview prefetch jobs, and the key and fetch code, which really differ.
- 1,903 tests, detekt, lint and the debug build pass.

- **Emulator (2026-11-07, debug build over another session's):** a local comic (Star Courier #1) opened with
  its page strip, paged forward to 6–7 and back to 2–3 after backgrounding and a forced memory trim
  (`am send-trim-memory … RUNNING_CRITICAL` then `COMPLETE`); no crash in the log. **Not checked on a device:** a
  Kavita or Stump comic, the only users of `RemoteComicPageSource`. The emulator has no such server, so only the
  39 page-source tests cover that path.

**Step 5.2 built (2026-11-07), archive-backed page sources.**
- `ArchiveComicPageSource` (app) is the comic pages of an archive on this device. It owns the `LocalComicPageLoader`,
  the nine page methods, and the clear and trim steps. A subclass gives `recordFor` (may read the archive),
  `knownRecordFor` (what is already known) and `entryNameFor`.
- `LocalComicPageSource` (imported comics, entry = `page.id`) and `DownloadedRemoteComicPageSource` (entry by page
  index) extend it. `LocalDocumentRepository` exposes its pages as `comicPages`; the `LocalComicDocumentDataSource`
  interface and the forwarding `ImportedComicArchivePageSource.kt` are gone. About 590 lines removed, 250 added.
- One small change: imported comics now prefetch previews by entry name (`page.id`), as their other calls did,
  instead of by index. `getPages` makes both the same.
- detekt: the two `TooManyFunctions` entries moved to the new class names (the 13-method `ComicPageSource`
  interface is the cause); stale entries for the removed methods were dropped.
- All tests, detekt, lint and the debug build pass. **Emulator:** a local comic (Star Courier #1) paged to 8 with
  every page-strip preview loading, then after a forced memory trim paged back to 4–5; no crash in the log.
  **Not checked:** a downloaded Kavita, Stump or Grimmory comic (the emulator has none); its 10 page-source tests
  pass.

**Step 5.3 built (2026-11-07), server connection controllers and UI state.**
- **UI state:** `RemoteSourceConnectionUiState` (feature:settings) is now one data class keyed by `source`, which picks
  the default name and URLs. It replaces the interface and `Kavita`/`Stump`/`GrimmoryConnectionUiState`.
  `availableLibraries` stays empty for Grimmory. The three `withStoredSession` helpers are one.
- **Result types:** `SourceSyncPreparation<Request, Session>`, `SourceSyncExecutionResult` and
  `SourceConnectExecutionResult` (`SourceSyncResults.kt`) replace the nine per-server sealed types.
  `KavitaSyncPreparation`/`KavitaSyncReady` (and Stump's and Grimmory's) are typealiases. The three catalog sync
  requests implement `SourceSyncRequest` (base URL and username, the library cache's key).
- **Controller:** `RemoteSourceConnectionController` holds the form edits, connect, `observeStoredSession`
  and sync. A subclass gives its coordinator calls, where the bar starts, its messages and which series to close.
  Kavita and Stump pass `RemoteSourceLibraryLists` (library cache + page cache); Grimmory passes none.
  `App{Kavita,Stump,Grimmory}RemoteSourceController` are now 60–80 lines each, keeping their public calls
  (`refresh`, Kavita's `fastRefresh`).
- **One behaviour change:** Kavita and Stump now ignore Connect or Sync while one is already running, as Grimmory
  did; before, a second one could interleave its progress and save over the first.
- About 1,430 lines removed, 730 added. detekt: the three controllers' `TooManyFunctions` entries became one for
  the base class; three coordinator `LongMethod` entries were renamed for their new parameter types; stale
  controller entries were dropped.
- All tests, detekt, lint and the debug build pass. **Emulator:** Settings shows Grimmory's saved sign-in and
  Kavita and Stump as not connected; Kavita's page has the right defaults; a sign-in to a dead local address
  (`10.0.2.2:9`, test values) showed "Connecting", then the failure, and re-enabled Connect; no crash.
  **Not checked:** a real sign-in or sync against any server.

**Step 5.4 built (2026-11-07), progress push loop and token refresh.**
- `pushDirtyProgress` (`ProgressPushPlan.kt`) is the one loop. It takes the unsent and failed positions oldest first
  and asks the server's `planFor` what to do with each: `Leave` it, `SettleLocally`, or `Push` (check the server's copy,
  then send). Settling, marking failures and stopping on an unreachable address are shared.
- Kavita, Stump and Grimmory each keep only their plan: Kavita's prefix filter and removed-chapter rule, Stump's
  local unread reset and diagnostics, Grimmory's position building.
- `TokenRefreshResult<T, Session>`, `runWithTokenRefresh` and `TokenRenewal` (`TokenRefresh.kt`) replace the two
  copies in `StumpAuthHelpers.kt` and `GrimmoryAuthHelpers.kt`. `StumpTokenRefreshResult`/`GrimmoryTokenRefreshResult`
  are typealiases, and `RefreshingTokens` carries the tokens from one request to the next within a run.
- About as many lines added as removed (250 each): the gain is one copy of the loop and of the token renewal instead of three and two. Nothing a reader sees changes.
- All tests (Kavita 13, Stump 6, Grimmory 11 progress tests and the catalog sync tests), detekt, lint and the debug
  build pass. Not checked on the emulator: pushing progress needs a server.
- **Seen, not part of this step:** lint warns twice (`UseKtx`) on `EncryptedSourceSessionStore.kt` from `8d2b885d`;
  warnings don't fail the build.

**Step 5.5 built (2026-11-07), cover repositories.**
- `SourceCoverRepository<Session, Api>` (app) holds the cache lookup, the download into the cache through the
  saved sign-in with address failover, warming and clearing. A subclass names its cache files, sets the session's
  active address and downloads one cover's bytes.
- `KavitaCoverRepository` (141 → 55 lines), `StumpCoverRepository` (161 → 70) and `GrimmoryCoverRepository`
  (135 → 55) are subclasses; their `*CoverDataSource` interfaces and Hilt wiring are unchanged.
- `remoteCacheFileName`/`remoteLegacyCacheFileNames` replace the byte-identical Kavita and Stump versions. Grimmory's
  pass its file id as `fileKey`, so every cache file keeps its name and no cover is downloaded again.
- One small change: a cover is now downloaded first and then written to the cache, instead of downloaded while the
  file was open. A sign-in renewal retries only the download.
- All tests (cover repositories, cache file names, cover backfill), detekt, lint and the debug build pass.
  **Emulator:** the offline Grimmory library looks the same with and without the change (main's build installed
  for comparison; covers not in the cache stay blank in both). **Not checked:** downloading a cover from a server.

**Step 5.6 built (2026-11-07), credential stores.**
- `EncryptedCredentialStore<S>` (app) reads and writes the seven fields every saved sign-in has, under the same keys
  as before, and forwards `sessionFlow`, `save` and `clear` to `EncryptedSourceSessionStore`. A subclass builds its
  session from those fields and adds its own: Kavita its API key; Grimmory treats a missing refresh token as no
  sign-in, as before.
- `SharedPreferences{Kavita,Stump,Grimmory}CredentialStore` are now about 30 lines each and `internal` (the shared
  session interface is internal; nothing outside `app` used them). Kavita's and Stump's files were renamed to match
  their class; their two `MatchingDeclarationName` baseline entries were dropped.
- Preference file names and keys are unchanged, so saved sign-ins load as before.
- New `KavitaCredentialStoreTest` (the API key round trip, a sign-in missing its token). All tests, detekt, lint and
  the debug build pass. **Emulator:** the saved Grimmory sign-in (a local stand-in at `10.0.2.2:6062`, user
  `tester`) loaded after the change. **Not checked:** saving a new sign-in in the app (needs a server).

**Next:** 5.7 (`withActiveBaseUrl` copies).

## Step 1. Decisions only the user can make (done, answers above)

These block or shape later steps.

- **`AppReorderHandleIcon`:** it has no callers, but three screens hand-build the identical handle (24 icon,
  40 grab area, `onSurfaceVariant`, "Drag to reorder …"):
  - `FilterChipsSettingsPage.kt` around line 160
  - `UserListEditor.kt` around line 301
  - `DrawerOrderSettingsGroup.kt` around line 70

  Either use the kit piece in all three, which the design system already documents (`AppSettingsGroup`), or remove
  it from the kit and the system.
- **Code that only tests use:** wire each into the app or delete it with its test.
  - `FlushPendingProgressOnStartupUseCase` (`data/repository`): the whole class.
  - `InMemoryRemoteItemLocalStateCache`, `RemoteSourceReachability` and `downloadedRemoteComicArchiveCodecFormats`
    (app).
  - `assembleLibraryFilterFacets` and `deriveGridRootItems` (`core:model`).
  - `resolveSupplementalIssueTitles` (`data:kavita`).
  - `evaluateRemoteDownloadBatchOutcome` (downloads).
  - `shortLabel` and `JournalRatingClearTag` (journal).
  - `visibleShelfBrowserEntries` (library-shelves).
  - `libraryFilterNamedPeriods`, `shouldShowCoverProgress`, `shouldShowSummaryProgress` and `progressDetailLabel`
    (library).
  - `prepareComicSpreadPlanState` (reader-comics) and `buildLibraryDirectionNormalizationPlan`
    (reader-preferences).
- **`LibraryPagingFeature.ENABLED = true`:** delete the switch and the in-memory fallback it guards, or keep it as a
  kill switch. If kept, say so in its KDoc.
- **Performance logging in release builds:**
  - `LibraryPerfLog.ENABLED`, `LibraryShelvesPerfLog.ENABLED` and `PAGED_WINDOW_BADGE_PERF_ENABLED` are always on.
    That is on purpose, but `ARCHITECTURE_RULES.md` says to turn them off for shipping builds.
  - Either change the rule, or tie them to `BuildConfig.DEBUG` plus a hidden setting.
- **Package name:** `applicationId` is `com.example.unifiedreader`.
  - The Play Store rejects `com.example`.
  - Changing it makes a new app: a fresh install that loses the library and journal, and the updater's package
    check must change with it.
  - Decide now whether the app will ever be on the Play Store. If not, leave it.

## Step 2. Guard rails (done, see above)

Cheap, and they keep the later steps from sliding back.

1. **Static analysis:**
   - Add detekt (with the compose rules) and an `.editorconfig`, plus Android lint, each with a baseline of
     today's findings.
   - Run them in `ci.yml` next to the tests.
   - Today there is no lint, detekt, ktlint or `allWarningsAsErrors` anywhere.
2. **Convention plugin (`build-logic/`):**
   - **Android modules:** 19 of them repeat the same `android {}` block (compileSdk 37, minSdk 28, Java 17,
     compose), about 350 lines in all. Replace that with `yabai.android.library` and `yabai.android.compose`
     plugins.
   - **JVM modules:** the 6 of them get a `yabai.jvm.library` plugin.
   - **Deprecations:** switch the deprecated `kotlinOptions { jvmTarget }` to `compilerOptions`.
   - **One JVM target:** put every module on the same target. `core:model` builds for 21 and the Android modules
     for 17, which is why `runSuspendCatching` cannot be `inline`. After this step, make it `inline`.
   - **Hard-coded test versions:** move `androidx.test.ext:junit:1.2.1` and `espresso-core:3.6.1`, written into
     6 build files, into `libs.versions.toml`.
3. **Room schema export:**
   - Turn on `exportSchema = true` with a `room.schemaLocation`, and commit `schemas/`.
   - Add a `MigrationTestHelper` test for the latest migrations.
   - This guards the "never downgrade, every bump needs a migration" rule with data, not care.
4. **A rule test for `runCatching` around suspend calls,** in the style of `DesignSystemRuleTest`. Or rely on the
   detekt rule `SuspendFunSwallowedCancellation`, if the detekt version has it.

**Done when:** CI runs lint, detekt and both test lines; all module build files are short; every module builds
for one JVM target.

## Step 3. Small shared helpers

Each is a short, low-risk change. Put JVM-safe helpers in `core:model` (or a new JVM `core:common`; today's
`core:common` is a 9-line Android module holding only `AppDispatchers`).

| Helper | Copies today | Note |
|---|---|---|
| `parseServerTimestampMillis` | 5: `KavitaDtos.kt` ~635, `KavitaJournalSyncSupport.kt` ~473, `GrimmoryJournalSyncSupport.kt` ~606, `GrimmoryDtos.kt` ~412, `StumpDtos.kt` ~358 | **Real bug risk.** They accept different formats, so one server string can parse in one place and fail in another. Use the widest (Instant, Offset, Local) and add tests. |
| `File.writeAtomically` (temp file + rename) | 4–5: `GrimmoryCacheHelpers.kt` `writeCachedFile`, `CoverFileWriters.kt` `writeCoverCacheFile`, `PersistentComicPagePreviewStore.kt`, `LocalCoverCache.kt`, `ComicArchiveCodecs.kt` | `writeGrimmoryCacheFile` is a pure alias. |
| `sha256Hex` | 6: `ComicArchiveCodecs.kt` (3×), `PersistentComicPagePreviewStore.kt`, `RemoteItemLocalStateCache.kt`, `GrimmoryCacheHelpers.kt`, `AppUpdateDownloader.kt` | |
| Lenient `Json` | 7 `Json { ignoreUnknownKeys = true }`: `KavitaApi`, `KavitaJournalSyncSupport`, `KavitaSessionRenewal`, `GrimmoryApi`, `StumpApi`, two in `feature/updates` | One instance in `core:network`. |
| Short day label ("3 Nov", with the year if not this year) | 5: `SettingsFormatting.kt`, `LibraryBookPageModel.kt`, `LibraryShelvesText.kt`, `DownloadsSheetModel.kt`, `JournalFormatting.kt` | They mix `Locale.US` and `Locale.getDefault()`; pick one. |
| Same-origin URL helpers | 2, byte-identical: `KavitaApi.kt` ~757–786 and `StumpApi.kt` ~510–539 (`resolveAgainst`, `isSameOriginAs`, `effectivePort`) | Put in `core:network`, with an `HttpResponse.throwIfError(...)` for the three APIs' error mapping. |
| Content-URI archive signature and `Cursor.getLongOrNull` | 2: `ComicArchiveCodecs.kt` ~253 and `LocalComicPageLoader.kt` ~977 | |
| Format checks | `isComicArchive` (`LocalDocumentSupport.kt`, `LibraryCoverPlaceholder.kt`) duplicates `isComicReaderFormat`; Stump's `toComicFormatType` duplicates `toReadableFormatTypeOrNull` | Use the core ones. |

## Step 4. Coroutine and threading hygiene (done, see above)

- **One injected application scope.** Provide an `@ApplicationScope CoroutineScope` from Hilt. It replaces the 16
  classes that create `CoroutineScope(SupervisorJob() + …)` and never cancel it:
  - **In `app`:** `StumpPageSource`, `KavitaPageSource`, `RemoteComicPageSource`, `DownloadedRemoteComicPageSource`,
    `RemoteCatalogRepositories`, `RemoteCoverController`, `JournalRecordingAdapters` (2),
    `DownloadedRemoteCoverCacheBackfillSideEffect`, `LocalDocumentRepository` and `RoomReaderSessionStore`.
  - **The preference stores:** settings, source navigation, library browse, and the four in reader-preferences.
  - **Others:** `ComicsReaderSessionAdapter` and `AppUpdates`.
- **Injected dispatchers.** Use the existing `AppDispatchers` instead of hard-coded `Dispatchers.IO`, which appears
  100 times (38 in `app`, 14 in journal, 11 in downloads). It makes tests deterministic.
- **Lifecycle-aware flows.** Use `collectAsStateWithLifecycle` instead of `collectAsState`; there are 17 uses,
  mostly in reader-comics (`ComicsReaderEntry.kt` ~64, `ComicPagesState.kt`).
- **Disk writes on the caller's thread:**
  - `EncryptedSourceSessionStore` does a synchronous `.commit()` inside a `suspend fun` without
    `withContext(io)`.
  - Check `SettingsPreferencesStore.kt` ~100 and `SourceNavigationPreferencesStore.kt` ~146 too.
- **Cancellation in the download worker.** `RemoteDownloadWorkerRunner.kt` ~105 catches cancellation and does not
  rethrow. That is probably right for WorkManager; say why in a comment.

## Step 5. Large consolidations (one per change, each checked on the emulator)

Each item below is real duplication, verified by diff. They also carry most of the "app is not thin" fix, because
once a generic version exists, each source needs only a small class in its own `data:*` module.

1. **Comic page cache and prefetch (~350 lines × 2).**
   - **Shared code:** `LocalComicPageLoader.kt` and `RemoteComicPageSource.kt` share:
     - the four `LruCache`s
     - `loadPageImageWithPriority`, `semaphoreFor` and `prefetchAround` (the same algorithm; only the cache key
       and the byte fetch differ)
     - the cancel and trim functions
     - the private `InFlightByteRequest`/`PrefetchJob` helpers
   - **Change:** extract a `ComicPageMemoryCache` plus a prefetch scheduler that takes a "fetch bytes" function.
   - **Check:** comic scrolling and memory on the emulator, `LibraryPerf`-style timings if needed.
2. **Archive-backed page sources (~150 lines).** `LocalDocumentRepository.kt` ~131–291 and
   `DownloadedRemoteComicPageSource.kt` ~151–308 map spec → (record, entry) → loader across the same 9 methods.
   Make one `ArchiveComicPageSource(recordFor, entryNameFor)`; `ImportedComicArchivePageSource` becomes
   `by` delegation.
3. **Source connection controllers and UI state (500+ lines).**
   - **Controllers:** `App{Kavita,Stump,Grimmory}RemoteSourceController` repeat the same handlers,
     `observeStoredSession`, `startConnect` and `updateConnection`. Replace them with one generic
     `RemoteSourceConnectionController<Session>`.
   - **Coordinators:** `App*Coordinator` each declare the same three sealed result types.
   - **UI state:** `SettingsScreen.kt` ~215–286 has three connection UI states that differ only in defaults; make
     them one `RemoteSourceConnectionUiState`.
4. **Progress push loop and token refresh (~200 lines).**
   - **Push loop:** `{Stump,Grimmory,Kavita}ProgressSyncSupport` share the dirty → newer-server-copy → push → settle
     loop. Make one generic `pushDirtyProgress`.
   - **Token refresh:** `StumpAuthHelpers.kt` and `GrimmoryAuthHelpers.kt` differ only in type names. Make one
     generic `TokenRefreshResult`.
5. **Cover repositories (~150 lines).** `{Kavita,Stump,Grimmory}CoverRepository` share `cachedCover`,
   `getCoverUri`, `warmCacheIndexes`, `clearCachedCovers` and `toCachedRemoteCover`; the cache file names are
   byte-identical for Kavita and Stump. Make a base `SourceCoverRepository` and one `remoteCacheFileName`.
6. **Credential stores (~55 lines × 3).** Make a base `EncryptedCredentialStore<S>`.
7. **`withActiveBaseUrl = { s, url -> s.copy(activeBaseUrl = url) }` (28 copies in 22 files).** Make it a member
   of `StoredSourceSession`, or a per-source `SourceSessionAccess` that bundles store, factory and runner.
8. **Reader session lifecycle.**
   - **Shared code:** `EpubReaderEntry`, `PdfReaderEntry` and `ComicsReaderEntry` each repeat:
     - the `ReaderActiveSessionTracker`
     - the flush and close lambdas
     - the ON_START/ON_STOP `DisposableEffect` and the back handler
   - **Change:** move it into `feature:reader-core` as `rememberReaderActiveSessionTracker` and
     `ReaderSessionLifecycleEffect`.
9. **Finished-progress journal event (~25 lines × 2).** `KavitaJournalSyncSupport.kt` ~396 and
   `GrimmoryJournalSyncSupport.kt` ~444. Make it a shared builder in `data:repository`.

## Step 6. Move behaviour out of `app`

`app` holds about 31,000 lines in 182 files. `ARCHITECTURE_RULES.md` says it should only wire modules. Do this
after step 5, which shrinks what has to move.

- **The comic engine** (`RemoteComicPageSource`, `LocalComicPageLoader`, `DownloadedRemoteComicPageSource`,
  `ComicArchiveCodecs`, `KavitaPageSource`, `StumpPageSource`, about 3,400 lines) → `feature:reader-comics`, or a
  new `data:comics` if the engine should stay out of UI modules.
- **Per-source code** (the 43 `Kavita*`, `Stump*` and `Grimmory*` files in `app`, about 4,900 lines: catalog sync,
  fetch, covers, progress sync, credentials) → their `data:*` modules or `data:repository`.
- **Screens:**
  - `ListsRoute.kt` (802 lines) → `feature:lists`.
  - `LibraryRoute.kt` and `LibraryTopBar.kt` → `feature:library`.
  - `AppListsModule.kt` hides `AppListsViewModel` and its Want-to-read mirroring rules. Move them to
    `feature:lists`.
- **`AppModule.kt` ~273–317** builds two anonymous `SourceRemoteJournalPusher` adapters with routing logic. Make
  them source-owned classes.
- **Coordinators over the 250-line limit (11):**
  - `AppReaderCoordinator` 528
  - `AppKavitaRemoteSourceController` 456
  - `AppLibraryStateRecomputeCoordinator` 406
  - `AppCoverPreloadController` 363
  - `AppKavitaCoordinator` 356
  - `AppStumpCoordinator` 352
  - `AppStumpRemoteSourceController` 340
  - `AppGrimmoryCoordinator` 317
  - `AppCatalogStateCoordinator` 302
  - `AppGrimmoryRemoteSourceController` 291
  - `AppPendingProgressFlushCoordinator` 281
- **`when (sourceType)` in `app`:** 13 branches in 12 files (`BookDescriptionRepository`, `RemoteCoverController`,
  `ComicPageSourceCacheController`, `RemoteServerAddressFailover`, `AppLibraryStateAssembler`, …) → a per-source
  interface.

**Done when:** `app` holds DI, navigation, the scaffold and thin coordinators, and no app coordinator is over
250 lines.

## Step 7. Security and release hardening

- **Cleartext traffic:** `usesCleartextTraffic="true"` is set for the whole app (`AndroidManifest.xml:13`). Add a
  `network_security_config.xml` that allows plain HTTP only where it is needed (LAN and Tailscale addresses, or
  user-added servers) and keeps GitHub (the updater) HTTPS-only.
- **The Kavita API key in URLs:**
  - It travels in query strings (`KavitaApi.kt` ~302, 614, 641, 687) and is stored in Room cover URLs.
  - Exceptions that log a URL (for example `RemoteCoverController.kt` ~181) can put it in logcat.
  - Redact `apiKey=` in logged messages, or move it to a header where Kavita allows.
- **Logging:** 38 `android.util.Log` calls run in release builds, some per page (`LocalComicPageLoader`,
  `RemoteComicPageSource`). Route them through one small logger that is quiet in release.
- **Release builds are not shrunk:** `isMinifyEnabled = false`. R8 would shrink and harden the APK, but it needs
  keep rules for Readium, kotlinx.serialization and Ktor, and a pass over every reader on the release build. Do it
  last, on its own.
