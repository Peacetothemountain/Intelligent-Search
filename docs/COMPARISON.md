# `main` vs `dev`: the same app, built two ways

`main` is Intelligent Search as the owner built it and ships it on Google Play. `dev` is the same app rebuilt the way
Claude would structure it, kept for comparison (it is not scheduled to replace `main`). Every feature and fix on
`main` has been merged into `dev`, so the two apps do the same things and look the same; what differs is how the
code is organised and how much of it is checked automatically.

Measured on 2026-10-17 (`main` 2d39b77, `dev` 9d9180a).

## What is the same

- **Every screen looks the same.** `dev` has a screenshot ("golden") of every settings page, the search overlay in
  each state, the widget editor, the rendered widgets and the voice sheet. Each restructuring step had to reproduce
  those images pixel for pixel before it was kept, and every image that also exists on `main` matches `main`.
- **Every feature behaves the same.** Search, actions, widgets, backups, the tutorial, the voice assistant, the debug
  menu: `main`'s changes are carried into `dev` whenever `main` moves forward.
- **Same package, same data.** `dev` reads and writes the same settings and database, so it could be installed over
  `main` without losing anything (it has not been, pending the owner's go-ahead).

## What is different

### 1. Big screens are split into focused files

| Screen | `main` | `dev` |
|---|---|---|
| Search overlay | one file, 2,798 lines | 921-line screen plus its search bar (398), quick apps (92), results (819), Best Match card (326) and recent search row (136), sharing state through `OverlayScope` |
| Widget editor | one file, 2,752 lines | 847-line screen plus one file per tab (Appearance 734, Color Studio 277, Shortcuts 280), its dialogs (341) and its draft state (`WidgetEditorState`, 83) |
| Voice assistant | one file, 1,145 lines | the session (199) plus its lifecycle host (79), the recognition manager (240), the waveform (284) and the sheet (376) |
| Web Search settings | 1,213 lines | 211-line page plus the shortcuts card and its badges |
| Main settings page | 807 lines | 325-line page plus the search role dialog |
| Tutorial overlay | 1,087 lines | 819 lines plus the bugdroid emoji and the spotlight pulse ring |

Why it matters: a change to one part (say, the Best Match card) touches one small file, so it is easier to read,
review and test, and two changes are less likely to collide. The cost is more files to navigate.

### 2. The search pipeline talks to the device through one door

On `main`, the search code calls Android directly (apps, contacts, calendar, files, usage, web suggestions). On
`dev`, all of that goes through `DeviceSearchSources`, which is handed to the search code. In tests a stand-in
device is handed over instead, so the whole search pipeline (loading, hidden apps, app lookup, web suggestions,
arithmetic, history) is tested without a phone (`SearchViewModelTest`).

### 3. Settings that need logic live in small tested stores

The widget editor keeps its unsaved edits in `WidgetSettingsStore`/`WidgetEditorState`, and the app pill profiles
in `SearchPillProfiles`, each with its own tests, instead of in the screens. Pages that are simple toggles keep using
the typed settings registry (`rememberSetting`), which both branches share.

### 4. More is checked automatically

| | `main` | `dev` |
|---|---|---|
| Source files | 141 | 166 |
| Source lines | 41,216 | 41,569 |
| Test files | 43 | 54 |
| Tests (`@Test`) | 223 | 260 |
| Screenshot goldens | 46 | 78 |
| Files over 800 lines | 13 | 11 |
| Largest file | `SearchOverlayScreen.kt`, 2,798 lines | `DebugDiagnosticsPage.kt`, 1,344 lines |

The source is about the same size (moving code adds a little), but `dev` has 37 more tests and 32 more screenshot
goldens, including the voice sheet in every state and the recognition manager driven through a simulated recognizer.

Both branches share the layout audit (`ResponsiveLayoutAuditTest`, run with `-PlayoutAudit`), which renders the app
on small phones up to tablets and an Android XR window at 85% to 200% text and reports anything clipped, split,
overlapping or off-screen; on `dev` it also covers the voice sheet.

## What was deliberately not done

- **Gradle modules (R5).** Splitting the app into separate build modules adds build complexity that a single app
  does not need; dropped by the owner's decision.
- **A ViewModel for every settings page.** Most settings pages only flip stored switches through the typed registry;
  wrapping each in a ViewModel would add code without removing any.
- **Rewriting working code for its own sake.** Every step was a move or a targeted fix, proven by the goldens, so
  `dev` never drifted from what the owner built.

## Still open

- **R6, on-device validation of `dev`.** Installing `dev` would replace the owner's installed app, so it waits for the
  owner (or a side-by-side install under a different package name).

## Which is better?

For a single developer shipping fast, `main`'s approach keeps everything for a screen in one place and has worked.
`dev`'s approach pays off as the app grows: smaller files, a search pipeline that is tested without a phone, and
more screens protected by goldens, so a change that breaks something is caught before it reaches Play.
