# What dev2 must do

`dev2` is Intelligent Search rebuilt from nothing. It shares no code and no look with `main`; what it shares is
this list. Every function and option below exists in `main` (9.3, 123) and must exist in `dev2`, working at least
as well. Each line is ticked when it works on a phone.

Status: `[ ]` not started, `[~]` built, not yet checked on a phone, `[x]` done.

## 1. Ways in

- [ ] Search screen opened from the launcher icon, the home screen widgets, the Quick Settings tile and the
      long-press app shortcuts.
- [ ] Handles the system search intents: `WEB_SEARCH`, `SEARCH`, `GLOBAL_SEARCH`, `SEARCH_LONG_PRESS`, the Pixel
      Launcher and Launcher3 search actions, and `ASSIST`/`VOICE_ASSIST`.
- [ ] Default digital assistant: a voice interaction service, a session (the voice sheet) and a real speech
      recognition service that forwards to the on-device recognizer.
- [ ] Android 17 QPR2 search engine role: shows the device's state and the one-time ADB steps (setup checklist
      and developer note).
- [ ] Settings reachable from the launcher, `APPLICATION_PREFERENCES`, `SEARCH_SETTINGS` and
      `MANAGE_SEARCH_PREFERENCES`.
- [ ] Back swipe from settings returns to the search screen (option).
- [ ] Global search content provider for system search (apps only, never history).
- [ ] AppFunctions (Android 16+): assistants can search apps and, if the user allows, contacts.

## 2. What search finds

- [ ] Apps, including the work profile and Private Space when unlocked; hidden apps are never shown.
- [ ] App shortcuts (static, dynamic, pinned), inline under their app, recent shortcuts as suggestions, a limit
      of 1 to 20.
- [ ] Contacts: call, message, open; direct call on tap (option); result count 0 to 20; permission flow.
- [ ] Calendar: upcoming events.
- [ ] Files: folders the user picked (Storage Access Framework) and photos or videos from the photo picker;
      hidden files (option); thumbnails (option); result count 0 to 20; open with the right app.
- [ ] Web suggestions from the chosen engine (Google, Bing, DuckDuckGo); result count 0 to 20.
- [ ] Previous searches (option), swipe to remove, fill into the search bar, clear all.
- [ ] Calculator: scientific expressions, percentages, bitwise operations, unit and currency conversion.
- [ ] Natural language actions: calendar events, alarms, timers, reminders, messages (including WhatsApp),
      calls, navigation ("directions home").
- [ ] System toggles from search: Wi-Fi, Bluetooth, flashlight, battery saver, Do Not Disturb, airplane mode,
      auto-rotate, hotspot, dark theme, NFC, location; inline volume sliders for media, ring and alarm.
- [ ] Smart clipboard suggestions (option, with a privacy warning).
- [ ] Suggested apps when the search is empty: Pixel Launcher predictions, then recent usage, then the app's own
      launch record.
- [ ] Context-aware quick apps (option).
- [ ] Debug: mock trending queries and a mock large dataset.

## 3. How search behaves

- [ ] Typo tolerance (fuzzy search, option), phonetic matching for names ("Steven" finds "Stephen"), acronyms,
      word starts ("notes" finds "Keep Notes").
- [ ] "Did you mean", using the keyboard's own spell checker ("Chec the timee" offers "Check the time").
- [ ] Best match highlighted with quick actions; ghost text completes it; Enter opens it.
- [ ] Priority weights for apps, contacts, files and web (four steps each).
- [ ] Quick launch: open an app as soon as it is the only match (option).
- [ ] Search bar at the bottom (option) and results ordered from the bottom up (option).
- [ ] Quick app panel: pills that search the current query inside chosen apps (up to 20, profiles, reorder),
      horizontal layout (option).
- [ ] Quick web shortcuts ("bangs"): 13 built in (Google, YouTube, Wikipedia, Reddit, Maps, Amazon, GitHub, Drive,
      DuckDuckGo, X, Stack Overflow, Play Store, Spotify), custom ones, hide or restore built-ins, a custom
      trigger symbol, and the direct shortcuts of installed apps.
- [ ] Quick search buttons for YouTube, Wikipedia, Play Store and Maps (each optional).
- [ ] Search engine: Google, Bing, DuckDuckGo, Tor (Tor Browser only, never a normal browser), or a custom URL
      template (`%s`), validated.
- [ ] Browser history and Google activity shortcuts that respect private engines.
- [ ] Recent searches recorded only from inside the app.

## 4. Look and feel options

- [ ] Theme: system, light, dark.
- [ ] Show wallpaper behind search, background blur, background transparency, search bar opacity.
- [ ] App animations on or off (and the system's Remove animations is always honoured).
- [ ] Haptics on or off.
- [ ] Icon packs, adaptive icon shape, dynamic icon masking, and monochrome outlines for apps that lack them.
- [ ] Search screen on or off (fall back to the assistant).
- [ ] Assistant starts listening right away, or opens for typing (option).

## 5. Widgets and shortcuts

- [ ] Two home screen search bar widget designs, resizable, drawn from boot (direct boot aware).
- [ ] Widget editor: colours per element, a colour studio (hue, saturation, lightness, opacity), sync all colours,
      transparency, G icon on or off, voice and assistant buttons on or off.
- [ ] Four widget shortcut slots from: voice search, Lens, assistant, translate (text and camera), weather,
      sports, dictionary, homework, finance, saved, news, Now Playing, any app, any web address.
- [ ] Pin a widget from inside the app.
- [ ] Quick Settings tile, home screen shortcut, launch portal page.

## 6. Privacy and safety

- [ ] Hidden apps, behind fingerprint, face or screen lock; protected pages block screenshots and other apps'
      overlays.
- [ ] Encrypted backup: export, import, restore, with an optional passphrase; device-bound keys in StrongBox or
      the TEE when there is no passphrase.
- [ ] Automatic weekly backup into a chosen folder, encrypted with a passphrase.
- [ ] Restored values are validated, so a bad backup file can never crash search.
- [ ] Only http(s) addresses are opened; custom URLs are checked; Tor searches never reach a normal browser.
- [ ] Nothing leaves the phone through Android backup; no analytics.

## 7. Help and tools

- [ ] First-run tutorial and the Finish Setting Up checklist.
- [ ] Support email, privacy policy.
- [ ] Debug menu (unlocked from the search bar): verbose logging, a performance readout, forced search errors,
      simulated web latency, mock data, reset the tutorial, clear history, device diagnostics (memory, battery,
      thermals), and turning debug off.
