# Changelog

## Unreleased - Codebase Overhaul
### Fixes
- Search history is no longer saved when history is turned off or when searching through Tor Browser, and deleting a history entry now matches regardless of letter case.
- Voice recognition stops when the assistant sheet is dismissed instead of listening in the background.
- Editing, removing and restoring web shortcuts (bangs) is saved in one step, so quick edits can no longer undo each other.
- The launcher's "Voice search" shortcut now starts voice search.
- Calculator: thousands separators ("1,000 + 1"), bits versus bytes ("100 Mb in MB"), and out-of-range bit shifts are handled correctly.
- Typing a file name such as "notes.txt" no longer offers "Go to website".
- App search ranks apps you have opened recently and often first (requires Usage Access).
- Icon and thumbnail caches are capped by memory rather than entry count.

### Settings, security and architecture
- Settings live in one typed store; older setting names and the previous DataStore copy are migrated automatically on first launch.
- Removed the fail-open encrypted vault, hidden-API reflection, unused code and about 2 MB of unused assets.
- Hidden Apps and Backup pages share one biometric unlock gate; the saved backup passphrase is kept only in credential-protected storage.
- Singletons are provided through Hilt with one shared application scope; slow package lookups moved off the main thread.
- Large screens were split into focused files.
- Hand-rolled animations stop when system animations are turned off, and voice status changes are announced by TalkBack.
- Release builds strip warning logs as well as debug logs; CI now fails on new lint warnings.

## Release Version 8.9 - System Architecture & Security Hardening
- **Titan M3+ Hardware Security**: Integrated StrongBox KeyStore (`KeyGenParameterSpec`) with hardware-backed key attestation verification, TEE fallbacks, and biometrically gated cryptographic operations.
- **Trampoline-Free Window Acceleration**: Direct subclassing of `MainActivity` in `SearchActivity` and `WidgetActivity`, eliminating cold-boot trampoline latency.
- **Continuous 120 FPS Rendering**: Memory zero-allocation in AGSL atmospheric shader loops and draw scopes, utilizing `derivedStateOf`, Choreographer pacing, and predictive back gestures.
- **On-Device Local Inference Engine**: Decoupled on-device vector embeddings (`LocalInferenceEngine`) with zero cloud telemetry or external AI dependencies.
- **Architectural Standards**: Clean unidirectional data flow (MVI/MVVM), strict edge-to-edge system insets via `WindowInsetsAnimationCompat`, and complete purge of legacy boilerplate metadata.

## Release Version 7.0 - Recent Updates

### Motion & Navigation Physics
- **RenderThread GPU Layer Compositing**: Migrated search overlay container morphing to `.graphicsLayer { scaleX = ..., scaleY = ..., transformOrigin = TransformOrigin(0.5f, 1.0f) }`, eliminating CPU main-thread re-layout calls for ultra-smooth rendering.
- **Velocity Momentum Preservation**: Passed `initialVelocity` momentum to `overlayProgressAnim.animateTo()`, ensuring smooth gesture returns across fast swipe-ups and mid-flight reversals.
- **Critically Damped Pixel Springs**: Applied critically damped spring physics (`dampingRatio = 0.92f`, `stiffness = 250f`) for instant gesture tracking without oscillation or bounce.
- **Settings Activity Slide Transition**: Registered smooth right-to-left horizontal slide transitions using Android 14+ `overrideActivityTransition`.

### Wallpaper & Theme Engine
- **Wallpaper Visibility**: Preserved `windowShowWallpaper` and `windowIsTranslucent` window attributes so live home wallpaper displays behind translucent search overlay screens when enabled.
- **Material You Dynamic Colors**: Enforced dynamic three-color Material You palette mappings on `@color/widget_icon_primary`, `@color/widget_icon_secondary`, and `@color/widget_icon_tertiary`.
- **System Design Widget Alignment**: Enforced `widget_search_colorful.xml` layout rules (hardcoded Google colors, strict G-Logo and Microphone structure).

### UI & System Cleanups
- **Widget Settings Row Scoping**: Scoped "Widget Action Icon" customization dropdown to Material Design theme configurations only.
- **Repository Optimization**: Purged intermediate scratch scripts, temporary logs, and build artifacts from git history.
