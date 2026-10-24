# Repository History

How the branches, tags and releases fit together, so any version, crash report or change can be traced to its code.

## Branches

| Branch | What it is |
|---|---|
| `main` | What ships. Protected: a push needs a commit that already passed `Build & Check`, and force pushes are blocked. |
| `dev` | Where work lands. `main` is always an ancestor of `dev`, and reaches it by fast-forward (`git push origin dev:main`) once CI is green. |
| `dev2` | A separate from-scratch rebuild with a new look (app ID `.dev2`, installs beside the main app). Its own checkout is `F:/Intelligent-Search-dev2`. |

History is one line from `Initial commit` (2026-07-30). Larger batches came in through merge commits titled `Merge: ...`, and
the rest are single commits. To read it, use `git log dev` or `git log main`, not `git log --all`. On the owner's PC,
`--all` also walks refs that T3 Code keeps under `refs/t3/` (its own checkpoints), and those hold copies of commits that
were never on GitHub.

## Tags

- **`v<versionName>-<versionCode>`**: an annotated tag on the commit that set that version in `app/build.gradle.kts`,
  for example `v9.3.1-125`. Every committed version has one.
- **`9.1.9`**: an older lightweight tag that the 9.1.9 GitHub Release is attached to. `v9.1.9-121` is the same commit.
- **`main-before-dev`**: where `main` stood on 2026-10-17, before the then `dev` line was adopted as `main`. It is an
  ancestor of today's `main`, so nothing was lost.
- **`gemini-search-plate-golden-v1`**: the reference commit for the locked Gemini search plate shader
  (`AGSL_GEMINI_PLATE` in `DynamicMeshShader.kt`).
- **`dev-test-<date>-<commit>`**: test builds for the phone, each published as a non-latest pre-release. Only the newest
  is kept.

## Versions

| Version | Code | Commit | Date |
|---|---|---|---|
| 5.0 | 6 | `bff858cb` | 2026-07-30 |
| 7.8 | 70 | `383f5d6e` | 2026-08-08 |
| 8.1 | 83 | `236aa6d6` | 2026-08-13 |
| 8.9 | 91 | `4bca789c` | 2026-09-03 |
| 9.02 | 97 | `33742ff1` | 2026-09-13 |
| 9.05 | 103 | `4804e704` | 2026-09-18 |
| 9.1 | 106 | `bcb9fdbc` | 2026-09-28 |
| 9.1.2 | 110 | `514790a5` | 2026-10-03 |
| 9.1.4 | 112 | `40032529` | 2026-10-04 |
| 9.1.5 | 113 to 115 | `e3a9e493` to `37329dd1` (tag on 115) | 2026-10-05 |
| 9.1.6 | 116 | `19bafdba` | 2026-10-07 |
| 9.1.7 | 118 | `4708a3a3` | 2026-10-09 |
| 9.1.8 | 120 | `d2575037` | 2026-10-11 |
| 9.1.9 | 121 | `b5c82927` (the released commit) | 2026-10-11 |
| 9.2 | 122 | `0f889e01` | 2026-10-13 |
| 9.3 | 123 | `e1ac99c9` | 2026-10-17 |
| 9.3.1 | 124 | `2ab4882f` | 2026-10-17 |
| 9.3.1 | 125 | `678bc5cf` (the released commit) | 2026-10-18 |
| 9.4 | 126 | the commit tagged `v9.4-126` | 2026-10-23 |
| 9.4.1 | 127 | the commit tagged `v9.4.1-127` | 2026-10-24 |

Some codes that reached Google Play were never committed with their number: 9.06 (105), 9.1.1 (109), and 111, 117 and
119. A crash report from one of them belongs to the code between the tags either side of it: 105 sits between
`v9.05-103` and `v9.1-106`, and 109 between `v9.1-106` and `v9.1.2-110`.

## Releases

GitHub Releases exist for 8.3, 8.8, 9.1.9 and 9.3.1 (125); 9.3.1 is marked Latest. Pushing a `v*` tag runs
`.github/workflows/release.yml`, which builds, signs and publishes the release. It needs the repository secrets
`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, and stops before
building when they aren't set. Until they are, releases are made by hand.

## Reading crash reports

Release builds are minified by R8, so a Play Console trace from a build without its mapping shows names like `yy2.j`.
The mapping file is in `app/build/outputs/mapping/release/mapping.txt` after a release build, and the release workflow
attaches it to each GitHub Release as `IntelligentSearch-<version>-mapping.txt`. An app bundle (`.aab`) uploaded to Play
carries its mapping automatically, so Play shows real names; an APK upload doesn't.

Crashes already traced to their fixes:

| Crash in Play Console | Version | Fixed by |
|---|---|---|
| `IntelligentSearchDatabase ... onOpen`, `SQLiteException` | 9.1.1 (109) | `514790a5`, then `e9af597f` (`DatabaseOpenPragmasTest`) |
| `SearchTileService.onClick`, "pendingIntentBackgroundActivityStartMode must not be set when creating a PendingIntent" | 9.1.5 (115) | `BackgroundLaunchOptions.forCreator`, with a fallback launch |
| `LayoutNodeSubcompositionsState.subcompose` in the overlay's list (a key used twice) | 9.06 (105) | `84ba2cc1` (`UniqueLazyItemsTest`) |

## Commits

Each commit message is one imperative line saying what changed for the user, with no AI attribution.
