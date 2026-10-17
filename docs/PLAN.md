# dev2 build plan

`dev2` is built from an empty repository. `docs/FEATURES.md` is the contract (every function `main` has),
`docs/DESIGN.md` is the look. It installs as `com.pixel.intelligentsearch.dev2`, next to the real app.

## Architecture

- **`:engine`** (plain Kotlin, no Android): normalizing, the index (prefix, typo-tolerant and phonetic matching),
  one scoring model, the calculator and converters, natural language actions, system toggle words, quick web
  shortcuts, and the query pipeline that turns text plus candidates into ranked sections. Unit tested on the JVM.
- **`:app`**: the Android side. Sources (apps, shortcuts, contacts, calendar, files, usage, predictions, web
  suggestions, history) feed the engine through one interface each; screens are Compose with a state holder per
  screen; settings are one immutable, versioned, serializable object in DataStore, so backups contain exactly the
  settings and a restore is validated by the same schema.
- Material 3 Expressive (Material You colour from the wallpaper) for every component; see `docs/DESIGN.md`.
- Dependencies are wired by hand in one app graph; no annotation processing, for fast builds.

## Phases

| Phase | Work | Done when |
|---|---|---|
| D0 | Project, Material You theme, type, icon | Builds, lint clean, engine tests run |
| D1 | Engine: index, matching, scoring, calculator, actions, toggles, shortcuts, pipeline | Engine tests cover every rule in FEATURES 2 and 3 |
| D2 | Settings model and store, backup format | Round-trip and validation tests |
| D3 | Sources: apps, shortcuts, profiles, contacts, calendar, files, usage, predictions, web, history | Tests with stand-in sources |
| D4 | Search screen: input line, results, best match, instant answers, keyboard navigation | Screenshot goldens, layout audit |
| D5 | Settings screens, searchable settings | Goldens for every section |
| D6 | Entry points: intents, tile, shortcuts, widgets and widget editor | Widgets render on device |
| D7 | Voice assistant, recognition service, search role | Works as default assistant |
| D8 | Privacy: hidden apps, biometric gate, encrypted and automatic backup, AppFunctions, global search | Tests and device check |
| D9 | Tutorial, setup checklist, debug menu | Goldens |
| D10 | Device validation, performance, baseline profile | Every FEATURES line ticked |

Every phase ends green (lint with warnings as errors, all tests, release build) before it is pushed.

## Progress

- 2026-10-17, D0 done: empty orphan branch, `:engine` and `:app`, the Material You theme (wallpaper colour,
  Material 3 Expressive), the cookie icon, the search screen frame. The first direction (ink and paper, no
  Material) was dropped the same day: the owner wants Material You as the core.
- 2026-10-17, design system: studied all 91 m3.material.io guideline pages (Material 3, M3 Expressive, the May 2026
  layout scaffold and spacing system, expressive lists and menus) and Neural Expressive; `docs/DESIGN.md` maps every
  foundation and component to where it is used. Type is now Google Sans Flex with all 30 baseline and emphasized
  styles, per optical size.
- 2026-10-17, D1 done: the engine (52 tests). Matching and ranking (typo-tolerant prefix distance, sound-alike
  names, camel-case words and acronyms, use and recency), the calculator (functions, calculator-style percentages,
  exact bitwise maths and bases, unit and currency conversion, phone numbers and dates refused), actions (timers,
  alarms, reminders, events, messages, calls, directions), toggles and volumes, the safe-address gate, engines and
  quick web shortcuts, and the `Search` pipeline (instant answers, weighted sections, best match, ghost text). About
  1.3 ms per query over 5,000 candidates on a desktop JVM. Every case from `main`'s engine tests is ported.
- 2026-10-17, D2 done: `Settings` is one immutable, versioned, serializable value covering every option in
  FEATURES, sanitized on every read and restore (ranges, safe addresses, package names, widget slots); the
  DataStore store updates it atomically. Backups (`BackupCodec`) are AES-256-GCM: with a passphrase via PBKDF2 (any
  phone), or with a device key (this phone only); wrong passphrases, tampering and foreign files fail with clear
  errors. Folder grants and debug switches never travel in a backup.
