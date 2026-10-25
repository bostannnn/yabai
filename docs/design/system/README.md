# yabai design system

The source of truth for how every screen looks. Live, browsable copy (tokens per theme, components with
previews, screen mocks with a spacing overlay): https://claude.ai/artifact/RaAkZ8f5bEnCVbW86Co1iF

This folder is the same content, kept in the repo so a session can read it without the artifact. When the app
and the system disagree, the app is wrong; when a decision changes, change the system first, here and on the
artifact, then the code.

- `project/README.md`: principles, copy, colour, type, shape, motion, icons.
- `project/layout.md`: window classes, the frame (gutter, top bar, content top, sections), alignment, surfaces,
  rows, and what a screen may not do.
- `project/titles.md`: no screen-name titles; what the bar shows on each screen.
- `project/tokens.json`: colour roles for eight of the twelve themes (from `UnifiedReaderTheme.kt`), the type
  roles, spacing and radius tokens. The spacing tokens are the target for `AppLayout` (plan section 10).
- `project/components/<Name>/`: guidelines (`README.md`) and a static preview (`preview.html`) per kit
  component and per screen mock. `bundle.css` styles them from the tokens; `bundle.js` holds the app's icon
  paths, copied from `core/ui-components/src/main/res/drawable/ic_*.xml`.
- `tools/gen_components.py`, `tools/gen_screens.py`: write the component folders. Edit them, run
  `python3 tools/gen_screens.py` (it runs the components script too), then publish the changed files to the
  artifact (the Design System type; publish only the changed `project/` paths, the index
  `project/design-system.json` last).

## Enforcement

`CLAUDE.md` ("UI work") gives every session the rules. The rule tests in `core:ui-components` hold them in CI:
`FeatureMaterialImportRuleTest` (kit instead of raw Material), `AppLayoutRuleTest` (gutter, one sheet width, one
content top) and `DesignSystemRuleTest` (`AppScreenTitle` only for content titles; literal dp in paddings and corner
radii per file may only go down from `core/ui-components/src/test/design-baselines/`, rewritten with
`./gradlew :core:ui-components:testDebugUnitTest -PupdateDesignBaselines` after removing some). Visual judgement
(alignment, hierarchy) is checked against the screen mocks with their spacing overlay.

The artifact also carries `project/fonts/manrope.ttf`, a copy of `app/src/main/res/font/manrope.ttf`, left
out here.

Previews need the artifact's generated `tokens.css`; to look at one locally, compile the tokens into CSS
variables per `[data-theme]` and load `components/bundle.css` and `components/bundle.js` beside it.
