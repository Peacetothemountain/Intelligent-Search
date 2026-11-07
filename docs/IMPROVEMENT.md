# Improvement Charter

How Intelligent Search gets better, one measured step at a time. `/improve`, `/debug` and `/audit` in
`.claude/commands/` follow this file. `docs/LEDGER.md` records what each cycle found, tried and kept.

## 1. What counts as an improvement

A change is an improvement only if **evidence shows it is better** and nothing else got worse. Something "looks
cleaner" or "feels faster" doesn't count until a number or a reproducible check backs it up.

Accepted evidence, strongest first:

1. A Play Console crash or ANR, a reproducible bug, or a failing test. Fixed means its cause is gone (section 1a).
2. A measured number moved the right way: CI time, lines of test code, lint baseline size, APK size, cold start, frame
   time or jank, memory, a WCAG contrast ratio, a touch target size.
3. A platform rule broken today: a lint warning, an Android or Play policy, a deprecated API with a removal date.
4. A written M3 or Android guideline the app breaks, cited by URL. Taste alone never counts.

Not improvements: renames, reformatting, moving code around, swapping one library for another of equal weight,
restyles nobody asked for, or "while I'm here" edits. Refactors only count when they make a measured fix possible or
remove a measured cost.

## 1a. Root causes, not more tests

A test added to guard against an issue is not the fix. Find why the issue can happen and remove that, so it can't
happen and the test isn't needed. The suite and CI should get smaller and better organized over time, not keep growing.

In order of preference, fix a failure by:

1. Making it impossible: one shared mechanism instead of copies, a type or required parameter, an injected clock or
   dispatcher, deleting the code path.
2. Having the build reject it: the compiler, or a lint rule.
3. Only then a test, when the design can't rule the failure out. Say why in the ledger entry. Real logic with many
   inputs (parsers, ranking, math), the data-safety tests (backups, migration, encryption) and `characterization/` earn
   their place; removing one of these needs a reason of its own.

A test can go once the failure it guards can no longer happen, when another test or a golden asserts the same thing, or
when it only restates the code and couldn't catch a bug. Say which in the ledger. Removing a test because it is failing
is never a reason: find out why it fails.

A slow or flaky test is a symptom too. Look for the cause (a real-time wait, a missing seam, logic that can only be
reached by rendering a screen) before adding retries, longer timeouts or more machines.

## 2. The ratchet

Each kept change must leave every number below **equal or better**. The current values are at the top of
`docs/LEDGER.md`; a cycle that improves one updates it in the same commit.

| Metric | Direction | How to measure |
|---|---|---|
| Unit, characterization and screenshot tests | all pass | CI `Build & Check` (memory: CI runs the tests, not this PC) |
| Lines of test code | never up without a written reason | `find app/src/test -name '*.kt' \| xargs cat \| wc -l` |
| CI time for `Build & Check` | never up without a written reason | `gh run list --branch dev`, full runs only; a cached docs commit isn't a measure |
| Flaky CI failures | never up | failed runs that pass on re-run with no change; each one is a target |
| Issues in `app/lint-baseline.xml` | never up | `grep -c '<issue$' app/lint-baseline.xml` |
| Release APK size | never up without a written reason | size of `app/build/outputs/apk/release/app-release.apk` |
| Known Play crashes without a traced fix | never up | crash table in `docs/HISTORY.md` |
| Phone metrics (cold start, jank, memory) | never worse | only when the owner allows phone use; same build type, same steps, 3 runs, median |

If a change makes one number better and another worse, it is a trade, not an improvement: write both numbers in the
ledger, and don't keep it unless the owner has asked for it.

## 3. Off limits

Read the memory index (`MEMORY.md`) before each cycle. These come from it and win over any finding:

- Locked: the Gemini plate AGSL shader, the approved app logo spec, Bugdroid clip loops and their reduced-motion
  behavior.
- Declined, never raise again: widget dark-mode recolor, Google branding changes.
- Design changes only touch the element the owner named; "matte" means grain only; animations get an HTML preview
  and approval before they're built.
- The app stays small: no heavy native libraries or models.
- Never `git checkout` or restore files with uncommitted changes. Never rewrite pushed history.

## 4. Don't loop

Before choosing or retrying anything, search what was already done:

1. `docs/LEDGER.md`: every target already tried, its result, and its `Retry only if` condition.
2. `git log dev --oneline` and `git log dev -S"<symbol>"` for the code involved.
3. `docs/HISTORY.md`: crashes already traced and fixed.
4. claude-mem search (`search` → `timeline` → `get_observations`) for earlier sessions on the same subject.

Rules:

- A target marked `rejected` or `reverted` isn't tried again unless its `Retry only if` condition has come true.
  Write down which evidence makes it true.
- Two failed attempts at the same target: stop, write what was learned, mark it `blocked`, and move on.
- One target per cycle, one commit per cycle. "Nothing worth changing" is a valid result, and a run that changes nothing
  commits nothing.
- The owner doesn't prompt each cycle. Choose the best course, keep going on `dev`, and leave the ledger as the record
  for them to read later. What still needs the owner: approving the merge to `main`, `v*` tags and releases, using the phone,
  anything in section 3, and choices a later reader couldn't undo.

### How runs work

`.github/workflows/improve.yml` runs every 4 hours on GitHub. It uses nothing from the owner's PC, GitHub login or signing
key, and each step starts only when the one before it passes, so waiting costs no Claude usage:

| Step | Claude? | What it can do |
|---|---|---|
| Decide | no | Stops when `MAX_WAITING` changes already wait on the owner |
| Work | yes | Reads and edits files. No shell, no GitHub writes |
| Commit | no | Refuses edits to `.github`, `.claude` and the Gradle wrapper; pushes the rest to a throwaway branch |
| CI | no | Lint, every test, screenshot comparisons, minified build |
| Smoke | no | Installs that build on an emulator, signed with a key made for the run, and checks that it opens |
| Review | yes, fresh | Read-only. Can only stop the run |
| Deliver | no | Moves `dev` forward and asks the owner in one pull request |

Only code that passes all of it reaches `dev`, so a failed run leaves nothing behind. Approving the pull request merges
it. Closing it takes those changes back out of `dev` and records them as declined (`declined.yml`). GitHub emails the
owner when a run fails.

## 5. Research before acting

Being sure of something isn't evidence. Before changing behavior:

- Read the code that actually runs, its tests, and its callers. Don't rely on names or memory.
- For platform behavior (Android 17, Compose, M3, Play policy), check the current docs or source. Notes from
  earlier sessions may be out of date: confirm them first.
- When the docs and the device disagree, the device wins. Write down what was seen.

## 6. Pick the right model and effort

Choose each subagent's model and effort for the job, not by habit. This section is the owner's standing permission to
set `model` and `effort` on Agent calls.

| Job | Model | Effort |
|---|---|---|
| Finding files, grepping, counting, reading logs, listing goldens | `haiku` | low |
| Reading a few files and summarizing them, running a known check, drafting copy | `sonnet` | low to medium |
| Normal bug fixes and features with a clear cause | inline (current model) | medium |
| Unclear root cause, concurrency, lifecycle, R8 or obfuscated traces, cross-module design | `opus` | high |
| Hardest problems: two failed attempts, a correctness risk to user data (backups, migration), a security review | the most capable model available | xhigh or max |

- At the start of each cycle, check which models exist (the environment's model list, or the `claude-api` skill).
  When a more capable model is available than the one this table names, use it for the top two rows.
- Go up a level when a cheaper attempt came back wrong or unsure. Don't go down to save tokens on rows 4 and 5:
  a wrong fix costs more than the tokens saved.
- Do short lookups inline; a subagent pays to read its brief and its context. Delegate only work that is broad, can
  run in parallel, or needs a fresh view.
- Give each subagent the goal, what has been ruled out, and the files to read. Check what it reports before acting on it.

## 7. Usage budget

The owner's Claude plan has weekly and session limits. Check them at the start of each cycle: the claude-mem start-up
notice reports weekly usage, and `/usage` in Claude Code shows both limits. If a scheduled run can't see a figure, it
works in Lean mode and writes "no figure visible" in the ledger.

| Weekly usage | Mode |
|---|---|
| under 70% | Normal. Subagents allowed as section 6 says. |
| 70 to 90% | Lean. At most 2 subagents, `haiku` or `sonnet` only unless the job is row 4 or 5. No `/audit` sweeps. |
| over 90% | Minimal. No subagents. Only crashes, data-loss bugs, or finishing a change already started. Tell the owner the level and offer to wait for the reset. |

Write the usage level in each ledger entry, so later cycles can see what each kind of work costs.

## 8. Finishing a cycle

1. Make all the edits, compile only (memory: test once at the end).
2. The cause of a bug is removed (section 1a). Reproduce it first: a failing run proves the fix. New behavior gets
   tests only for logic the design can't make safe.
3. Update the ratchet values in `docs/LEDGER.md` and add the cycle's entry.
4. One short, plain commit on `dev`, American spelling, attribution as the session's current instructions say.
   Push `dev` and let CI run. `main` is reached only by the pull request the owner approves.
5. See it working on the emulator without the phone (`docs/DEVICE_TESTING.md`). Install on the phone only as `MEMORY.md`
   says (release build, only when the owner allows, once per batch).

## 9. Layers of checking

Each layer in the table above exists to stop bad code, not to create work. Most of what a layer reports isn't worth acting
on. Act on what is confirmed and serious: a failing check, a crash or ANR, lost or exposed data, a wrong result for the
user, a security hole, a weakened test, or a change to something off limits. Drop the rest with no commit, comment or
ledger line. Never one commit or one reply per finding.
