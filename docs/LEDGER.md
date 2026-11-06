# Improvement Ledger

Append-only. Read it before choosing a target (`docs/IMPROVEMENT.md` section 4). Never delete an entry. If a later
cycle proves an entry wrong, add a new entry that says so.

## Ratchet values

Update these in the same commit as a change that improves them. Never let one get worse (`IMPROVEMENT.md` section 2).

| Metric | Value | Measured at |
|---|---|---|
| Lines of test code | 52,098 | 2026-11-06, replaces the `@Test` count (owner: root causes over more tests) |
| CI time, `Run Build & Check` | 527s (was 1118s) | run 42277305877 on `8de98e0a` |
| Flaky CI failures | 1 in the last 40 runs (B12) | 2026-11-06 |
| Screenshot goldens | 232 | `519e738d`, 2026-11-01 |
| Lint baseline issues | 19 | `0260afa7`, 2026-11-05 |
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
| B13 | Run `assembleRelease` beside the tests, not during them | Tests took 20 minutes with R8 overlapping and 13 with R8 cached on the same commit, before the `settle` fix. Parallel jobs under one aggregator named `Build & Check`, which must check `needs` results (a skipped job counts as passing). Saving smaller now: measure first. | 2 | 2026-11-06 |
| B14 | One `FileResultPicture` instead of 4 copies of `if (filesThumbnails)` | `OverlayBestMatchCard:274`, `OverlayDetailPane:261`, `OverlayPinnedRow:112`, `OverlaySearchResults:690`. Then `FileThumbnailsScopeTest`, which greps source lines, can go. | 2 | 2026-11-06 |
| B15 | One `SettingsTopBar` and a small lint module | 18 raw `TopAppBar(` calls, with drift: 16 Outlined back arrows, 2 Filled. Replaces `SettingsPageTitleTest` and `LayeringTest`, which read source text. | 2 | 2026-11-06 |
| B16 | Inject dispatchers so tests can stop polling in real time | 117 `Dispatchers.*` uses and no seam. `SearchViewModelTest.runOn` and `FileTypeFilterTest:181` assert nothing happened after about 250ms, which passes wrongly on a slow machine. Large. | 2 | 2026-11-06 |
| B17 | One table-driven backup round trip | 7 `*BackupTest` classes each build Room and run PBKDF2 for their own settings; a forgotten key can slip through. Over `SettingsManager.ALL`, a new setting needs no new test. Backup data: use the strongest model. | 2 | 2026-11-06 |
| B18 | Share one recorded run between the morph physics rules | `MorphBouncePhysicsTest` is now the slowest class, 113s of 556s. Its 23 tests hold real rules about physics, so this only saves time: lowest priority. | 3 | 2026-11-06 |

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
