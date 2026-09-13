# Google App & Pixel Launcher Parity Specification

This document defines the strict architectural and design contracts modeled after Google App (`com.google.android.googlequicksearchbox`) and Pixel Launcher search. All future changes, refactors, and features MUST preserve every principle and component detailed below.

---

## 1. Core Architecture & Multi-Tier Execution Pipeline

The search pipeline is organized into distinct, latency-bounded tiers to guarantee instant interaction:

```
User Keystroke
     │
     ├───► Tier 0 (<0.5ms Synchronous)
     │       ├── RadixTree direct prefix lookup
     │       ├── MathematicalExpressionEngine (arithmetic, scientific, base conversions, unit/currency)
     │       └── SystemActionRouter (Wi-Fi, Bluetooth, Flashlight, Volume sliders, Battery)
     │
     ├───► Tier 1 (<3ms Asynchronous Local Corpus)
     │       ├── In-Memory Corpus Index (Apps, Contacts, Files, Shortcuts via CorpusIndexManager)
     │       ├── Phonetic DoubleMetaphone & Radix Tree Prefix Token Search
     │       ├── Dynamic Recency & Relevance Scoring Matrix
     │       └── Instant Cached Web Suggestions (<3ms memory hit)
     │
     └───► Tier 2 (80ms Debounced Remote Network)
             └── WebSearchProvider (Google suggest API, with DDG/Bing support and LRU cache)
```

### Protected Source Files:
- `app/src/main/java/com/pixel/intelligentsearch/core/search/UnifiedSearchCoordinator.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/search/CorpusIndexManager.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/search/MathematicalExpressionEngine.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/search/SystemActionRouter.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/search/QueryNormalizer.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/search/DoubleMetaphone.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/system/NativeAppPredictionProvider.kt`
- `app/src/main/java/com/pixel/intelligentsearch/core/data/GlobalSearchProvider.kt`

---

## 2. OneBox Instant Answer Cards

Instant answers must be presented using Google-standard OneBox cards defined in `com.pixel.intelligentsearch.core.ui.OneBoxCards.kt`:

1. **MathResultOneBox**:
   - Displays scientific and arithmetic results with prominent `= <result>` typography (`GoogleSansFlex`, 32.sp bold).
   - "Copy result" and "Open Calculator" Material 3 assist chips.
2. **ConversionOneBox**:
   - Formatted unit and currency conversions with copy assist chip.
3. **DictionaryOneBox**:
   - Google Dictionary definitions with provider label.
4. **UrlNavigationOneBox**:
   - Direct web URL navigation chip card with protocol resolution.
5. **TimeWeatherOneBox**:
   - Real-time weather and time card with Alarm Clock intent integration.

### Protected Rules:
- Cards must use `MaterialTheme.colorScheme.surfaceContainerHigh` with 24.dp rounded corners and subtle 0.35 alpha outline borders.
- Never replace OneBox cards with plain text rows or web search cards.

---

## 3. Search Suggestions & Search History Styling

The suggestions list matches Google App's layout and interaction model:

1. **Clean Suggestion Pills**:
   - Web suggestions MUST NOT display a magnifying glass icon.
   - Suggestions are contained within `surfaceContainerHigh` rounded pill rows (`RoundedCornerShape(28.dp)`).
2. **Recent Search History at Top**:
   - Previous matching searches are displayed at the very top of the list.
   - History items feature the clock icon (`Icons.Default.History`) in `onSurfaceVariant` or `primary` tint.
   - One-tap removal button (`Icons.Default.Close`) on the right side of history rows to delete individual entries instantly.
3. **Diagonal Query Insertion Arrow**:
   - Every suggestion and history item has a `Icons.Default.NorthWest` arrow icon button.
   - Tapping the insert arrow updates the text field with the suggestion and positions the cursor at the end of the query without launching the search.
4. **Bold Prefix Match Annotation**:
   - The un-typed suffix of a suggestion is styled with `FontWeight.Bold` (`GoogleSansFlex`), while the matching prefix remains regular weight.
5. **Expressive Row Clickable**:
   - Rows use `Modifier.expressiveRowClickable` with bounded ripple, 0.985f micro-scale spring physics, and subtle haptic tick.

---

## 4. Monotheme Search Bar & Provider Indicator

1. **Provider Indicator Underneath Input**:
   - Inside the pill search bar (`searchBarContent`), the active search engine name (`searchProviderName`) is placed directly underneath the `BasicTextField`.
   - Revealed smoothly (`fadeIn` + `expandVertically`) as soon as typing begins (`hasStartedTyping = true`).
2. **Dynamic Search Engine Support**:
   - Automatically reflects Google, DuckDuckGo, Bing, or user-defined custom search URL hostname.
3. **Zero Clutter**:
   - No hero search cards, trending queries clutter, or image/video/news filter tabs.

---

## 5. Page Transitions & Material 3 Motion Physics

All transitions adhere to Material 3 Expressive motion tokens:

1. **Settings NavHost Shared Axis**:
   - `enterTransition`: `slideInHorizontally(0.22f)` + `fadeIn` + `scaleIn(0.94f)`.
   - `exitTransition`: `slideOutHorizontally(-0.10f)` + `fadeOut` + `scaleOut(0.96f)`.
   - Physics: `Spring.StiffnessMediumLow`, damping ratio 0.84f.
2. **Window Activity Transitions**:
   - `overrideActivityTransition` with `slide_in_right`, `slide_out_left`, `slide_in_left`, `slide_out_right`.
3. **Predictive Back Navigation**:
   - `PredictiveBackHandler` enabled on root destinations.
   - Smooth 0.92f root scale and 0.25f alpha morphing.
   - Search overlay container remains centered on back gesture and never drifts horizontally.

---

## 6. System Haptics & Vibration Ergonomics

1. **Subtle Predictive Back Micro-Haptic**:
   - `PixelHapticEngine` standardizes tactile feedback to the subtle predictive back vibration (`PRIMITIVE_LOW_TICK` at 0.85f or `PRIMITIVE_TICK` at 0.40f) across all interactive elements.
2. **Silent Widget Launch**:
   - Entering the search overlay from the home screen search bar widget has zero haptic vibration.

---

## 7. Widget Dynamic Material You Theming

1. **Dynamic Color Harmonization**:
   - `SearchWidgetProvider` uses `dynamicDarkColorScheme` / `dynamicLightColorScheme` to ensure identical color roles between launcher widgets and in-app preview.
   - Wave light animation uses `MaterialTheme.colorScheme.primary`.
2. **Widget Customization Layout**:
   - Pinned live preview at top of screen.
   - Segmented navigation tabs ("Appearance", "Widget Shortcuts").
   - Custom Color Palette on top, transparency slider under color opacity, hex code pill bar under palette, Material Design inner pill container.
   - Jitter-free dynamic squiggly slider.

---

## 8. Encrypted Backup & Restore

1. **Pill-Shaped Passphrase Bar**:
   - Input styled in Material 3 pill shape (`RoundedCornerShape(percent = 50)`) with lock icon and visibility toggle.
2. **Full Customization Persistence**:
   - `BackupContentPayload` (schemaVersion 2) persists and restores `PREFERENCES_CUSTOMISATIONS` SharedPreferences (widget colors, sizes, icons, shortcuts, layout switches, themes) alongside DataStore settings and Room search history.
3. **Interactive Password Prompt**:
   - Restoring password-protected backups prompts the user with an interactive dialog rather than failing.
4. **Main Thread Dispatching**:
   - All backup/restore callbacks execute strictly on `Dispatchers.Main`.

---

## 9. Automated Regression Testing

The `app/src/test/java/com/pixel/intelligentsearch/core/search/GoogleAppParityVerificationTest.kt` test suite validates:
- Tier 0 instant math, bitwise operations, and unit conversions.
- System action keywords and routing.
- Query normalizer diacritic stripping, tokenization, and CamelCase initials extraction.
- Radix tree prefix retrieval speed and accuracy.
- Search suggestions merged ranking, deduplication, and prefix highlighting.
- Search provider URL template resolution.
- Backup content payload schema version 2 completeness.
- Reflection checks verifying all OneBox cards exist and compile.

Always run `./gradlew testDebugUnitTest` to verify that all parity tests pass before any release.
