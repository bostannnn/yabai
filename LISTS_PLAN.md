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

The user picked this first. Plain shelves only (magic shelves come only over OPDS).

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

## Version 2, still to do

- Kavita reading lists editable and synced (create, rename, add, move, remove, delete: `api/ReadingList/*`), and
  their items kept in Room instead of memory. Lists other users promoted stay read-only (parse `ownerUserName`).
  `server_lists` / `server_list_items` were made general for this (a `kind`, positions).
- Want to read mirrored to Kavita's Want to read for Kavita series (`api/want-to-read/add-series`).
- The library home's reading card follows the reading order you are in the middle of.
- Keep the next 3 downloaded, per list; smart lists saved from filters; a finished-list card kept by the journal;
  `.cbl` import; "In your lists" on the book page.

## Ownership

- `core:database`: entities, `UserListDao`, `MIGRATION_30_31`; `ServerListEntity`, `ServerListItemEntity`,
  `ServerListDao`, `MIGRATION_31_32` (lists kept on a server).
- `data:grimmory`: `GrimmoryShelvesApi.kt` (DTOs, `KtorGrimmoryShelvesApi`), reached as `GrimmoryApi.shelves`.
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
