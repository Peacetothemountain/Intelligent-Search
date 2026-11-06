---
description: Read-only sweep that finds evidence-backed improvement candidates and adds them to the ledger backlog
argument-hint: "[optional area: crashes | lint | size | tests | perf | a11y | a file or feature]"
---

Audit Intelligent Search for improvement candidates. Area: $ARGUMENTS (if empty, all of the areas below).

This changes no app code. It only adds rows to the Backlog table in `docs/LEDGER.md`. Follow `docs/IMPROVEMENT.md`.
Don't run this when weekly usage is over 70% (section 7); tell the owner instead.

## How

1. Read `docs/LEDGER.md` first. Skip anything already in the backlog or entries unless you have new evidence; if you
   do, write what's new.
2. Run cheap fact-gathering with `haiku`/low subagents in parallel, one per area, each told to return facts with file
   paths and line numbers, not opinions:
   - crashes: Play crash table vs tests; `try`/`catch` that swallows errors; `!!` on platform results
   - lint: what the 67 baseline issues are and which can be fixed safely
   - size: largest resources and dependencies in the release APK (`r8-analyzer` skill for keep rules)
   - tests: groups of tests guarding one failure because the code lacks a shared mechanism; tests the compiler or
     lint could replace; duplicates; real-time waits (`IMPROVEMENT.md` section 1a)
   - perf: main-thread I/O, work in composition, missing `remember`/`key` (`anr-audit-and-fix` skill)
   - a11y: touch targets under 48dp, missing content descriptions, contrast below WCAG AA
3. Check every candidate yourself, or with a `sonnet`/medium subagent: open the code and confirm the finding is real.
   Drop anything you can't confirm or that touches the off-limits list (section 3).
4. Add the confirmed ones to the backlog with their evidence and strength (1 to 4, section 1). Commit the ledger only;
   don't push unless the owner asks.
5. Report the top 3 candidates in a few lines, strongest first, and stop. `/improve` takes them from there.
