# AppButton

Pill buttons, 40 tall, `button` text. Pressed, they shrink a little and their corners move toward square.

- Kit: `AppButton` (filled: `primary`), `AppTonalButton` (`surface-container-highest`), `AppTextButton`, `AppIconButton`, `AppTonalIconButton`; colours from `AppButtonDefaults` only (`ButtonDefaults` is forbidden outside the kit by a test).
- At most one filled button per surface: the main action (Continue, Connect, Show 20 books).
- Destructive actions are text in `error`, or a filled `error` button inside a confirm dialog.
- A button's spinner is one size (18), from the kit.
