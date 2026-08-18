# Changelog

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
