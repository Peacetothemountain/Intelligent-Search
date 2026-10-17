# dev2 design

Material You is the core, built from the full current system: Material 3, Material 3 Expressive (May 2025, plus
the November 2025 lists and menus and the May 2026 layout and spacing systems), and Gemini's Neural Expressive
(I/O 2026). Sources: m3.material.io (all 91 guideline pages, content version 2026-10-13) and Google's I/O 2026
coverage of Neural Expressive.

`dev2` shares no composition with `main`. `main` uses one rounded card per settings group with dividers, wavy
sliders, Google Messages switches, the Gemini gradient pill, the matrix sparkles, the bugdroid and bouncing morph
shapes; none of these appear here.

## Foundations

### Colour

- **Wallpaper scheme** (user-generated dynamic colour) for the whole app, light and dark.
- **Colour style**: tonal spot (default), vibrant, expressive, fruit salad, rainbow, monochrome, neutral and
  fidelity scheme variants, generated from the wallpaper seed.
- **Contrast**: standard, medium, high; follows the system contrast setting by default (Android 14+).
- **Content-based colour, as a second source**: the best match card takes a scheme from the app's own icon, a
  contact card from the contact's photo, a file card from its thumbnail. At most two sources on a screen;
  semantic colours (error, success) never change.
- **Static success colour** (a custom role) for completed actions; error stays the scheme's error.
- Tonal roles carry meaning: primary container for the best match and setup, tertiary for the calculator and
  the FAB menu, secondary for actions, surface container levels for grouping.

### Typography

- **Google Sans Flex** (variable, SIL Open Font License, bundled), with Roboto and the Noto Sans collection as
  fallbacks. It shares its roundness with the Material shapes.
- **All 30 styles**: the 15 baseline and 15 emphasized styles. Emphasized is used for the best match, selected
  list and menu items, primary actions, unread counts and the answer in an instant answer.
- **Neural Expressive display type**: light weights at display sizes (greeting, transcript, timer and calculator
  figures), with the key part in an emphasized weight ("12**:30**", "= **1,284**.75").
- Variable axes (weight, width, optical size, grade, roundness) tuned per role, and the language-height line
  heights for tall scripts.

### Shape

- The 10-step corner scale: none 0, extra small 4, small 8, medium 12, large 16, large increased 20, extra large
  28, extra large increased 32, extra extra large 48, full.
- **The 35 Material shapes** for identity: app and contact icon masks (user choice), settings category tiles, the
  voice indicator, widget buttons, decorative crops on hero cards.
- **Shape morph** on interaction: buttons and icon buttons morph when pressed and selected, toggle buttons
  round to square when switched on, the listening indicator morphs with the voice level, the loading indicator
  cycles shapes.
- **Tension**: square and round mixed on purpose (asymmetric tile corners, a square toggle among round ones).
- Optical roundness for nested containers (outer radius minus padding).

### Motion

- **Motion physics**: `MotionScheme.expressive()`, spatial springs for movement, size, shape and rotation;
  effects springs for colour and opacity; fast for small parts, default for sheets, slow for full screen.
- **Transitions**: container transform from the search bar into focused search, from a result into its detail
  pane, from a settings tile into its section; forward and backward between levels; lateral between tabs;
  enter and exit for sheets and menus. Predictive back on every screen.
- With Remove animations on, everything is instant.

### Elevation

- Tonal elevation by surface container level; shadows only where a floating element needs protection (floating
  toolbar, FAB, menus); scrims behind modal surfaces.

### Layout and spacing

- **The layout scaffold**: bars, rails and panes, on the five breakpoints (compact, medium, expanded, large,
  extra large), using Navigation3 with the Material adaptive library.
- **Search** uses the supporting-pane layout from expanded up: results in a flexible pane, the selected result's
  detail in a contained pane, a drag handle to resize it (snapping to 360 or 412 dp, width remembered), the
  expanded navigation rail in the leading rail and a vertical floating toolbar in the trailing rail.
- **Settings** uses list-detail: the category grid becomes an expanded navigation rail with the section beside it.
- **Spacing tokens** on the 8 dp scale (space100 = 8 dp); search margins 24 dp, 12 dp when focused; a density
  setting (comfortable or compact) maps vertical padding to other tokens; spacing stays the same at 200% text.
- Right-to-left mirrored throughout.

### Icons

- Material Symbols Rounded; outlined when unselected, filled when selected, at 20, 24, 40 and 48 dp optical sizes.

## Components, and where each is used

| Component | Use in dev2 |
|---|---|
| Search (contained, expressive) | The search screen: focused full-screen contained search, gaps between result groups, trailing Lens and profile avatar (personal, work, private space) |
| Search app bar | The settings search over all settings |
| Expressive lists (segmented) | Every result group and settings section; highlighted selection; app shortcuts expand inline; swipe to reveal Pin and Hide; multi-select for hidden apps |
| Vertical menus (expressive) | Long-press on any result: vibrant selected item, gaps, a shortcuts submenu |
| Button groups (standard and connected) | Result actions; the scope selector (All, Apps, People, Files, Web); theme, contrast and density choices |
| Split button | "Open ▾" on the best match, "Start ▾" on a timer, "Search ▾" choosing the engine |
| Floating toolbar (vibrant) and FAB | The search screen's controls; vertical in the trailing rail on large screens; an orbiter on XR |
| FAB menu | Create from search: event, alarm, timer, note, navigate |
| Extended FAB (small, medium, large) | Onboarding "Get started", setup steps |
| Icon buttons (sizes, widths, toggle) | Result row actions; system toggles as toggle buttons that morph |
| Loading indicator (contained) | Web suggestions loading, indexing, pull down to refresh the index |
| Progress indicators (wavy) | Setup progress, backup progress, timer ring |
| App bars (large flexible) | Settings title with subtitle, collapsing on scroll |
| Navigation rail (expanded and collapsed) | Search scopes and settings categories on large screens |
| Carousel (hero, multi-browse) | Recent photos and files; the widget design and icon pack pickers |
| Sliders (standard with inset icon, centred, vertical XL) | Blur and opacity; priority weights (centred, minus to plus); inline volume for media, ring and alarm |
| Switch (with icons) | Every on/off setting |
| Cards | Instant answers (calculator, conversion, timer, date) |
| Chips (suggestion, filter, assist, input) | Recent searches and "Did you mean"; file type filters; "Open in Maps"; chosen quick apps |
| Bottom and side sheets | The voice assistant; contact and file details on phones; details as a side sheet on large screens |
| Dialogs | Confirmations, the biometric prompt, full-screen widget editor on phones |
| Snackbar | Undo after removing a recent search or hiding an app |
| Tooltips (plain and rich) | Icon buttons with a pointer; setup explanations |
| Badges | Settings tiles (things to finish), new features |
| Tabs | Widget editor (Appearance, Colours, Shortcuts) |
| Date and time pickers | Editing an event, alarm or reminder parsed from a query before creating it |
| Text fields (outlined) | Custom search address, passphrase, custom shortcut fields |
| Checkbox, radio | Multi-select and single-select lists |

## Neural Expressive

- **The glow**: a moving gradient in the scheme's own tones traces the search bar's edge while voice is listening
  or an answer is being worked out, and the voice sheet's top edge while it listens. It is never permanent.
- **Fluid answers**: instant answers are visual and act in place (start the timer, drag the volume, flip a
  toggle) instead of being text.
- **Haptics throughout**: a single vocabulary of Compose haptic types (segment ticks on sliders and button
  groups, confirm and reject on actions, toggle on and off, gesture thresholds on swipes, long press on menus).
- **Speak and type without losing context**: switching between voice and keyboard keeps the query.

## Hero moments (kept to two)

1. **The best match**: a content-coloured card with a large shaped icon that morphs on press, emphasized display
   type and a split button.
2. **Voice**: the morphing listening shape with the glow and the thin live transcript.

## Accessibility

- Touch targets at least 48 dp (56 dp on XR); visible focus and hover states; full hardware keyboard navigation.
- TalkBack labels and actions on every control, including swipe actions.
- Text to 200% without loss; contrast levels; Remove animations honoured.

## Identity

- Launcher label "Search 2" while it installs next to `main`.
- Icon: a nine-sided cookie holding a search glyph in the wallpaper's accent tones, with a monochrome layer cut out
  for themed icons.
