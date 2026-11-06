# Improvement Charter

How Intelligent Search gets better, one measured step at a time. `/improve`, `/debug` and `/audit` in
`.claude/commands/` follow this file. `docs/LEDGER.md` records what each cycle found, tried and kept.

## 1. What counts as an improvement

A change is an improvement only if **evidence shows it is better** and nothing else got worse. Something "looks
cleaner" or "feels faster" doesn't count until a number or a reproducible check backs it up.

Accepted evidence, strongest first:

1. A Play Console crash or ANR, a reproducible bug, or a failing test. Fixed means a new test fails without the fix
   and passes with it.
2. A measured number moved the right way: test count, lint baseline size, APK size, cold start, frame time or jank,
   memory, a WCAG contrast ratio, a touch target size.
3. A platform rule broken today: a lint warning, an Android or Play policy, a deprecated API with a removal date.
4. A written M3 or Android guideline the app breaks, cited by URL. Taste alone never counts.

Not improvements: renames, reformatting, moving code around, swapping one library for another of equal weight,
restyles nobody asked for, or "while I'm here" edits. Refactors only count when they make a measured fix possible or
remove a measured cost.

## 2. The ratchet

Each kept change must leave every number below **equal or better**. The current values are at the top of
`docs/LEDGER.md`; a cycle that improves one updates it in the same commit.

| Metric | Direction | How to measure |
|---|---|---|
| Unit, characterization and screenshot tests | all pass | CI `Build & Check` (memory: CI runs the tests, not this PC) |
| `@Test` count in `app/src/test` | never down | `grep -r "@Test" app/src/test --include=*.kt \| wc -l` |
| Issues in `app/lint-baseline.xml` | never up | `grep -c '<issue$' app/lint-baseline.xml` |
| Release APK size | never up without a written reason | size of `app/build/outputs/apk/release/app-release.apk` |
| Known Play crashes without a test | never up | crash table in `docs/HISTORY.md` |
| Phone metrics (cold start, jank, memory) | never worse | only when the owner allows phone use; same build type, same steps, 3 runs, median |

If a change makes one number better and another worse, it is a trade, not an improvement: write both numbers in the
ledger and ask the owner before keeping it.

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
- One target per cycle. A cycle ends with a ledger entry even when nothing changed. "Nothing worth changing" is a
  valid result.

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
notice reports weekly usage, and `/usage` in Claude Code shows both limits.

| Weekly usage | Mode |
|---|---|
| under 70% | Normal. Subagents allowed as section 6 says. |
| 70 to 90% | Lean. At most 2 subagents, `haiku` or `sonnet` only unless the job is row 4 or 5. No `/audit` sweeps. |
| over 90% | Minimal. No subagents. Only crashes, data-loss bugs, or finishing a change already started. Tell the owner the level and offer to wait for the reset. |

Write the usage level in each ledger entry, so later cycles can see what each kind of work costs.

## 8. Finishing a cycle

1. Make all the edits, compile only (memory: test once at the end).
2. Every bug fix gets a test that fails without it. New behavior gets tests.
3. Update the ratchet values in `docs/LEDGER.md` and add the cycle's entry.
4. One short, plain commit on `dev`, American spelling, attribution as the session's current instructions say.
   Push `dev`, let CI run, then `git push origin dev:main` only after CI is green and only if asked.
5. Install on the phone only as `MEMORY.md` says (release build, only when the owner allows, once per batch).
