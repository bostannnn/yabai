yabai is a reader for books and comics from Kavita, Grimmory, Stump and local files, on Android tablets first and phones second. Its look is **glass chrome over flat content, colour from the covers, type from one face**. This system is the source of truth for every screen: when the app and this system disagree, the app is wrong. Build every screen from the kit in `core:ui-components` (the components below carry the same names), never from raw Material `Card`, `Surface`, `Button` or the Material Icons artifacts.

Read the Layout and spacing and the Names and titles sections before changing any screen: they are where the app drifted.

## Principles

- **The books are the colour.** Covers carry the hue; chrome stays neutral and quiet. The reading card and the book page take a tint from the cover (AppCoverColors); nothing else invents colour.
- **One of everything.** One gutter per window class, one content top, one panel, one sheet width, one header for every sheet, one row. A screen that needs something new adds it to the kit first and to this system second.
- **Content names itself; screens don't.** The rail and the drawer say where you are. A screen never prints its own name as a title (see Names and titles).
- **Edges line up.** Everything on a screen hangs off the gutter. A heading sits on the same line as the panel or grid it heads. Two panes start at the same height.
- **Glass only when something is under it.** The top bar is see-through at rest and turns to glass once content scrolls beneath it.

## Content fundamentals

- Sentence case everywhere, shelf names included: "Reading progress sync", "Open-source licenses", "In progress", "Up next".
- Address the reader as "you" and name things by what people recognise: "Search Kavita", "Nothing downloaded", "Show 35 books", "Continue · chapter 1 of 6".
- Buttons say what happens: Continue, Details, Check for updates, Connect, Show 35 books. Errors say what went wrong and what to do: "Sign in to sync this server's libraries."
- Numbers are digits with a thin separator: "4% · Page 4 of 74", "1,474", "2 h 9 min". Use the middle dot ` · ` to join facts on one line.
- No emoji, no exclamation marks.

## Visual foundations

- **Colour.** Twelve themes in the app, all palettes over the same Material 3 roles; eight are in this system (Graphite first, the default dark; Paper, the light default; High Contrast, OLED Reader, Sepia Reading, Blueprint, Panel Pop, Detective File). Terminal 95, Crimson Court, VHS Night and the device's dynamic colours use the same roles. Screens sit on `background`; panels on `surface-container`; dialogs, menus and the search pill on `surface-container-high`; side sheets on `surface`. Selection is always `primary-container` with `on-primary-container`. `primary` is the accent: at most one filled button per surface. Secondary text is `on-surface-variant`. Hairlines are `outline-variant`.
- **Type.** Manrope everywhere in the chrome (`--font-sans`); book text has its own fonts in the reader. Hierarchy comes from size and weight, never from a second face: `display-title` (only for a title that is the content), `section-heading`, `panel-title`, `group-label`, `sheet-label`, then Material's text styles. Weights in use: 400, 500 and 600, never heavier: a title stands out by size, not by a heavier weight. No capitals with wide tracking, no negative tracking under 24. Every count, percentage, time and stat sets tabular figures.
- **Shape.** Six radii: `radius-cover` 6, `radius-inner` 8, `radius-control` 12, `radius-container` 20, `radius-sheet` 28, `radius-pill`. Pills are for things you tap to act or pick: buttons, chips, tabs. Fields and tracks hold things and take `radius-control`: the search field, text fields, the segmented row's track (its segments `radius-inner`, 12 less the 4 around them). Panels and the bar are containers; dialogs and sheets are sheets. A state is a dot and a word, never a filled pill. No other radius.
- **Elevation.** Content is flat on the ground. Only floating things cast a shadow: the top bar (6), side sheets (16), dialogs and menus (their kit defaults). Panels have no border and no shadow.
- **Glass.** One blurred material (Haze) for the top bar, the drawer and the reader bars and panels, tinted at 0.86 for bars and 0.72 for the large reader panes. Where blur is unavailable the fallback is opaque.
- **Motion.** AppMotion owns durations and easing: fade-through between top-level screens, shared axis into a page and back, predictive back on every screen and sheet, a press shrink on buttons and chips. Nothing bounces; nothing moves on its own while idle. The launch-only [Book hop](components/AppLaunchSplash/README.md) is the approved playful exception: one 820 ms native icon animation, with no added startup wait.
- **States.** Pressed: the control shrinks a little and its corners morph toward square. Selected: `primary-container`. Disabled: Material's 38% content alpha. Focus: Material's default indicator.

## Iconography

Material Symbols Rounded, weight 400, unfilled, 24 dp, bundled as vector drawables in `core:ui-components` (`AppIcons`); the previews draw the same paths from `components/bundle.js`. Icons take the colour of the text beside them. A filled variant exists only for stars and Stop. No emoji, no icon fonts at runtime.

## Using the components

The previews are static renditions written from the Compose kit; the code in `core:ui-components` is the implementation. The frame's numbers are `AppLayout`; the labels are `AppGroupLabel` (on a page) and `AppSheetLabel` (in a sheet), the hairline between a sheet's sections `AppSheetDivider`. Each component's guidelines say which kit function it is, what you pass it and when to use it. Classes in `components/bundle.css` start with `yb-` and read only the tokens.
