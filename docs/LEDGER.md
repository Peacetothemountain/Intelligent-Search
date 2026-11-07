# Improvement Ledger

Append-only. Read it before choosing a target (`docs/IMPROVEMENT.md` section 4). Never delete an entry. If a later
cycle proves an entry wrong, add a new entry that says so.

## Ratchet values

Update these in the same commit as a change that improves them. Never let one get worse (`IMPROVEMENT.md` section 2).

| Metric | Value | Measured at |
|---|---|---|
| Lines of test code | 51,985 | 2026-11-07, B18, up 32 for a written reason (see its entry); replaces the `@Test` count (owner: root causes over more tests) |
| CI time, `Run Build & Check` | 527s (was 1118s) on a commit that changes only tests; 872s (was 1003s) on one that changes app code, run 42356421669 on `f15b95a3` | runs 42277305877 on `8de98e0a` and 42335671213 on `b19aaa19`. Only compare like with like: app compile and R8 come from cache when app code didn't change. Since B13 there is no single step to time: measure from the start of the run to `Build & Check` finishing |
| Flaky CI failures | 1 in the last 40 runs (B12) | 2026-11-06 |
| Screenshot goldens | 232 | `519e738d`, 2026-11-01 |
| Lint baseline issues | 11 | 2026-11-07, B19 |
| Release APK size | 12,920,597 bytes | local build 2026-11-05, `8fc5df48`; up 16 KB from `0260afa7` for the owner-requested Developer Note animations |
| Known Play crashes without a traced fix | 0 | `docs/HISTORY.md` crash table |
| Cold start / jank / memory | not yet measured | first cycle the owner allows phone use; write the device, build and method here |

## Backlog

Candidates found by `/audit` or noted during a cycle, each with its evidence. `/improve` picks from here first, starting
with the strongest evidence.

| ID | Target | Evidence | Strength (1 to 4) | Found |
|---|---|---|---|---|
| B1 | Remove the unused axes from `res/font/google_sans_flex.ttf` | Largest non-dex entry in the APK: 4,130,704 bytes, 2,402,505 compressed. `core/theme/Type.kt:18-40` uses only `wght` and holds `wdth`, `slnt`, `GRAD`, `ROND` and `opsz` at their defaults; no other code sets an axis. fontTools `instancer` keeping `wght` only: 275,484 bytes, about 104,000 deflated, same 682 glyphs. Estimated APK 16.02 MB to about 13.7 MB. Rendering should not change: CI goldens plus one phone check prove it. The font's license (OFL 1.1) allows modified copies. | 2 | 2026-11-05 |
| B2 | Stop decoding full-size Bugdroid tutorial bitmaps on the main thread | `feature/settings/TutorialBugdroidEmoji.kt:44-82` decodes 4 PNGs of 418x587 in composition with no `inSampleSize`: 981,464 bytes each, 3.93 MB of heap, for a 40x56dp image (`:150`, about 140x196 px on a 3.5x phone). `inSampleSize = 2` gives 979,792 bytes in total. Decode time not measured; needs the phone. Only runs in the tutorial (`TutorialSpotlightOverlay.kt:635`). Not a Bugdroid clip, so not locked. | 2 | 2026-11-05 |
| B3 | Add tests for untested permission, At a Glance media and search lock logic | No test references `forgetDecline`, `forgetDeclinesNowAllowed`, `noteDecline` or `DECLINED_PERMISSIONS` (`feature/settings/PermissionRationale.kt:154,170,179`), so nothing asserts a decline is forgotten once allowed. `glanceMedia` and `scaledArtwork` (`core/glance/GlanceSource.kt:119,162`) have no test; the only `MediaMetadata` tests are for other code. `BackupRestoreScreenTest.kt:466` replaces `BiometricSearchGate` with a fake, so `authenticate` (`:51`) never runs under test. | 2 | 2026-11-05 |
| B4 | Raise the 4 confirmed 32dp icon buttons to 48dp | `Modifier.size(32.dp)` at `feature/settings/SearchRoleInfoDialog.kt:376` and `BackupRestoreScreen.kt:389,548,557`. M3 asks for 48dp (https://m3.material.io/foundations/designing/structure). Compose widens the touch area, but 548 and 557 sit side by side and share it. `ResponsiveLayoutAuditTest` doesn't flag them. Changes layout, so the owner must approve it. | 2 | 2026-11-05 |
| B5 | Clear the mechanical `UseKtx` lint issues | 53 of the baseline's issues are `UseKtx`. Source still has 26 `Uri.parse` and 9 `Bitmap.createBitmap`; the rest are `SharedPreferences.edit`. Some entries are stale (`UserGrantedFiles.kt` has moved to `.toUri()`). Real baseline count is 66: the ledger's 67 comes from the documented grep also matching the `<issues` header line. Lint hasn't been run to confirm the final number. | 3 | 2026-11-05 |
| B6 | Cut the Android explanation from the Setup Checklist search bar help | `feature/settings/SetupChecklist.kt:151-152` opens with "Android only lists an app as a search engine once it's on the phone's search engine list", against the owner's rule that copy gives results and steps, not Android mechanics (2026-10-29, memory `user-copy-results-not-internals`). | 4 | 2026-11-05 |
| B7 | Correct the stale release notes in `docs/HISTORY.md` | Lines 69-72 say 9.3.1 is the Latest release and that signing secrets may be unset. `gh release list` shows `v9.5.4-132` as Latest, plus `v9.5.1-129` and a dev pre-release, built by the release workflow. Docs only. | 4 | 2026-11-05 |
| B8 | Show the battery level for a "battery" search | Pixel 11, 2026-11-05: "battery" shows Battery Saver with "Normal battery consumption" and no level, while the Diagnostics page has it all. Owner to decide. | 4 | 2026-11-05 |
| B9 | Remove the Developer Note's repeats once Intelligent Search is default | Pixel 11, 2026-11-05: two cards say the same thing, and "Copy Command" still copies commands that no longer apply. Layout change, so it goes with the note's replacement animation. | 4 | 2026-11-05 |
| B10 | Plain copy for Quick App Panel, Context Aware Quick Apps and Files | Pixel 11, 2026-11-05: "Fixed Applications Sourced from Application Search Menu", "User's App Opening Cycles", "via System Pickers" explain internals, against memory `user-copy-results-not-internals`. | 4 | 2026-11-05 |
| B11 | Add tests for At a Glance media | The part of B3 still open: `glanceMedia` and `scaledArtwork` (`core/glance/GlanceSource.kt`) have 0 references in `app/src/test` on 2026-11-06. The decline helpers from B3 are now covered. | 2 | 2026-11-06 |
| B12 | Stop CI downloading Android runtimes it should have cached | CI run 42256710298 failed on `0b9b1148`: `OverlayWaitsForPermissionGrantTest > classMethod`, `IOException` in Robolectric's `MavenArtifactFetcher`. The re-run passed with no code change. `sdk = [31]` was added 2026-10-30, but `.github/workflows/ci.yml` keys the cache `robolectric-<version>-sdk34`, and that entry (592 MB) dates from 2026-10-22 and is never rewritten. So SDK 31 is very likely fetched from the network on every run (inferred; the cache's contents weren't opened). 1 failure in the last 40 runs. Proof: the key changes with the set of SDKs in use, and a run shows the new cache holding `android-all` for 31. | 1 | 2026-11-06 |
| B13 | Run `assembleRelease` beside the tests, not during them | Tests took 20 minutes with R8 overlapping and 13 with R8 cached on the same commit, before the `settle` fix. Parallel jobs under one aggregator named `Build & Check`, which must check `needs` results (a skipped job counts as passing). Measured 2026-11-06 on `b19aaa19`, an app code change: 1003s in all, with `minifyReleaseWithR8` starting at 433s and `assembleRelease` at 949s, so about 516s of R8 overlapping the tests. On `8de98e0a`, tests only, R8 came from cache and the whole run took 526s. | 2 | 2026-11-06 |
| B14 | One `FileResultPicture` instead of 4 copies of `if (filesThumbnails)` | `OverlayBestMatchCard:274`, `OverlayDetailPane:261`, `OverlayPinnedRow:112`, `OverlaySearchResults:690`. Then `FileThumbnailsScopeTest`, which greps source lines, can go. | 2 | 2026-11-06 |
| B15 | One `SettingsTopBar` and a small lint module | 18 raw `TopAppBar(` calls, with drift: 16 Outlined back arrows, 2 Filled. Replaces `SettingsPageTitleTest` and `LayeringTest`, which read source text. | 2 | 2026-11-06 |
| B16 | Inject dispatchers so tests can stop polling in real time | 117 `Dispatchers.*` uses and no seam. `SearchViewModelTest.runOn` and `FileTypeFilterTest:181` assert nothing happened after about 250ms, which passes wrongly on a slow machine. Large. | 2 | 2026-11-06 |
| B17 | One table-driven backup round trip | 7 `*BackupTest` classes each build Room and run PBKDF2 for their own settings; a forgotten key can slip through. Over `SettingsManager.ALL`, a new setting needs no new test. Backup data: use the strongest model. | 2 | 2026-11-06 |
| B18 | Share one recorded run between the morph physics rules | `MorphBouncePhysicsTest` is now the slowest class, 113s of 556s. Its 23 tests hold real rules about physics, so this only saves time: lowest priority. | 3 | 2026-11-06 |
| B19 | Clear the lint baseline issues that have a fix | 8 of the 19 can go: 5 `ModifierParameter` (`GeminiCornerSwipeWave.kt:80`, `OneBoxCards.kt:193`, `SettingsComponents.kt:53,205,428`), 2 `UseKtx` `SharedPreferences.edit` in `SettingsManager.kt:508,526` and 1 `UseCompatLoadingForDrawables` in `IconPackManager.kt:135`. The rest stay: `StrongBoxSecurityManager.kt:454` needs `commit()`'s result, which the KTX `edit` drops; `NaturalLanguageIntentParser.kt` keeps `Uri.parse` (2026-11-05 lesson); the 5 `DiscouragedApi` read another app's icon pack by name, which has no other way. Proof: `grep -c '<issue$' app/lint-baseline.xml` 19 -> 11 and lint green | 3 | 2026-11-07 |

## Entries

### 2026-11-06: B8, B9, B10, RAM chart, layout audit
- Command: owner said "fix all your recommendations"
- Target and evidence: battery search with no level; repeated default card and unusable Copy Command; internals in Settings copy; RAM categories drawn as a trend line with values clamped to 15-88%
- Prior work checked: this ledger
- Research: `-PlayoutAudit` run after the change: 0 failures, 781 accepted small-target findings, and it does not render the Developer Note dialog
- Models used: inline
- Usage level: unknown, not visible in this session
- Change: battery level answer; default card and Copy Command hidden once default; plainer subtitles; RAM bars at true share
- Before -> after: lint baseline 19 -> 19; release APK 12,920,597 -> 12,920,597 bytes
- Result: kept, pending CI; recent-search doubling seen once in a screenshot, not reproduced, phone was disconnected
- Retry only if: the doubled row is seen again on the phone
- Commit: see `git log dev`
- Lesson: `AtAGlanceOverlayTest.calendarEventsShowInsideAtAGlance` fails from 23:00 local because its event is one hour ahead and rolls to tomorrow. Recording a whole golden class also rewrites unrelated goldens by sub-threshold noise: restore those.

### 2026-11-05: Audit backlog B1 to B7, wrong contact results
- Command: /audit, then owner said "do all"
- Target and evidence: B1 to B7, plus a bug seen on the Pixel 11: "battery", "butter" and "better" listed Matt Potter, "water" a contact labeled (Father)
- Prior work checked: this ledger (empty), `git log dev`, claude-mem audit rounds
- Research: Double Metaphone reads B as P and drops vowels; a 6-letter word gets 2 typing slips, so "butter" was within reach of "Potter"
- Models used: haiku/low for six fact sweeps, inline for fixes
- Usage level: unknown, not visible in this session
- Change: font holds the weight axis only; mascot decodes at half size; 4 buttons at 48dp; sound and two-slip matches must begin with the same sound; Developer Note clip removed; lint `UseKtx` cleanup; copy and docs fixes; new tests
- Before -> after: APK 16,022,970 -> 12,904,213 bytes; lint baseline 66 -> 19; `@Test` 2299 -> 2316
- Result: kept, CI `Build & Check` green on `0260afa7`; not yet checked by eye on the phone
- Retry only if: n/a
- Commit: 0260afa7
- Lesson: `toUri()` throws under plain JVM tests where `Uri.parse` returned null, so `NaturalLanguageIntentParser.kt` keeps `Uri.parse`. Driving the phone suspends its accessibility services, and always look before pressing.

### 2026-11-06: RAM curve restored, release 9.5.5 (133)
- Command: owner said "I like the old ram chart. I want that animation, but with the correct information"
- Target and evidence: the bars from the entry above were rejected. The curve's only wrong reading was the 15% floor (the 88% cap never fired, since the ceiling is 1.35x the largest value) and an 8dp lift off the baseline
- Prior work checked: this ledger, `8836d91a`
- Research: legend figures come from `DiagnosticsReadings.usedBreakdown` and add up to Used, so the data needed no change
- Models used: inline
- Usage level: unknown, not visible in this session
- Change: curve, stems, pulse and breathing restored from before `8836d91a`; points placed by `ramPointFraction` with no floor or cap; version 9.5.5 (133)
- Before -> after: release APK 12,920,597 -> 12,920,597 bytes; lint baseline 19 -> 19
- Result: kept; this entry supersedes the bars in the entry above
- Retry only if: n/a
- Commit: see `Release 9.5.5 (133)` in `git log dev`
- Lesson: don't replace an owner's animation to fix its data. Fix the data and keep the animation.

### 2026-11-06: At a Glance test that fails after 23:00
- Command: /improve (owner started the automation)
- Target and evidence: `AtAGlanceOverlayTest.calendarEventsShowInsideAtAGlance` fails whenever the local hour is 23, because its event is one hour ahead and rolls to tomorrow. CI runs in UTC, so a run reaching this test from 23:00 to 24:00 UTC (18:00 to 19:00 EST) would fail and hold back `main`. Noted as a lesson in the first 2026-11-06 entry, never fixed
- Prior work checked: this ledger (B1 to B10 all worked, B3 partly: see B11), `git log dev -S`, claude-mem #30979, #30980, #30997. `gh run list` shows no failed CI run on `dev` in its recent history, so this hadn't hit CI yet
- Research: `calendarEventWhen` is correct: an event past midnight should say Tomorrow, so the test was wrong, not the app. `LocalGlanceClock` already lets tests hold the time, and `AtAGlanceGoldenTest` and `GlanceTimeTest` use it. `calendarEventWhen` had no direct test
- Models used: inline (Opus 5.5); targeted lookups, so no subagents
- Usage level: owner reports full; no figure visible in this session. Normal mode
- Change: `AtAGlanceOverlayTest` holds its clock at noon today, assertions untouched; new `CalendarEventWhenTest` (6 tests at fixed times: today, midnight rollover, now, weekday, all-day west of UTC). No app code changed
- Before -> after: at local hour 23, 1 of 19 tests in `AtAGlanceOverlayTest` failed -> 0 of 19. 23 clock-touching test classes (313 tests) pass at local hours 23, 00 and 12. `@Test` 2331 -> 2337; lint baseline and APK unchanged
- Result: kept
- Retry only if: n/a
- Commit: see `Fix At a Glance test that failed after 23:00` in `git log dev`
- CI: attempt 1 failed on an unrelated download error (B12); attempt 2 on the same commit passed, `Build & Check` green on `0b9b1148`
- Lesson: to test another hour, set `JAVA_TOOL_OPTIONS=-Duser.timezone=GMT+07:00` (choose the offset for the hour wanted) and run the classes with `--tests`. Tests that read the wall clock only fail at some hours, so a pass at one hour proves little.

### 2026-11-06: Owner's rule, real-time waits in tests, CI running twice (includes B12)
- Command: owner said "having to add a test just to avoid an issue is not the solution", and to take the best course without prompting
- Target and evidence: 3 independent reviewers each found `settle()` sleeping a fixed 120ms per step, so 3.7s per default call with nothing running, about 265 call sites. 34.6 CPU minutes per CI run. CI ran the same 24-28 minute job again when a commit reached `main`, and `dev` couldn't write the Gradle cache. 400 of 3305 tests were one audit test skipping itself in every run
- Prior work checked: this ledger; the reviewers read the tests and main code; I confirmed `Settle.kt`, `ci.yml`, branch protection (`strict: false`, check `Build & Check`)
- Research: `validate-wrappers: true` in the CI log means `setup-gradle` already checks the wrapper, so the separate step repeated it
- Models used: opus/high, 3 read-only reviewers (about 510k tokens); inline for the changes
- Usage level: owner reported full; no figure visible. Normal mode
- Change: `settle` waits in real time only while a coroutine IO or Room thread is working, up to the old limit; CI pushes run on `dev` only and `dev` writes the cache; Robolectric cache key names every SDK in use (B12) with a restore fallback; audit excluded from normal runs instead of skipping; redundant wrapper step removed; charter, `CLAUDE.md` and commands now put root causes before tests. No assertions and no app code changed
- Before -> after: full suite 34.6 CPU minutes (CI) -> 9.5 (local run, same 0 failures; CI figure to confirm); 3305 tests with 400 skipped -> 2911 with 0 skipped, same tests run
- Result: kept. CI `Build & Check` green on `89526acd`: 1118s -> 773s. Full suite passed twice locally as well. The new Robolectric cache is 124 MB larger than the old one, about one runtime, which supports the B12 reading that SDK 31 was missing
- Retry only if: captures turn flaky; then find the background work `settle` can't see instead of restoring the sleep
- Commit: see `Stop tests sleeping and CI running twice` in `git log dev`
- Lesson: when one helper appears in most slow tests, measure the helper first. All 3 reviewers guessed "rendering" was the cost until they did the arithmetic.

### 2026-11-06: Enter acting on stale results, permission flow copied four times
- Command: continuing without prompts, from the 3 reviewers' findings
- Target and evidence: (1) `submitWhenSettled` waited up to 500ms for composed state to catch up to the view model, then went ahead anyway, so with a late composition Enter or the voice check opened the previous query's Best Match. Read from the code; not reproduced on a phone. (2) Contacts, calendar, phone calls and location each held their own copy of the same flow, about 60 lines each, and they had drifted: phone calls skipped the check on return from App info
- Prior work checked: this ledger; `git log dev -S"withTimeoutOrNull(500)"`; claude-mem #14522
- Research: `awaitSearch` returns once the view model holds this query's results, so nothing needs to wait for composition
- Models used: inline
- Usage level: owner reported full; no figure visible. Normal mode
- Change: Enter reads `viewModel.uiState.value` through the same `visibleAppsIn` and `bestMatchIn` the screen draws with, and the timeout is gone; one `rememberPermissionRequest` driven by a `PermissionPrompt` for all four, and contacts uses the same single-permission request as the others
- Tests removed, 20 in all: guarded a copy that no longer exists (2 in `PhoneCallsRequestTest`, 2 in `DeclinedPermissionTest`); same flow with and without a flag, and only one order happens on a phone (4 in `GrantedInSettingsTest`, keeping the Android-order version and the extra checks); repeats (`enterRunsAFullTimerPhraseToo`, `aHighlightedResultOpens`, `enterRunsTheShortPhrasingsAction`, `aCancelledSwipeStaysOnThePage`); a retired key only `REMOVED_NAMES` mentions (`anOlderBuildsSearchTheWebNoLongerSkipsTheBestMatch`, `theOldContactActionsSwitchNoLongerHidesThem`, 2 for `search.android.settings`); constants restated (`testRoleManager_constantsIntegrity`, `testFrameRateCategoryConstants`, `mockLargeDatasetHasFiftyContactsAndFiftyFiles`); `theManifestDeclaresExactlyOneNotificationListener`, whose permission check moved into `androidFindsOneListenerInTheApp`
- Test changed, assertions untouched: the fake prompt in `SearchSourcesPermissionTest` answered in the removed multi-permission format
- Before -> after: app code 76,634 -> 76,502 lines; test code 52,322 -> 52,098; full suite 2911 tests -> 2891, 0 failures
- Result: kept. CI `Build & Check` green on `2cafacd8`, 630s. Permission dialogs not checked by eye on the phone; goldens unchanged
- Retry only if: a phone shows Enter opening the wrong result, or a permission flow behaving differently from before
- Commit: see `Fix Enter on stale results, share one permission flow` in `git log dev`
- Lesson: read the app code behind a slow test before speeding the test up. Half of this batch was a real race that a timeout was hiding.

### 2026-11-06: Goldens let real changes through
- Command: continuing without prompts, from a reviewer's finding
- Target and evidence: `changeThreshold = 0.001f` allowed 0.1% of each image to change, up to 4,284 pixels on the tallest pages. A throwaway branch ran every golden on CI at threshold 0: 143 of 247 tests differed. Measured with the comparator's own distance, 119 of the 137 images I could compare differ by at most 0.041, which is the faint color difference between Windows and Linux; every other image reaches at least 0.54. Those were real changes CI had passed, such as the tablet golden still saying "Normal battery consumption" when the app says "Battery Saver Off"
- Prior work checked: this ledger, whose lesson that recording a class "rewrites unrelated goldens by sub-threshold noise" has the same cause
- Research: `differ` 0.3.0 `ColorKt.distance` is the Euclidean distance of RGBA from 0 to 1, read from the bytecode. In Roborazzi, recording with verify on rewrites only goldens that fail the comparison
- Models used: inline
- Usage level: owner reported full; no figure visible. Normal mode
- Change: `GOLDEN_OPTIONS` ignores color distance up to 0.05 and allows no other pixel to change; `roborazzi.test.verify` is always on, so a record run only rewrites what really changed
- Goldens re-recorded, 20, all for changes already shipped and none from this batch: `overlay_tablet/detail_setting_*` (Battery Saver Off); `overlay/empty_*`, `overlay/tutorial_*`, `overlay_tablet/empty_*` (the placeholder's "..." became one "…" character); `settings/launch_portal_on_*`, `settings/backup_*`, `settings/doodle_gallery_*`, `settings/hidden_apps_unlocked_*`, `widget/countdown_setup_*`, `overlay/file_type_filter_*` (icons and top bars from earlier commits). I looked at the differences in all of them
- Before -> after: changed pixels allowed per golden, up to 4,284 -> 0; goldens that didn't match the app, 20 -> 0; the other 217 goldens untouched by recording
- Result: kept. CI `Build & Check` green on `8de98e0a`, 527s, so the limit holds on Linux
- Retry only if: CI fails a golden with no change to its screen; measure the new distance before raising the limit
- Commit: see `Stop goldens passing real changes` in `git log dev`
- Lesson: a limit on how many pixels change was the wrong measure, because the noise is faint in color, not few in number. Measure the noise before setting a tolerance. The limit does still pass a real change fainter than 0.05, about 7 color levels per channel.

### 2026-11-06: Verifying without plugging in the phone
- Command: owner said to find a method to "test and verify and change without me having to plug in my phone"
- Target and evidence: every check by eye waited on a cable, and driving the phone turned off the owner's app lock (2026-11-05 entry)
- Prior work checked: this ledger, memories `test-phones` and `search-engine-role-allowlist`
- Research: emulator accelerated by WHPX on this PC, Android 17 image installed, with the Pixel launcher, Gboard, Play services and the permission controller. `avdmanager` crashes on the `android-37.0` image, so the AVD was written by hand
- Models used: inline
- Usage level: owner reported full; no figure visible. Normal mode
- Change: `IS_Pixel_XL` AVD on the owner's PC; `docs/DEVICE_TESTING.md`; charter section 8 points to it
- Before -> after: boots in 45s; release 9.5.5 (133) installs with no cable and no owner action. Launching and driving the app on it: NOT verified
- Result: blocked at the last step. `device_open` fails: "JAVA_HOME is set to an invalid directory ... jdk-17.0.20.101-hotspot". Windows already holds the right value, so T3's process has the old one from before the JDK update
- Retry only if: T3 Code has been restarted, then `device_open` on `IS_Pixel_XL`. Wireless debugging is also untested: the owner has to pair the phone once
- Commit: see `Add a way to test without plugging in the phone` in `git log dev`
- Lesson: an updated JDK leaves running apps with the old path; check the machine's value before calling an environment variable wrong.

### 2026-11-06: B14, one place reads File Search's Show Thumbnails
- Command: /improve (scheduled run)
- Target and evidence: B14. Four copies of `if (filesThumbnails)` in the overlay, guarded by `FileThumbnailsScopeTest`, which greps source lines and only catches a web picture read on the same line as `imageUrl`, `showThumbnail` or `Web(`. Chosen over B11 and B13 to B17: it is the smallest, it removes a cause, and it takes test code down
- Prior work checked: this ledger, `git log dev -S"FileResultPicture"` (only this ledger's backlog), claude-mem #23374, #24408. CI green on `5a9f0753` before starting
- Research: read all 4 call sites and `FileIconThumbnail`. Their off-state pictures differ (size, container color, symbol), so each stays as a `whenOff` slot, moved word for word; merging them would be a visual change nobody asked for
- Models used: inline (Opus 5.5), no subagents
- Usage level: no figure visible. Lean mode
- Change: new `FileResultPicture(s, file, size, whenOff)` takes a `FileItem` and is the only reader of the switch in the overlay; `FileIconThumbnail` is now private to its file; `FileThumbnailsScopeTest` removed
- Test removed, 1: the failure is now much harder to write, since the picture can't be drawn around the switch and the switch isn't read at the call sites. It isn't strictly impossible: new code could still read `settingsState.filesThumbnails` directly. No test now references the switch at all. No golden draws a file with it off, so CI doesn't prove the 4 `whenOff` slots; they were checked by reading the diff
- Before -> after: readers of the switch in the overlay 4 -> 1; test code 52,108 (the 52,098 above was already out of date) -> 52,078; app code 76,502 -> 76,507 lines, the KDoc and signature; release APK, unsigned local builds of `5a9f0753` and this change, 12,908,309 -> 12,908,309 bytes, with `classes2.dex` up 628 bytes (4,892,612 -> 4,893,240) and absorbed by zip alignment; lint baseline 19 -> 19. The 12,920,597 in the ratchet is a signed build, so it isn't comparable and stays as it is
- Result: kept, pending CI
- Emulator: not used. `device_list` now shows `IS_Pixel_XL`, but `device_open` still fails: "Failed to allocate an emulator port for IS_Pixel_XL", from `avdmanager list avd`: "JAVA_HOME is set to an invalid directory: C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot\". This run's own environment holds the same old path. Whether T3 Code was restarted since the last entry isn't known
- Retry only if: a change to the switch needs its off state proven; then render one file result with it off
- Commit: see `Read file thumbnails switch in one place` in `git log dev`
- Lesson: an APK can keep its exact size when its code changes, so compare dex sizes and checksums before believing two builds really differed.

### 2026-11-06: One setup for every run, and layers of checking before main
- Command: owner said to set up an environment where the only block is the final approval before merging, reviewed by Claude first, and that the layers exist to block bad code, so only the best of each layer's reports should be acted on
- Target and evidence: (1) `device_open` failed even with `IS_Pixel_XL` already booted by hand, because T3's process held a deleted `JAVA_HOME`. (2) Once that cleared, every command after `device_open` failed with `DEVICE_IN_USE`, because sessions from 2026-10-29 and 10-30 still claim `emulator-5554`, `5558` and `5560`, the ports T3 and the doc used. (3) `RELEASE_STORE_FILE` is relative, so worktree release builds came out unsigned without saying so, and those can't be installed. (4) Nothing reviewed code before `main`, and nothing separated what was reviewed from newer `dev` commits. No proxy, sandbox or permission rule was blocking: Android and GitHub hosts answered (`m3.material.io` answered too, refusing only the HEAD request), and user settings hold no restrictions
- Prior work checked: the 2026-11-06 emulator entry above, memory `emulator-before-phone`
- Research: a `workflow_dispatch` promote job was rejected: it has to be on `main` first, and `GITHUB_TOKEN` can't push commits that change workflow files. A pull request needs neither
- Models used: inline (Opus 5.5)
- Usage level: no figure visible. Lean mode
- Change: `scripts/run-env.ps1` (stable JDK link, user `JAVA_HOME`, worktree signing, status report, `-Emulator` on port 5580); `/review-candidate`; `candidate` branch and pull request as the owner's approval; `.claude/settings.json` refuses merges and pushes to `main`; CI no longer runs for pull requests to `main`, which hold commits it already passed; charter section 9
- Before -> after: `device_open` and driving the app: failed -> works after the owner restarted T3 Code: launched 9.5.5 (133), skipped the tutorial, read the search screen and took a screenshot on `emulator-5580`. Signed release from a worktree: no -> yes, installed at 12,920,597 bytes, matching the ratchet. Claude review before `main`: none -> required. `device_screenshot` still fails; the `agent-device` `screenshot` command works, so the docs use that
- Result: kept, pending CI. The old sessions are left alone, since they belong to other threads
- Retry only if: `DEVICE_IN_USE` appears on `emulator-5580`, or `device_screenshot` is needed
- Commit: see `Set up runs and a review before main` in `git log dev`
- Lesson: an error's first line isn't always its cause. The port error was really `JAVA_HOME`, and the helper failure after it was really `DEVICE_IN_USE`, two lines higher in the output.

### 2026-11-06: No turns spent waiting
- Command: owner said scheduled messages shouldn't spend tokens, and neither should a new prompt that only says something is still being waited on
- Target and evidence: runs were told to wait for CI. A wait longer than 10 minutes moves to the background and restarts the session with a new prompt when it ends, re-reading its whole context to learn one result. This session did that 3 times
- Prior work checked: charter section 4; the next run already reads CI on the newest commit first, so the wait added nothing
- Models used: inline (Opus 5.5)
- Usage level: no figure visible. Lean mode
- Change: runs and reviews push and end; the next run reads the result; the 4-hour task's prompt says the same
- Before -> after: prompts spent per run waiting on CI, at least 1 -> 0
- Result: kept. For the two entries above marked pending: CI `Build & Check` green on `0aac9df7` (721s against the ratchet's 527s, with no test or workflow change to explain it; not investigated) and on `0621f24c`
- Retry only if: failing commits sit on `dev` long enough to hold up a review
- Commit: see `Stop spending turns waiting on CI` in `git log dev`
- Lesson: a wait is only free if nothing has to wake up afterward.

### 2026-11-07: Replaced the local setup with one workflow on GitHub
- Command: owner said the setup was overly complicated and less secure, that using their local key on a public project is dangerous, and that the process should be automated with checks, ending in only "do you want to merge, yes or no"
- Target and evidence: the setup in the two entries above ran an unattended agent with full access on the owner's PC, holding their signing keystore and their GitHub login (`repo` and `workflow` scopes), and only a deny list stopped it merging. It also dropped CI for pull requests to `main`, so an outside pull request would have run no checks. Waiting for CI restarted sessions and spent usage
- Prior work checked: the two entries above. Pushed files, history and tags: the keystore and `local.properties` were never committed
- Research: `anthropics/claude-code-action` docs and `action.yml`. `GITHUB_TOKEN` can't change workflow files or approve its own pull request, and needs nothing from the owner
- Models used: inline (Opus 5.5); haiku for the docs lookup
- Usage level: no figure visible. Lean mode
- Change: `improve.yml`, `declined.yml`, `smoke.sh`; removed `scripts/run-env.ps1`, `/review-candidate`, `.claude/settings.json` and the `candidate` idea; restored CI for pull requests to `main`; both T3 scheduled tasks paused
- Before -> after: unattended runs holding the owner's keystore and login: yes -> no. Merge without the owner: stopped by a deny list -> refused by GitHub. Claude usage spent waiting: at least one prompt per run -> none
- Result: kept, proof of the full run recorded below when it has run
- Retry only if: n/a
- Commit: see `Run improvements on GitHub, ending in one question` in `git log dev`
- Lesson: when asked to remove blocks, don't remove them by handing an agent the owner's keys. Move the work somewhere the keys aren't.

### 2026-11-07: Review here, not on GitHub; pipeline removed
- Command: owner said Claude's review before their approval was meant to happen here, not on GitHub, and that running Claude on GitHub would burn tokens for no reason
- Target and evidence: `improve.yml` from the entry above ran Claude on GitHub. It never ran: it was never on `main`, no token was stored, and no pull request was opened
- Change: removed `improve.yml`, `declined.yml`, `smoke.sh`; added `/merge-check`; every GitHub setting from the last two entries restored to what it was before them; runs hold `sdk.dir` only, never the signing key; runs push and end instead of waiting for CI; the review task deleted and the 4-hour task re-enabled
- Before -> after: Claude on GitHub: none -> none. Unattended runs holding the signing key: yes -> no
- Result: kept. This supersedes the entries `One setup for every run` and `Replaced the local setup with one workflow on GitHub`
- Retry only if: the owner asks for runs with the PC off
- Commit: see `Review here before merging, not on GitHub` in `git log dev`
- Lesson: when a request can be read two ways and the build is large, say the plan in one line first. Two rebuilds came from not doing that.

### 2026-11-06: B15, one top bar for settings pages
- Command: /improve (scheduled run)
- Target and evidence: B15. 18 settings pages each wrote out their own `TopAppBar`, title and back button, guarded by `everySettingsTopBarTitleIsAPageTitle`, which reads source text with a regex. Chosen over B11 (only adds tests, against the owner's rule), B13 (saving is small, measure first), B16 (large) and B17 (backup data, wants its own run)
- Prior work checked: this ledger, `git log dev -S"SettingsTopBar"` (ledger text only), claude-mem search (no earlier attempt). CI green on `821bbc59` before starting
- Research: read all 18 calls. The drift B15 listed isn't visible: `Filled` and `Outlined` `ArrowBack` in `Symbols.kt` have identical path data. The two color forms differ only in the scrolled color, and no bar here has a `scrollBehavior`, so it is never used. All 18 already resolve to the same package `IconButton`, so haptics don't change
- Models used: inline (Opus 5.5), no subagents
- Usage level: owner said during this run they have full use with no limits; no figure visible. Normal mode, and charter section 7 now says so for runs that can't see a figure
- Change: new `SettingsTopBar(title, onBack, ...)` takes the title as a `String` and draws it as a `SettingsPageTitle`; every page uses it; `settingsTopBarColors` is private; `@OptIn` and imports it left unused are gone. `CountdownConfigActivity` stays as it is: it is its own activity, outside settings, and was never in the 18
- Test changed, assertion untouched: `aPageTitleIsTheHeadingAndPaneTitle` now renders the real bar. It passes locally, as do all 16 tests in `SearchPillsScreenTest`, which covers the rewritten title in its picker states. The rest of the suite, lint and the goldens are left to CI
- Test removed, 1: the source grep. A page using `SettingsTopBar` can't get the title wrong. It isn't strictly impossible: new code could still call `TopAppBar` itself, and nothing now catches that
- The lint module half of B15: not built. It would be larger than the 48 lines of `SettingsPageTitleTest` and `LayeringTest` it replaces and add a module to every CI build, so `LayeringTest` stays
- Before -> after: raw `TopAppBar` in settings 18 -> 1; app code 76,507 -> 76,372 lines; test code 52,078 -> 52,060; release APK, unsigned local builds, 12,908,309 -> 12,908,309 bytes, with `classes2.dex` down 6,436 bytes (4,893,240 -> 4,886,804); lint baseline 19 -> 19
- Result: kept, pending CI. Nothing should look different, so no emulator check; the goldens are the proof
- Retry only if: a golden changes on a settings page, or a raw `TopAppBar` turns up in settings
- Commit: see `One top bar for settings pages` in `git log dev`
- Lesson: check that drift a backlog item names can actually be seen before counting it as evidence. Here it couldn't, and the real gain was less code.

### 2026-11-06: B17, every setting through one backup round trip
- Command: owner said "Continue"
- Target and evidence: B17. Round trips were written one setting at a time, so a new setting had none unless someone remembered. How many settings had none wasn't counted; the strength is the missing guarantee, not a known bug. Chosen over B13 and B16 as the next backlog item, before B13's new numbers below were known
- Prior work checked: this ledger, `git log dev -S"SettingsManager.ALL" -- app/src/test`, claude-mem search (no earlier attempt). CI green on `b19aaa19` before committing
- Research: `createBackupPayload` copies the whole settings file, so a setting can't be forgotten on export. What can go wrong is on restore: the name-based blocks, the type and range checks, the opt-in list, and the older snapshot pass that runs after them
- Models used: inline (Opus 5.5); fable/xhigh for one independent review, as charter section 6 asks for backup data (about 200k tokens)
- Usage level: owner said they have full use with no limits; no figure visible. Normal mode
- Change: new `EverySettingBackupTest`, over `SettingsManager.ALL`: both values of every setting, old spellings, out-of-range numbers, removed names, and a named list of what a restore holds back. No app code changed
- Result of the first run: every declared setting already behaves correctly, so this found no bug
- Proof it can fail: restore broken on purpose, then put back, 7 ways: an opt-in a file can switch on, a setting silently dropped, a device-local setting exported, removed names restored, ranges unchecked, ranges clamped instead of rejected, old spellings left behind. The first version missed old spellings, and the review found it would pass a clamp; both were fixed, and all 7 are now caught
- Tests removed, 18, all asserting what the new test or `BackupPreferenceSchemaTest` asserts, or restating a constant: all 3 in `KeyboardOptionsBackupTest` (file deleted); `theSettingIsAKnownString`; 3 in `AtAGlanceSettingsBackupTest`; 2 in `InstantAnswersBackupTest`; 3 in `LaunchPortalBackupTest`; 2 in `LiveCurrencyRatesBackupTest`; `aBackupRestoresTheAppsButNotTheSkippedFoldersOrTheOldFileTypes`; `countdownWidgetEventsStayOnThisPhone`; 2 in `WidgetShapeBackupTest`. Tests with logic of their own stay (pill opacity, quick panel, widget color, currency schedule, `characterization/`)
- Not covered: keys outside `ALL`, such as the per-design widget colors; their own tests stay. Also no longer pinned: `SHOW_KEYBOARD_ON_OPEN` defaulting to on, and individual names staying in `REMOVED_NAMES`. The review found nothing reads those names, so one slipping out would be an unused leftover
- Before -> after: settings with a restore test, not counted -> all in `ALL`; test code 52,060 -> 51,924 lines; backup tests 105 -> 92 (18 out, 5 in), all passing locally; the rest of the suite, lint and goldens left to CI; app code, APK and lint baseline unchanged
- CI on `b19aaa19` (B15, entry above): green, 1003s. Not a regression, and it answers the 721s left unexplained on `0aac9df7`: see the ratchet row and B13
- Result: kept, pending CI
- Retry only if: a setting needs a restore rule of its own; add it to the test's named lists
- Commit: see `One backup test for every setting` in `git log dev`
- Lesson: break the code on purpose before trusting a new test. This one passed first time while missing two of the failures it was written to catch.

### 2026-11-07: B13, the minified build runs beside the tests
- Command: /improve (scheduled run)
- Target and evidence: B13. `testDebugUnitTest` took 649s on `b19aaa19`, where R8 ran 510s on the same four cores, and 398s on `fca3e3da`, where R8 came from cache. Chosen over B11 (only adds tests, against the owner's rule), B16 (large, wants its own run) and B18 (marked lowest priority, saves less)
- Prior work checked: this ledger; `git log origin/dev -S"assembleRelease" -- .github/workflows/ci.yml`; claude-mem search (#31271, no earlier attempt). CI green on `fca3e3da` before starting
- Research: read both runs' logs and step times. Branch protection requires the context `Build & Check` from GitHub Actions, with `strict: false`, so the job name must stay. No test or doc depends on the job layout. `setup-gradle` keys its home cache by job and falls back to any entry on the same OS, so a new job starts warm
- Models used: inline (Opus 5.5), no subagents
- Usage level: no figure visible. Normal mode
- Change: `ci.yml` has jobs `Lint & Tests` and `Minified Build` in parallel, and a `Build & Check` job that needs both, runs with `always()` and fails unless both report `success`. Without `always()` a failure would skip it, and a skipped required check counts as passing. Shared setup moved word for word to `.github/actions/gradle`, so its pinned versions can't drift between the jobs. No app or test code changed
- Before -> after: whole `Run Build & Check` step on an app code commit, 1003s -> about 750s expected, an estimate from the log (tests started 354s in, plus 398s), NOT measured yet. Gate script run locally under `bash -e`, as Actions runs it: only `success success` exits 0; failure, cancelled or skipped in either job exits 1. The real gate, the composite action and the Gradle cache have not run on GitHub yet. Test code, app code, APK and lint baseline unchanged
- Trade, written per charter section 2: a commit with R8 cached or docs only gains nothing and takes a few seconds longer for the third job, and every commit uses more runner minutes (free on this public repo). Actions cache was already 10.5 GB in 77 entries, over GitHub's usual 10 GB, so old entries are being evicted; the second job's entries add to that, though the newest ones are what a run needs
- How to read the result: this commit touches no app code, so it can't show the saving, and `minifyReleaseWithR8` should come `FROM-CACHE`. Measure on the next commit that changes app code: start of the run to `Build & Check` finished, against 1003s
- Result: kept, pending CI
- Retry only if: `Build & Check` fails while both jobs passed, or passes while one failed (fix at once, since it gates `main`); or the next app code commit isn't faster than 1003s (then revert this)
- Commit: see `Run the minified build beside the tests` in `git log dev`
- Lesson: check a shell gate under the same flags the runner uses. `set -e` is ignored inside `( ... ) && ...`, so my first check of this one passed cases that really fail.

### 2026-11-07: Cycles run back to back
- Command: owner said "Runs need to happen concurrently. One after the next. Do not wait for me."
- Target and evidence: a 4-hour gap between runs. Read as one after another, not at the same time: the runs share one thread and worktree, and two pushing to `dev` together would collide
- Research: the scheduler's triggers are interval, fixed time, once and webhook, with nothing for "when the last run ends". What a short interval does while a run is active isn't documented, and an overlapping run would stop at the unfinished-work check
- Models used: inline (Opus 5.5)
- Usage level: owner said they have full use with no limits; no figure visible. Normal mode
- Change: each run's last action is `run_scheduled_task_now`; the 4-hour interval stays only to restart a broken chain; a CI run still in progress no longer holds up a cycle; the chain stops when a run finds unfinished work or no candidate has evidence
- CI on `49b44ad7` (B13, entry above): all three jobs green, about 76s, as expected with nothing to rebuild. The saving is still to be measured on an app code commit. A failing job hasn't gone through the gate on GitHub yet
- Before -> after: gap between runs, 4 hours -> none, once proven by a run starting from another
- Result: kept, pending that proof
- Retry only if: `run_scheduled_task_now` returns null from inside a run, or starts a run on top of an active one
- Commit: see `Run improvement cycles back to back` in `git log dev`
- Lesson: more runs means commits land faster than CI finishes, so read the newest finished run rather than the newest commit.

### 2026-11-07: B16, tests that say nothing happened now wait for the work to end
- Command: /improve (scheduled run)
- Target and evidence: B16. A query handed in from another app must stay on the phone until Enter, and the tests for that checked "no request went out" after a fixed 250ms. Reproduced: with the gate removed and the request taking 400ms, `editingAHandedInQueryMakesItTheUsersOwn` and `aHandedInQueryIsSearchedOnThePhoneOnlyUntilTheUserPressesEnter` both passed. Chosen over B11 (only adds tests, against the owner's rule) and B18 (weaker, saves time only)
- Prior work checked: this ledger, `git log dev -S"runOn()"`, claude-mem #31243, #31237. Newest finished CI, `49b44ad7`, green; `503c11a4` still running at the start, green (1m42s) by the time of this push
- Research: read the suggestion and live answer paths. `Settle.kt`'s thread check wasn't reused: a parked worker that has just been handed a task looks idle, so on a busy machine it would be worse than the 250ms
- Models used: inline (Opus 5.5), no subagents
- Usage level: no figure visible. Normal mode
- Change: `SearchViewModel` and `LiveAnswerEngine` take `@IoDispatcher` and `@DefaultDispatcher` (`DispatchersModule`, which provides the same `Dispatchers.IO` and `Default` as before). In tests a `@TestInstallIn` module wraps them to count each task from hand-over to end, and `finishBackgroundWork()` runs the looper and those tasks until nothing is left; it replaces `runOn()` and the wait in `FileTypeFilterTest`. Assertions untouched. The 3 copies of `await` became one `awaitState`. The other 106 `Dispatchers.*` uses stay until a test needs them: converting them all has no measured gain
- Proof, the same breaks again: the 2 tests above now fail on their own assertions (`expected:<[]> but was:<[weather today, weather tomorrow]>`). So do `aQueryWithABangAsksForNoWebSuggestions` with the bang check removed (`was:<[!w android]>`) and `awaitSearchWaitsForTheRunThatReplacedTheOneItWasWaitingFor` with `awaitSearch` returning on a cancelled job. That last one was not a real gap before: on an earlier, blunter break of `awaitSearch` its wait passed wrongly, but a later assertion in the test failed. Those two were not run against the old wait with these exact breaks
- Not counted by `finishBackgroundWork`: real-time `delay`s (the answer debounce, the spelling pause), and code on dispatchers still hard-coded (`UnifiedSearchCoordinator`, `CorpusIndexManager`, Room). None of the 4 checks depends on them, since both gates are decided on the main thread. `LiveAnswerViewModelTest`'s `assertNull(state.liveAnswer)` lines read at once with no wait, so they can't see a late answer; left alone, because an answer sent through the debounce would still arrive after this helper returns
- Before -> after: `Thread.sleep` in tests 15 -> 12; hard-coded `Dispatchers.*` in app code 117 -> 106; test code 51,924 -> 51,953 lines, up 29: the reason is that the checks guarding what leaves the phone can now fail; app code 76,372 -> 76,406 lines; release APK, unsigned local builds, 12,908,309 -> 12,908,309 bytes, with `classes2.dex` up 128 bytes (4,886,804 -> 4,886,932); lint baseline 19 -> 19
- Tests run here: `SearchViewModelTest` and `FileTypeFilterTest` 3 more times with no failures, plus `LiveAnswerViewModelTest`, `LiveAnswerEngineTest`, `StartupSyncTest`, `RecordOpenedTest`, `SubmitQueryTest`. The rest of the suite, lint and goldens are left to CI; every Hilt test now runs through the counting wrapper
- Result: kept, pending CI. Nothing should look different, so no emulator check
- This commit changes app code, so its CI run is the measure B13 was waiting for: start of the run to `Build & Check` finished, against 1003s
- Back-to-back runs: this run started at 3:26, the minute `503c11a4` was pushed, which fits a run starting from another. Inferred from the time; nothing here shows what started it
- Retry only if: a test fails with "background work still running", or a Hilt test turns flaky without a change to its screen; then look at what the wrapper changes before removing it
- Commit: see `Wait for background work in tests, not a fixed time` in `git log dev`
- Lesson: to prove a "nothing happened" check, make the thing happen slowly. Removing a gate alone doesn't show it: a fast request is caught by the fixed wait and the test looks fine.


### 2026-11-07: B18, the morph physics rules share one run per seed
- Command: /improve (scheduled run)
- Target and evidence: B18. `MorphBouncePhysicsTest` took 92.5s here (113s on CI by the backlog), and a profile of it (JFR, 5,909 samples) put 92% of the time in planning drops (`forecast`, `sheetStep`). Seven of its tests each ran their own 30 to 60s world for the same seeds, so seeds 1 to 6 were simulated seven times over. The physics itself wasn't changed: planning is what the phone needs, and the tests already prove a seed gives the same run. Chosen over B11, which turned out to be mostly done already: `GlanceMediaCardTest` covers `scaledGlanceArtwork` and `glanceArtistOf`, and only the private `glanceMedia` has no test
- Prior work checked: this ledger, `git log origin/dev -S"MorphBouncePhysicsTest"` (no earlier attempt at its time), claude-mem #31329. Newest finished CI, `f15b95a3`, green
- Models used: inline (Opus 5.5), no subagents
- Usage level: no figure visible. Normal mode
- Change: the 7 seeded rule tests became rules watching one shared run per seed, in the order and over the time each watched before; each test reports its own rule's first failure, and a failing rule stops being watched while the run goes on for the others. Rule bodies and assertions are word for word. No app code changed
- Proof: the physics broken 8 ways, each run against the old tests and the new: forecast rules off, every touch a bounce, a shape skipped, every later bounce sparkling, dents growing back, a fifth shape, landings off screen, a shape above the card. The same tests failed both times, with the same messages, in all 8 (the fifth-shape break failed nothing in either, because planning refuses a fifth shape on its own)
- Before -> after: `MorphBouncePhysicsTest`, local, 92.5s -> 51.6s, and its 7 rules 55.1s -> 14.6s, 23 of 23 passing; test code 51,953 -> 51,985 lines, up 32: the reason is the shared runner, which buys back about 41s of CPU on every CI run; app code, APK and lint baseline unchanged. Only this class was run here; the rest is left to CI
- CI on `f15b95a3` (B16), the first app code commit since B13: 872s from the start of the run to `Build & Check`, against 1003s on `b19aaa19`; `Minified Build` took 333s beside the tests. That is the measure B13 was waiting for, so B13 stays
- Result: kept, pending CI
- Retry only if: one of these tests fails on CI with no change to the physics; check that every rule still sees the frames it saw before
- Commit: see `Share one physics run between the morph rules` in `git log dev`
- Lesson: profile a slow test before shortening it. Here the cost was in planning the phone needs too, so sharing the runs was the only saving that left every check intact.

### 2026-11-07: B19, lint baseline issues that had a fix
- Command: /improve (scheduled run)
- Target and evidence: B19. 8 of the 19 baseline issues had a direct fix: 5 `ModifierParameter`, 2 `UseKtx` `SharedPreferences.edit` in `SettingsManager`, 1 `UseCompatLoadingForDrawables` in `IconPackManager`. Chosen as the only backlog item left with a ratchet number behind it: B11 is mostly done (B18 entry) and only adds tests
- Prior work checked: this ledger, claude-mem search (earlier `ModifierParameter` fixes in other files, none of these). Newest finished CI, `f15b95a3`, green; `e600895f` still running at the start
- Research: every caller of the 4 reordered composables uses named arguments, which the compiler confirmed. `GeminiCornerSwipeWave.kt` is not the locked `AGSL_GEMINI_PLATE` (that is `DynamicMeshShader.kt`), and only its parameter order moved. `SettingsCard`'s old default held its width and 4dp inset, so a caller passing a modifier had to repeat them: `Modifier.backupCard` was that copy, and the widget design switcher repeated it with 16dp
- Models used: inline (Opus 5.5), no subagents
- Usage level: no figure visible. Normal mode
- Change: `modifier` moved to the first optional parameter of `GeminiCornerSwipeWaveLayer`, `AnimatedCopyChip`, `SettingsRow` and `SettingsRowToggle`; `SettingsCard` defaults to `Modifier` and always applies its own width and inset, so `backupCard` is gone (its 4 cards pass only their test tag) and the switcher passes 12dp on top of the card's 4dp, the same 16dp as before; the two mirror writes use KTX `edit`, which applies as before; icon pack drawables load through `ResourcesCompat.getDrawable`. 8 entries out of `app/lint-baseline.xml`
- Before -> after: lint baseline 19 -> 11 (`grep -c '<issue$'`); app code 26 lines in, 28 out; test code 51,985 -> 51,985. Layout should not change: same width and insets everywhere, proven by the goldens on CI, so no emulator check. Release APK not rebuilt for a change this small; lint itself is left to CI as well
- Result: kept, pending CI
- Retry only if: lint reports one of the 8 again, or a settings or backup golden shifts by a card inset
- Commit: see `Clear eight lint baseline issues` in `git log dev`
- Lesson: a modifier default that carries layout makes every caller who passes a modifier copy it. Put the layout inside and keep the default `Modifier`.

<!--
Copy this for each cycle, newest at the bottom.

### YYYY-MM-DD: <short target>
- Command: /improve | /debug | /audit
- Target and evidence: <what, and the proof it matters>
- Prior work checked: <ledger IDs, commits, claude-mem observation IDs, or "none found">
- Research: <docs or source read, what they showed>
- Models used: <e.g. haiku/low for search, opus/high for root cause>
- Usage level: <weekly %, mode>
- Change: <files, one line each> or "none"
- Before -> after: <metric values>
- Result: kept | reverted | rejected | blocked | no change needed
- Retry only if: <condition, or "n/a">
- Commit: <sha> | none
- Lesson: <one line for future cycles>
-->
