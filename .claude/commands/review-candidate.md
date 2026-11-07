---
description: Claude's review of dev before the owner's approval; opens or updates the pull request to main
---

Review what would reach `main`, and hand it to the owner only if it should get there. Follow `docs/IMPROVEMENT.md`
section 9: this review blocks bad code. It doesn't produce a to-do list.

## 1. Choose the commit

Run `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-env.ps1`, then `git fetch origin`. Take the newest
`dev` commit whose `CI Check & Build` run succeeded. Stop with one line if `origin/main` or `origin/candidate` already
contains it. If `origin/candidate` has commits `main` doesn't, review only `origin/candidate..<commit>`; otherwise review
`origin/main...<commit>`.

## 2. Review

Run `/code-review high` on that range. Read `MEMORY.md` first, because locked and declined items aren't findings. Then
give each finding that looks like it could block to a fresh agent told to disprove it from the code. Only what survives
counts. A blocker is a crash or ANR, lost or exposed data, a result that is wrong for the user, a security hole, or a
change to something locked or declined.

For a change people can see, install the signed release on `IS_Pixel_XL` and look at it (`docs/DEVICE_TESTING.md`). If the
device tools fail, say so in the pull request and go on.

Everything else is dropped. Style, naming, tidying, possible future problems and anything you couldn't confirm get no
commit, comment or ledger line. A real problem that doesn't block can take one backlog line if it has strong evidence.

## 3. Act on the result

- Nothing blocks: go to step 4.
- 1 to 3 blockers with a clear fix: fix them all in one commit on `dev`, wait for CI, check only that fix, and go to step 4
  with that commit. One round only.
- More than 3, or a fix that isn't clear: leave `candidate` and the pull request alone, add one ledger entry naming the
  blockers, and stop. The candidate isn't ready, and the next cycle's target is those blockers.

## 4. Hand it to the owner

Fast-forward with `git push origin <commit>:refs/heads/candidate` and never force it. That also creates the branch the
first time. Open a pull request from `candidate` to `main`, or edit the open one, and link it to this thread. The body is
one short summary written for the owner, replaced each time:

- What people will notice, then what changed underneath.
- What was checked: CI, the review, the emulator or why it wasn't used.
- What was fixed in review, and anything the owner should look at. Nothing else.

Post no other comments. Never merge: the owner's merge is the approval. New `dev` commits don't enter this pull request
until the next review moves `candidate`, so it always holds exactly what was reviewed.
