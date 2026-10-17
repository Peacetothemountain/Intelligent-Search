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
- No Material dependency: the design system in `ui/` is built on Compose Foundation.
- Dependencies are wired by hand in one app graph; no annotation processing, for fast builds.

## Phases

| Phase | Work | Done when |
|---|---|---|
| D0 | Project, design tokens, fonts, icon | Builds, lint clean, engine tests run |
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

- 2026-10-17, D0 done: empty orphan branch, `:engine` and `:app`, the ink and paper palettes with five accents,
  Space Grotesk and JetBrains Mono, the prompt-mark icon, the search screen frame.
