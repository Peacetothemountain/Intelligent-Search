<div align="center">

<h1 align="center">
  <svg width="42" height="42" viewBox="0 0 24 24" fill="none" style="vertical-align: middle; margin-right: 8px;">
    <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z" fill="#4285F4"/>
    <path d="M12 1.5l.6 1.4 1.4.6-1.4.6-.6 1.4-.6-1.4-1.4-.6 1.4-.6.6-1.4z" fill="#FBBC05"/>
    <path d="M19 4.5l.4.9.9.4-.9.4-.4.9-.4-.9-.9-.4.9-.4.4-.9z" fill="#EA4335"/>
    <path d="M17 10.5l.5 1.1 1.1.5-1.1.5-.5 1.1-.5-1.1-1.1-.5 1.1-.5.5-1.1z" fill="#34A853"/>
  </svg>
  Intelligent Search
</h1>

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

## 🚀 Join the Google Play Closed Beta Test

Want early access to test new release builds of **Intelligent Search** on your device? You can sign up for access to the **Google Play Store Closed Beta Track**!

<div align="center">

[<img src="https://img.shields.io/badge/Join%20Closed%20Beta-Google%20Play-3DDC84?style=for-the-badge&logo=googleplay&logoColor=white" alt="Join Closed Beta">](https://forms.gle/wZCyu67zc4L7qgAs8)

</div>

> [!IMPORTANT]
> **🔒 Closed Beta Privacy & Non-Spam Guarantee**  
> Your email address is collected **exclusively** for the purpose of registering your Google Account for Google Play Store Closed Beta testing permissions.  
> - **Zero Marketing Emails**: We will **never** send promotional emails, newsletters, or marketing communications.  
> - **Zero Third-Party Sharing**: We will **never** sell, share, or transfer your email address to any third party.  
> - **Single Purpose**: Your email address is strictly used to populate the Google Play Console closed testing list so your Google Play Store account can download beta builds.

---

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

---

## 🏷️ Trademarks & Legal Disclaimer

Android, Google, Google Pixel, Google Play, and the Google Play logo are registered trademarks of **Google LLC**. 

- **Intelligent Search** is an independent software application developed by **NB Designs**.
- This application and repository are not affiliated with, sponsored by, or endorsed by Google LLC.
- All web search queries and search engine options (Google, DuckDuckGo, Bing, or Custom Search URLs) execute directly via standard web intents to their respective providers.

---

Copyright (c) 2026 **NB Designs**. All Rights Reserved.
