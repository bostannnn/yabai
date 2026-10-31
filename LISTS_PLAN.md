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

## Version 2 (after the device check)

- Kavita reading lists editable and synced (create, rename, add, move, remove, delete: `api/ReadingList/*`), and
  their items kept in Room instead of memory. Lists other users promoted stay read-only (parse `ownerUserName`).
- Grimmory shelves (`/api/v1/shelves`, `POST /api/v1/books/shelves`), sorted by their own `sort`; no Your order.
- Want to read mirrored to Kavita's Want to read for Kavita series (`api/want-to-read/add-series`).
- The library home's reading card follows the reading order you are in the middle of.
- Keep the next 3 downloaded, per list; smart lists saved from filters; a finished-list card kept by the journal;
  `.cbl` import; "In your lists" on the book page.

## Ownership

- `core:database`: entities, `UserListDao`, `MIGRATION_30_31`.
- `feature:lists` (new): models, `UserListStore` (Room), the pure parts (entries resolved against the catalog,
  Stalled, next in list, order), and the screens and sheets.
- `core:ui-components`: `AppSectionHeading` (moved out of feature:library, as its design-system page said it
  would when a second screen needed it), `AppBookRow` gains a leading slot for the number, `AppListCover`.
- `app`: `AppListsModule` (DAO and store, construction only), `AppListsViewModel` (hands the store and its state
  to the composition), `ListsRoute.kt` (wiring), one branch in the scaffold, the reader-context field and the
  Finished label. No list logic in `AppViewModel` beyond delegation.
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
