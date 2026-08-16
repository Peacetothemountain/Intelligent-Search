<div align="center">

# 🔍 Intelligent Search

**A Pixel-Inspired Search Overlay & Home Screen Widget Experience for Android**

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge&logo=androidstudio)](https://github.com/Peacetothemountain/Intelligent-Search)
[![Target Android](https://img.shields.io/badge/Android-15%2B%20%2F%20SDK%2035-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com)
[![Version Code](https://img.shields.io/badge/Version-v7.8%20(106)-blue?style=for-the-badge)](https://github.com/Peacetothemountain/Intelligent-Search/releases)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)](LICENSE)

<br/>

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">](https://play.google.com/store/apps/details?id=com.pixel.intelligentsearch)

*Click the badge above to download Intelligent Search on the Google Play Store.*

---

</div>

## 🌟 Overview

**Intelligent Search** is a fast, highly customizable search application designed to bring authentic Google Pixel Search Bar styling and system-wide search overlays to any Android device. Engineered with modern Jetpack Compose, AGSL GPU shaders, dynamic Material You color systems, and RenderThread layer compositing, Intelligent Search delivers instant access to installed apps, system settings, contact shortcuts, web search engines, and device media.

---

## ✨ Features at a Glance

<table>
  <tr>
    <td width="50%">
      <h3>📱 Authentic Pixel Styling</h3>
      <ul>
        <li>Native Pixel Launcher pill containers and dynamic elevation.</li>
        <li>Dual design engines: <b>Material Design Expressive</b> (Monet 3-color palette) and <b>System Design</b> (Google brand colors).</li>
        <li>Smooth gesture-driven search overlay expansion.</li>
      </ul>
    </td>
    <td width="50%">
      <h3>⚡ Ultra-Fast Performance</h3>
      <ul>
        <li><b>RenderThread Layer Compositing</b> for zero-overhead animation.</li>
        <li><b>Critically Damped Spring Physics</b> for instant touch response.</li>
        <li><b>AGSL GPU Ambient Shader</b> for dynamic background visuals.</li>
      </ul>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <h3>🎨 Deep Customization</h3>
      <ul>
        <li>Resizable home screen widgets with custom action buttons.</li>
        <li>Background transparency controls (0% to 100%).</li>
        <li>Third-party icon pack support for search cards and widgets.</li>
      </ul>
    </td>
    <td width="50%">
      <h3>🛡️ Privacy First Architecture</h3>
      <ul>
        <li><b>100% On-Device Processing</b> for local search queries.</li>
        <li>Encrypted local SQLite search history database.</li>
        <li>User-controlled permission gating for Contacts & Calendar.</li>
      </ul>
    </td>
  </tr>
</table>

---

## 🏗️ Architecture & Technology Stack

```
Intelligent Search Architecture
├── UI Layer (Jetpack Compose, AGSL Shaders, Material You Monet)
├── Feature Modules
│   ├── Search Overlay (SearchViewModel, Matrix & Shader Backgrounds)
│   ├── Settings & Customization (SettingsScreens, Icon Pack Resolution)
│   ├── Widgets & Tiles (SearchWidgetProvider, QuickSearchTileService)
│   └── Voice & Intents (IntelligentVoiceInteractionSession)
└── Core Infrastructure
    ├── Data Providers (SystemDataProvider, WebSearchProvider, SettingsManager)
    └── Local Database (Room SQLite - IntelligentSearchDatabase)
```

---

## 🛠️ Build Requirements & Setup

- **IDE**: Android Studio Jellyfish (2024.1.1) or newer
- **JDK Target**: JDK 17 / JDK 21
- **Android Target SDK**: SDK 35 (Android 15+) / Android 17 compatibility target
- **Minimum Supported SDK**: SDK 29 (Android 10+)

### Building from Source

```bash
# Clone the repository
git clone https://github.com/Peacetothemountain/Intelligent-Search.git
cd Intelligent-Search

# Build Release APK
./gradlew assembleRelease
```

The compiled release binary is generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📦 Automated Release Pipeline

Automated build and release workflows are managed via GitHub Actions (`.github/workflows/release.yml`) utilizing secure **Blacksmith runners** (`blacksmith-4vcpu-ubuntu-2404`) for fast, isolated compilation.

- **Releases Page**: Compiled release binaries (`v7.8`) are available under the repository [Releases Section](https://github.com/Peacetothemountain/Intelligent-Search/releases).

---

## ⚖️ License & Intellectual Property

This software repository, source code, visual assets, and underlying algorithms are proprietary software of **NB Designs**.

- **Official License**: Read the complete terms in the [`LICENSE`](LICENSE) file.
- **Privacy Policy**: Read our data processing policies in [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md).

Copyright (c) 2026 **NB Designs**. All Rights Reserved.
