# Privacy Policy & Privacy Governance

**Effective Date:** October 4, 2026  
**Last Updated:** October 4, 2026  
**Application:** Intelligent Search (`com.pixel.intelligentsearch`)  
**Developer / Publisher:** NG Designs  
**Contact:** [support.ngdesigns@gmail.com](mailto:support.ngdesigns@gmail.com)  
**Hosted Policy URL:** [https://github.com/Peacetothemountain/Intelligent-Search/blob/main/PRIVACY_POLICY.md](https://github.com/Peacetothemountain/Intelligent-Search/blob/main/PRIVACY_POLICY.md)

---

## 1. Executive Summary & Core Privacy Commitment

**Intelligent Search** is engineered and published by **NG Designs** with a strict, non-negotiable **On-Device First Privacy Architecture**. 

We believe that your personal device search queries, contacts, schedule, files, and installed applications are your private property. Intelligent Search is built so that **your personal data never leaves your device**.

* **Zero Cloud Data Exfiltration**: We do not maintain, operate, or connect to remote servers that collect, store, profile, or analyze your searches or personal information.
* **Zero Advertising & Zero Tracking**: Intelligent Search contains **no advertisements**, **no advertising SDKs**, **no commercial tracking beacons**, and **no third-party behavioral analytics libraries** (such as Firebase Analytics, Mixpanel, or Facebook SDK).
* **Zero Sale of User Data**: We have never sold, rented, monetized, or shared your personal information, and we never will.

---

## 2. On-Device Data Processing & Android Permissions

Intelligent Search requires certain Android platform permissions solely to enable search and device-management functionality on your local device. Every permission is handled with minimal privilege and strict user transparency:

### A. Contacts (`android.permission.READ_CONTACTS`)
* **Purpose**: Allows you to search your contacts by name, display phone numbers, and trigger one-tap calls or SMS messages directly from the search bar.
* **Processing & Security**: Contact indexing is executed entirely within your device's local memory. Contact data is **never** transmitted over the internet, never backed up to remote servers, and never shared with third parties.
* **User Control**: Contacts search is disabled by default. Enabling it requires your explicit runtime consent, preceded by an in-app prominent disclosure dialog explaining local-only usage. You can disable Contacts search at any time in **Settings -> Search Shortcuts -> Contacts**, or revoke the permission via Android System Settings.

### B. Calendar (`android.permission.READ_CALENDAR`)
* **Purpose**: Displays your upcoming agenda items, meetings, and events directly in the search overlay when you search for relevant terms.
* **Processing & Security**: Queried strictly on-demand from the Android `CalendarContract` provider. Events are processed in ephemeral memory during the active search session and are **never** persisted to remote servers or transmitted off-device.
* **User Control**: Calendar access is requested at runtime only when Calendar search is toggled on in Settings.

### C. Installed Applications & Shortcuts (`android.permission.QUERY_ALL_PACKAGES`)
* **Purpose**: As a Pixel-inspired universal search launcher and indexer, the primary core functionality of Intelligent Search is locating, organizing, and launching installed applications and dynamic app shortcuts.
* **Processing & Security**: App metadata (package names, display labels, launch intents, dynamic shortcuts) is indexed into a local, in-memory radix tree and cached locally. This data remains strictly within the app's sandboxed storage.

### D. Files, Photos, & Videos (Android System Pickers)
* **Zero Broad Storage Permissions**: In full compliance with Google Play's Photo and Video Permissions policy, Intelligent Search **does not** request or declare broad storage permissions (such as `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `READ_MEDIA_VISUAL_USER_SELECTED`, or `READ_EXTERNAL_STORAGE`).
* **Scoped System Pickers**: When you choose to index or open local files and media, Intelligent Search relies exclusively on Google-recommended system pickers:
  * **Android Photo Picker (`ActivityResultContracts.PickMultipleVisualMedia`)**: Allows you to interactively pick specific photos and videos without granting broad storage access to your media library.
  * **Storage Access Framework (SAF - `ActivityResultContracts.OpenDocumentTree`)**: Allows you to explicitly designate a specific directory for document indexing. Persistable read grants are retained locally on your device.
* **Data Guarantee**: File paths, thumbnails, and media contents are processed 100% locally and are never exfiltrated.

### E. App Usage Statistics (`android.permission.PACKAGE_USAGE_STATS`)
* **Purpose**: Used optionally to rank your most frequently and recently used applications at the top of search suggestions.
* **Processing & Security**: Queried locally via Android `UsageStatsManager`. Access requires explicit authorization through the Android "Usage Access" system settings screen. No usage metrics or device statistics are ever transmitted externally.

### F. System Settings & Toggles (`WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`)
* **Purpose**: Powers quick search-action tiles (such as Wi-Fi status, Do Not Disturb, Flashlight, Brightness, Screen Timeout, Auto-Rotate, and Airplane Mode).
* **Processing & Security**: Reads network connectivity state locally to update toggle UI, and applies setting changes strictly when commanded by the user.

### G. Biometric Security & Hardware Keys (`USE_BIOMETRIC`)
* **Purpose**: Protects vaulted (hidden) applications, private search history, and sensitive app configuration with biometric authentication (fingerprint/face unlock).
* **Processing & Security**: Handled strictly by Android KeyStore, hardware Secure Element (SE) / StrongBox, and Titan M3+ security chips where supported. Biometric data never leaves the hardware security enclave and is never accessible to Intelligent Search.

---

## 3. Web Search & External Search Engines

When you execute a web search or request live autocomplete suggestions:
1. **Direct Communication**: Search queries are transmitted directly and securely (via HTTPS) to your chosen search engine provider (e.g., Google, DuckDuckGo, Bing, Brave, or your configured Custom Search URL).
2. **No Intermediary Logging**: Intelligent Search operates **no intermediary proxy server**, tracking gateway, or cloud middleman. We never intercept, inspect, log, or record your web searches.
3. **Third-Party Privacy Policies**: Your interactions with external search engines are governed by the respective provider's privacy policy:
   * [Google Privacy Policy](https://policies.google.com/privacy)
   * [DuckDuckGo Privacy Policy](https://duckduckgo.com/privacy)
   * [Microsoft Bing Privacy Statement](https://privacy.microsoft.com/en-us/privacystatement)
4. **Tor Browser Integration**: If configured, web queries can be routed directly to the official Tor Browser application on your device for enhanced anonymity.

---

## 4. Local Data Storage, Encryption, & Backup Security

* **Local SQLite Database (Android Room)**: Your search history and custom bang configurations are stored in an encrypted local database (`IntelligentSearchDatabase`) isolated within Android's protected application sandbox.
* **One-Tap History Erasure**: You can clear all search history, reset index caches, or disable search history logging at any time via **Settings -> Search Sources -> Web -> Search History -> Clear History**.
* **Encrypted Backups**: Intelligent Search provides an encrypted backup and restore mechanism. When exporting a backup file:
  * Application preferences and configurations are encrypted locally using industry-standard **AES-256-GCM** encryption.
  * Encryption keys are derived using **PBKDF2** with high iteration counts and cryptographic salt from a user-supplied password.
  * Backup files are saved strictly to the storage location or cloud drive chosen by the user via the Android system file creator.

---

## 5. Third-Party Libraries & Software Architecture

* **Zero Analytics SDKs**: Intelligent Search contains **no** third-party analytics trackers, telemetric SDKs, or crash-reporting services that monitor user behavior.
* **Open & Verifiable Dependencies**: All third-party libraries integrated into Intelligent Search are standard, open-source AndroidX, Jetpack Compose, KotlinX, Hilt, and Room components maintained by Google and the Android Open Source Project (AOSP).

---

## 6. Closed Beta Testing & Email Governance

For users who voluntarily enroll in the **Google Play Store Closed Beta Testing Track**:
* **Single-Purpose Collection**: Email addresses provided via the Google Play Closed Beta sign-up form are used **exclusively** to register your Google Account for Google Play Store testing permissions.
* **Strict Non-Disclosure**: Your email address is never sold, rented, leased, disclosed, or transferred to any third party.
* **Zero Marketing / Non-Spam Guarantee**: NG Designs will **never** send promotional marketing emails, advertisements, sponsored messages, or newsletters.

---

## 7. User Data Rights & Data Deletion

Because Intelligent Search operates on an On-Device First model and does not collect or transmit user data to external servers:
* **Right to Access & Control**: You have direct, unhindered access to all settings, search history, and indexed data within the application UI on your physical device.
* **Right to Erasure**: You can delete all search history at any time from within the app settings. 
* **Complete Removal**: Uninstalling Intelligent Search from your device immediately and permanently purges all local app data, databases, preferences, and cache files stored in the app's sandboxed storage.

---

## 8. Compliance with International Privacy Regulations

### A. General Data Protection Regulation (GDPR / UK GDPR)
Under the GDPR, Intelligent Search acts neither as a data controller nor as a data processor for personal data on remote servers, because no personal data is transferred off your device. All data subjects retain absolute local control, rectification, and erasure capabilities directly within the app.

### B. California Consumer Privacy Act (CCPA / CPRA)
* We do not collect personal information for commercial sale.
* We do not "sell" or "share" personal information or sensitive personal information as defined under the California Consumer Privacy Act.

### C. Children's Online Privacy Protection Act (COPPA) & GDPR-K
Intelligent Search is a general-audience device utility and is not directed to children under the age of 13 (or under 16 in applicable European jurisdictions). We do not knowingly collect or solicit personal information from children.

---

## 9. Updates to This Policy

We may periodically update this Privacy Policy to reflect application updates, new features, or regulatory compliance requirements. Any modifications will be indicated by the **"Last Updated"** date at the top of this document. Continued use of Intelligent Search following any updates constitutes your acceptance of the revised policy.

---

## 10. Contact Information & Developer Support

If you have any questions, feedback, or security inquiries regarding this Privacy Policy or our privacy practices, please contact us:

* **Developer / Publisher:** NG Designs  
* **Support Email:** [support.ngdesigns@gmail.com](mailto:support.ngdesigns@gmail.com)  
* **Official Repository:** [https://github.com/Peacetothemountain/Intelligent-Search](https://github.com/Peacetothemountain/Intelligent-Search)  
* **Application Package:** `com.pixel.intelligentsearch`
