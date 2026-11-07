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
- Never commit with a failing test, and never skip one to get past it. Don't delete, `@Ignore`, loosen or rewrite a test's assertions just to make it pass. If a test is genuinely wrong or the behaviour change is intended, say so and get the owner's OK first.
- Don't re-record goldens (`-Proborazzi.record=true`) to clear a screenshot failure unless the visual change is intended.
  Look at the diff images in `app/build/outputs/roborazzi` first and say which goldens changed and why.
- Lint fails on any warning not in `app/lint-baseline.xml`. Fix new warnings; don't add them to the baseline.
- Fix the root cause so the failure can't happen, rather than adding a test to guard against it. Add a test only when
  the design can't rule the failure out (`docs/IMPROVEMENT.md` section 1a). Reproduce a bug before fixing it.
- The `characterization/` tests pin current behaviour of ranking, backups, bangs and settings migration. A failure
  there means behaviour changed: treat it as a regression unless the change was asked for.
- The layout audit (`-PlayoutAudit`) is slow and runs only when asked or when a change touches layout.

## Getting a change onto `main`

`main` only takes a pull request that has passed `Build & Check` and that the owner has approved. Nobody pushes to it
directly, and Claude never approves or merges. `.github/workflows/improve.yml` does everything up to that question, on
GitHub: the owner approves the pull request for yes, or closes it for no.

Work the owner asks for in a session goes on `dev` as usual. The next run that delivers reviews everything since `main`, including it.

## Finding your way through the history

`docs/HISTORY.md` maps the branches and tags, every version code to its commit, and known Play Console crashes to
their fixes. Each version is tagged `v<versionName>-<versionCode>`. Read history with `git log dev` or `git log main`,
not `--all`: the `refs/t3/` checkpoint refs on the owner's PC hold copies of commits that were never on GitHub. Pushing a
`v*` tag starts the release workflow, so tag only a release you mean to publish.

## Improving the app

`docs/IMPROVEMENT.md` sets the standard for every change: evidence before changing anything, metrics that must never
get worse, which model and effort to use for each job, and how much to do at each level of weekly usage.
`docs/LEDGER.md` records every target already tried. Read it before choosing or retrying one, so work isn't repeated.
Use `/improve`, `/debug` and `/audit` to run a cycle.
