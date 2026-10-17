# dev2 design

The rule: nothing may look like `main`. `main` is Pixel-native (Material You colours from the wallpaper, rounded
pills and squircles, Google's four colours, glass, the Gemini plate, the bugdroid, bouncing shapes and springs).
`dev2` is an instrument: typographic, squared-off, quiet, fast.

## Principles

1. **Type does the work.** Hierarchy comes from size, weight and the two typefaces, not from cards, shadows or
   colour fills. Space Grotesk for names, queries and headings; JetBrains Mono for labels, counts, numbers and
   key hints. Both are bundled (SIL Open Font License, texts in `assets/licenses`).
2. **One accent.** Ink (`#0F1012`) or paper (`#F3F0E8`) surfaces, one text colour and its dimmed variant,
   hairlines, and a single accent the user picks: vermilion (default), cobalt, signal green, amber or bone.
   Not taken from the wallpaper.
3. **Lines, not boxes.** Lists are rows separated by 1 dp hairlines. Corners are 4 dp (7 dp on app icons).
   No elevation, no blur on components, no gradients.
4. **The input line is home.** Search is a command line: an accent `›` prompt, large query text, ghost text for
   the best match, and the results stacked above it, nearest first. The bar is at the bottom by default.
5. **Everything is numbered and labelled.** Sections carry mono labels with counts (`APPS 03`); rows carry their
   position, so keyboard users and screen readers always know where they are.
6. **Motion is short and straight.** 120 to 220 ms, ease-out, slides and fades along one axis. No springs, no
   bounces, no looping decorations. With Remove animations on, changes are instant.
7. **Works everywhere.** Phones from 320 dp, foldables, tablets, desktop windows and Android XR; text from 85% to
   200%; hardware keyboards (arrows, Enter, Escape) and TalkBack as first-class input.

## Components (built in `ui/`)

- `Text`: the only text primitive (no Material dependency).
- Input line, scope bar, section label, result row, best match block, instant answer block (calculator, action,
  toggle, setting), square toggle, stepped ruler slider, key-hint chip.
- Settings: one scrolling page of numbered sections with an index rail on wide screens; every setting is also a
  search result, so typing "blur" in search jumps straight to it.

## Identity

- Name on the launcher: "Search 2" while it installs next to `main`.
- Icon: the prompt mark (an accent chevron and cursor bar) on ink, with a monochrome layer for themed icons.
- Widgets: a flat bar with a squared frame, an accent block and the word "Search", in ink or paper.
- No mascot.
