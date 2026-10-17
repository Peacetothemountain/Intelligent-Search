# dev2 design

Material You is the core: colour from the wallpaper, Material 3 Expressive components, shapes, type and motion.
What changes from `main` is how screens are composed, so `dev2` is unmistakably Material and still nothing like the
current app.

## What `main` looks like (and `dev2` avoids)

- Settings pages as one large rounded card per group, rows divided by lines, outline icons and chevrons.
- Wavy sliders, Google Messages-style switches.
- The Gemini gradient search pill at the bottom, the matrix sparkle background, the bugdroid, bouncing morph shapes.
- Big dark answer cards with outlined chips.

## dev2's Material You

1. **Wallpaper colour, everywhere.** `dynamicLightColorScheme`/`dynamicDarkColorScheme` (Android 12+ is the
   minimum, so always available). Tonal roles carry meaning: the best match in primary container, the calculator
   in tertiary container, actions in secondary container. The launcher icon and the window background follow the
   wallpaper too.
2. **Segmented lists.** Every list (results and settings) uses Material 3 Expressive segmented list items: each row
   its own tonal surface, 3 dp apart, large outer corners on the group and small inner ones. No dividers.
3. **Material shapes as identity.** Icon containers, the voice indicator, settings categories and widget buttons use
   `MaterialShapes` (cookie, clover, sunny, gem, flower, pill), and the loading indicator morphs between them.
4. **Connected button groups and split buttons** for result actions (Open | New event | Info; Start ▾), toggle
   button groups for choices (System | Light | Dark).
5. **A floating search bar with a FAB.** The search field floats at the bottom in a full-round surface with the
   voice FAB beside it; scope filter chips (All, Apps, People, Files, Web) sit above it; results stack upward.
6. **Settings home is a grid**, not a list: a large title, a "Search settings" bar, the setup progress card, then
   category tiles, each with its own Material shape. Sections open as segmented lists. On wide screens the grid
   and the open section sit side by side (list-detail).
7. **Expressive motion.** `MotionScheme.expressive()` springs for components; shared-axis transitions between
   screens; shape morphs on press. With Remove animations on, everything is instant.
8. **Roboto Flex** for the whole type scale, bundled (SIL Open Font License, text in `assets/licenses`), so type is
   the same on every device.
9. **Works everywhere.** Phones from 320 dp, foldables, tablets, desktop windows and Android XR; text from 85% to
   200%; hardware keyboards (arrows, Enter, Escape) and TalkBack as first-class input.

## Improvements over `main` that show in the design

- Every setting is also a search result: typing "blur" in search shows the slider inline.
- Instant answers act in place (start the timer, flip the flashlight, drag the blur) without leaving search.

## Identity

- Launcher label "Search 2" while it installs next to `main`.
- Icon: a nine-sided cookie holding a search glyph, in the wallpaper's accent tones, with a monochrome layer for
  themed icons.
- Widgets: a full-round search bar with shaped buttons, and a 2 by 2 tile with a search field and two shortcuts.
