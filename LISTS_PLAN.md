# Lists: your own lists and reading orders

Live handoff for the lists feature. Mocked on the design canvas
https://claude.ai/artifact/E6rjomsfqMNXPsp8hnRAxA (first pass, then "Second pass: lists that do the work for
you"). Asked for on 2026-10-31: "let's build lists and I'll try on device". The user is fine with Kavita reading lists
and Grimmory shelves staying separate (each holds only its own source's books); only the user's own lists mix sources.

## Version 1 (being built)

- **Lists in the rail and the drawer**, below the sources, above Journal (`TopLevelDestination.Lists`,
  `SourceNavigationDestination.Lists`). No screen title; the bar holds "Search lists" and New list.
- **Lists screen** (`feature:lists`, `ListsScreen`): the reading order you are in the middle of as a card at the
  top (Continue), then sections:
  - **Your lists**: Want to read (built in, always first), Stalled (built in, works itself out: books started and
    not opened for 21 days, every source), then the lists you made. 2×2 cover mosaic tiles.
  - **Kavita reading lists**: the ones the app already loads (read-only in version 1); a tap opens the Kavita
    list in the library, as the Kavita lists chip does.
- **List page**: mosaic, name in `display-title`, description, "14 books · 6 read", Continue, Edit, an Order
  control (Your order, numbered, Read next follows it / Date added / Title) and Hide finished; rows are
  `AppBookRow` with a number. A book that is no longer in the library keeps its row, greyed, from the copy of its
  title and cover saved when it was added.
- **Editing**: name, description, drag to reorder (`AppReorder`) or a row's menu (Move up, Move down, Move to the
  top), remove, Delete list.
- **Add to list**: a pill on the book page (`LibraryItemActionContext.onAddToList`) and a button in the library's
  multi-select bar. One side sheet (`AddToListSheet`): Want to read, your lists with checkboxes (one book) or Add
  (several books), New list. New list asks for a name only; the order starts as Your order.
- **Read next follows the list** when a book was opened from the list page (`AppUiState.readerListContext`):
  the completion picks the list's next unread book, from any source, and Finished says
  "Up next in The Long Eclipse · 7 of 14". Opening a book from anywhere else clears the context.
- **Room v31** (`MIGRATION_30_31`, its own file): `user_lists` and `user_list_entries` (list id, item id,
  position, added time, and a copy of the item's display fields, as the journal tables keep, so a row still shows
  when its book is gone).

## Version 2, first piece: Grimmory shelves (built 2026-10-31)

The user picked this first. Plain shelves only (magic shelves came in the third pass; the "OPDS only" note once here was wrong: Grimmory has REST endpoints for them).

- **Lists screen**: a "Grimmory shelves" section after Kavita reading lists, while Grimmory is connected: your
  shelves and the public ones, by name, tiles like Your lists with "5 books · by title". Search finds them too.
- **A shelf's page** is the list page: caption "Grimmory shelf · on the server" (someone else's public shelf:
  "someone else's, read-only", no Edit, no Order chip), no numbers, Order chip with Date added and Title only (a
  shelf has no order on the server, so the choice is kept on this device; a new shelf starts from the server's
  `sort` field). Never a reading order, so Read next follows the series and opening a book doesn't mark it.
- **Editor** ("Edit shelf"): name, Date added / Title, remove books, Delete shelf ("goes from Grimmory too"); no
  description, no drag or move menu; rows in the order the page shows. Done renames (PUT keeps icon and public)
  and removes on the server.
- **Add to list**: a "Grimmory shelves" section (your own shelves only) when every book being added is a Grimmory
  book; ticking saves on the server first, so the box flips once Grimmory took it.
- **New list**: "On this device" / "On Grimmory" (`AppSegmentedRow`) with a line saying what each means, shown
  while Grimmory is connected (and, from Add to list, every book is a Grimmory book). From the bar a new shelf
  opens its page; from Add to list the books go on it.
- **Refresh**: `GET /api/v1/shelves`, `GET /api/v1/users/me` (only when a public shelf exists, to tell yours from
  others'), and `GET /api/v1/shelves/{id}/books` per shelf, when Lists opens or Add to list opens for Grimmory
  books, at most every five minutes (`ShelfRefreshCooldownMillis`; a failed try waits too). Kept in Room
  (`server_lists`, `server_list_items`, **Room v32**, `MIGRATION_31_32`) so shelves show offline; the order and
  Hide finished chosen here survive refreshes. A refresh failure is silent; a refused or failed change shows an
  error toast ("Grimmory didn't save that: Shelf already exists: Reading club", "Couldn't reach Grimmory, so
  nothing changed.") and nothing changes here.
- The server API was read from the Grimmory source (`ShelfController`, `ShelfService`,
  `BookController.addBookToShelf`, `ShelvesAssignmentRequest`: both sets must be sent; only your own shelves can
  be assigned; update replaces name, icon, iconType and publicShelf, so a rename sends the old icon back).

## Version 2, the rest (built 2026-10-31)

Asked for as "finish what is yet to be done". Each piece is its own commit; Room is now **v33**.

- **In your lists** on the book page (`LibraryBookPageLists`, fed through `LibraryItemActionContext.listsContaining`):
  Want to read, your lists, shelves and Kavita reading lists holding the book (never Stalled or a smart list),
  "Number 6 of 14 · Read next follows this list" in an order; a tap opens the list on Lists.
- **Want to read on Kavita** (`ServerWantToRead`, `KavitaWantToReadAdapter`, `api/want-to-read/add-series` and
  `remove-series`): ticking a Kavita book adds its series; unticking takes the series off once no other book of it
  is left in Want to read. One way only (the app's list drives Kavita's). A failure toasts; the app's list changes.
- **Keep the next 3 downloaded** (`user_lists.keepNextDownloaded`, `ListDownloads.kt`, `KeepListBooksDownloaded`
  in app): a switch on your lists and Want to read; the next three unread server books are queued a second after
  the lists, library or reading change, each once per run (`AppViewModel.onKeepDownloaded`, no toast). Finished
  books are not removed (the canvas left that open; removing downloads stays the user's call).
- **The home card follows the order** (`homeReadingOrder`, `LibraryReadingOrderCard` in the action context,
  `LibraryShelvesZone`): when the book read last was finished and is in the order under way, and the order's next
  book is this library's. "Next in X · 7 of 14", "Then A, B", Read/Continue and Open list. A book in two orders
  follows the one under way (opened most recently with something unread).
- **A list finished** (`FinishedLists.kt`, `finished_lists` table, `ListFinishedOverFinished` in app,
  `JournalListFinishedDialog` and `JournalFinishedListsPanel` in feature:journal): finishing the last unread book of
  a reading order opened from it shows the card over Finished once for the list as it is (same book count); the
  journal's month shows "Lists finished" rows, kept after the list is deleted. Time read sums the journal sessions;
  "started" is the first session (else the list's creation).
- **`.cbl` import** (`CblImport.kt`, `AppCblImport.kt`): New list's "Import a reading order (.cbl)"; matched by
  normalised series name and issue number (any source; a year or volume that agrees breaks ties); an issue not
  found keeps a placeholder row ("Not in your library", id `cbl:<series>#<number>`). Toast: "Imported 4 of 5 …".
- **Kavita reading lists as list pages** (`KavitaListsApi`, `KavitaReadingListClientAdapter`, the shelf store
  generalised: `ServerShelfClient.isOrdered`, `fetchItems`, `move`; `ServerListStores` holds one store per server):
  kept in `server_lists` (kind `ServerReadingList`); a refresh reads the lists only and a list's issues when its
  count changed or its page opens. Numbered, Read next follows one opened from Lists (promoted ones too). Your own:
  rename (the update replaces summary, promoted, dates and tags, so they are read and sent back), reorder (moves
  computed around issues the library doesn't hold, `fullOrder`), remove, delete; Add to list for Kavita issues;
  New list "On Kavita". Ownership: `ownerUserName` vs the session's username (a promoted list without an owner
  name counts as someone else's). The library's own Kavita lists chip still reads the old in-memory controller.
- **Smart lists** (`SmartLists.kt`, `user_lists.smartFilter`, Save as list in `LibraryFilterSheet`,
  `SaveFiltersAsListDialog` in app): the list keeps the library's `LibraryFilterState` (line-per-filter text); its
  books are `applyLibraryFilters` over that library's books and issues (not series) now; Date added / Title; the
  editor renames or deletes; the toast's Open goes to it.
- **Read next after the app is stopped**: `KeepReaderListContext` saves the list context with the screen's saved
  state and puts it back when the same book reopens first after a restart.

## Third pass: fixes, smart lists from rules, magic shelves, collections (built 2026-10-31)

After 0.26.0 the user asked for a design pass ("where are errors, especially in the + menu"), for the
Kavita lists chip to go from the Kavita home, and for "magic shelves, or local lists built from metadata",
then: keep plain and magic shelves "with a filter", option A (collections move to Lists), and "create magic
shelves on device from metadata available on device and save them in Grimmory". Mocked as "Third pass" on
the canvas (boards P3*, notes q1-q3); the user took every proposed answer on the six open choices. Design
system first (`ListsScreen`, `AppListCover`, `AppMenu`, `AppChip`; Design System artifact version 30).

- **Lists screen**: a filter row at the content top (All, Your lists, Smart, then Kavita and Grimmory while
  they have lists; `ListsFilter`, kept in the route's saved state), and one section per place
  (`listsSections`): Your lists (Want to read, Stalled only while it holds a book, yours and your smart
  lists), Kavita (reading lists, then collections), Grimmory (magic shelves, then shelves), each by name.
  Smart shows what updates itself wherever it lives. Tile captions name the kind ("Reading list · 32
  issues", "Collection · 9 series", "Magic shelf · 44 books", "Shelf · 55 books"; "2 of 5 read" for a
  reading order). The under-way card says "Reading order · book 3 of 5".
- **New** is a menu (`AppMenuItem` gained a `supporting` line): List, Smart list, Import a reading order.
  **New list** asks the name first (focused), then "Keep it" rows with their lines always shown
  (`AppSettingsRow` + `AppRoundCheck`; `AppSettingsRow` and `AppSheetLabel` gained `horizontalInset` for
  rows inside a dialog card); the import button left it. Copy says "this device", never "tablet".
- **Add to list**: one label per section, New list last after a divider, no footnote; smart lists, magic
  shelves and collections are not offered.
- **Book page**: Mark finished, Download and Add to list are three equal tonal tiles (icon over label),
  so they never truncate. In your lists now names smart lists and magic shelves too (never Stalled).
- **`AppListCover`**: one to three books show the first cover whole; four or more the mosaic.
- **Rules** (`core:model` `ListRules.kt`): `ListRules` (all/any, rules, groups one level deep),
  `ListRule` (field, operator, values, low/high, time unit), `ListRuleField` (Author, Series, Genre, Tag,
  Mood, Language, Publisher, Pages, Published year, Status, Last read, Added, Format, Grimmory shelf,
  Downloaded and Source on this device only, and Library for lists saved from filters), matching (case,
  width and ё aside, like the library's filters), pills and summaries, a line-text encoding, and
  `LibraryFilterState.toListRules` (Filters › Save as list, and smart lists saved before rules, which
  read back as rules with their source as a rule). No personal rating: the device keeps ratings only in
  the journal, and Grimmory's are not synced.
- **Smart lists on this device** mix every source (`withSmartBooks`, `smartListEntries`), keep their rules
  and How many (10, 25, 50 or every match) in `user_lists.smartFilter` (`SmartListSpec`), and sort by Date
  added, Title or Published (new `UserListOrder.Published`; `UserListEntry.publishedYear` for it).
- **The smart list editor** (`SmartListEditor`, `SmartListRuleSheets`, state `ListsRouteState.smartDraft`):
  name, Keep it (On this device / On Grimmory, set when made), rules with all/any and groups, Sort by and
  How many, the matches beside the form on Expanded (count, nine covers, "and N more"). Add a rule lists
  the fields by section (Downloaded and Source greyed for a magic shelf); a rule's sheet picks how it asks,
  then values with counts from the books (tags under Set in, When, About), numbers, years or a span, and
  says how many books it matches beside Save rule. A list's page shows its rules as pills and Edit rules.
- **Grimmory magic shelves** (`GrimmoryMagicShelvesApi`, `GrimmoryMagicShelfRules` in data:grimmory,
  `GrimmoryMagicShelfClientAdapter` in app): `GET/POST/DELETE /api/magic-shelves` (an update replaces the
  whole shelf, so icon and isPublic go back as read; `filterJson` is a JSON string), books from
  `GET /api/v1/app/books/ids?magicShelfId=N` (Grimmory picks them). Rules convert both ways; a shelf whose
  rules the app can't read still shows, with "change them in Grimmory's web app" and no Edit. Grimmory
  names no owner for a public magic shelf, so every one counts as yours; the server refuses changes to
  someone else's and the toast says so. Kept in `server_lists` (kind `ServerMagicShelf`, id
  `grimmory:magic:N`) with the rules in the new `rules` column (**Room v35**, `MIGRATION_34_35`, after main's v34 for tag sorting).
- **Kavita collections** (`KavitaCollectionsApi` in data:kavita, reached as `KavitaApi.lists`;
  `KavitaCollectionClientAdapter`): `GET api/Collection?ownedOnly=false`, `GET
  api/Series/series-by-collection`, rename (`POST api/Collection/update`, sending summary, cover lock and
  promoted back), remove series (`POST api/Collection/update-series`; removing the last deletes the
  collection on Kavita), delete. Kept as kind `ServerCollection` (`kavita:collection:N`), its items the
  app's series ids, rows that open the series in the Kavita library. Yours are editable; promoted by
  someone else or Kavita+-synced ones are read-only.
- **Stores**: one `RoomServerShelfStore` per server and kind (`ServerShelfClient.kind`,
  `ServerListDao.replaceKind`, `ServerListStores.forList/forKind/forNewList`), so a server's kinds refresh
  without wiping each other.
- **The Kavita lists chip is gone** from the library home with its dialog and `LibraryReadingListScopeState`,
  and so is everything behind it: `AppKavitaReadingListController`, `AppKavitaReadingListState`
  (`AppUiState.kavitaReadingLists`), `KavitaListScopeTop` and feature:library's `LibraryListScopeTop`, the
  collection scope in `LibraryDerivedStateInput`, the picked list kept in the saved route (its old keys are
  cleared on the next save, so an upgrade never lands in a list with no way out), Read next by a picked
  Kavita list, and `KavitaApi`'s `getReadingLists`, `getReadingListItems`, `getCollections` and
  `getCollectionSeries` with their DTOs and the core:model types `ReadingListSummary`, `ReadingListItemRef`,
  `CollectionSummary`, `CollectionSeriesRef`. Lists reads Kavita through `KavitaApi.lists` only.

Status: built in worktree `lists-design-improvements-37bfbc` (branch `claude/lists-design-improvements-37bfbc`,
from main at 0.26.0). Tests: `ListRulesTest` (7), `ListsSectionsTest` (4), `SmartListsTest` (+2),
`RuledServerListStoreTest` (2), the 34→35 migration, `GrimmoryMagicShelvesApiTest` (8),
`GrimmoryMagicShelfRulesTest` (9), `KavitaCollectionsApiTest` (8). Never tried against the real Grimmory
or Kavita. Main's tag sorting (384b3fc8, Room v34) was merged in (de65a77d): the lists migration is now 34 to 35, and
the Tag rule groups tags under Set in, When and About by where you sorted them (unsorted ones are About).
The shared emulator held this branch's earlier debug build at the old numbering (its v34 had the lists
column but not the tag tables); its app data was cleared on 2026-10-31 at the user's go-ahead and the merged
build (v35) installed, so it starts empty: no local books, lists, journal or server sessions.

## Fourth pass: a Lists screen that stays short (built 2026-11-01)

After 0.29.0 the user said Lists "is now working, but [not] very usable": 60+ Kavita lists and 60+ Grimmory lists
always on screen, no way to find your way through them. Mocked as "Fourth pass" on the canvas (boards P4*, notes
r1-r4). The user picked **A** (All as an overview, a server's lists as rows) over B (two panes on the tablet); the
other three choices were taken as proposed (no Smart chip, a row per server on All, Back from a place returns to
All). Design system first: `ListsScreen` rewritten, new `AppListRow` and `AppLetterStrip`, icons `keep`, `keep_off`,
`view_list`, `visibility_off` (Material Symbols Rounded, from google/material-design-icons); Design System artifact
versions 37 and 38.

- **Place row**: All, Your lists, then Kavita and Grimmory; no Smart chip (smart lists and magic shelves become a
  kind inside their place). Counts leave hidden lists out. Back from a place returns to All.
- **All, the overview**: the card, Pinned (every pinned list, captions naming the place), Your lists (one row, See
  all when more), then one sideways row per server (opened lately first, then by name, twelve at most) with See all N
  in the heading, and "11 lists hidden · What Lists shows" under it.
- **A place**: a tool row (kinds as tab pills with counts, Sort: Name, Recently opened, Most books, Year for Kavita;
  Show as rows or covers), Recent (up to six opened lately), then everything not finished in the sort's order; by name
  under letter labels with `AppLetterStrip` past 20 lists; Finished and Hidden folded at the end. Rows by default for
  Kavita and Grimmory, covers for Your lists; each place keeps its kind, sort and Show as.
- **Pin, Hide, Select several**: a long-press menu on a tile or row (or ⋯ on a list's page); the selection bar has
  Select all, Pin and Hide; Hide's toast has Undo. All of it on this device only; nothing changes on a server.
- **What Lists shows** (bar menu, side sheet): Kavita lists other people share, Collections; Grimmory public shelves
  of other people, empty shelves; Finished lists (Fold at the end, Hide, With the rest); the hidden lists with Show.
- **Search** splits into Lists (names) and Books in your lists (each book with the lists holding it).

To build (expected ownership):

- `core:database`: a per-list preferences table (list id, pinned time, hidden, last opened) for every list kind, and
  the reading list's first and last year on `server_lists`: one migration from v36 (check main's version first).
- `data:kavita`: keep `startingYear`/`endingYear` from `api/ReadingList/lists` on `KavitaReadingList` (the DTO already
  has them; the mapping drops them).
- `core:ui-components`: `AppListRow`, `AppLetterStrip`, the four drawables in `AppIcons`. The library's multi-select
  bar lives in `feature:library` (`LibraryDownloadSelection.kt`); a shared selection bar would go into the kit.
- `feature:lists`: `ListsFilter` loses Smart; the overview and place page replace the single grid in `ListsScreen`
  (split into files: it must not become a hotspot); the list menu, selection, What Lists shows, search sections;
  per-place choices in the route's saved state or the preferences table.
- `app`: `AppListsViewModel` hands the preferences store to the composition.

## Ownership

- `core:database`: entities, `UserListDao`, `MIGRATION_30_31`; `ServerListEntity`, `ServerListItemEntity`,
  `ServerListDao`, `MIGRATION_31_32` (lists kept on a server); `FinishedListEntity`, `MIGRATION_32_33`
  (keep downloaded, smart filters, finished lists).
- `data:grimmory`: `GrimmoryShelvesApi.kt` (DTOs, `KtorGrimmoryShelvesApi`), reached as `GrimmoryApi.shelves`.
- `data:kavita`: `KavitaListsApi.kt` (reading lists and Want to read), reached as `KavitaApi.lists`.
- `feature:journal`: `JournalFinishedLists.kt` (the list-finished card and the period's finished lists).
- `feature:library`: In your lists on the book page, the reading card's order variant, Save as list in Filters.
- `feature:lists` (new): models, `UserListStore` (Room), the pure parts (entries resolved against the catalog,
  Stalled, next in list, order), and the screens and sheets. `ServerShelves.kt`: `ServerShelfClient` (what a
  source implements), `RoomServerShelfStore` (Room, cooldown refresh, server-first changes), `toUserList`.
- `core:ui-components`: `AppSectionHeading` (moved out of feature:library, as its design-system page said it
  would when a second screen needed it), `AppBookRow` gains a leading slot for the number, `AppListCover`.
- `app`: `AppListsModule` (DAOs and stores, construction only), `AppListsViewModel` (hands the stores and their
  state to the composition, routes a change to the user's store or the shelf store), `ListsRoute.kt` (wiring), `AddToListHost.kt`,
  `GrimmoryShelfClientAdapter` (the Grimmory session, address failover and token refresh around
  `GrimmoryShelvesApi`), one branch in the scaffold, the reader-context field and the Finished label. No list
  logic in `AppViewModel` beyond delegation.
- `feature:navigation`: the Lists destination in the rail and the drawer.
- `feature:library`: the Add to list pill on the book page and the multi-select button.

## Status

- 2026-10-31: version 1 built in worktree `app-improvement-suggestions-5f047f` (branch
  `claude/app-improvement-suggestions-5f047f`, from main 9838e216), committed `f886b9de` (code and tests) with
  this file and the design system in the commit after it; not merged into main, not released. Design system first:
  `ListsScreen` and `AppListCover` added, `AppBookRow` got its number slot, `AppSectionHeading` moved into the
  kit, Lists in the rail mocks; published to the Design System artifact.
- Full suite passes (1593 tests, both lines). New tests: `UserListDaoTest`, the 30→31 migration,
  `UserListBooksTest` (order, next to read, Stalled, under way, captions), `AppReaderCompletionOrderTest`.
- Checked on the private emulator (`yabai_settings_35`, port 5556, debug build, the Local library), Medium 800 and
  phone 411: Lists in the drawer; Want to read and Stalled; Add to list from a book page, New list from the sheet;
  the list page (numbers, progress lines, finished rows faded with a check); the editor (Move to the top, Cancel
  discards, Done saves); a crossover list Neon Ronin #1, Star Courier #1, Neon Ronin #2: finishing #1 offered
  Star Courier #1 ("Crossover · 2 of 3"), Read next opened it, finishing that offered Neon Ronin #2 and
  "Back to the list" returned to the list page; the reading order card on Lists.
- Not seen: the rail (Expanded), Kavita reading lists under Lists and the multi-select Add to list (no server on
  the emulator; multi-select only selects server books), Stalled with real stalled books, drag to reorder.
- Fixed during the check: the list page closed after a book from it was read (its place now lives in
  `AppListsViewModel`); the Finished label was cut off; the list page's Edit fell off a phone's width and the
  reading order card squeezed its title.
- Known: a book opened from a list and read after process death follows its series (the list context is not
  saved); local books can't be multi-selected yet.
- A signed release build of `f886b9de` (versionCode 25, still named 0.24.0) was handed to the user to install on
  the tablet with `adb install -r`. Installing it moves the tablet to Room v31: the next release must carry
  `MIGRATION_30_31`, so lists have to be merged into main before it.
- 2026-10-31, Grimmory shelves (version 2's first piece), same branch, not committed yet: design system first
  (`ListsScreen` mock: the shelves section, Add to list for a Grimmory book, New list kept on Grimmory; published
  to the Design System artifact, version 18), then the code above. Full suite passes (1613 tests, both lines). New
  tests: `GrimmoryShelvesApiTest` (6), `ServerListDaoTest` (3), the 31→32 migration, `ServerShelfStoreTest` (10:
  cooldown, failed refresh, not connected, local choices kept, server-first changes, a shelf as a list, draft order).
- Checked on the private emulator (5556, debug build installed over v1's, so the 31→32 migration ran with the v1
  lists intact) against a **fake Grimmory server** written from the server's source and run on the Mac (no real
  Grimmory was used: its password isn't ours to type). Seen: the section and captions; a shelf's page, Order
  menu (Date added, Title only) and read-only public shelf; rename (icon kept) and remove with Done; Add to list
  ticking a shelf; New list on Grimmory from Add to list (the book went on it) and from the bar (its page opens
  and stays open); Delete shelf; a refused name and an offline change, each an error toast with nothing changed;
  shelves still shown offline after a restart; phone width (411) for Lists, New list and the bottom sheet.
- Fixed during the check: the editor bar said "Edit list" for a shelf; a shelf's editor listed books in the
  server's order instead of the page's. Also fixed for every list: a list just made from the bar could close at
  once (the page opened before the database handed the list back); the page now waits 500 ms before closing a
  list it can't find.
- Not seen: the real Grimmory server (the first try on the tablet is the first against it), Add to list for a
  multi-select of Grimmory books, the Kobo shelf Grimmory makes for Kobo sync (it shows as a plain shelf).
  Known: shelves refresh only when Lists or Add to list opens (not with the library's Refresh); changes made
  offline are not queued; when Grimmory was first connected the shelf list was asked for twice, likely a 401 and
  token refresh while the new session was being saved (harmless).
- The tablet needs Room v32 now: a release with lists must carry both `MIGRATION_30_31` and `MIGRATION_31_32`.
- 2026-10-31, the rest of version 2 (commits 2b5a75cb..a1be193a on the same branch, not merged, pushed or
  released): design system first (`ListsScreen` and `LibraryHome` guidelines and mocks, published as version 24,
  merged onto another session's Browse/Pages/Search changes there). Full suite passed at 1637 tests after the Kavita
  commit; the module suites passed after the last two. Checked on the emulator (5556, debug, 32→33 migration over the
  earlier build): In your lists and opening a list from it; the Local home card following Crossover; the
  list-finished card (4 books, 2 series, 4 h 36, 15 days) and Lists finished in the journal; Save as list, the
  smart list's tile, page and editor, the toast's Open; `.cbl` import (4 of 5 matched, "003" as 3, a greyed
  placeholder); Keep the next 3 on local-only and Grimmory lists (queued the two Grimmory books, skipped the local).
- Not checked: anything against a real Kavita (reading lists, Want to read on Kavita) or the real Grimmory; the
  Expanded rail; drag to reorder; the restart fix (needs the app killed with a book open). Local books still can't be
  multi-selected in the library (a library selection change, not lists).
- 2026-11-01, after 0.28.0 on the tablet: "I don't see content of kavita collections in new lists menu, but reading
  list is shown". A collection keeps its series as `kavita:series:<library>::<id>`, but Lists looked them up in the
  catalog, which holds only issues, so `toUserList` dropped every series (reading lists hold issue ids, which it
  finds). Fixed with `listItemsById` (`CollectionSeries.kt`): the catalog by id plus each series a collection holds,
  a comic series built as the library's grid builds it (`buildKavitaSeriesContainer`, opens series browse), a book
  series as its first book (the library shows Kavita books one by one; a book series of several books shows only
  its first). `toUserList` keeps the server's id on each entry, so taking a book series off still names the
  series. `CollectionSeriesTest` (3). Kavita's `series-by-collection` was checked in its source: with no page size
  it sends every series. The book page's In your lists still doesn't name collections.
- 2026-11-01, fourth pass designed (see its section): canvas boards P4* and the Design System (ListsScreen,
  AppListRow, AppLetterStrip, four icons) on branch `claude/lists-menu-ux-redesign-4765ae`; main 0.29.1 merged in.
  Nothing built yet.
- 2026-11-01, fourth pass built on the same branch (Room **v37**, `MIGRATION_36_37`). Kit: `AppListRow`,
  `AppLetterStrip` (Cyrillic and other first letters get their own, after Z), icons `Keep`, `KeepOff`, `ViewList`,
  `VisibilityOff`. Database: `list_preferences` (pin time, hidden, page opened), `list_settings` (key and value:
  each place's kind, sort and Show as; What Lists shows), `server_lists.startYear`/`endYear` from Kavita.
  `feature:lists`: `ListPreferences.kt` (store and choices), `ListsBrowse.kt` (pure: `ListsCatalog`, `overview`,
  `placePage`, `search`, row lines), `ListsCells.kt` (the grid as a list of cells with their gaps, so the letter
  strip can jump by index), `ListsScreen.kt`, `ListsParts.kt`, `WhatListsShowsSheet.kt`; `ListsRouteState` holds
  the selection, the sheet flag and the grid's scroll (kept while a list's page is open). App: `AppListsViewModel`
  (preferences, choices, pin, hide, page opened), `ListsTopBar` (⋯ with What Lists shows and Select several; the
  selection bar with Select all, Pin and Hide and an Undo toast; ⋯ on a list's page with Pin and Hide), search's
  books open their book page in the library. The Smart chip is gone (an old saved "smart" opens All).
  Deviations from the mock, put back into the design system (v41): "At the end" for "Fold at the end" (it didn't fit
  its segment), "See all N hidden".
- Tests: `ListsBrowseTest` (9, replaces `ListsSectionsTest`), `ListPreferenceDaoTest` (2), the 36→37 migration,
  `KavitaListsApiTest` (years). Checked on a private emulator copy (`yabai_lists_35`, port 5558, debug build) against
  a stand-in Kavita (scratchpad `fake_kavita.py`: 62 reading lists, 4 collections, empty library) and 30 seeded lists
  of your own: All as an overview (Pinned, Your lists in one row with See all, Kavita's sideways row with See all 66),
  the Kavita place (tabs with counts, Sort, rows, A to Z with letters, the strip jumping to S, two columns in
  landscape), the long-press menu (Edit greyed for a shared list), Pin, Select several and Hide with Undo, the
  Hidden fold, What Lists shows, search (lists highlighted, books with where they are), Back from a place to All,
  the scroll kept and Recent after a list's page. Not checked: Grimmory (no stand-in), covers in rows (the stand-in's
  lists hold no issues), the real Kavita or the tablet, TalkBack on the letter strip.
