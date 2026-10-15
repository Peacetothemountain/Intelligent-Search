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
- Work-profile and Private Space apps open in their own profile instead of the personal copy.
- "Meeting tonight at 9" lands at 9 PM, "alarm in 10 minutes" sets an alarm ten minutes out, and "text Sarah: on my way" is understood.
- Japanese, Hindi, Korean and other non-Latin names are searchable without being split apart.
- The Contacts switch stays off when turned off; opening Search Pills no longer resets your pills, and the shortcut slider no longer deletes pills.
- Tutorial steps can't be skipped by a quick double tap; "Slide to remove all" removes the list currently shown.
- The voice sheet vibrates once when dismissed, and its waveform no longer rebuilds the whole sheet many times a second.
- Browser History opens the right place for the selected engine: Google My Activity, Bing history, or, for engines that keep no history (DuckDuckGo, Brave Search, Startpage, Ecosia, Tor), the app that holds your history.

### Settings, security and architecture
- Settings live in one typed store; older setting names and the previous DataStore copy are migrated automatically on first launch.
- Removed the fail-open encrypted vault, hidden-API reflection, unused code and about 2 MB of unused assets.
- Hidden Apps and Backup pages share one biometric unlock gate; the saved backup passphrase is kept only in credential-protected storage.
- Singletons are provided through Hilt with one shared application scope; slow package lookups moved off the main thread.
- Large screens were split into focused files.
- Hand-rolled animations stop when system animations are turned off, and voice status changes are announced by TalkBack.
- Release builds strip warning logs as well as debug logs; CI now fails on new lint warnings.
- Bugdroid animations are stored cropped to the character (about 1 MB smaller), share one player, and the Backup animation loads only after unlocking.
- The CPU performance hint now follows the display's real refresh rate instead of always assuming 120 Hz.

### Security
- Custom search engines and custom shortcuts accept only https addresses that pass strict checks (no other schemes, credentials, script characters or look-alike hosts), are re-checked every time they are used, and open only in browser apps.
- Custom shortcuts restored from a backup can no longer carry app links.
- Hidden Apps and Backup stay protected from screenshots, screen recording, Recents thumbnails and floating windows while open, and ask again after 30 seconds away.
- The saved backup passphrase can only be decrypted while the phone is unlocked and shortly after you authenticate; export passphrases need at least 10 characters.
- Old-format backups (which anyone could create) need confirmation before restoring, and no restore can switch on contact sharing, clipboard suggestions or web suggestions.
- With Tor selected, typed addresses, copied links and copied-text searches open only in Tor Browser.
- Copied passwords (clipboard items marked sensitive) are never shown or searched.
- Other apps can no longer open specific settings pages; crash logs are no longer written to storage readable before first unlock; error logs no longer include package names or file addresses.

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
