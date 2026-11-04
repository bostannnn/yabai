# AppDialog

A question or a short form over the screen: `AppDialogCard` up to 380 wide, `radius-sheet` 28, `inset-dialog` 24, on `surface-container-high`; title 24 SemiBold, body 14; pill actions at the end.

- Kit: `AppDialog`, `AppDialogCard`, `AppListDialog` (a searchable list), `AppDialogConfirmButton` / `AppDialogDismissButton`.
- A destructive confirm is `error` / `on-error`.
- The content starts `grid-gap` 16 below the title (or its subtitle); the actions 24 below the content.
- A search field inside a dialog uses `AppSearchFieldDefaults.dialogContainerColor`.
- The Finished screen keeps its own card (640 with the cover beside, see FinishedScreen), with the dialog's corners, padding and window margin from `AppDialogCardDefaults`.
