---
description: Systematic, evidence-first debugging of an Intelligent Search bug or crash
argument-hint: "<symptom, crash trace, or steps to reproduce>"
---

Debug this in Intelligent Search: $ARGUMENTS

Follow `docs/IMPROVEMENT.md` (budget in section 7, models in section 6, off limits in section 3). The goal is the real
root cause, proved, then removed so it can't come back (section 1a). A guess that hides the symptom isn't a fix, and
neither is a test that guards against it.

## 1. Has this been seen before?

Before forming any theory, check:
- The crash table in `docs/HISTORY.md`, and `docs/LEDGER.md` entries for the same component.
- `git log dev --oneline -S"<class or method from the trace>"`, plus `git log dev -L` on the suspect function.
- claude-mem `search` → `timeline` → `get_observations` for the symptom and the class names.

If an earlier fix exists, find out why it didn't hold (regression, different path, different version) before writing
new code. For a Play Console trace, find the version code's commit in `docs/HISTORY.md` and read that code, not
today's. De-obfuscate with that release's `mapping.txt`.

## 2. Reproduce

Reproduce it in the smallest place you can: a unit or Robolectric test first, then the phone only if the owner allows
(release build, per `MEMORY.md`). If it won't reproduce, say what you tried and what evidence is missing. Don't fix
something you can't see.

## 3. Find the cause

List the possible causes, then rule each out with evidence (code read, log, test), not reasoning alone. Look further
even when the first cause looks right: confirm it explains every symptom. If the cause involves threading, lifecycle,
process death, R8, or data (backups, migration, the database), use a more capable model at high effort or above.
If the first theory turns out wrong, go up a level instead of guessing again.

## 4. Fix and prove

1. Have a failing run that shows the root cause, from step 2.
2. Make the fix that removes the cause. Compile. Keep a test only if the design can't rule the failure out.
3. Check the fix doesn't make any ratchet value worse (`docs/LEDGER.md`).
4. Add a ledger entry; for a Play crash, also add a row to the crash table in `docs/HISTORY.md`.
5. Commit and push per section 8. Report: cause, evidence, fix, test name, commit, CI status.

Two failed fixes in a row: stop, record what you learned in the ledger as `blocked`, and report back. Don't try a
third variation of the same idea.
