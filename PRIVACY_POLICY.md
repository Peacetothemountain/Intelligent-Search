# Privacy Policy & Privacy Governance

**Effective Date:** October 4, 2026  
**Last Updated:** October 22, 2026  
**Application:** Intelligent Search (`com.pixel.intelligentsearch`)  
**Developer / Publisher:** NG Designs  
**Contact:** [support.ngdesigns@gmail.com](mailto:support.ngdesigns@gmail.com)  
**Hosted Policy URL:** [https://github.com/Peacetothemountain/Intelligent-Search/blob/main/PRIVACY_POLICY.md](https://github.com/Peacetothemountain/Intelligent-Search/blob/main/PRIVACY_POLICY.md)

---

## 1. Executive Summary & Core Privacy Commitment

**Intelligent Search** is engineered and published by **NG Designs** with a strict, non-negotiable **On-Device First Privacy Architecture**. 

We believe that your personal device search queries, contacts, schedule, files, and installed applications are your private property. Intelligent Search is built so that **your personal data stays on your device**. The exceptions are things you choose to send: web searches and live web suggestions go to the search engine you pick (see Section 3), and voice input may be handled by your device's speech recognizer (see Section 2.H). Live currency rates are downloaded from the European Central Bank without sending any of your data (see Section 3). Instant answers you switch on (dictionary definitions, weather forecasts and on-device translation) are described in Section 3; all are off until you turn them on.

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
* **Purpose**: Used optionally to rank your most frequently and recently used applications at the top of search suggestions, and to answer "screen time" searches with today's total and the apps that took most of it.
* **Processing & Security**: Queried locally via Android `UsageStatsManager`. Access requires explicit authorization through the Android "Usage Access" system settings screen. No usage metrics or device statistics are ever transmitted externally.

### F. System Settings & Toggles (`WRITE_SETTINGS`, `ACCESS_NOTIFICATION_POLICY`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`)
* **Purpose**: Powers quick search-action tiles (such as Wi-Fi status, Do Not Disturb, Flashlight, Brightness, Screen Timeout, Auto-Rotate, and Airplane Mode).
* **Processing & Security**: Reads network connectivity state locally to update toggle UI, and applies setting changes strictly when commanded by the user.

### G. Biometric Security & Hardware Keys (`USE_BIOMETRIC`)
* **Purpose**: Protects hidden applications, the encrypted backup screen, and your saved backup passphrase with your fingerprint, face unlock, or screen lock.
* **Processing & Security**: Authentication is handled entirely by Android. Encryption keys are created inside the Android Keystore (in the StrongBox security chip, such as Titan M, where the device has one, otherwise in the processor's secure area) and cannot be copied out. Biometric data is never accessible to Intelligent Search.

### H. Microphone (`android.permission.RECORD_AUDIO`)
* **Purpose**: Voice search, used only when you start it (for example from the microphone button or when Intelligent Search is your default assistant).
* **Processing & Security**: Intelligent Search records nothing itself and stores no audio. Speech is turned into text by your device's speech recognizer. The on-device recognizer is used when available; on devices without one, Android's speech recognition service (for example Google's) handles the audio under its own privacy policy.
* **User Control**: The microphone permission is requested at runtime and can be revoked in Android System Settings.

### I. Bluetooth & Secure Settings (`BLUETOOTH_CONNECT`, `WRITE_SECURE_SETTINGS`)
* **Purpose**: `BLUETOOTH_CONNECT` lets the Bluetooth quick toggle show and change Bluetooth state. `WRITE_SECURE_SETTINGS` cannot be granted by installing the app; it only works if you grant it yourself over ADB, and is then used only to register Intelligent Search as the device's search engine choice.
* **Processing & Security**: Read and applied locally only when you use these features.

### J. Approximate Location (`android.permission.ACCESS_COARSE_LOCATION`)
* **Purpose**: Only if you turn on **Use Approximate Location** for Weather Answers, the weather for "here" uses your phone's approximate location instead of the home location you chose. Precise location is never requested.
* **Processing & Security**: The permission is requested when you choose this, after an in-app explanation. The location is read only when you search for the weather, rounded to about a kilometre, and sent only to Open-Meteo for the forecast (see Section 3). It is never stored or sent anywhere else.
* **User Control**: Turn off **Use Approximate Location** in **Settings -> Web Search -> Instant Answers**, or revoke the permission in Android System Settings.

### K. On-Device Assistants (Android App Functions)
* **Purpose**: On Android 16 and later, on-device assistants can ask Intelligent Search to search your installed apps, run a calculation, or (only if you turn on **Settings -> Search Sources -> Contacts -> Share with Assistants**, which is off by default) search your contacts.
* **Processing & Security**: Requests are answered on the device. Hidden apps are never returned, and contacts are never shared while contact search or sharing is off, or contacts access is not granted.

---

## 3. Web Search, External Search Engines, Live Currency Rates & Instant Answers

When you execute a web search or request live autocomplete suggestions:
1. **Direct Communication**: Search queries are transmitted directly and securely (via HTTPS) to your chosen search engine provider (e.g., Google, DuckDuckGo, Bing, or your configured Custom Search URL).
   * **Live suggestions are on by default**: while web search is on, the text you type in the search bar is sent to your search engine as you type to fetch suggestions. Turn this off in **Settings -> Web Search -> Web Suggestions**.
2. **No Intermediary Logging**: Intelligent Search operates **no intermediary proxy server**, tracking gateway, or cloud middleman. We never intercept, inspect, log, or record your web searches.
3. **Third-Party Privacy Policies**: Your interactions with external search engines are governed by the respective provider's privacy policy:
   * [Google Privacy Policy](https://policies.google.com/privacy)
   * [DuckDuckGo Privacy Policy](https://duckduckgo.com/privacy)
   * [Microsoft Bing Privacy Statement](https://privacy.microsoft.com/en-us/privacystatement)
4. **Tor Browser Integration**: If configured, web queries can be routed directly to the official Tor Browser application on your device for enhanced anonymity. Searches sent to Tor Browser are never saved to your search history.

**Live Currency Rates** are separate from web searches and send nothing you type:
* **On by default, and only with the Calculator**: Currency answers come from the Calculator, so rates are downloaded only while both **Settings -> Search Sources -> Calculator** and **Live Currency Rates** are on. Nothing is downloaded until you first convert a currency in the search bar.
* **What is downloaded**: The European Central Bank's public daily exchange-rate file (`https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml`), over HTTPS, then about once a day. The rates are kept on your device so conversions work offline.
* **What is sent**: The request is the same for every user. It contains nothing you typed and no account, identifier or other personal information, and it names the app only as the fixed User-Agent `IntelligentSearch/1.0` instead of Android's default, which would name your phone model. As with any internet request, the European Central Bank can see your IP address.
* **Tor and turning it off**: Nothing is downloaded while Tor is your search engine. Turning off **Live Currency Rates** or the **Calculator** stops the downloads at once. With Live Currency Rates off, conversions use the rates already on your device.

**Instant Answers** show some answers inside the search overlay. Each is **off by default**, and you turn it on in **Settings -> Web Search -> Instant Answers** (Dictionary and Weather) or **Settings -> Search Sources** (On-Device Translation). Dictionary and Weather Answers also need Web and **Web Suggestions** on, and make no requests at all while Tor is your search engine. Every request is over HTTPS, carries no account or identifier, and names the app only as the fixed User-Agent `IntelligentSearch/1.0`; like any internet request, it lets the service see your IP address.
* **Dictionary Answers** (Free Dictionary API, `https://api.dictionaryapi.dev`, built from Wiktionary): when you search for a definition ("define serendipity", "serendipity meaning", "serendipity?"), the word is sent to look it up. Definitions are kept on your device for up to 30 days so they show again offline; turning Dictionary Answers off deletes them. [Free Dictionary API](https://dictionaryapi.dev/)
* **Weather Answers** (Open-Meteo, `https://api.open-meteo.com` and `https://geocoding-api.open-meteo.com`): when you search for the weather, the coordinates of the place (your home location, or your approximate location if you chose it), rounded to about a kilometre, are sent for the forecast. A place named in the search ("weather in Johnson City"), or typed when you choose your home location, is sent to Open-Meteo's place search to find it. Forecasts are kept on your device for 15 minutes and place lookups for 30 days; turning Weather Answers off deletes them. Your home location is saved in the app's settings and included in your encrypted backups. [Open-Meteo Terms & Privacy](https://open-meteo.com/en/terms)
* **On-Device Translation** (Google ML Kit): searches like "translate hello to Spanish" are translated **on your phone**; the text you translate is never sent anywhere. Each language's model (about 30 MB) is downloaded from Google's servers once, only when you tap **Download Language** on a translation card, over Wi-Fi unless you choose **Use Mobile Data**, and never while Tor is your search engine. While translation is on and in use, Google's ML Kit library may send Google anonymous usage and performance information about the translation feature (such as which languages were used and how long it took), never the text. ML Kit does not start until you first translate with the switch on. [Google Privacy Policy](https://policies.google.com/privacy)
* **Screen Time** answers are read from Android's usage statistics on your phone (Section 2.E) and send nothing.

---

## 4. Local Data Storage, Encryption, & Backup Security

* **Local Storage**: Your search history is stored in a local database (`IntelligentSearchDatabase`, Android Room) and your settings and custom bangs in local app preferences. These are not separately encrypted; they are protected by Android's app sandbox and your device's storage encryption, and are excluded from Android cloud backup and device-to-device transfer.
* **Recent Search Cards**: The search overlay keeps your recent queries as cards for quick reuse. **Clear all** on those cards removes them, and the **Search History** switch in **Settings -> Search Sources -> Web** stops saving them. Your full web search history is kept by the browser or search engine you choose, under its own privacy policy.
* **Encrypted Backups**: Intelligent Search provides an encrypted backup and restore mechanism. When exporting a backup file:
  * Application preferences, hidden apps, and search history are encrypted locally using **AES-256-GCM**.
  * With a passphrase, the key is derived from it using **PBKDF2** (600,000 iterations, random salt), so the backup can be restored on any device.
  * Without a passphrase, the key is created inside this device's Android Keystore and never leaves it, so the backup can only be restored on the same device and installation.
  * Backup files are saved strictly to the storage location or cloud drive chosen by the user via the Android system file creator.
  * Restoring a backup never switches on contact sharing, clipboard suggestions, web suggestions, Dictionary Answers, Weather Answers, Use Approximate Location, On-Device Translation or the **Live Currency Rates** switch itself; it can only leave them as you set them on this phone or switch them off. It does bring back your **Calculator** and search engine choices, which also decide whether currency rates are downloaded (see Section 3): with Live Currency Rates on, a restore that turns the Calculator on or changes your search engine from Tor to another lets the downloads resume.
  * A passphrase you choose to save on the phone is encrypted with an Android Keystore key that works only while the phone is unlocked and shortly after you authenticate.

---

## 5. Third-Party Libraries & Software Architecture

* **Zero Analytics SDKs**: Intelligent Search contains **no** third-party analytics trackers, telemetric SDKs, or crash-reporting services that monitor user behavior. The one library that reports anything to its maker is Google's ML Kit, which may send Google anonymous performance information about translation, and only once you have turned on and used On-Device Translation (see Section 3).
* **Open & Verifiable Dependencies**: The libraries integrated into Intelligent Search are open-source AndroidX (including Jetpack Compose, Room, DataStore, Media3, and App Functions), Material Components, Kotlin/KotlinX, and Hilt components maintained by Google, JetBrains, and the Android Open Source Project (AOSP), plus Google's ML Kit Translation and Language Identification for On-Device Translation (see Section 3).

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
Under the GDPR, Intelligent Search acts neither as a data controller nor as a data processor for personal data on remote servers, because the app sends no personal data to us or to any server we operate. What you choose to send to your search engine goes directly to that provider (see Section 3). Downloading live currency rates sends no personal data; like any internet request, it lets the European Central Bank see your IP address. Instant Answers you turn on send only what each needs (a word to the Free Dictionary API, rounded coordinates or a place name to Open-Meteo, language downloads from Google), as described in Section 3. All data subjects retain absolute local control, rectification, and erasure capabilities directly within the app.

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
