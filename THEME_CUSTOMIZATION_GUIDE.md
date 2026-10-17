# Theme Customization Guide

A theme is a palette. Every theme shares the same typography, shape scale, spacing, and
components; only colors change. This was decided on 2026-10-17 (see `UI_REDESIGN_PLAN.md`):
the earlier per-theme shapes, fonts, and "personality" treatments (scanlines, halftone,
frames, dashed borders) were removed because they made the app read as mismatched.

## Main Files

- `feature/settings/src/main/java/com/example/unifiedreader/feature/settings/ThemeMode.kt`
  - The selectable themes: label, description, persisted `storageValue`, dark/light flag,
    and the four preview swatches shown in Settings.
- `app/src/main/java/com/example/unifiedreader/ui/theme/UnifiedReaderTheme.kt`
  - One Material 3 `ColorScheme` per theme, and `toSemanticColors()` which maps Material
    roles onto the app's semantic colors.
- `app/src/main/java/com/example/unifiedreader/ui/theme/UnifiedReaderDesignTokens.kt`
  - The single `Typography` and `Shapes` used by all themes, plus the shared palette constants.
- `core/ui-theme/src/main/java/com/example/unifiedreader/ui/theme/UnifiedReaderThemeTokens.kt`
  - `UnifiedReaderSemanticColors` and `UnifiedReaderPresentationTokens` (sizes), read by
    feature code through `UnifiedReaderThemeTokens.colors` and `.presentation`.
- `core/ui-components`
  - The component kit every screen is built from. Components read colors from the theme
    and sizes from the presentation tokens; themes never configure components directly.

## How To Add A Theme

1. Add a `ThemeMode` entry in `ThemeMode.kt`. The `storageValue` is persisted; never rename
   it without a migration. Set `isDark` and the four `previewSwatches` (surface, reader,
   sync, source) to representative colors from the scheme you are about to write.
2. Add a `darkColorScheme(...)` or `lightColorScheme(...)` in `UnifiedReaderTheme.kt` and map
   the new mode to it in `resolveUnifiedReaderTheme()`.

   Role conventions:
   - `primary` is the reader and progress accent.
   - `secondary` is the sync, status, and server accent.
   - `tertiary` is the source, filter, and navigation accent.
   - `*Container` colors back selected chips, badges, and count labels.
   - `background`, `surface`, and `surfaceContainer*` layer the app shell and panels.
3. Run the app and check the library, drawer, settings, a sheet, a dialog, and the reader.

That is the whole job. There is nothing to add to the component kit or to feature code.

## Semantic Colors

`ColorScheme.toSemanticColors()` derives these from the scheme, so a new theme gets them
for free:

- surfaces: `appBackground`, `surfaceBase`, `surfaceRaised`, `surfaceOverlay`,
  `surfaceSelected`, `onSurfaceSelected`
- reader: `readerChrome`, `readerChromeElevated`, `readerScrim`, `readerAccent`,
  `onReaderAccent`, `readerAccentContainer`, `onReaderAccentContainer`
- sync: `syncAccent`, `onSyncAccent`, `syncAccentContainer`, `onSyncAccentContainer`
- source: `sourceAccent`, `onSourceAccent`, `sourceAccentContainer`, `onSourceAccentContainer`

If a component means "reader progress", use reader tokens. If it means "server, sync,
status", use sync tokens. If it means "source, filter, navigation", use source tokens.

## Shape, Type, and Spacing

These are global and live outside the theme:

- Three corner radii: cover 6 dp, control 12 dp (chips, buttons, fields), container 20 dp
  (panels, sheets, bars). `MaterialTheme.shapes` and `UnifiedReaderPresentationTokens`
  both encode this scale.
- One typeface and one `Typography` (a bundled UI font is planned; see the redesign plan).
- Spacing values in `UnifiedReaderDefaultPresentationTokens`.

Change them in one place and every theme follows. Do not reintroduce per-theme overrides.

## Practical Rules

- Keep contrast legible in both the selected and unselected states of chips and rail items;
  those use `surfaceSelected` / `onSurfaceSelected`.
- Keep the preview swatches in sync with the scheme; they are hand-picked, not derived.
- Legacy `Light` and `Dark` modes are hidden from Settings and resolve to Paper and Graphite.
