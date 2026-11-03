# UI polish: Lists, Journal, Filters, the library

Live handoff for the polish pass the user asked for on 2026-11-01. Each item below says what is wrong, where it
lives in the code today, and the proposed fix. Nothing here is built yet.

How each step runs (as in `UI_REDESIGN_PLAN.md`): a mock first when the look changes, the design system
(`docs/design/system/`) changed before the code, then the code, then the emulator. Spacing comes from `AppLayout`
(`contentTop` 8, `headingGap` 8, `gridGap` 16, `inset` 20, `sectionGap` 24); a literal dp a step removes lowers the
design baseline, never the other way round.

## Order

| # | Step | Size | Needs a mock |
|---|---|---|---|
| 1 | Lists: rows that stand apart | small | no (system rule only) |
| 2 | Filters: breathing room between sections | small | no |
| 3 | Mark as unread, back on every book | small | no |
| 4 | Pages sort: drop the page rail | small | no (suggested A) |
| 5 | Lists: new list and the editors | medium | yes |
| 6 | Filters: Library and the other chip groups | medium | yes |
| 7 | Journal: one grid, even widgets, no holes | large | yes |

Steps 1-3 are fixes against rules the system already has; 4-7 change the look and get a board first.

---

## 1. Lists: rows that stand apart `[~]`

**What is wrong.** In the list viewer (the Lists screen in row view, a place's page, search results), each list
row touches the next one: no gap and no divider.

**Where.** `feature/lists/.../ListsCells.kt:197-215`: `gapBefore` returns `Dp.Hairline` between two `Rows` cells,
and `PanelSegment` (`ListsScreen.kt:336-353`) only rounds and pads the first and last segment, so a run of rows reads
as one block with no separation. On Expanded the two lists in one line sit in `Box(weight(1f))` with no horizontal
gap (`ListsScreen.kt:237-256`). Search results (`ListsCell.Books`) use the same divider-less segments (:258-269).

**Decided (2026-11-01): A.** The options were:

- **A (recommended): one panel, divided rows.** Keep the shared panel, put `AppSettingsRowDivider` between rows
  (as the plain list editor and settings pages do), and a `gridGap` between the two columns on Expanded, each column
  its own panel.
- **B: separate cards.** Each row its own `AppPanel` with `headingGap` between them.

Write the choice into `docs/design/system/components/ListsScreen/README.md` and `AppListRow/README.md` first.

## 2. Filters: breathing room between sections `[~]`

**What is wrong.** Some sections of the Filters panel sit almost on the divider or on each other.

**Where.** `feature/library/.../LibraryFilterSheet.kt` and `LibraryFilterSections.kt`:

- **TopChips** (Tag, Mood, Genre; `LibraryFilterSections.kt:175-206`) have no bottom padding: without the "All N"
  button the chips sit straight on the divider.
- **Places** (Set in) and **Piles** (When) (`LibraryFilterSections.kt:100-172`): no bottom padding either.
- **Pages slider** (`LibraryPagesFilterSection.kt:44-115`): histogram, slider and scale are `spacedBy(4.dp)`, then
  an 8 dp spacer before the From/To fields; it reads cramped.
- **Chips** (Reading status, Format, Location) do get a 14 dp bottom (`SectionBottomPadding`, :520), so sections
  end unevenly.
- Literal dp throughout: `ChipGap` 8, 14, 12, 6, 4.

**Fix.** One rule for every section, written into `components/LibraryFilters/README.md`: the label (`AppSheetLabel`),
the body, then the same space before the divider for every style. Likely a kit `AppSheetSection` (label + body +
bottom space) so no section can forget it. Give the slider stack `headingGap` between its parts. Lower the design
baselines afterwards.

## 3. Mark as unread, back on every book `[~]`

**What is wrong.** The user can't find how to mark a book unread. It was not fully removed, but it is hard to find
and missing in places.

**What exists.** a56b14c0 (2026-10-23, "Add a book page for every book in the library") deleted the item menu that
held "Mark as read" and "Mark as unread". Today the only control is one button on the book page
(`LibraryBookPage.kt:416-421`, logic in `LibraryBookPageModel.kt:156-165`):

- A finished book shows **Mark unread**; anything else shows **Mark finished**.
- A **half-read** book has no way back to unread: you must Mark finished, then Mark unread.
- **Local and Stump** books get no read-status button at all (`supportsManualReadStatusAction()`,
  `core/model/.../DomainModels.kt:148-155`).
- No long-press, multi-select or reader-menu action.

**How it works today.** `ApplyReadStatusChangeUseCase` (`data/repository/.../ApplyReadStatusChangeUseCase.kt`)
saves 0 % with `isExplicitUnreadReset = true` and `SyncState.DIRTY`, clears the local reader position, and the next
sync uploads the reset. Stump's upload already handles the flag (`StumpProgressSyncSupport.kt:106`); only the
`supportsManualReadStatusAction()` gate hides the button there.

**Decided (2026-11-01): Mark unread resets progress on the server too**, on Kavita, Grimmory and Stump.

**Fix.**

- The book page shows **Mark unread** whenever the book has progress (half-read included) next to Mark finished, so
  both are one tap.
- Local and Stump books get it too (lift the gate; local books just clear progress).
- Add Mark finished / Mark unread to the library's multi-select bar, so a pile can be reset at once.
- Check on the emulator against mock servers that a **half-read** book's reset reaches Kavita and Grimmory (the
  path was built for finished → unread) and that the next refresh does not pull the old position back.
- **Journal: keep the reading days (decided 2026-11-01).** Marking unread today deletes the book's reading days
  (`journalRepository.clearReadingEventsForItem`, :82). Change it to take back only the finish, as the
  finished-status take-back does (`finished-status-take-back`), and update `ApplyReadStatusChangeUseCaseTest`.

## 4. Pages sort: drop the page rail `[~]`

**What is wrong.** Sorting by Pages shows the letter rail on the right, filled with page numbers (0, 10, 20 ... 1k,
?). It looks bad.

**Where.** `AlphabetNavigationRail` (`feature/library/.../LibraryNavigationControls.kt:46-150`), a feature-local
rail, not the kit's `AppLetterStrip` (which Lists uses). Turned on per sort by `usesAlphabetRail`
(`LibrarySupport.kt:195-204`: Author, Title, Series, Pages). The page stops are `LibraryPageStops`
(`core/model/.../LibraryPageFilter.kt:30`), labelled by `libraryPageSection` (`LibraryPageSort.kt`). Every section
takes `weight(1f)`, so a few stops are stretched over the whole height; the rail is 28 dp on a raised background and
pushes the grid in by 44 dp.

**Decided (2026-11-01): A, no rail for Pages.** The options were:

- **A (simplest): no rail for Pages.** Drop `PAGES` from `usesAlphabetRail`, as Date added and Last read already
  have none. The cover line already says "212 pages".
- **B: section headings instead.** Group the grid under quiet headings ("Under 50 pages", "50-100", "100-200", ...
  "1,000+", "Unknown length"), no rail.
- **C: a plain scroll thumb** that shows the page count of the cover under it while dragging.

While here: move the library's rail onto the kit `AppLetterStrip` so Lists and the library share one look, and give
it a guideline (there is none for `AlphabetNavigationRail`).

## 5. Lists: new list and the editors `[~]` (lists from the library built)

**What is wrong.** Creating a list does not look finished.

**Where.**

- **New list** dialog (`NewListDialog.kt:39-100`): `AppDialogCard` with the name, then "Keep it" rows with
  `horizontalInset = 0.dp`, no spacing in the Keep-it column.
- **Smart list editor** (`SmartListEditor.kt:116-517`): the busiest screen. Nested panels for rule groups
  (`RuleGroupBlock`, half-inset padding), a cramped "Add rule / Add a group" row (`spacedBy(4.dp)`, half inset), 18 dp
  icons with 6 dp spacers, a 16 dp chevron, a mis-indented block at :453, a Matches preview grid with 8 dp gaps and
  padding boxes; on Expanded a fixed 320 dp preview column.
- **Plain list editor** (`UserListEditor.kt:64-214`): name, description, order, then the books; closest to done.
- Rule sheets (`SmartListRuleSheets.kt`): mostly tokens already.

**Fix.** A board with the new-list flow end to end (new list → plain or smart → rules → preview → save), on phone
and Expanded. Things to settle on it: one panel per part with `AppGroupLabel` above (Name, Keep it, Rules, Sort,
Matches), rule groups as indented rows rather than a panel inside a panel, "Add rule" as a full-width row at the end of
the rules panel, the preview grid using the library's cover sizes and `gridGap`. Then the system
(`components/ListsScreen/`, a new editor page if needed), then the code with no literal dp left.

**Board (2026-11-02):** https://claude.ai/artifact/3zhJZtXwTpvNMEYLj9cBUy ("Making a list"). Seen on the tablet first
(0.34.0, landscape): New list dialog with the title on the field and edge-to-edge Keep it rows; the smart list editor
with the form at ~60 % and a 320 dp preview of small covers, a jargon "all of these ↓" chip, small Add rule / Add a group
links and a rule row that only offers ×. The user has no list of their own yet (only Want to read and Stalled).

- **A, sections like Settings:** two panes on Expanded (form beside Matches, equal halves). Name; Keep it as radio
  rows; Rules as one panel: "Books that match" with All / Any segments, one row per rule (field icon, plain words,
  "· 8,234 books" for that rule alone), a group as indented rows under "And one of these", Add rule and Add a group as
  full rows; Order (Sort by, How many). Matches: a cover grid that fills its pane (`auto-fill`, 104 minimum), "and N
  more"; no rules → a line saying so; nothing matches → which rule empties it ("Without Published before 1800, 1,284
  would match"). Phone: one column, Matches as a strip under Rules, the count in the Rules label.
- **B, rules as a sentence:** the same page, the rules panel reads "Books whose [genre] is [Fantasy] or [Horror] /
  [and] [pages] under [400 pages]", every word a chip to tap; "+ and…" and "+ any one of…" add.
- **C, one New list page:** no dialog. Name, Description, "Its books: You pick them / Rules pick them", Keep it (its
  lines follow the choice), then Order and an empty Books pane (pick) or Rules and Matches (rules). The + menu would
  hold New list and Import a reading order.
- A and B share the dialog (radio tiles, the picked one in `primary-container`) and the list editor (form beside a
  numbered Books panel with dividers, Delete list as a row).
- Data rows on the board: many rules + a group + 3 matches, first open with no server (no Keep it), rules matching
  nothing, a list with no books. Every count, rule and book comes from the component's data, not the tablet's.

**Built (2026-11-02): A.** The user picked A. Design system first (`ListsScreen` smart list editor, New list and a new
"Editing a list" bullet; `AppDialog`; `AppSettingsGroup`), republished to the Design System artifact with the 0.34
changes it lacked.

- Kit: `AppDialogCard`/`AppDialog` content starts `gridGap` below the title (the New list title sat on its field;
  `AppDialog`'s own 16 and the updater's paddings moved accordingly); `AppGroupLabel(count)`.
- `NewListDialog`: Keep it as tiles (`AppRoundCheck` first, the picked one in `surfaceSelected`, 12 corners, 4 apart).
- `SmartListEditor` + new `SmartListEditorParts.kt`: Keep it as radio rows in a panel; the rules panel (All of these /
  Any of these segments once there are two, rule rows with the field's icon, `phrase()` and that rule's own count, a
  group as "And this group" with All/Any and indented rules, Add rule / Add a group as rows); Order label; Matches as
  an equal pane on Expanded (grid of at least 104 wide covers, three rows, "and N more") or under the rules as a strip
  with the count in the Rules label. No rules / nothing matches say so; `smartListEmptiedBy` names the rule that empties
  an all-of list. Per-rule counts and the culprit run on `Dispatchers.Default` (`smartListRuleCounts`).
- `UserListEditor`: form (Name, Description, Order, Delete) beside Books on Expanded; an empty list says how to add books.
- Checked on the emulator (landscape and portrait): empty editor, a rule, two rules with nothing matching (the hint
  named the rule), the dialog and the list editor. Not committed, not on the tablet.

**Rows, second look (2026-11-03):** after 0.35 the user still found list rows and a list's book rows "sticked to
each other": the 0.34 dividers were not enough. Board https://claude.ai/artifact/KnqyXEgdWUado71jqjPGim ("List rows
with room"): A each row its own card (12 corners, 8 apart, two columns of books on wide screens), B open rows with
bigger covers and no panel, C a list's books as numbered covers. Waiting for the pick.
The user then asked why lists need their own UI at all ("Home page + filter is exactly that"): reuse the library.
Board https://claude.ai/artifact/QNHx6zjPifQUwG9h59PayS ("Lists from the library"): a smart list is saved Filters
(Save as list, then a name), a list's page is the library grid (numbered covers for lists you pick), editing a smart
list is the grid with Filters and Save, editing your own list is select covers, then Remove or Move to the top. Drops the
smart list editor, the rule sheets, the list page's rows and the list editor page; costs cross-source smart lists,
"none of", rule groups and time rules unless Filters gains them. Waiting for the user's call.
2026-11-03: the user asked for the complete picture; the same board now holds every screen (23 boards): Lists and its
places as covers (no rows); + menu (Smart list, List, Import); new smart list from Lists (pick a library, then that
library with Filters, Save as list, name) or from any library's Filters; the smart list page (grid, rule labels, Read,
Change filters, Filters chip); its ••• (Change filters, Rename, Keep next 3 downloaded, Pin, Hide, Delete); Change filters
= the same panel with Discard / Save changes; Rename; New list (name + Keep it); an empty list's Add books; the library
in adding mode ("Adding to …", Done); the numbered list; its •••; select covers (Move to the top, Remove); library
selection Add to list; the Add to list sheet; phone versions.

**Built (2026-11-03): lists from the library.** The user said "Yes, build it". Design system first (`ListsScreen`),
republished to the Design System artifact.

- Lists and its places show covers only (Show as is gone). New is Smart list / List / Import.
- **Smart list** from Lists: pick a library (`SmartListSourceDialog`), that library opens with Filters titled "New
  smart list" and Cancel / Save as list, then the name dialog; the new list opens. Filters › Save as list in any
  library still works. The picks are stored beside the rules (`filter.` lines in `smartFilter`, read by
  `decodeSmartListSpec`; an older app still reads the rules). **Change filters** opens the same panel titled with the
  list's name, Discard / Save changes. While a list's mode is on, the library's own filters are put aside and never
  saved to disk (`persist = false`); they come back when it ends. Older smart lists and magic shelves still open the
  rule editor.
- **A list's page** is a cover grid (`UserListPage`, the library's cover size): numbers in Your order, a check on
  finished books, "Next ·" on the next one, Read/Continue and Change filters or Add books. ••• holds Change filters, Add
  books, Select books, Rename, Pin, Hide, Delete (`RenameListDialog`, `DeleteListDialog`).
- **Add books** opens the library in picking mode ("Adding to “X”", N picked, Done; `selectAnyItem`); Close or leaving
  the library goes back to the list. Long-press a cover on the list (or Select books) → Move to the top / Remove.
- Fixed on the way: Change filters opened on an unfiltered grid (the filters were set right after opening the
  library, and the open's retained presentation won); they now go in before the library opens.
- Checked on the emulator (portrait, landscape with docked Filters, a phone-size screen): new smart list, Change
  filters → Save changes and Discard, Add books → Done, Move to the top, Remove, Rename, Delete, New › List. All unit
  tests pass. Not committed, not on the tablet.
- **Not built yet:** the Filters chip on a list's page (narrow what shows) from the board; Kavita/Stump series can't
  be picked in Add books; Save as list always makes a list on this device (no magic shelf from Filters).

## 6. Filters: Library and the other chip groups `[ ]`

**What is wrong.** Some filter chips need a redesign: Library first, maybe others.

**Where.** Sections map to styles in `LibraryFilterSheetModel.kt:30-46`:

- **Library** (with Author, Series, Publisher, Language) is a **Row** that opens a facet page
  (`LibraryFilterFacetPage.kt`) of `TextFacetRow`s. For a handful of libraries that is a detour.
- **Location** (on this device / on the server) is a **Chips** group; it reads like a source picker too, next to
  Library.
- The applied-filter chips above the grid (`LibraryFilterRow.kt:49-210`) are `AppChip`s in a row.

**To settle on the board.**

- Library as inline chips with a count ("Fantasy 214"), when there are few (say up to 8), the Row and facet page
  past that. Same rule for Language.
- Whether Library and Location become one "Where" section.
- A look at every chip group together (Reading status, Format, Tag/Mood/Genre, applied chips) for one chip style:
  selected state, counts, the "All N" button.

Ask the user which other chips bother them before the board.

## 7. Journal: one grid, even widgets, no holes `[ ]`

**What is wrong.** The widgets have very different sizes and the layout leaves holes.

**Where.** `feature/journal/.../JournalStatsContent.kt:122-285`:

- From 880 dp the page splits into **two independent columns** (weights 7 and 5) with nothing tying their heights,
  so the shorter one leaves a hole.
- **Month**: start column What you read + Ratings, end column Reading days + Pages + When you read.
- **Year**: start column holds only the year calendar, end column Time by month + Pages + Ratings: the biggest
  mismatch.
- Panels vanish when empty (Ratings, Pages, When you read, What you read), so a column can shrink to almost nothing.
- Inside: the month calendar caps cells at 56 dp and can be narrower than its panel; charts are 72, 96 or 116 dp
  tall; the summary panel's cells use 16/12 padding on phones and 20/2 on wider screens; literal 16, 18, 20, 28 dp.

**Fix.** A board first (phone, tablet portrait, tablet landscape; a full month, a thin month, a year):

- Rows instead of columns: widgets placed in rows of 1, 2 or 3 equal-height cells (12-column grid; each widget has
  a fixed span: calendar 6, pages 6, when you read 4, ratings 4, time by month 4 ...), so every row ends flush.
- A few standard widget heights (small chart, large chart, list) rather than each picking its own.
- Empty widgets either keep their place with a short empty line or the row re-flows; never a hole.
- Charts fill their panel width; the calendar centres in its panel.
- Tokens for every gap (`gridGap` between widgets, `inset` inside).

Then `components/JournalScreen/README.md` and the code. Keep everything the journal shows today: say plainly on the
board if a widget is dropped or merged (see `journal-readability-board`: the user missed removed data last time).

---

## Decisions

- **Mark unread resets progress on the server too** (the user, 2026-11-01).

- **Marking unread keeps the book's reading days** in the Journal and only takes back the finish (the user,
  2026-11-01).

Suggested by Claude and agreed by the user (2026-11-01):

- **Lists rows: A, one panel with dividers.** It is how settings, the plain list editor and the book rows already
  look, so Lists stops being the odd one out; separate cards would add a fourth row style. On Expanded, each of the
  two columns is its own panel with `gridGap` between them.
- **Pages sort: A, no rail.** Date added and Last read already go without one, every cover already says its page
  count, and the page stops are not something you jump to. Headings (B) can follow later if scrolling a long
  library by length feels lost; a scroll thumb (C) is a new component for one sort. While here, move the library's
  A-Z rail onto `AppLetterStrip`.
- **Filter chips: Library as inline chips with counts** when there are up to 8, a row and its page past that; same
  rule for Language. Keep Location as its own section (it is about where the file is, not which library). Then one
  chip style across all groups on the board, and the user points at anything else that still looks wrong.

## Open questions

None right now.

## Status

2026-11-01: plan written from the user's notes and a pass over the code; all open questions decided.

Steps 1-4 built 2026-11-01 in worktree `ui-polish-plan-195c1e` (branch `claude/ui-polish-plan-195c1e`), design system
first (`AppListRow`, `AppSideSheet`, `LibraryFilters`, `LibraryHome`, `ListsScreen` guidelines):

- **1.** Kit `AppListRowDivider` (starts under the text, past the cover). `PanelSegment` in `ListsScreen.kt` draws one
  panel per column, `gridGap` apart on Expanded, with a divider above every row but the first; `ListsCell.Rows` and
  `Books` carry `nextCount` so a column the last line leaves empty rounds off a line early.
- **2.** Kit `AppSheetSectionEnd` (`gridGap`) ends every chips/rows/slider section in Filters; the chip gap and the
  slider's stack use `headingGap`, From/To `gridGap`.
- **3.** The book page shows Mark unread under the progress bar of a half-read book; local books get Mark finished and
  Mark unread (`supportsManualReadStatusAction`); the library's selection bar has ••• with Mark finished / Mark
  unread for the picked books (`AppViewModel.onSetReadStatus(items, ...)`, one toast). The journal keeps the reading
  days: Mark unread now takes back only a finish from the last 24 hours (`JournalDao.deleteFinishedReadingEventsForItemSince`)
  instead of deleting every event. **Stump stays off**: its gate says user-triggered unread/complete calls are
  unreliable, and there is no Stump server to check against; re-enable once checked.
- **4.** Pages is out of `usesAlphabetRail`; the page-range section helpers are gone (the paged path never had them).

Checked on the emulator (debug build, 2026-11-02): Lists rows divided, two panels in landscape (a seam where one
column ended its panel was fixed with `IntrinsicSize.Min`), Filters sections even, Pages sort with no rail, Mark
unread under a half-read book's bar, the selection bar's ••• menu. Mark unread itself was not tapped (no test server
to receive the reset). All unit tests pass (1,669). Not committed, not on the tablet, not released.
Next: the boards for 5-7.
