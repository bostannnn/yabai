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

### Where it stopped (2026-10-18, end of the third session)

- **Branch:** the fixes after 0.8.0 were made on `claude/app-review-doc-01d75d` and fast-forwarded into the
  local `main` on 2026-10-18. `main` is not pushed to `origin`, and the fixes are in no release yet: the 0.8.0
  release holds #1-#5 only.
- **Done:** every P0 item (#1-#6, #41) and, from P1, #7, #8, #9, #10, #12, #13, #14, #15, #16, #17, #23, #24 and #25.
- **Next, in the suggested order:** the rest of P1: #18, #19 with #42, #21, #22.
- **Decided, not built:** #11 (the most recent read wins; see its entry for what to check first) and #20
  (refresh detects progress from the series list's `PagesRead` and `LatestReadDate`).
- **Not checked on a device:** #6, #7, #8, #41 (unit tests only); #10 (no reachable source on the tablet has an
  EPUB or PDF: the Kavita libraries are all comics and Grimmory was unreachable from the app); #24 with a real
  solid CBR (tested against junrar's solid archive; no RAR tool on the Mac to make one); #4 against a live Kavita
  server. #12 was checked on the emulator with a local EPUB, #23 and #25 with a local CBZ, and #9 with a local EPUB (see their entries). #13 and #14 were checked on the tablet on
  2026-10-18 against the live Kavita server, with a signed release build of `fa324a09` installed over 0.8.0.
- **Seen on the tablet, not fixed:** a download queued while its source is not Online is stored as Queued but
  never scheduled (`canScheduleDownload` requires Online), so the sheet shows "Queued" indefinitely with no
  WorkManager job until a startup or connectivity change runs `ensureScheduled`. Pre-existing; not from these fixes.
- **New finding, not fixed:** CI (`ci.yml`) runs only `testDebugUnitTest`, which skips the plain-Kotlin
  modules (`core:model`, `data:grimmory`, `data:kavita`, `data:stump`): their 122 tests never run in CI.
  Run them with `:core:model:test :data:grimmory:test :data:kavita:test :data:stump:test`.
- **How each fix was done:** a regression test that fails without the fix (checked by switching the fix
  off), the full suite including the JVM `test` tasks, then one commit for the fix and one for this file
  with its hash.

### Fixed

Fixed, each with a regression test that fails without the fix. Full suite with #17: 1233 tests
(1111 `testDebugUnitTest` + 122 JVM `test`), 0 failures.

- #1 EPUB reader crash on activity recreation (`faa5bd4b`). Checked on the emulator.
- #2 Kavita sync dropping other sources' progress (`7651eab8`).
- #3 Grimmory comic progress wiped on sync (`a17c3864`). Field names come from Grimmory's source.
- #4 Kavita EPUB progress in the wrong unit (`a107c095`). The local side was checked on the emulator, but not against a live Kavita server.
- #5 EPUB import crash on cover hrefs with spaces (`1935a96d`). Checked on the emulator.
- #41 Kavita pending flush dropping progress outside its batch (`575028cf`). Only the catalog syncs treat a missing Kavita item as deleted on the server; the flush leaves it for a later push.
- #8 EPUB sample-book fallback (`66753926`). A missing file shows "This book's file isn't available…", and the sample generator lives only in `androidTest`. Also fixed: closing a reader that never showed its book (still opening, or any error screen) re-saved the stored progress as DIRTY with a new timestamp.
- #9 EPUB layout-mode change jumping back to the opening position (`92f5adf8`). The navigator rebuilt for a new layout mode was created with the locator the book was opened at, and its first location event saved that spot as progress. It now starts where the previous navigator stopped (`EpubNavigatorResumePoint`, kept in `EpubNavigatorSurface.kt` because `EpubReaderEntry.kt` is a hotspot). Checked on the emulator with an 8-chapter EPUB: from chapter 3 in Page mode, Scroll kept chapter 3 at 25 %, and Page again opened the page holding the scrolled-to paragraph. The unit test covers the resume point; the Readium navigator itself can't run in unit tests.
- #15 "Mark as unread" moving the item to the top of "Last read" (`b8b1d888`). The paged query, the cold-start root-id query and the filtered query took `MAX(updatedAt)` over every progress row, including the reset row "Mark as unread" writes with the current time; they now skip reset rows, as the in-memory order does, so the item sorts with the unread ones. The shelves input query still counts reset rows, which only adds candidates that the in-memory shelves then drop. Unit tests only (Room, in memory).
- #16 Removed badges staying until you scroll (`28be7b0e`). The paged grid unions its early window badges with the recompute's maps, and the window's copy was resolved again only when the loaded window's size or endpoints changed, so a badge the recompute dropped (the Finished fade after "Mark as unread", Downloaded after deleting the file) stayed until a page loaded or the sort changed. The window badges (now `rememberLibraryWindowBadges` in `LibraryWindowBadges.kt`, out of `LibraryScreen.kt`) are also resolved again whenever one of the recompute's badge maps changes; the resolve is the existing bounded one, off the main thread. Tested with a Robolectric Compose test (activity-compose added to `feature:library`'s test classpath). Not checked on a device: paging is Kavita-only and the emulator has no Kavita server.
- #17 Paged A–Z rail never refreshed (`0e20bf11`). The rail was queried once per sort, so it went stale after a sync, and after the v28 migration it stayed hidden because its one query raced the root rebuild and found no roots. The three alphabet-bucket DAO queries are now Room flows, which re-run on every write to `library_grid_roots`, and the screen collects the current sort's flow; the re-query is the same per-source `GROUP BY`, and roots are written only by syncs and the rebuild. The DAO test checks that the buckets follow root writes (empty, then a, b, then a, b, c); the old one-shot query couldn't be observed at all, so this test has no "fix off" run. Not checked on a device (Kavita-only paging).
- #12 Route lost after process death (`d6829c2c`). The route always stores the open book's item, so the reader reopens before the catalog loads; a series sub-screen is kept until its source's catalog has loaded. Checked on the emulator (see below).
- #6 Downloads stopped by Android recorded as user cancels (`bcfa819b`). A cancellation is a user cancel only when the entry is already Canceling or Canceled; any other stop puts the entry back to Queued with its finished files kept, and the worker returns `Result.retry()`, so WorkManager's rerun resumes after them. "Clear completed" now only clears the list; Remove on a single failed or canceled entry still deletes its files. Entries already wrongly marked Canceled before this fix stay that way (Retry resumes them). Unit tests only.
- #7 Stale comic resume anchor beating synced progress (`5af76586`). The session now stores the progress page with the resume anchor (`resumeProgressPage`), and the anchor, segment and scroll offset are ignored once the saved progress is on another page, as after a pull from another device. Sessions saved before keep the anchor only when it is the progress page itself. A page restored after the reader opened (a pull for a downloaded comic) also moves the anchor, which the pager and the webtoon list follow; before, only the page counter changed and the next settle saved the old page. Unit tests only.
- #10 Downloaded remote EPUB opening before its progress pull (`ef693736`). When the source is reachable, a downloaded EPUB or PDF now opens after its progress pull, bounded at 2 s, and opens anyway on a failure or timeout; both readers restore their position once and ignore a later pull. PDF had the same gap. Downloaded comics still open at once, since they now follow a late pull (#7). Offline opens are unchanged (no pull is attempted). Unit tests only.
- #13 Paged-library covers not loading after a rail jump or a deep scroll (`a2275a42`). The visible-cover prefetcher now looks grid slots up in the paging snapshot list, which keeps unloaded placeholders as nulls at their positions, instead of its compacted `items`, which shifted every slot after a jump or once leading pages were dropped. Checked on the tablet against the live Kavita server: after rail jumps to S and to W every visible cover loaded, and the log showed them being fetched, not served from the cache.
- #14 Paged-library scroll position lost on return (`7ab1f02f`). The ViewModel keeps the pager of the current source, sort and filters (`LibraryGridPagingCache`), so coming back to the same view reuses its loaded pages; asking for another view cancels the old pager, where each request used to add a `cachedIn(viewModelScope)` pager that ran for the ViewModel's lifetime. The screen holds the saved position, without reporting the clamped one, until the grid can reach it: it waits for the first load, reads the last loaded slot to append more pages when placeholders are off (Last read, Date added), then scrolls there; a drag or fling, or a view that shrank below the saved index, ends the wait. The decision is unit-tested (`libraryScrollRestoreStep`). Checked on the tablet on Title (a rail sort): the position survived returning from a series view, from the reader (opened from a series), and from a switch to Grimmory and back. Not checked on Last read or Date added, which page without placeholders.
- #23 `__MACOSX` entries as pages, and GIF/BMP/AVIF/HEIC pages dropped (`2ecfd1d4`). Page entries skip `__MACOSX` folders and dot-files (the `._` AppleDouble copies and `.DS_Store`), as Kavita (`Parser.HasBlacklistedFolderInPath`) and Grimmory (`CbxReaderService`) do, and accept jpg, jpeg, png, webp, gif, avif, bmp, heic and heif. Kavita counts png/jpeg/jpg/webp/gif/avif and Grimmory adds bmp and heic, so page indexes now match both, except that Kavita leaves out a BMP or HEIC page that the app shows. BitmapFactory decodes every format, AVIF only from Android 12, so on Android 9-11 an AVIF page shows the decode error instead of disappearing. The one filter covers local import, local reading and downloaded remote archives. Checked on the emulator with a CBZ zipped as macOS does (`__MACOSX/._page` next to each page, a `.DS_Store`) holding PNG, GIF, BMP and JPEG pages: 6 pages in order, all decoded.
- #25 Comics marked complete too early (`6d0b6f28`). Webtoon mode: the reading position is the page at the top of the viewport, and the last page takes over only once the strip is scrolled to its end (a short last page never reaches the top); before, its top edge coming into view made it the position, so the debounced save marked the book finished and opened the completion dialog. Reaching the end still finishes the book and opens the dialog, as reaching the last page does in paged mode. Follow-up `917ee0db`: reopening restores the scroll with placeholder heights, and as the real images load the end of the strip moves down, so a book reopened at the end reported the second-to-last page and the debounced save took it out of Finished (seen on the emulator: "5 / 6"). A webtoon position is now reported only when a scroll produced it; the end of the strip is the last page at a full offset (1.0) and is the only position that finishes a webtoon, so a tall last page at the top of the screen or a one-page strip no longer finishes it; and a restore of the last page that lands at the end records the end (older sessions, a jump to the last page, a switch from paged mode on it). Paged mode: the swipe past the last spread is decided on release, one finger only, at least 48 dp, mostly horizontal and in the reading direction; before, it fired mid-gesture past the touch slop, so the first finger of a pinch qualified. Checked on the emulator: in webtoon mode with most of the last page on screen the counter stays on the page at the top ("5 / 6") with no dialog, and scrolling to the end shows "6 / 6" and opens the dialog; in paged mode drags of 20-60 px on the last page do nothing and a full swipe opens the dialog. After `917ee0db`, a book read to the end and reopened (force-stop and relaunch) still shows "6 / 6" once its pages load, and stays under Recently Finished. A pinch couldn't be driven through adb. Seen there, not changed: webtoon's deliberate overscroll at the end needs one pointer event to move more than 24 px, so a slow drag past the end does nothing.
- #24 Solid CBRs decoding only their first page (`f841d280`). A file flagged solid continues the stream of the files before it, so the first read of one extracts the whole archive once, in order, through one junrar `Archive` into `cache/comic-archives-solid/`, and every later page reads from there; files not flagged solid (usually the first, often the cover) keep the direct read. The two most recent extractions are kept, and clearing the RAR cache clears them too. junrar reuses its decoder across `extractFile` calls on one `Archive` and resets it only for non-solid headers (checked in the 7.5.5 bytecode). Tested against a real solid archive: junrar's own `rar4-solid.rar` (464 bytes, now in `app/src/test/resources/comic-archives/`), whose later files fail from a fresh `Archive`; every file now reads through `RarComicArchiveCodec`, and the test fails without the fix. The cache logic is also tested with a fake archive. Not checked with a real solid CBR on a device. RAR5 is still unsupported (section 4).

#41, #8 and #12 were done in that order (third session) because restoring a reader at cold start is what
triggers the first two. #41 and #8 have unit tests only.

#12 on the private emulator (`yabai_settings_35`, debug builds), with a local EPUB: open it, Home,
`am kill`, relaunch from the launcher. The old 0.7.0 build reopened on the library, and the saved route
had lost the book. The 0.8.0 build at `aeb722a2` reopened in the book. Back then returned to the
library and cleared the saved book, and the crash log stayed empty. Not checked: a remote book or a
series sub-screen (no server on the emulator); the unit tests cover those.

Next is #18, "Select all" in paged views. Learned while fixing these:

- **Process death (#12):** after process death the app opened on the library, not the book. Seen on the emulator; fixed in `d6829c2c`.
- **Hanging suite:** `SourceNavigationPreferencesStoreTest`'s IO-retry test could wait forever: with a 0 ms retry delay the store's startup fallback was replaced before the test saw it. It hung the suite for 30 minutes during #25; made deterministic in `bfdc4440`.
- **"0% · 1/2" label:** the library's progress label and the early "finished" flag have the same cause as #21. Readium reports progression at position starts, and the reader marks a book finished once it reaches the last position's start. A 2-position test book was marked finished at 50%.

### Verification pass, 2026-10-18 (second session)

Each P0 fix and every P1 item was re-read against the code at `fcddeadb`; the highest-rated items were
re-checked line by line and, where the contract lives outside the repo, against the dependency itself.

- #1: Readium 3.1.2's `createDummyFactory` returns a `SingleFragmentFactory` that intercepts only the
  navigator class, and `EpubNavigatorFragment.onResume` throws `RestorationNotSupportedException` if the
  placeholder is still attached, so removing it right after `super.onCreate` is the required pattern. Sound.
- #2: fixed for Stump and Grimmory. The same loop still marks a dirty Kavita snapshot CLEAN when its item
  is not in the batch it was given, and the pending flush passes a subset. Tracked as #41 (2.12), fixed in `575028cf`.
- #3: checked against Grimmory's public source (github.com/grimmory-tools/grimmory, a Booklore fork):
  `CbxProgress { page, percentage }`, the service maps a CBX `fileProgress.positionData` to `page`, and the
  web reader saves `currentPage + 1` with `(currentPage + 1) / pages`. The app matches exactly.
- #4: the model is right (chapter index as `pageNum`, clamped below the page count unless finished, local
  CFI kept when the remote is the same chapter). Still not checked against a live Kavita server. Accepted
  costs: progress within the same chapter on another device is ignored, and an old save in chapter 0
  without the stored index is cleared once by a remote "unread".
- #5: the new resolver is the only EPUB href resolution on the import path. Sound.
- #6: still open at the time (fixed since in `bcfa819b`). WorkManager's stop (network constraint lost, execution limit, Doze) reaches the
  `CancellationException` branch of `RemoteDownloadWorkerRunner.runEntry`, and `finalizeCanceled` moves a
  Running entry to Canceled; the rerun returns Success without resuming; `clearCompleted` calls `remove()`
  on Canceled entries, which deletes the files of every item the batch had downloaded.
- P1: 17 of 19 items are present as described or worse. 21 is backwards (see its entry), and 11 is a
  design choice that needs a product decision, like 20. Two problems the first pass missed are #41 and #42.

## Prioritized fix list

How the list is ordered:

- Crashes and silent data loss come first, then wrong reading position, then broken features you hit often, then rarer issues, then polish.
- Within a tier, items you hit often come first.
- Each item is one fix session. Bugs share an item only when they share a cause or a file region.

**P0: crashes and silent data loss**
1. EPUB reader crashes on rotation, theme change or process death (1.1). Reproduced. Fixed `faa5bd4b`.
2. Kavita sync marks other sources' pending progress as synced without uploading it (2.1). Fixed `7651eab8`.
3. Grimmory comic progress is wiped on every sync and open, and pushed as 0% (2.2). Fixed `a17c3864`.
4. Kavita EPUB progress is sent in the wrong unit, so books are marked finished or reopen at the wrong place (2.3). Fixed `a107c095`.
5. Importing an EPUB with a space or `%` in its cover href crashes, and the parsing runs on the main thread (1.2). Reproduced. Fixed `1935a96d`.
6. Downloads stopped by Android are recorded as cancelled, and "Clear completed" deletes their files (2.9). Fixed `bcfa819b`.
41. Kavita pending flush marks dirty Kavita progress outside its batch as synced without uploading it (2.12). Numbered out of order to keep the references above stable. Fixed `575028cf`.

**P1: progress lands in the wrong place**
7. A stale comic resume anchor overrides newer synced progress and pushes it back (2.4). Fixed `5af76586`.
8. The EPUB sample-book fallback overwrites real progress (2.7). Fixed `66753926`.
9. Changing the EPUB layout mode jumps back to the position the book was opened at (2.6). Fixed `92f5adf8`.
10. A downloaded remote EPUB opens before its progress pull, and the pulled progress is ignored (2.8). Fixed `ef693736`.
11. Pending progress is pushed without checking the server; last writer wins on push, newest wins on pull (2.5). Decided: the most recent read wins. Before pushing a queued save, fetch the server's progress and keep the server's copy when it is newer than the local save. Kavita's progress carries `lastModifiedUtc`; check what Grimmory and Stump return. Also check that opening and closing a book without moving doesn't re-stamp its progress, or the local copy would look newest.
12. After process death, the reader or series route is not restored (2.10). Fixed `d6829c2c`, after #41 and #8: restoring a reader route at cold start is what triggers both.

**P1: broken features hit often**
13. Paged library: covers don't load after a rail jump or a deep scroll. Fixed `a2275a42`.
14. Paged library: the scroll position is lost when returning from the reader. Fixed `7ab1f02f`.
15. Paged library: "Mark as unread" puts the item at the top of "Last read". Fixed `b8b1d888`.
16. Paged library: removed badges stay until you scroll. Fixed `28be7b0e`.
17. Paged library: the A–Z rail is never refreshed. Fixed `0e20bf11`.
18. Paged library: "Select all" only selects the loaded window.
19. Kavita address failover never triggers on push failures and never returns to the LAN address.
20. Kavita fast sync (the library refresh) never pulls progress. Decided: detect progress made elsewhere through the series list, not one request per chapter. Kavita's `all-v2` series already carries the user's `PagesRead` and `LatestReadDate` (checked in 0.8.9 and `develop`); adding them to the change fingerprint re-fetches only series read since the last sync.
21. EPUB finish detection fires too early (the start of the last Readium position), and a right tap on a reopened finished book does nothing. The first pass had this backwards: the end *is* detected.
22. The EPUB footnote heuristic hijacks TOC and appendix links; external links do nothing.
23. Comic archives: `__MACOSX` junk entries become pages, and GIF/BMP/AVIF/HEIC pages are dropped. Fixed `2ecfd1d4`.
24. Solid CBRs decode only their first page. Fixed `f841d280`.
25. Comics are marked complete too early: the top edge of the last page in webtoon mode, or a small swipe on the last page. Fixed `6d0b6f28`.
42. A Wi-Fi to cellular handoff leaves Kavita marked Offline until a manual refresh: the automatic re-probe only runs after connectivity was reported false, which the network callback never emits while another network is already up. Pairs with #19.

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
    - A cancelled page read shows as "Unable to decode this page": the archive codecs wrap `withContext(IO)` in `runCatching` and swallow `CancellationException` (`ComicArchiveCodecs.kt:73-161`).
39. Small remote-source fixes:
    - Stump: page count comes from ComicInfo, and `.zip`/`.rar` files are dropped.
    - Grimmory: "Mark as read" writes "100", and "finished" is never cleared.
    - Grimmory: the source shows "Signed in" after a failed refresh, and journal pushes fail for deleted books.
    - Kavita: the `LibraryType` mapping is wrong.
40. Library and download feedback: filter values that differ only by case lose items, and download queue results are never shown.

The unfinished features in section 4 are feature work that needs product decisions, so they don't get fix sessions.

Suggested order after the second pass: 6, 41, 7, 10, 13, 14, 24, 25, 23, then the rest of P1. 6, 41, 7, 10, 13, 14, 24, 25, 23, 9, 15, 16, 17, 8 and 12 are done; 18 is next.

Decisions, 2026-10-18: #11 most recent read wins; #20 uses the series read fields; #41, then #8, then #12.

## 1. Crashes

| # | What | Where | Status |
|---|------|-------|--------|
| 1.1 | **Rotating, toggling dark mode, resizing in multi-window, or process death with an EPUB open crashes the app.** The cause: `EpubNavigatorFragment` is added to the activity's `supportFragmentManager`, so Android saves it. On recreate the default factory can't build it because it has no no-arg constructor (`InstantiationException`). The container id also comes from `View.generateViewId()`, so it won't match after a restore. Fix: use Readium's `createDummyFactory()` together with a stable container id, or remove the fragment before state is saved. | `feature/reader-epub/.../EpubReaderEntry.kt:885,910,1091`, `app/.../MainActivity.kt` | Fixed `faa5bd4b` |
| 1.2 | **Importing an EPUB whose OPF cover href contains a space or a stray `%` kills the app.** The cause: `java.net.URI(packagePath).resolve(href)` throws, and nothing on the import path catches it. The trace also shows this parsing running on the main thread, so a large EPUB can cause an ANR. | `app/.../LocalDocumentArchiveParsing.kt:168`; call path `LocalDocumentImportProbe.kt:37` → `AppViewModel.onDocumentImported` | Fixed `1935a96d` |

## 2. Progress and data loss

| # | What | Where | Status |
|---|------|-------|--------|
| 2.1 | **Kavita sync silently drops other sources' pending progress.** `pushDirtyKavitaProgress` walks every DIRTY/FAILED snapshot in the shared store, but it only receives Kavita items. Any snapshot whose item isn't in that list is marked CLEAN (an unread reset is deleted) without being uploaded. That covers all Stump and Grimmory progress, plus Kavita items outside the batch. Stump and Grimmory skip unknown items instead. | `app/.../KavitaProgressSyncSupport.kt:30-41` | Fixed `7651eab8`; residual gap is 2.12 |
| 2.2 | **Grimmory comics lose their progress on every sync and every open.** `toReadingProgress` handles only EPUB and PDF; comics get `null`. With `progressStateIncluded`, that becomes `isExplicitUnreadReset = true`, and the sync clears the CLEAN local progress. The push side sends `percent ?: 0.0`, so the server also gets 0%. | `data/grimmory/.../GrimmoryProgressParsing.kt:25-35`, `GrimmoryDtos.kt:341`, `app/.../GrimmoryProgressSyncSupport.kt:112` | Fixed `a17c3864`; field names and one-based page confirmed against Grimmory's source |
| 2.3 | **Kavita EPUB progress is pushed in the wrong unit.** The app sends the Readium position (about one per 1 KB, often hundreds per book) as Kavita's `pageNum`, but Kavita counts spine files. The value isn't capped. Past the spine count, Kavita marks the book read; the next pull sets it finished and replaces the CFI with a last-page locator. Below that, a pull rescales the position to a percent that is far ahead. Two reviewers found this independently. | `app/.../KavitaProgressSyncSupport.kt:50`, `EpubReaderEntry.kt:652-675`, `data/kavita/.../KavitaDtos.kt:532-549` | Fixed `a107c095`; not checked against a live Kavita server |
| 2.4 | **A stale comic resume anchor beats newer synced progress, and then overwrites the server.** The reader opens at `resumeAnchorPage` (the local session), not at the progress just pulled. The settle effect then saves that page as DIRTY and pushes it. The anchor comes from the session prefs (`ComicsReaderSessionAdapter.kt:246-251`); `ComicReaderRestoreStatePolicy.kt` drops it only when progress has no PAGE position, never on timestamp, and a pull writes progress only. The settle marks `hasUserNavigated`, so the later `restorePageIfUnchanged` is rejected. | `feature/reader-comics/.../ComicsReaderState.kt:323,375-377`, `ComicReaderLaunchPolicy.kt:22`, `ComicsPagedReaderSurface.kt:55,61-81` | Fixed `5af76586` |
| 2.5 | **Pending progress is pushed without checking the server first, for all three sources.** Progress saved offline overwrites newer progress made on another device, and the server stamps it with the current time. Only the full catalog sync compares timestamps. Design limitation rather than a defect: the pull path compares timestamps and will replace a DIRTY local with a newer remote, so which device wins depends on whether the auto-flush or the pull runs first after reconnect. | `app/.../KavitaProgressSyncSupport.kt:44-69`, `GrimmoryProgressSyncService.kt:58-95`, `StumpProgressSyncService.kt:60-98` | Checked; product decision |
| 2.6 | **Changing the EPUB layout mode (Page, Spread, Scroll or Auto) jumps back to where the book was opened, then saves that spot.** The navigator is rebuilt with the frozen `resolvedInitialLocator`: the factory is `remember(publication, preferences.layoutMode)` and the `DisposableEffect` keyed on it recreates the fragment with `initialLocator`; dispose saves the real spot, then the new fragment's first event re-saves the stale one DIRTY. Rotation is unaffected (no `configChanges`, so the activity is recreated). | `EpubReaderEntry.kt:164-169,599-626,929,983-992` | Fixed `92f5adf8` |
| 2.7 | **When the document URI is null, the reader quietly opens the generated developer sample EPUB.** Closing it saves the sample's locator as DIRTY over the real progress. The URI is null for a remote item whose source is Checking or Offline, has no session, or whose cache was evicted after a cold-start route restore. Narrower than it sounds: non-downloaded Kavita and Stump books go through `PrepareRemoteFile` and only open on Ready; Grimmory streams (`RemoteDocumentReadCache` returns null on a connectivity flip, no session, or an empty cached file) and cold-start route restore reach the fallback. | `EpubReaderEntry.kt:445-454,645-678`, `SampleEpubPublicationSupport.kt`, `app/.../AppModule.kt:860-866`, `RemoteDocumentReadCache.kt:34-36,77` | Fixed `66753926` |
| 2.8 | **For downloaded remote EPUBs, the reader opens first and the progress pull runs afterwards.** Pulled progress is ignored after the first location event, and the first page turn overwrites the server's position. The navigator effect is not keyed on the initial locator, so a late pull never moves it even before the lock. | `app/.../AppReaderCoordinator.kt:181-197`, `EpubReaderEntry.kt:157-169,645-647,983-989` | Fixed `ef693736` |
| 2.9 | **Android stopping a download is recorded as a user cancel.** This happens on network loss, the job time limit, or Doze. The entry shows "Canceled after 12/50", and WorkManager's re-run sees Canceled and does nothing. "Clear completed downloads" then calls `remove()` on Canceled entries, which **deletes the files they downloaded**. `ensureScheduled` reschedules only Queued, PausedConnectivity and Running. Fix: in the cancellation branch finalize as Canceled only when the status was Canceling (a user cancel), otherwise leave it for WorkManager's rerun; and stop deleting files from "Clear completed". | `feature/downloads/.../RemoteDownloadWorkerRunner.kt:102-106`, `RemoteDownloadQueueController.kt:188-243,296-300` | Fixed `bcfa819b` |
| 2.10 | **After process death, the open reader or series view isn't restored, and the saved route is then overwritten.** `withActiveRouteSnapshot` restores only items found in `items`, which is still empty while the catalog loads on IO. Tests pass only because the fake repository is synchronous. A regression from `a0c853b1`. `getItems()` is `itemsState.value`, empty until IO hydration; the reader fallback item is saved only for off-catalog items; every state publish re-saves the route. | `app/.../AppInitialUiStateFactory.kt:14-38`, `AppActiveRouteStateBridge.kt:25,32-55`, `RemoteCatalogRepositories.kt:174` | Fixed `d6829c2c` |
| 2.11 | **Marking a book unread deletes all its journal events**, so past months' finished counts drop. After FINISHED, no new events are recorded, so a re-read is never logged. | `data/repository/.../ApplyReadStatusChangeUseCase.kt:80-81`, `feature/journal/.../JournalStore.kt:137-143,288` | Code |
| 2.12 | **Kavita pending flush marks dirty Kavita progress outside its batch as synced.** `pushDirtyKavitaProgress` treats a dirty Kavita snapshot whose item is missing from `items` as a chapter deleted on the server and marks it CLEAN (an unread reset is dropped) without uploading. The catalog sync passes the full catalog, but the pending flush passes only pending items found in the in-memory catalog, and `seedItemsByIds` publishes a bounded per-source subset before hydration. An auto-flush at cold start with a restored Kavita reader route pushes that one book and drops every other book's pending progress. Stump and Grimmory skip unknown items. Fix: apply the deleted-chapter cleanup only from the catalog sync and skip unknown items in the flush. Found independently by two reviewers. | `app/.../KavitaProgressSyncSupport.kt:40-53`, `AppPendingProgressFlushCoordinator.kt:101-106,120-131`, `RemoteCatalogRepositories.kt:252-291` | Fixed `575028cf` |

## 3. Broken or incorrect features

### Library (paged grids)
- **Covers never load after an A–Z rail jump or a deep scroll** on Title, Author or Series. Fixed `a2275a42`. The prefetcher maps absolute grid
  indices onto `itemSnapshotList.items`, which excludes placeholders. Kavita has no remote fallback URL, so cards stay as
  placeholder art. Paging is Kavita-only (`LibraryPagingFeature.kt:23`). Rail sorts use `enablePlaceholders = true, maxSize = 360`, so the compacted snapshot and the absolute grid indices diverge after a jump or once leading pages are dropped, and the card itself never fetches (`RemoteCoverController.kt:75-85`). `feature/library/.../LibraryScreen.kt:152,373-382`, `LibraryVisibleCoverPrefetcher.kt:100-104`, `RemoteCatalogRepositories.kt:314-319`. **Checked.** Fix confirmed on the tablet.
- **Returning from the reader loses the scroll position.** Fixed `7ab1f02f`. A new Pager starts with 0 items, the grid clamps the index, and
  the clamped index is then saved. Broader than described: any re-entry (source or sort switch and back) rebuilds the Pager, and on Last read or Date added any index past the first ~180 is clamped and re-saved even after the first page lands. `UnifiedReaderScaffold.kt:280-292`, `LibraryScreen.kt:232,248-254,303-312`, `AppReaderCoordinator.kt:369-375`. **Checked.** Fix confirmed on the tablet (series, reader and source-switch returns, Title sort).
- **"Mark as unread" moves the item to the top of "Last read"**, because the SQL takes `MAX(updatedAt)` and includes reset rows. The in-memory
  path excludes them, so the two orders disagree. Limited to user-marked items: sync deletes rather than writes reset rows. `core/database/.../RemoteCatalogDao.kt:203-216,249-263`, `AppProgressSummaries.kt:18`. **Checked.** Fixed `b8b1d888`.
- **Removed badges stay until you scroll**, for example the Finished fade after "mark unread" or "downloaded" after deleting a download.
  Worse than described: the overlay is left-biased for removals and keyed on the loaded snapshot, not the viewport, so it stays stale across in-window scrolling until a new page loads or the sort changes.
  `LibraryScreen.kt:160-199`, `LibraryWindowBadges.kt:33-41`. **Checked.** Fixed `28be7b0e`.
- **"Select all" in paged views selects only the loaded window** (about 180 roots), and picks made earlier are pruned after a rail jump.
  Both prune paths (`LibraryScreen.kt:325-333`, `UnifiedReaderScaffold.kt:134-146`) drop earlier picks once `maxSize` evicts pages.
  `LibraryScreen.kt:234-247,320-342`, `UnifiedReaderScaffold.kt:176-178`. **Checked.**
- **The paged A–Z rail is computed once per sort and never refreshed.** It goes stale after a refresh, and on the first launch after the v28 migration it stays hidden.
  The v28 migration deletes `library_grid_roots` and the rebuild in `viewModelScope` races the rail query.
  `LibraryScreen.kt:218-221`, `UnifiedReaderDatabase.kt:920-931`, `AppViewModel.kt:111-113`. **Checked.** Fixed `0e20bf11`.
- **Filter values that differ only by case lose items:** the facet list removes duplicates ignoring case, but matching is exact-case. `LibraryFilters.kt:309,426`. Code.

### EPUB reader
- **Fixed-layout EPUBs never save progress.** `PaginationListener.onPageChanged` is called only for reflowable books. `EpubReaderEntry.kt:1030-1037`.
- **End of book is detected too early, not too late** (corrected in the second pass): `isAtEnd` is `currentProgress >= 0.999 || reportedPage >= bookPageCount`,
  and Readium reports a position at its start, so the book is finished when the last position begins: 50% of a 2-position book, 90% of a 10-position story,
  the last ~1 KB of a novel. Real gaps: the right tap uses only `>= 0.999`, so a book reopened at its end does nothing on tap, and `hasAutoRequestedCompletion`
  is `rememberSaveable`, so the dialog cannot reappear in a session. Whether spread mode reports the last position could not be settled statically.
  `EpubReaderEntry.kt:651-690,1090`. **Checked.**
- **The footnote heuristic hijacks normal links:** any link with "note" or "fn" in it, or a fragment link into a later file in the last 20% of the book, opens a
  popup instead of navigating. That covers the book's own TOC page and appendix links. In-content links only: the app's TOC panel goes through
  `EpubTocNavigationRequest` and is unaffected, and links without a fragment navigate normally. A forced match falls back to the whole target element,
  so a chapter `<section id>` shows the chapter as a popup. `EpubReaderEntry.kt:995-1041,1339-1348,1407-1448`. **Checked.**
- External web links do nothing (`onExternalLinkActivated = Unit`, `:1028`). The progress bar ignores RTL (`EpubReaderBottomDock.kt:79`).
- "Read next" and "start from beginning" are ignored for EPUB (`EpubReaderFactory.kt:19-27`).

### Comics and PDF
- **`__MACOSX/._*` AppleDouble entries are counted as pages:** Fixed `2ecfd1d4`. the page count doubles, the first pages fail to decode, and indexes no longer match Kavita.
  `app/.../LocalDocumentSupport.kt:285` (checks the extension only). Two reviewers found this. The filter is applied at `ComicArchiveCodecs.kt:50`,
  `DownloadedRemoteComicPageSource.kt:357` and `LocalDocumentImportProbe.kt:99`; only `isDirectory` entries are excluded. An all-GIF/BMP archive yields
  zero pages and the generic "Unsupported file" dialog. **Checked.**
- **In solid CBRs only the first stored page decodes.** Fixed `f841d280`. Each page builds a fresh junrar `Archive`, and solid unpacking needs the earlier entries.
  `app/.../ComicArchiveCodecs.kt:147-157`. Confirmed in junrar 7.5.5's bytecode: `Archive.doExtractFile` seeds the unpack window only for non-solid
  headers, so a fresh `Archive` per page yields a CRC failure that `runCatching` turns into "Unable to decode this page". RAR5 throws from the
  constructor and is swallowed to an empty list, so it shows the same generic dialog. **Checked.**
- **Webtoon mode marks the book finished and shows the completion modal** as soon as the top edge of the last page appears. Fixed `6d0b6f28`.
  `ComicsContinuousReaderSurface.kt:173-190`: `currentVisiblePosition` prefers the last page whenever any of it is visible, and the 250 ms debounce then
  requests completion ahead of the deliberate 24 px overscroll trigger. Paged mode: `ComicTerminalSwipeNavigation.kt:18-50` fires mid-gesture once the
  horizontal drag exceeds `touchSlop`, with no release or velocity check, so the first finger of a pinch can qualify. Both persist `isFinished = true`. **Checked.**
- **Tall strips are downsampled to a 4096 px long edge regardless of width**, so an 800×12000 strip decodes 200 px wide and looks blurry. `ComicPageImageDecoders.kt:9`.
- Fit-width opens tall pages vertically centred, not at the top. Any small horizontal drag on the last page ends the book, including the first finger of a pinch.
  Tapping the progress bar's thumb jumps a page. A failed page has no retry. GIF, BMP, AVIF and HEIC pages are dropped. A memory trim wipes the shared RAR cache.

### Remote sources
- **Kavita address failover never triggers during progress and journal pushes,** because errors are caught per item. Moving from Wi-Fi to mobile
  data marks Kavita Offline and blocks every non-downloaded item. `RemoteServerAddressFailover.kt:25-39`, `KavitaProgressSyncSupport.kt:86-95`.
  Once on the backup address, the app never returns to the LAN address. **Checked.** The progress and journal pushes catch per item and rethrow only
  cancellation (and Unauthorized), so the block never throws the retryable error the failover looks for, while the failures still mark Kavita Offline via
  `markFromSyncFailures`. New (#42): the automatic re-probe (`AppViewModel.kt:1002-1022`) requires a prior `false` from `AppNetworkConnectivityMonitor.kt:25-54`,
  which never fires on a Wi-Fi to cellular handoff, so Kavita stays Offline until a manual refresh or retry.
- **Kavita fast sync, which the library refresh button runs, never pulls progress:** `pullUnchangedProgressDuringFastSync` and `pullJournalDuringFastSync` are always false.
  `KavitaCatalogSyncService.kt:417,597-598`. **Checked**, by design: the flags default false, every caller omits them, and `AppViewModelTest` asserts it.
  Opening a book still pulls (`syncProgressBeforeOpen`), so only cards and Continue Reading go stale.
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
- **EPUB:** no bookmarks, highlights or search. No global typography default; every book opens at Serif 1.0×.
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
