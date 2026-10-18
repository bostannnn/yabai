# App review, 2026-10-18

Branch `claude/app-review-2af490` at `528347c2`. Seven parallel code reviews (EPUB reader, comics/PDF,
Kavita, Stump/Grimmory, library/DB, downloads/journal/import, settings/shell), then spot checks.

Labels:

- **Repro**: reproduced on the private emulator (`yabai_settings_35`, debug build).
- **Checked**: the code path was re-read and confirmed after the agent reported it.
- **Code**: confirmed by the agent's trace through the code, not run.
- **Plausible**: depends on server behaviour or timing that hasn't been verified.

Baseline: `testDebugUnitTest` passes, 1033 tests, 0 failures.

## Status

Fixed (each with a regression test; full suite 1161 tests, 0 failures):

- #1 EPUB reader crash on activity recreation (`faa5bd4b`). Checked on the emulator.
- #2 Kavita sync dropping other sources' progress (`7651eab8`).
- #3 Grimmory comic progress wiped on sync (`a17c3864`). Field names come from Grimmory's source.
- #4 Kavita EPUB progress in the wrong unit (`a107c095`). The local side was checked on the emulator, but not against a live Kavita server.
- #5 EPUB import crash on cover hrefs with spaces (`1935a96d`). Checked on the emulator.

Next is #6, downloads stopped by the OS. Two things were learned while fixing these:

- **Process death (#12):** after process death the app opens on the library, not the book. Seen on the emulator.
- **"0% · 1/2" label:** the library's progress label and the early "finished" flag have the same cause as #21. Readium reports progression at position starts, and the reader marks a book finished once it reaches the last position's start. A 2-position test book was marked finished at 50%.

## Prioritized fix list

How the list is ordered:

- Crashes and silent data loss come first, then wrong reading position, then broken features you hit often, then rarer issues, then polish.
- Within a tier, items you hit often come first.
- Each item is one fix session. Bugs share an item only when they share a cause or a file region.

**P0: crashes and silent data loss**
1. EPUB reader crashes on rotation, theme change or process death (1.1). Reproduced.
2. Kavita sync marks other sources' pending progress as synced without uploading it (2.1).
3. Grimmory comic progress is wiped on every sync and open, and pushed as 0% (2.2).
4. Kavita EPUB progress is sent in the wrong unit, so books are marked finished or reopen at the wrong place (2.3).
5. Importing an EPUB with a space or `%` in its cover href crashes, and the parsing runs on the main thread (1.2). Reproduced.
6. Downloads stopped by Android are recorded as cancelled, and "Clear completed" deletes their files (2.9).

**P1: progress lands in the wrong place**
7. A stale comic resume anchor overrides newer synced progress and pushes it back (2.4).
8. The EPUB sample-book fallback overwrites real progress (2.7).
9. Changing the EPUB layout mode jumps back to the position the book was opened at (2.6).
10. A downloaded remote EPUB opens before its progress pull, and the pulled progress is ignored (2.8).
11. Pending progress is pushed without checking the server; needs a conflict policy (2.5).
12. After process death, the reader or series route is not restored (2.10).

**P1: broken features hit often**
13. Paged library: covers don't load after a rail jump or a deep scroll.
14. Paged library: the scroll position is lost when returning from the reader.
15. Paged library: "Mark as unread" puts the item at the top of "Last read".
16. Paged library: removed badges stay until you scroll.
17. Paged library: the A–Z rail is never refreshed.
18. Paged library: "Select all" only selects the loaded window.
19. Kavita address failover never triggers on push failures and never returns to the LAN address.
20. Kavita fast sync (the library refresh) never pulls progress.
21. EPUB end of book is not detected: no completion dialog, and the book is not marked finished.
22. The EPUB footnote heuristic hijacks TOC and appendix links; external links do nothing.
23. Comic archives: `__MACOSX` junk entries become pages, and GIF/BMP/AVIF/HEIC pages are dropped.
24. Solid CBRs decode only their first page.
25. Comics are marked complete too early: the top edge of the last page in webtoon mode, or a small swipe on the last page.

**P2: correctness, less frequent**
26. Fixed-layout EPUBs never save progress.
27. Tall comic strips are blurry because of the 4096 px decode cap.
28. Network timeouts: the 5 s socket timeout on unpaginated catalog calls, and the 2-minute Kavita download timeout.
29. Refreshed tokens are lost when the retried call fails, and the refresh locks are separate (all sources).
30. Grimmory downloads vanish after a sync over the other address.
31. Download worker: it doesn't resume in a fresh background process, and expedited work fails on Android 9–11.
32. An EPUB that fails strict validation is imported as a comic.
33. Journal: pages read are inflated, per-day averages are wrong, and "mark unread" wipes history (needs a product decision).
34. Settings reorder has no accessibility actions, and stale bounds remain after hiding a chip.
35. The open-source licenses page is incomplete (legal).

**P3: small fixes and copy**
36. Shell navigation: Back from Settings or Journal resets the hidden Kavita section, and a sync drags you out of Settings.
37. Settings copy and theme: the "opens on launch" and "not opened yet" text is wrong, "System Dynamic" sits under Light, and the window flashes light before the first frame.
38. Comic and PDF reader polish:
    - Fit-width pages open centred instead of at the top.
    - The progress bar tap mapping is off.
    - A failed page has no retry.
    - The RAR cache is wiped on memory trim.
    - The PDF journal flush scope is wrong.
39. Small remote-source fixes:
    - Stump: page count comes from ComicInfo, and `.zip`/`.rar` files are dropped.
    - Grimmory: "Mark as read" writes "100", and "finished" is never cleared.
    - Grimmory: the source shows "Signed in" after a failed refresh, and journal pushes fail for deleted books.
    - Kavita: the `LibraryType` mapping is wrong.
40. Library and download feedback: filter values that differ only by case lose items, and download queue results are never shown.

The unfinished features in section 4 are feature work that needs product decisions, so they don't get fix sessions.

## 1. Crashes

| # | What | Where | Status |
|---|------|-------|--------|
| 1.1 | **Rotating, toggling dark mode, resizing in multi-window, or process death with an EPUB open crashes the app.** The cause: `EpubNavigatorFragment` is added to the activity's `supportFragmentManager`, so Android saves it. On recreate the default factory can't build it because it has no no-arg constructor (`InstantiationException`). The container id also comes from `View.generateViewId()`, so it won't match after a restore. Fix: use Readium's `createDummyFactory()` together with a stable container id, or remove the fragment before state is saved. | `feature/reader-epub/.../EpubReaderEntry.kt:885,910,1091`, `app/.../MainActivity.kt` | **Repro** |
| 1.2 | **Importing an EPUB whose OPF cover href contains a space or a stray `%` kills the app.** The cause: `java.net.URI(packagePath).resolve(href)` throws, and nothing on the import path catches it. The trace also shows this parsing running on the main thread, so a large EPUB can cause an ANR. | `app/.../LocalDocumentArchiveParsing.kt:168`; call path `LocalDocumentImportProbe.kt:37` → `AppViewModel.onDocumentImported` | **Repro** |

## 2. Progress and data loss

| # | What | Where | Status |
|---|------|-------|--------|
| 2.1 | **Kavita sync silently drops other sources' pending progress.** `pushDirtyKavitaProgress` walks every DIRTY/FAILED snapshot in the shared store, but it only receives Kavita items. Any snapshot whose item isn't in that list is marked CLEAN (an unread reset is deleted) without being uploaded. That covers all Stump and Grimmory progress, plus Kavita items outside the batch. Stump and Grimmory skip unknown items instead. | `app/.../KavitaProgressSyncSupport.kt:30-41` | **Checked** |
| 2.2 | **Grimmory comics lose their progress on every sync and every open.** `toReadingProgress` handles only EPUB and PDF; comics get `null`. With `progressStateIncluded`, that becomes `isExplicitUnreadReset = true`, and the sync clears the CLEAN local progress. The push side sends `percent ?: 0.0`, so the server also gets 0%. | `data/grimmory/.../GrimmoryProgressParsing.kt:25-35`, `GrimmoryDtos.kt:341`, `app/.../GrimmoryProgressSyncSupport.kt:112` | **Checked** |
| 2.3 | **Kavita EPUB progress is pushed in the wrong unit.** The app sends the Readium position (about one per 1 KB, often hundreds per book) as Kavita's `pageNum`, but Kavita counts spine files. The value isn't capped. Past the spine count, Kavita marks the book read; the next pull sets it finished and replaces the CFI with a last-page locator. Below that, a pull rescales the position to a percent that is far ahead. Two reviewers found this independently. | `app/.../KavitaProgressSyncSupport.kt:50`, `EpubReaderEntry.kt:652-675`, `data/kavita/.../KavitaDtos.kt:532-549` | Code |
| 2.4 | **A stale comic resume anchor beats newer synced progress, and then overwrites the server.** The reader opens at `resumeAnchorPage` (the local session), not at the progress just pulled. The settle effect then saves that page as DIRTY and pushes it. | `feature/reader-comics/.../ComicsReaderState.kt:323`, `ComicReaderLaunchPolicy.kt:22`, `ComicsPagedReaderSurface.kt:55,72-80` | Code |
| 2.5 | **Pending progress is pushed without checking the server first, for all three sources.** Progress saved offline overwrites newer progress made on another device, and the server stamps it with the current time. Only the full catalog sync compares timestamps. | `app/.../KavitaProgressSyncSupport.kt:44-69`, `GrimmoryProgressSyncService.kt:58-95`, `StumpProgressSyncService.kt:60-98` | Code |
| 2.6 | **Changing the EPUB layout mode (Page, Spread, Scroll or Auto) jumps back to where the book was opened, then saves that spot.** The navigator is rebuilt with the frozen `resolvedInitialLocator`. | `EpubReaderEntry.kt:164-169,914,967,977` | Code |
| 2.7 | **When the document URI is null, the reader quietly opens the generated developer sample EPUB.** Closing it saves the sample's locator as DIRTY over the real progress. The URI is null for a remote item whose source is Checking or Offline, has no session, or whose cache was evicted after a cold-start route restore. | `EpubReaderEntry.kt:445-454`, `SampleEpubPublicationSupport.kt` | Code |
| 2.8 | **For downloaded remote EPUBs, the reader opens first and the progress pull runs afterwards.** Pulled progress is ignored after the first location event, and the first page turn overwrites the server's position. | `app/.../AppReaderCoordinator.kt:182-196`, `EpubReaderEntry.kt:164-169` | Code |
| 2.9 | **Android stopping a download is recorded as a user cancel.** This happens on network loss, the job time limit, or Doze. The entry shows "Canceled after 12/50", and WorkManager's re-run sees Canceled and does nothing. "Clear completed downloads" then calls `remove()` on Canceled entries, which **deletes the files they downloaded**. | `feature/downloads/.../RemoteDownloadWorkerRunner.kt:102-106`, `RemoteDownloadQueueController.kt:188-243` | Code |
| 2.10 | **After process death, the open reader or series view isn't restored, and the saved route is then overwritten.** `withActiveRouteSnapshot` restores only items found in `items`, which is still empty while the catalog loads on IO. Tests pass only because the fake repository is synchronous. A regression from `a0c853b1`. | `app/.../AppInitialUiStateFactory.kt:14-38`, `AppActiveRouteStateBridge.kt:32-55` | Code, timing-dependent |
| 2.11 | **Marking a book unread deletes all its journal events**, so past months' finished counts drop. After FINISHED, no new events are recorded, so a re-read is never logged. | `data/repository/.../ApplyReadStatusChangeUseCase.kt:80-81`, `feature/journal/.../JournalStore.kt:137-143,288` | Code |

## 3. Broken or incorrect features

### Library (paged grids)
- **Covers never load after an A–Z rail jump or a deep scroll** on Title, Author or Series. The prefetcher maps absolute grid
  indices onto `itemSnapshotList.items`, which excludes placeholders. Kavita has no remote fallback URL, so cards stay as
  placeholder art. `feature/library/.../LibraryScreen.kt:152,373-382`, `LibraryVisibleCoverPrefetcher.kt:100-104`. **Checked.**
- **Returning from the reader loses the scroll position.** A new Pager starts with 0 items, the grid clamps the index, and
  the clamped index is then saved. `UnifiedReaderScaffold.kt:280-292`, `LibraryScreen.kt:248-254,303-312`. Code.
- **"Mark as unread" moves the item to the top of "Last read"**, because the SQL takes `MAX(updatedAt)` and includes reset rows. The in-memory
  path excludes them, so the two orders disagree. `core/database/.../RemoteCatalogDao.kt:203-216,249-263`. Code.
- **Removed badges stay until you scroll**, for example the Finished fade after "mark unread" or "downloaded" after deleting a download.
  `LibraryScreen.kt:160-190`, `LibraryWindowBadges.kt:33-42`. Code.
- **"Select all" in paged views selects only the loaded window** (about 180 roots), and picks made earlier are pruned after a rail jump.
  `LibraryScreen.kt:239-247,326-334`. Code.
- **The paged A–Z rail is computed once per sort and never refreshed.** It goes stale after a refresh, and on the first launch after the v28 migration it stays hidden.
  `LibraryScreen.kt:218-221`. Code.
- **Filter values that differ only by case lose items:** the facet list removes duplicates ignoring case, but matching is exact-case. `LibraryFilters.kt:309,426`. Code.

### EPUB reader
- **Fixed-layout EPUBs never save progress.** `PaginationListener.onPageChanged` is called only for reflowable books. `EpubReaderEntry.kt:1030-1037`.
- **End of book is often never detected:** `totalProgression` stays below 0.999 for books under 1000 positions, and in spread mode the last
  position isn't reached. As a result there's no completion dialog, the book isn't marked finished, and a right tap on the last page does nothing. `EpubReaderEntry.kt:677-678,1075`.
- **The footnote heuristic hijacks normal links:** any link with "note" or "fn" in it, or a fragment link into a later file in the last 20% of the book, opens a
  popup instead of navigating. That covers the book's own TOC page and appendix links. `EpubReaderEntry.kt:1379-1453`.
- External web links do nothing (`onExternalLinkActivated = Unit`, `:1028`). The progress bar ignores RTL (`EpubReaderBottomDock.kt:79`).
- "Read next" and "start from beginning" are ignored for EPUB (`EpubReaderFactory.kt:19-27`).

### Comics and PDF
- **`__MACOSX/._*` AppleDouble entries are counted as pages:** the page count doubles, the first pages fail to decode, and indexes no longer match Kavita.
  `app/.../LocalDocumentSupport.kt:285` (checks the extension only). Two reviewers found this.
- **In solid CBRs only the first stored page decodes.** Each page builds a fresh junrar `Archive`, and solid unpacking needs the earlier entries.
  `app/.../ComicArchiveCodecs.kt:147-157`.
- **Webtoon mode marks the book finished and shows the completion modal** as soon as the top edge of the last page appears.
  `ComicsContinuousReaderSurface.kt:179`.
- **Tall strips are downsampled to a 4096 px long edge regardless of width**, so an 800×12000 strip decodes 200 px wide and looks blurry. `ComicPageImageDecoders.kt:9`.
- Fit-width opens tall pages vertically centred, not at the top. Any small horizontal drag on the last page ends the book, including the first finger of a pinch.
  Tapping the progress bar's thumb jumps a page. A failed page has no retry. GIF, BMP, AVIF and HEIC pages are dropped. A memory trim wipes the shared RAR cache.

### Remote sources
- **Kavita address failover never triggers during progress and journal pushes,** because errors are caught per item. Moving from Wi-Fi to mobile
  data marks Kavita Offline and blocks every non-downloaded item. `RemoteServerAddressFailover.kt:25-39`, `KavitaProgressSyncSupport.kt:86-95`.
  Once on the backup address, the app never returns to the LAN address.
- **Kavita fast sync, which the library refresh button runs, never pulls progress:** `pullUnchangedProgressDuringFastSync` and `pullJournalDuringFastSync` are always false.
  `KavitaCatalogSyncService.kt:417,597-598`.
- **The 5 s socket timeout covers unpaginated catalog calls** (Kavita `all-v2` with PageSize 10000, Grimmory `/books`, Stump `getSeries`).
  Large libraries will time out and be marked Offline. `core/network/.../NetworkModule.kt:26-27`. Plausible.
- **Grimmory downloads "disappear" after a sync over the other address.** The file name hashes `coverUrl`, which includes the host, and the old
  file is orphaned. `GrimmoryCacheHelpers.kt:73-80`, `GrimmoryContentRepository.kt:57-60`.
- **Refreshed tokens are dropped when the retried call fails** (Grimmory, Stump, Kavita). If the server rotates refresh tokens, the user has
  to sign in again. Each repository has its own refresh lock. Plausible.
- Stump page count comes from ComicInfo's `pageCount` rather than the real count. Stump drops `.zip` and `.rar`. Grimmory "Mark as read" writes `"100"` as the
  position. Re-reading a finished Grimmory book never clears "finished" on the server. A failed Grimmory refresh still shows "Signed in".
  Kavita `LibraryType` constants are wrong, which is latent because they aren't read anywhere.

### Downloads, import, journal
- Downloads don't resume in a fresh background process: connectivity starts as Unknown, so the worker pauses. `AppModule.kt:368`.
- On Android 9–11, downloads are expedited but the worker has no `getForegroundInfo`, so they never run. Not on your devices. `RemoteDownloadQueueWorkSupport.kt:38`.
- A Kavita download has a 2-minute request timeout, so large files on a slow link never finish. Plausible. `KavitaApi.kt:709-712`.
- An EPUB that fails the strict `mimetype` check is imported as a **comic** made of its illustrations. `LocalDocumentImportProbe.kt:65-81,124-153`.
- Journal "pages read" is inflated: the first event counts from page 0, so opening a book at 38% credits those pages to today, and "Mark as read" credits the whole book.
  `JournalStateComputer.kt:542-590`. Per-day averages divide by the full month even mid-month.
- Download queue results (rejected or already queued) are never shown, because `cd18124f` removed the messages.

### Settings and shell
- "X opens on launch" (drawer order) is false: launch restores the last route. `SettingsHub.kt:106-109`, `DrawerOrderSettingsGroup.kt:58,77`.
- Reordering is now drag-only, with no accessibility actions; `moveSourceUp`/`moveSourceDown` are dead code. After hiding a filter chip, stale bounds break the next drag.
  `core/ui-components/.../AppReorder.kt`.
- The Open-source licenses page lists only Manrope and Material Symbols. It's missing Readium (BSD-3 notice required), junrar (UnRAR licence), the 7 OFL reader
  fonts, and the Apache 2.0 libraries.
- Back from Settings or Journal while a Kavita section is active silently resets the hidden library section; the next Back exits. `UnifiedReaderRoute.kt:145-152`.
- A successful sync forces `topLevel = Library`, which pulls you out of Settings.
- "System Dynamic" is listed under Light. `applyAndroidThemeMode` does nothing on a `FragmentActivity`, so the window flashes light before the first frame.
- "Applies to books you have not opened yet" (reading defaults) is inaccurate.

## 4. Unfinished features

- **Accounts:** none of the sources can sign out or remove a server. The required "Name" field is stored but never shown.
- **Stump:** only CBZ and CBR are imported (no EPUB or PDF). Mark read/unread isn't pushed. There's no journal, rating or review sync.
- **Grimmory:** reading CBX progress isn't implemented (see 2.2). The clear-finished plumbing is never used. `forActiveGrimmoryServer` filters nothing.
- **Kavita:** the startup connectivity probe is dead code (`consumeStartupProbeBudget` is never overridden). API-key login is never used. Reading lists are browse-only and kept only in memory.
- **EPUB:** no bookmarks, highlights or search. No global typography default; every book opens at Serif 1.0×. The sample-book fallback is still live (2.7).
- **Comics:** split wide pages have state, a preference and rendering, but nothing creates them and there's no toggle. A zoomed paged view can't turn pages. Webtoon mode has no pinch zoom.
  Curl is offered on API 28–32, where it falls back to a slide.
- **PDF:** tap and buttons only, with no swipe, RTL, spreads or continuous mode. An encrypted PDF shows a generic error.
- **Formats:** RAR5 CBRs aren't supported (junrar 7.5.5), and they hit the misleading "choose an EPUB, PDF, CBZ, or CBR" dialog. No "Open with"
  intent filter, so files can't be opened from a file manager.
- **Paging:** search, series browse, Kavita scopes, status and location filters, filtered rail sorts, and the Stump, Grimmory and Local sources are still in memory. M3 isn't started. C2 facet routing
  isn't wired. Comic writers never feed the Author filter or sort.
- **Downloads:** no byte-level progress or resume, no storage screen, and no cleanup of files for items removed on the server.
- **Journal:** re-reads aren't tracked, and reading sessions have no idle cap.
- **UI plan:** Compose BOM upgrade (section 1) and cover-derived colour (section 5) are untouched, the settings redesign hasn't been checked on the tablet, and the shelf instrumentation tests haven't been run.

## 5. Checked and fine

- Room migrations 1→28 match the entities: columns, nullability, primary keys, indices and defaults.
- The filtered SQL query binds its values, with no injection.
- AND/OR filter semantics, Date added ordering and the SQL rail buckets agree with the in-memory path.
- The `error("not implemented")` defaults in `KavitaApi` and `GrimmoryApi` are all overridden in production; only fakes use them.
- Downloads use temp file + rename and are stored in `filesDir`.
- `PdfRenderer` usage is thread-safe and closed properly.
- Settings page back and restore works, and every hub row opens a page.
- Theme, filter-panel and drawer-order persistence work.
- Import is still reachable from the + button.
