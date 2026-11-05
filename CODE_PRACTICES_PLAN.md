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

**Next:** step 1.

## Step 1. Decisions only the user can make

These block or shape later steps. Record each answer here.

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

## Step 2. Guard rails (do before the big refactors)

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

## Step 4. Coroutine and threading hygiene

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
