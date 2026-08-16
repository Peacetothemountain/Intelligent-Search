# Intelligent Search

**Intelligent Search** is an advanced, high-performance, pixel-inspired search overlay and home screen widget application engineered for Android. Built with modern Jetpack Compose, AGSL GPU shaders, dynamic Material You color systems, and RenderThread layer compositing, it delivers fluid 120Hz search interactions across installed apps, system settings, contact shortcuts, web search engines, and local device media.

---

## Key Features

### 🔍 Native Pixel Search Overlay & Widgets
- **Pixel Launcher Aesthetics**: Authentic Google Pixel Search Bar styling featuring rounded pill containers, custom elevation physics, and system-wide search overlays.
- **Dual Design Engine Architecture**:
  - **Material Design Expressive**: Supports Monet dynamic 3-color pastel palettes (`system_accent1`, `system_accent2`, `system_accent3`).
  - **System Design**: Features hardcoded Google brand colors with strict G-Logo and Microphone structure.
- **Customizable Home Widgets**: Resizable widget options supporting background transparency, custom action buttons (Voice Search, Gemini, Web Shortcuts), and custom icon packs.

### ⚡ 120Hz RenderThread Performance
- **GPU Layer Compositing**: Search overlay container morphing is driven via `.graphicsLayer { scaleX = ..., scaleY = ... }`, completely eliminating main-thread re-layout calls for smooth 120Hz rendering.
- **Critically Damped Spring Motion**: Custom spring animation physics (`dampingRatio = 0.92f`, `stiffness = 250f`) provide zero-latency touch response and smooth gesture returns during swipe-ups.
- **GPU AGSL Stardust Shader**: Real-time AGSL GPU particle shader (`APP_WIDE_STARDUST_SHADER`) rendering dynamic ambient waves and stardust motion with zero CPU overhead.

### 🛡️ Privacy First & On-Device Security
- **100% On-Device Processing**: Search indexing, fuzzy taxonomy matching, contacts resolution, and calendar querying execute entirely on your physical device.
- **Local Search History Governance**: Search queries are stored exclusively in a local SQLite database (`IntelligentSearchDatabase`) managed via Android Room. Search history can be toggled on/off or cleared at any time via **Settings -> Search Sources -> Web -> Search History**.
- **Strict Permission Guardrails**: Calendar events (`READ_CALENDAR`) and contacts (`READ_CONTACTS`) are gated behind explicit user settings toggles and queried strictly on-demand.

---

## Project Structure

```
com.pixel.intelligentsearch/
├── core/
│   ├── data/             # SystemDataProvider, SettingsManager, RecentSearchManager, Database
│   ├── navigation/       # Navigation routes and intent handlers
│   ├── theme/            # Color tokens, typography, and Material You themes
│   └── ui/               # AGSL GPU shader backgrounds and reusable UI components
├── feature/
│   ├── search/           # SearchOverlayScreen, SearchViewModel, MatrixBackground
│   ├── settings/         # SettingsScreens, CustomIconsScreen, BouncyClickable
│   ├── voice/            # VoiceInteractionSession
│   └── widget/           # SearchWidgetProvider, WidgetActivity
└── App.kt                # Application entry point and Hilt Dependency Injection graph
```

---

## Build Requirements & Setup

- **IDE**: Android Studio Jellyfish (2024.1.1) or newer
- **JDK Version**: JDK 17 / JDK 21
- **Android SDK Target**: SDK 35 (Android 15+) / Android 17 compatibility target
- **Minimum SDK**: SDK 29 (Android 10+)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/Peacetothemountain/Intelligent-Search.git
cd Intelligent-Search

# Build the release APK
./gradlew assembleRelease
```

The compiled binary will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## CI/CD & Automated Releases

Automated build and release pipelines are managed via GitHub Actions (`.github/workflows/release.yml`) utilizing secure **Blacksmith runners** (`blacksmith-4vcpu-ubuntu-2404`) for fast, isolated compilation.

- **Releases Page**: Tagged release binaries (`v7.8`) are published to the repository [Releases Section](https://github.com/Peacetothemountain/Intelligent-Search/releases).

---

## Licensing & Privacy Governance

This repository and all contained source code, binaries, UI designs, and algorithms are proprietary software.

- **License**: Refer to the [`LICENSE`](LICENSE) file for complete proprietary software terms.
- **Privacy Policy**: Refer to [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) for full on-device data processing governance.

Copyright (c) 2026 **NB Designs**. All Rights Reserved.
