# Repository History

How the branches, tags and releases fit together, so any version, crash report or change can be traced to its code.

## Branches

| Branch | What it is |
|---|---|
| `main` | What ships. Protected: a push needs a commit that already passed `Build & Check`, and force pushes are blocked. |
| `dev` | Where work lands. Reaches `main` by a pull request the owner approves (`docs/IMPROVEMENT.md` section 4). Commits before 2026-11-07 reached it by fast-forward. |

History is one line from `Initial commit` (2026-07-30). On 2026-11-01 it was cleaned up to one commit per release
(`Release <version> (<code>)`) plus the main feature changes between releases, each holding exactly the code it had;
the small fixes, agent merge commits and failed builds in between were folded into them. The full earlier history (702
commits) is kept in a backup bundle on the owner's PC, `Downloads/IntelligentSearch-history-backup-2026-11-01.bundle`
(`git clone` it, or `git fetch` from it, to read the old commits). To read history, use `git log dev` or `git log main`,
not `git log --all`. On the owner's PC, `--all` also walks refs that T3 Code keeps under `refs/t3/` (its own
checkpoints), and those hold copies of commits that were never on GitHub.

## Tags

- **`v<versionName>-<versionCode>`**: an annotated tag on that version's release commit, for example `v9.3.1-125`.
  9.4.1 (127), 9.5 (128), 9.5.2 (130) and 9.5.3 (131) were never tagged; their `Release` commits are in the table below.
- **`9.1.9`**: an older lightweight tag that the 9.1.9 GitHub Release is attached to. `v9.1.9-121` is the same commit.
- **`main-before-dev`**: the code `main` held on 2026-10-17, before the then `dev` line was adopted as `main`: one
  commit off the main line. `v9.3-123` is likewise one commit off the main line, as 9.3 was released from that old
  `main`.
- **`gemini-search-plate-golden-v1`**: the reference commit for the locked Gemini search plate shader
  (`AGSL_GEMINI_PLATE` in `DynamicMeshShader.kt`).
- **`dev-test-<date>-<commit>`**: test builds for the phone, each published as a non-latest pre-release. Only the newest
  is kept.

## Versions

| Version | Code | Commit | Date |
|---|---|---|---|
| 5.0 | 6 | `bff858cb` | 2026-07-30 |
| 7.8 | 70 | `8006804e` | 2026-08-08 |
| 8.1 | 83 | `f978c6b9` | 2026-08-13 |
| 8.9 | 91 | `ed567cba` | 2026-09-03 |
| 9.02 | 97 | `d13d4321` | 2026-09-13 |
| 9.05 | 103 | `43d3c424` | 2026-09-18 |
| 9.1 | 106 | `74f968d6` | 2026-09-28 |
| 9.1.2 | 110 | `0f11ec70` | 2026-10-03 |
| 9.1.4 | 112 | `4ac9c40d` | 2026-10-04 |
| 9.1.5 | 113 to 115 | `3cebfa8c` (113 and 114 folded into it) | 2026-10-05 |
| 9.1.6 | 116 | `b105abe8` | 2026-10-07 |
| 9.1.7 | 118 | `6019e6d2` | 2026-10-09 |
| 9.1.8 | 120 | `fb969db2` | 2026-10-11 |
| 9.1.9 | 121 | `35e71b58` | 2026-10-11 |
| 9.2 | 122 | `d82c56cd` | 2026-10-13 |
| 9.3 | 123 | `1239667b` (off the main line) | 2026-10-17 |
| 9.3.1 | 124 | `434b1fca` | 2026-10-17 |
| 9.3.1 | 125 | `8213cc84` | 2026-10-18 |
| 9.4 | 126 | `15f7f3a3` | 2026-10-23 |
| 9.4.1 | 127 | `1cefc68b` | 2026-10-24 |
| 9.5 | 128 | `8401eb49` | 2026-10-26 |
| 9.5.1 | 129 | `71f2679b` | 2026-10-26 |
| 9.5.2 | 130 | `f552d950` | 2026-10-30 |
| 9.5.3 | 131 | `9ce6a1c5` | 2026-10-30 |
| 9.5.4 | 132 | see `Release 9.5.4 (132)` | 2026-11-01 |
| 9.5.5 | 133 | see `Release 9.5.5 (133)` | 2026-11-06 |

Some codes that reached Google Play were never committed with their number: 9.06 (105), 9.1.1 (109), and 111, 117 and
119. A crash report from one of them belongs to the code between the tags either side of it: 105 sits between
`v9.05-103` and `v9.1-106`, and 109 between `v9.1-106` and `v9.1.2-110`.

## Releases

GitHub Releases exist for 8.3, 8.8, 9.1.9, 9.3.1 (125), 9.5.1 (129) and 9.5.4 (132); 9.5.4 is marked Latest. Dev test
builds are published as non-latest pre-releases. Pushing a `v*` tag runs `.github/workflows/release.yml`, which builds,
signs and publishes the release using the repository secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. The workflow stops before building if any of them is missing.

## Reading crash reports

Release builds are minified by R8, so a Play Console trace from a build without its mapping shows names like `yy2.j`.
The mapping file is in `app/build/outputs/mapping/release/mapping.txt` after a release build, and the release workflow
attaches it to each GitHub Release as `IntelligentSearch-<version>-mapping.txt`. An app bundle (`.aab`) uploaded to Play
carries its mapping automatically, so Play shows real names; an APK upload doesn't.

Crashes already traced to their fixes:

| Crash in Play Console | Version | Fixed by |
|---|---|---|
| `IntelligentSearchDatabase ... onOpen`, `SQLiteException` | 9.1.1 (109) | 9.1.2 (110), then 9.3 (123) (`DatabaseOpenPragmasTest`) |
| `SearchTileService.onClick`, "pendingIntentBackgroundActivityStartMode must not be set when creating a PendingIntent" | 9.1.5 (115) | `BackgroundLaunchOptions.forCreator`, with a fallback launch |
| `LayoutNodeSubcompositionsState.subcompose` in the overlay's list (a key used twice) | 9.06 (105) | 9.3 (123) (`UniqueLazyItemsTest`) |

## Commits

One commit per batch of changes, its message one short imperative line saying what changed for the user, with no
AI attribution. Releases are `Release <version> (<code>)`.
