---
description: One measured improvement cycle for Intelligent Search, following docs/IMPROVEMENT.md
argument-hint: "[optional target, ledger backlog ID, or area]"
---

Run one improvement cycle on Intelligent Search. Requested target: $ARGUMENTS (if empty, choose one from evidence).

Your job is to make the app measurably better, or to show with evidence that the best candidate isn't worth changing.
Don't change anything just to have changed something. A cycle that ends "no change needed" with a good ledger entry
is a success.

## Step 0: Budget and tools

- Read the weekly usage figure from the claude-mem start-up notice (or ask the owner to run `/usage`) and set the mode
  from `docs/IMPROVEMENT.md` section 7. Say the mode in one line before doing anything else.
- Check which Claude models this session offers. Use section 6 to choose the model and effort for each step below.
  When a more capable model is available than the table names, use it for the hard steps.

## Step 1: Learn the history (always, even when you're sure)

Read `docs/IMPROVEMENT.md`, `docs/LEDGER.md`, the `MEMORY.md` index, and the parts of `docs/HISTORY.md` that apply.
Then search for earlier work on the candidate target: `git log dev --oneline -S"<symbol>"`, then claude-mem
`search` → `timeline` → `get_observations`. In normal mode, a `haiku`/low subagent can run these searches and return
only the matches.

If the ledger says the target was rejected, reverted or blocked and its `Retry only if` condition hasn't come true,
choose another target and say why.

## Step 2: Choose one target

In this order: a requested target that passes Step 1; then the backlog item with the strongest evidence; then crashes
and ANRs; then ratchet metrics that can go down (lint baseline, APK size); then gaps in test coverage around code
that changed recently. Write one sentence: what it is, the evidence (`IMPROVEMENT.md` section 1), and which number
or check will prove it got better.

If you can't name a measurable result, it isn't a target. Add it to the backlog as an idea and choose again.

## Step 3: Research

Read the code that actually runs, its tests and its callers. Check the current platform docs or source for every
platform behavior you're relying on, even ones you're confident about. Notes and memories may be stale, so check them
against the code. Use a more capable model at higher effort when the cause is unclear (section 6, rows 4 and 5).
Stop and ask the owner if the fix would touch anything in section 3.

## Step 4: Measure, change, measure

1. Write down the "before" value: the metric, or a test that fails today.
2. Make the smallest change that moves it. No unrelated edits.
3. Compile. Add the test that proves it (it must fail without the change).
4. Write down the "after" value. If any ratchet metric got worse, revert your change (only your edits; never restore
   files that had uncommitted changes before you started) or ask the owner whether the trade is worth it.

## Step 5: Record and ship

- Add the ledger entry (template in `docs/LEDGER.md`) and update the ratchet values. Include `Retry only if` and a
  one-line lesson.
- Commit and push as `docs/IMPROVEMENT.md` section 8 says. CI runs the tests.
- Report to the owner in a few lines: target, evidence, before → after, commit, CI status, usage mode, and the next
  strongest candidate (don't start it).
