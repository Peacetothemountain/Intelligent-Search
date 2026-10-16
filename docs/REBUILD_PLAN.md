# Intelligent Search rebuild (branch `dev`)

Goal: rebuild the app's internals from the ground up, keeping every feature and the exact design language
(colours, shapes, motion, layouts, the locked Gemini plate shader and morph/bouncer animations).
`main` is untouched until the owner merges `dev`.

## How "looks the same" is proven

Without a device, every screen is rendered headlessly (Robolectric native graphics + Roborazzi) **before** it
is rebuilt. Those golden images are committed; the rebuilt screen must reproduce them pixel for pixel (small
anti-aliasing tolerance only). A screen is not "done" until its goldens pass. Device checks follow for motion,
video, haptics and anything the renderer can't show (AGSL shaders, TextureView video).

## Target architecture

1. **Unidirectional state per screen.** `XxxRoute` (wires the ViewModel) + stateless `XxxScreen(state, onEvent)`.
   Composables stop reading SharedPreferences, PackageManager or singletons directly; ViewModels expose an
   immutable `UiState` built from repository flows. This is also what makes every screen screenshot-testable.
2. **Repositories instead of `object` singletons.** Apps, contacts, files, shortcuts, icons, settings and
   history each get an injected repository with a Flow API (replacing `SystemDataProvider`, `IconPackManager`,
   `MaterialOutlineManager`, raw prefs access). Search ranking stays in the existing tested engine.
3. **Small components.** The giant composables (search overlay ~2.7k lines, widget settings ~2.75k, debug
   page ~1.3k, main settings ~0.8k) are decomposed into focused, previewable components in a design-system
   package with the same tokens.
4. **Modules last.** Once boundaries are clean inside `:app`, split into Gradle modules with convention plugins:
   `:core:model`, `:core:data`, `:core:search` (pure JVM where possible), `:core:security`,
   `:core:designsystem`, `:feature:search`, `:feature:settings`, `:feature:widget`, `:feature:voice`, `:app`.
   Doing it last avoids moving code twice.

## Phases

| Phase | Work | Done when |
|---|---|---|
| R0 | Screenshot harness; goldens of every settings page (light + dark), overlay states, tutorial, voice sheet, widgets | Goldens committed, suite green |
| R1 | Settings: SettingsRepository + one ViewModel per page, stateless screens, shared components | All settings goldens pass |
| R2 | Search overlay: decompose, UiState from SearchViewModel, no direct system calls in composition | Overlay goldens pass, tests green |
| R3 | Data layer: repositories replace object singletons | Unit tests green |
| R4 | Widgets, tile, voice: same treatment | Goldens + tests green |
| R5 | Gradle modules + convention plugins | Clean build, CI green |
| R6 | Device validation on the Pixel; motion/video/haptics checks | Owner sign-off, merge to `main` |

Ground rules carried over: locked visual code is never edited; deletions only of proven-unused code; every
phase ends green (tests, lint, release build) and is pushed to `dev`.

## Progress

- 2026-10-15, R0 done: 60 goldens (settings pages, overlay states, widget editor views, widget renders) verified
  stable; the harness runs compose effects on a standard test dispatcher and waits out loading indicators.
- 2026-10-15, widget slice of R1 and R4 done: the widget editor keeps its drafts in `WidgetSettingsStore`
  (`WidgetDraftFields`, `WidgetEditorState`) and is split into tab, dialog and state files; the widget renderer
  builds its per-width views through `buildResponsiveViews` and reads the same store, so an older build's
  shortcuts, transparency and G icon now reach the widget and unset element colours follow the theme colour.
- 2026-10-16, R1 reassessed: the other settings pages already bind through the typed `rememberSetting`
  registry, so a ViewModel per page would add code without removing any. The one page on raw keys, Application
  Search, now uses the tested `SearchPillProfiles` store.
- 2026-10-16, R2 under way (overlay 2,635 to 2,205 lines): best-match ranking (`BestMatch`), web search and
  quick-app launching (`webSearchLaunch`, `quickAppSearchIntent`) are tested functions outside composition;
  one `SwipeToDismiss` helper replaces three copied swipe rows and gives each a TalkBack Dismiss action.
- 2026-10-16, R2 done: the overlay is `SearchOverlayScreen` (849 lines) plus `OverlaySearchBar`,
  `OverlayQuickAppPanel` and `OverlaySearchResults`, sharing state through `OverlayScope`; goldens unchanged.
- 2026-10-16, R3 (search slice) done: `DeviceSearchSources` is the injected door to apps, usage, shortcuts,
  calendar, contacts, files and web suggestions for both `SearchViewModel` and `CorpusIndexManager`, and
  `SearchViewModelTest` runs the pipeline on a stand-in device (load, hidden apps, indexed app lookup, web
  suggestions, arithmetic, history). The other `object` singletons are stateless utilities or system entry
  points and stay as they are.
- 2026-10-16, more settings pages split: Web Search (page, `WebQuickShortcutsCard`, `WebShortcutBadges`) and
  the main page's `SearchRoleInfoDialog`.
- 2026-10-16, R5 dropped by owner decision: for a single-app project, Gradle modules add build complexity for
  little gain.
- Open, waiting on the phone: R4 voice session (1,125 lines) and R6 device validation of `dev`.
- Purpose (owner, 2026-10-16): `dev` is a comparison build, showing how the app would be built differently from
  the owner's own `main`; it is not scheduled to replace `main`.
- Verification: targeted tests per change, then lint, all tests and the release build once per batch of commits
  before pushing.
