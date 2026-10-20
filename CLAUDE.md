# Intelligent Search

## Tests gate every change

The test suite in `app/src/test` (unit, characterization and Roborazzi screenshot tests) defines how the app is
expected to behave. No change is finished until it passes.

- Before every commit run, from the repo root:

  ```
  ./gradlew lintDebug testDebugUnitTest
  ```

  Both must pass. This includes the screenshot goldens in `app/src/test/screenshots`, which CI also compares, so
  catch visual changes here before pushing. None of it needs a phone attached.
- Never commit with a failing test, and never skip one to get past it (the layout audit skipping itself is
  expected). Don't delete, `@Ignore`, loosen or rewrite a test's assertions just to make it pass. If a test is genuinely wrong or the behaviour change is intended, say so and get the owner's OK first.
- Don't re-record goldens (`-Proborazzi.record=true`) to clear a screenshot failure unless the visual change is intended.
  Look at the diff images in `app/build/outputs/roborazzi` first and say which goldens changed and why.
- Lint fails on any warning not in `app/lint-baseline.xml`. Fix new warnings; don't add them to the baseline.
- Every bug fix gets a test that fails without the fix. New behaviour gets tests alongside it.
- The `characterization/` tests pin current behaviour of ranking, backups, bangs and settings migration. A failure
  there means behaviour changed: treat it as a regression unless the change was asked for.
- The layout audit (`-PlayoutAudit`) is slow and runs only when asked or when a change touches layout.

## Getting a change onto `main`

`main` is protected: GitHub rejects any push to it whose commit hasn't already passed the `Build & Check` CI job,
and force pushes are blocked. So:

1. Commit on `dev` and push `dev`.
2. Wait for the `CI Check & Build` run on that commit to go green (`gh run watch`).
3. Then push the same commit to `main` (`git push origin dev:main`, a fast-forward).

If CI fails, fix it on `dev` and repeat; never push to `main` around it.
