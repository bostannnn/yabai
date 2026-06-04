# Theme Customization Guide

This app now has a real theme system, not just a few color swaps. A theme can affect:

- Material color schemes
- App-specific semantic colors
- Shape
- Typography
- Density and spacing
- Component accents such as shelves, drawer selection, filter chips, progress bars, badges, top bars, and settings chips

The visual redesign is not "done forever." The current pass gives the app a stronger design foundation and three bold theme directions. There is still room to make themes affect more component behavior, motion, imagery, reader chrome, and source-specific identity.

## Main Files

- `feature/settings/src/main/java/com/example/unifiedreader/feature/settings/ThemeMode.kt`
  - Defines selectable theme modes.
  - Stores labels, descriptions, persistence values, dark/light metadata, and preview swatches.

- `app/src/main/java/com/example/unifiedreader/ui/theme/UnifiedReaderTheme.kt`
  - Defines actual Material 3 color schemes.
  - Resolves `ThemeMode` into colors, typography, shapes, and presentation tokens.
  - Maps Material colors into app semantic colors.

- `app/src/main/java/com/example/unifiedreader/ui/theme/UnifiedReaderDesignTokens.kt`
  - Defines base typography and shapes.
  - Defines theme-specific typography and shapes for bold themes.

- `core/ui-theme/src/main/java/com/example/unifiedreader/ui/theme/UnifiedReaderThemeTokens.kt`
  - Exposes shared semantic color tokens and presentation tokens to feature modules.

## How To Add A New Theme

1. Add a new `ThemeMode` entry.

   Edit `ThemeMode.kt`.

   Use a stable `storageValue`. Do not rename it later unless you also add migration logic, because this value is persisted.

   ```kotlin
   NeonNoir(
       storageValue = "neon_noir",
       label = "Neon Noir",
       description = "Use electric blue, acid pink, and black glass surfaces.",
       previewSwatches = ThemePreviewSwatches(
           surface = 0xFF070A10,
           reader = 0xFF54D7FF,
           sync = 0xFFFF4FB8,
           source = 0xFFE8FF62,
       ),
       isDark = true,
   ),
   ```

2. Add the Material color scheme.

   Edit `UnifiedReaderTheme.kt`.

   Use `darkColorScheme(...)` or `lightColorScheme(...)`.

   Convention:

   - `primary` is the reader/progress accent.
   - `secondary` is the sync/status/server accent.
   - `tertiary` is the source/filter/navigation accent.
   - `primaryContainer`, `secondaryContainer`, and `tertiaryContainer` are used for selected pills, chips, badges, and count labels.
   - `background`, `surface`, and `surfaceContainer*` control most of the app shell and card layering.

   ```kotlin
   private val NeonNoirUnifiedReaderColorScheme = darkColorScheme(
       primary = Color(0xFF54D7FF),
       onPrimary = Color(0xFF001F2A),
       primaryContainer = Color(0xFF003848),
       onPrimaryContainer = Color(0xFFC6F3FF),
       secondary = Color(0xFFFF4FB8),
       onSecondary = Color(0xFF410020),
       secondaryContainer = Color(0xFF6B0038),
       onSecondaryContainer = Color(0xFFFFD8EC),
       tertiary = Color(0xFFE8FF62),
       onTertiary = Color(0xFF272E00),
       tertiaryContainer = Color(0xFF465100),
       onTertiaryContainer = Color(0xFFF3FFAD),
       background = Color(0xFF070A10),
       onBackground = Color(0xFFEAF1FF),
       surface = Color(0xFF0D111A),
       onSurface = Color(0xFFEAF1FF),
       surfaceContainer = Color(0xFF171D29),
       surfaceContainerHigh = Color(0xFF202838),
       surfaceContainerHighest = Color(0xFF2A3347),
       outline = Color(0xFF7F8EA6),
       outlineVariant = Color(0xFF334053),
       scrim = Color(0xFF000000),
   )
   ```

3. Wire the color scheme into theme resolution.

   In `resolveUnifiedReaderTheme(...)`, add:

   ```kotlin
   themeMode == ThemeMode.NeonNoir -> NeonNoirUnifiedReaderColorScheme
   ```

4. Decide whether the theme needs custom typography.

   Edit `UnifiedReaderDesignTokens.kt` if the theme should feel different through type.

   Examples from current themes:

   - `Terminal95` uses monospace.
   - `CrimsonCourt` uses serif headings and labels.
   - `PanelPop` uses heavier weights.

   Then wire it in `ThemeMode.toTypography()` in `UnifiedReaderTheme.kt`.

5. Decide whether the theme needs custom shapes.

   Edit `UnifiedReaderDesignTokens.kt` if corners should change.

   Examples:

   - Terminal themes can use sharp or nearly square corners.
   - Gothic themes can use softer, roomier shapes.
   - Comic themes can use chunky, squared panel shapes.

   Then wire it in `ThemeMode.toShapes()`.

6. Set presentation tokens.

   Edit `ThemeMode.toPresentationTokens()` in `UnifiedReaderTheme.kt`.

   These values control app-specific shape, density, and rhythm:

   - `cardCornerRadius`
   - `coverCornerRadius`
   - `controlCornerRadius`
   - `chipCornerRadius`
   - `shelfCardHorizontalPadding`
   - `shelfCardVerticalPadding`
   - `shelfItemSpacing`
   - `searchHeight`
   - `accentRailWidth`

7. Compile.

   Recommended minimum check:

   ```bash
   ./gradlew :core:ui-theme:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:library:compileDebugKotlin :feature:library-shelves:compileDebugKotlin :feature:navigation:compileDebugKotlin :app:compileDebugKotlin
   ```

8. Check visually on a real device.

   Especially check:

   - Settings theme chips
   - Navigation drawer selected states
   - Library filters and sort controls
   - Continue Reading, Up Next, and Recently Finished shelves
   - Search field active and inactive states
   - Progress bars
   - Downloaded, read, and rating badges
   - Long titles with the theme typography
   - Compact phone width
   - Light/dark status bar icon readability

## What Can Be Changed

### Color Scheme

Change Material colors in `UnifiedReaderTheme.kt`.

High-impact values:

- `primary`, `onPrimary`
- `primaryContainer`, `onPrimaryContainer`
- `secondary`, `onSecondary`
- `secondaryContainer`, `onSecondaryContainer`
- `tertiary`, `onTertiary`
- `tertiaryContainer`, `onTertiaryContainer`
- `background`, `onBackground`
- `surface`, `onSurface`
- `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`
- `outline`, `outlineVariant`
- `scrim`

### Semantic Colors

`ColorScheme.toSemanticColors(...)` maps Material colors into app intent.

Current semantic tokens:

- `appBackground`
- `surfaceBase`
- `surfaceRaised`
- `surfaceOverlay`
- `surfaceSelected`
- `onSurfaceSelected`
- `readerChrome`
- `readerChromeElevated`
- `readerScrim`
- `readerAccent`
- `onReaderAccent`
- `readerAccentContainer`
- `onReaderAccentContainer`
- `syncAccent`
- `onSyncAccent`
- `syncAccentContainer`
- `onSyncAccentContainer`
- `sourceAccent`
- `onSourceAccent`
- `sourceAccentContainer`
- `onSourceAccentContainer`

If a component means "reader progress," use reader tokens. If it means "server/sync/status," use sync tokens. If it means "source/filter/navigation," use source tokens.

### Shape

Change Material shapes in `UnifiedReaderDesignTokens.kt`.

These affect Material components that read `MaterialTheme.shapes`.

Also change presentation tokens when app-specific components need custom dimensions.

### Typography

Change `Typography` in `UnifiedReaderDesignTokens.kt`.

Good theme-specific changes:

- Font family
- Font weight
- Title scale
- Label weight
- Line height

Be careful with:

- Monospace labels in narrow chips
- Serif headings in compact cards
- Extra-bold text in buttons and badges

### Density And Rhythm

Change `UnifiedReaderPresentationTokens` in `UnifiedReaderTheme.kt`.

This is where the theme can feel compact, luxurious, chunky, or sharp without changing layout code everywhere.

### Theme Preview

Change `previewSwatches` in `ThemeMode.kt`.

The preview swatches are currently representative, not derived automatically from the actual `ColorScheme`. Keep them aligned when changing palettes.

## Where Themes Already Show Up

Themes currently affect:

- App color scheme
- Status/navigation bar light/dark icon choice
- Library top bar connectivity pill
- Search field shape, height, cursor, border, and active accent
- Settings theme chips and swatches
- Settings cards and list/detail panes
- Navigation drawer selection
- Navigation settings startup badge
- Library filter chips, toggle chips, alphabet rail, and filter dialog
- Library sort control chip
- Library covers, downloaded badge, and cover radius
- Library progress bars
- Read and rating badges
- Library shelves, shelf headers, count pills, cards, placeholders, and accent rails

## What Still Could Improve

The visual revamp is meaningfully better now, but there are still strong next steps:

- Add screenshot coverage or preview matrices for all themes.
- Make `System Dynamic` preview swatches reflect real runtime dynamic color, or label them as representative.
- Reduce palette duplication between `ThemeMode.previewSwatches` and `UnifiedReaderTheme.kt`.
- Push theme tokens into remaining plain components that still use raw `MaterialTheme.colorScheme` values.
- Add theme-specific motion: sharper instant transitions for Terminal, slower heavier motion for Crimson, snappier motion for Panel Pop.
- Make source identity stronger: Kavita, Stump, Grimmory, and Local could each get source-aware accents inside the broader theme.
- Extend theme personality into empty states, loading states, dialogs, menus, and reader chrome.
- Consider theme-specific icon treatment or small bitmap/texture assets, especially for bold themes.
- Add contrast tests for semantic accent pairs.
- Build real device checks into the release flow, because typography and density issues often only show up on phone screens.

## Practical Rules

- Do not change an existing `storageValue` after release unless you add migration.
- Do not rely only on `MaterialTheme.colorScheme` in feature components when there is a semantic token for the intent.
- Prefer semantic tokens for app meaning and Material colors for generic Material behavior.
- Keep preview swatches in sync with the actual theme.
- Test long labels, dense shelves, and selected chips on narrow screens.
- Color alone will not make a theme feel designed. Pair palette with shape, type, density, and component behavior.
