# Intelligent Search 2 (dev2)

A from-scratch rebuild of Intelligent Search by NG Designs: every function of the current app, none of its look.

- `docs/FEATURES.md`: what it must do (the current app's full function list, ticked as each works).
- `docs/DESIGN.md`: how it looks.
- `docs/PLAN.md`: how it is built, and progress.

Build: `./gradlew :engine:test :app:assembleDebug`. It installs as `com.pixel.intelligentsearch.dev2`, next to the
published app, so testing it never touches the real one.
