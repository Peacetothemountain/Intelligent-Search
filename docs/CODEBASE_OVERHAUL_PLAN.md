# Codebase Overhaul Plan

Goal: remove what isn't needed, rewrite what is badly built, and leave a codebase that is small, honest,
tested and easy to change. Baseline (Oct 15, 2026, after commit `e9bdde8`): 44.6k lines of Kotlin in
105 files, 175 JVM unit tests, 0 instrumented tests, 207 lint warnings, 2.1 MB of assets.

## Ground rules (apply to every phase)

- **Locked code is never edited**: `AGSL_GEMINI_PLATE` in `DynamicMeshShader.kt`, and `MaterialMorphAnimation`,
  `MorphAnimationEngine`, `BouncerState` in `SettingsScreens.kt`. When `SettingsScreens.kt` is split, the
  locked block stays in that file untouched; everything else moves out around it.
- **No behaviour change unless it's a fix.** Refactors keep what the user sees identical. Every setting the
  user has today must survive (migrations are tested).
- **Every phase ends green**: unit tests, a minified signed release build, install on the Pixel 11 Pro XL
  as an update, smoke test of the touched areas, logcat clean. Then one commit in the repo's style
  (one imperative line, no attribution). Nothing is pushed unless asked.
- **Delete only what is proven unused** (no references in code, resources, manifest, reflection or tests).
- **Fix it properly, not minimally (owner's standing instruction).** Whenever code is touched, if there is a
  significantly smarter or cleaner way to do it, rewrite it that way and update every caller and every
  similar method, instead of patching the one reported spot.
- **Never `git checkout`/restore over uncommitted work.**

## Phase 0: Safety net (before touching structure)

| Item | Status |
|---|---|
| Secrets scan of all history, backup branches and the stash | Done: clean |
| Android Lint baseline | Done: 0 errors, 207 warnings (134 UseKtx, 13 UnusedResources, 4 StaticFieldLeak, 2 PrivateApi, ...) |
| Independent review of commits `2335768` and `e9bdde8` | To do |
| Dependency vulnerability check against published advisories | To do |
| Add Robolectric (test-only) so Android-dependent code can be unit tested | To do |
| Characterization tests for what later phases rewrite: settings read/write + legacy-key migration, backup export → import round trip, bang URL building, widget/overlay intent factories, coordinator ranking | To do |

## Phase 1: Delete dead weight

Measured candidates (each re-verified before deletion):

- **Unused code**: `SonicMicroFeedbackEngine` (417 lines), `EncryptedDataVault` + the fail-open
  `BiometricSearchGate.authenticateForCryptographicAccess`, `SecureSettingsRepository` store/retrieve API
  (only the security-level query is used), the unused `ScrollDetentController` / `MagneticDismissController`,
  six unused `ADPFThermalManager` APIs, `FrameMetricsMonitor.jankStats`, 4 of 5 `AGSLBackdropShader`
  modifiers (and its unused caches), unused `SearchSectionType`, `AnimatedCopyChip`, and ~170 never-referenced
  functions/values (heaviest: `Shape.kt` 20, `Color.kt` 15, `WindowFramePacing.kt` 9, `Type.kt` 9,
  `ExpressiveMotionTokens.kt` 8, `SystemToggleManager.kt` 7).
- **Theatre**: `MemorySanitizer` "zero-trace" wiping of immutable Strings, "military-grade" claims in KDoc.
- **Hidden-API reflection**: `SearchManager.launchAssist` (widget) and `PowerProfile` (debug screen).
- **Dead settings**: `auto_voice` (written, never read), `last_search_query` (read, never written).
- **Resources**: 43 of 47 asset files (2.1 MB, leftover animation JSON), 13 unused drawables/layouts/strings
  (incl. 3× bugdroid emoji PNGs at ~184 KB each in three densities), no-op ProGuard rules
  (`androidx.graphics.surface`, `RoomDatabase_Impl`, over-broad navigation keeps).
- **Build cruft**: `debugSymbolLevel = "FULL"` + `packageNativeDebugSymbols` (no native code of ours), the
  misleading `abiFilters` comment, `kotlin.incremental=false` / `ksp.incremental=false`, catalog bypasses.

## Phase 2: One settings system

Today: SharedPreferences (`PREFERENCES_CUSTOMISATIONS`) and DataStore both hold settings, ~290 hand-typed
key strings across ~460 call sites, up to four spellings per setting (`search.pills`/`search_pills`,
`show_wallpaper`/`show.wallpaper`/`search.background.show.wall`), dual writes, alias tables in two places,
a "mirror guard", and live bugs from keys drifting apart.

Target:
- A typed `SettingsRepository` on DataStore with every key **declared once** (name, type, default, range,
  backup policy, direct-boot visibility).
- One-time migration that reads every legacy spelling from SharedPreferences, writes DataStore, and is
  covered by tests built from real exported preference files.
- Screens and the overlay observe the repository (no `SharedPreferences` parameters threaded through 38
  composables, no `rememberXPreference` helpers with triplicated key maps).
- Widgets read a small device-protected mirror so they render correctly before first unlock.
- Backup export/import is generated from the key registry (replaces the hand-written lists in
  `BackupManager` and `BackupPreferenceSchema`).

## Phase 3: Architecture

- `core` stops depending on `feature`: icon loading + caches, click debouncing and widget refresh move
  behind `core` interfaces.
- DI: remove secondary constructors that bypass Hilt (`SecureSettingsRepository`, `UnifiedSearchCoordinator`,
  `NexusLauncherBridge`, ...), convert hand-rolled singletons (`PixelHapticEngine`, `TactileSonicEngine`,
  `ADPFThermalManager`) to injected singletons, drop the duplicate `IntelligentSearchDatabase.INSTANCE`.
- One injected application `CoroutineScope` instead of three unmanaged ones; one direct-boot context helper
  instead of nine copies.
- Composables stop calling `PackageManager`/`ContentResolver` on the main thread: lookups move to
  ViewModels/repositories on IO (fixes the per-keystroke app scan in Web Search settings).
- App startup injects `SystemToggleManager`/`MultiProfileManager` lazily (no camera/network registration
  on widget- or boot-only process starts).

## Phase 4: Split the giant files

| File | Lines | Split into |
|---|---|---|
| `SettingsScreens.kt` | 3,560 | nav host, main screen, info/attestation dialog, shared settings components, engine brand icons (locked block stays) |
| `SearchOverlayScreen.kt` | 3,200 | screen scaffold, result sections, intent builders, icon loader, input/keyboard handling |
| `WidgetSettingsScreen.kt` | 3,100 | per-design editor, live preview, colour pickers, path icon assets |
| `SearchWidgetProvider.kt` | 2,000 | config resolver, RemoteViews binder, bitmap renderers, intent factory |
| `DebugScreen.kt` | 1,600 | diagnostics collector (testable) + UI pages |

Rule: pure moves first (one commit), then cleanup in the new files (separate commit), so regressions
are easy to bisect.

## Phase 5: Remaining bugs from the audit

Voice recognition kept running after the sheet closes; Tor searches saved to history (plus a real "pause
history" behaviour for the existing toggle); `user_setup_search_engine` rewritten on every open (write
only on explicit user action); tile click API guard (34 → 35); exported widget receivers accepting
internal refresh actions; `removePackage` wiping every profile + unlocked bulk re-index race; history
case-sensitivity mismatch; usage signals never fed into ranking; URL instant answer matching file names;
math engine (`1,000`, shifts ≥ 64, `Mb` vs `MB`, regex compiled per keystroke); ~120 `Log.w`/`Log.e`
calls left in release (some with package names); `setThreadPriority(-10)` on the main thread; Private
Space lock not observed; edited-bang save race; icon packs loaded with no memory cap.

## Phase 6: User-facing quality

- Strings into `strings.xml` (~190 hard-coded settings strings + toasts) so the app can be translated.
- Accessibility pass on the overlay, widgets, tile and voice sheet (labels, roles, 48 dp targets, no
  colour-only state, no double-announced titles).
- Remaining per-frame work outside settings (overlay ambient animations honour animator scale 0).

## Phase 7: Tooling & CI

Lint in CI with a checked-in baseline (fail on new issues), Gradle wrapper validation (done), dependency
updates through the catalog, alpha dependency review (`appfunctions 1.0.0-alpha13`), test coverage report.

## Phase 8: Device validation

Pixel 11 Pro XL (API 37) after every phase; emulators for API 31 and 33 (minSdk is 31), a large-screen /
foldable profile, landscape, 200% font, RTL; a work profile; reboot-before-unlock; backup → restore on the
same phone and on a second install.

## Phase 9: Play policy & docs

Permission justifications (`QUERY_ALL_PACKAGES`, `PACKAGE_USAGE_STATS`, `RECORD_AUDIO`,
`WRITE_SECURE_SETTINGS`), privacy policy and README kept in step with each phase, CHANGELOG entry per phase.

## Open decisions for the owner

- Old branches `backup-*` (up to 366 commits not on `main`) and `stash@{0}`: keep or delete.
