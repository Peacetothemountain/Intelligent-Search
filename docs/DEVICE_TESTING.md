# Testing Without Plugging In the Phone

Work is checked on an emulator first, then over wireless debugging for what only the real phone can show. Neither
needs a cable.

## 1. The emulator: `IS_Pixel_XL`

A dedicated Android Virtual Device on the owner's PC. Other AVDs on that PC belong to other projects, so leave them
alone.

| | |
|---|---|
| System | Android 17, API 37 (`system-images;android-37.0;google_apis;x86_64`) |
| Screen | 1344x2992 at 480 dpi, Pixel 10 Pro XL's profile (there is no Pixel 11 profile) |
| Includes | Pixel launcher, Gboard, Google Play services, Google's permission controller |
| Rendering | The PC's GPU (`-gpu host`) |
| Boot | 45 seconds, measured 2026-11-06 |

Boot it by hand on port 5580, then install a debug build, which needs no signing key:

```
emulator -avd IS_Pixel_XL -port 5580 -no-boot-anim -no-audio -gpu host
./gradlew assembleDebug
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
```

If it refuses to install over a build signed with another key, run `adb -s emulator-5580 uninstall` with the app's package
first. A session with the owner, in their checkout, can build the release instead.

Then `device_open` with `emulator-5580`, so the owner can watch it in the Device panel, and drive it with the
`agent-device` command `device_open` returns. Take pictures with its `screenshot` command: `device_screenshot` failed
("The device hub could not complete the request") on 2026-11-06 while that command worked.

Keep to port 5580, and don't `device_open` the powered-off `IS_Pixel_XL`. T3 Code would choose the lowest free port, and
three old device sessions from October still claim `emulator-5554`, `5558` and `5560`, so `open` then fails with
`DEVICE_IN_USE`. They belong to other threads: don't close or reuse them.

T3 Code passes its own environment to `avdmanager`. If it was started before a JDK update, `device_open` fails with
`JAVA_HOME is set to an invalid directory` until T3 Code is restarted.

`avdmanager` crashes on the `android-37.0` image ("null"), so this AVD's `.ini` and `config.ini` were written by hand from
another Android 17 AVD. Recreate it the same way if it is ever lost.

To show the app notifications (Catch Up, the package tile), grant its listener with `adb shell cmd notification
allow_listener com.pixel.intelligentsearch/com.pixel.intelligentsearch.core.glance.NotificationAccessListener`, run
`adb unroot` (this image's adbd runs as root, and `cmd notification post` then fails as package "root"), then
`adb shell pm grant com.android.shell android.permission.POST_NOTIFICATIONS` and
`adb shell "cmd notification post -t 'Title' <tag> 'Text'"`. `cmd notification list` shows what is live. Undo the
listener and the grant afterwards (`disallow_listener`, `pm revoke`).

To show Up Next and its leave-by time, add an event to the emulator's local calendar (`_id=1`, "Test") with
`adb shell "content insert --uri content://com.android.calendar/events --bind calendar_id:i:1 --bind title:s:Dinner
--bind dtstart:l:<ms> --bind dtend:l:<ms> --bind eventTimezone:s:America/New_York --bind eventLocation:s:'<place>'"`,
then `pm grant` the app `READ_CALENDAR` and `ACCESS_COARSE_LOCATION`. `adb emu geo fix` doesn't move the fused fix the app
reads, so read where the emulator thinks it is (`adb shell dumpsys location | grep "last location"`) and choose a real
address within a few hours' drive of it; a farther one rightly gets no leave-by time. Delete the event and revoke both
afterwards.

To see what TalkBack actually says, turn on TalkBack settings > Display speech output (open
`com.google.android.marvin.talkback/com.android.talkback.TalkBackPreferencesActivity`), set up the screen with TalkBack off,
then `adb shell am force-stop com.callstack.agentdevice.snapshothelper` (while it runs, Android keeps TalkBack unbound) and
`adb shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`.
A panel at the bottom then shows each spoken line; read it with `adb exec-out screencap`. `adb shell input tap` and key
shortcuts bypass TalkBack (a tap clicks), so touch with `adb emu event mouse <x> <y> 0 1` then `... 0 0`, and step to the
next item by sending a row of `event mouse` moves left to right with the button held. Scroll with TalkBack off. Afterwards
set the services back to `null` and turn Display speech output off.

Unlike the phone, driving the emulator can't turn off the owner's app lock, and it holds none of their real data.

## 2. What needs the real phone

These can't be settled on the emulator:

- Real speed: cold start, jank and memory. The emulator's numbers only compare one emulator build with another.
- Haptics.
- The owner's own apps, contacts, files and wallpaper colors.
- Whatever depends on the phone's own Android build or Google's servers, such as the search engine role's allowlist.

Use wireless debugging for these. It is the same `adb`, without the cable, and the owner pairs it once:

1. On the phone: Developer options > Wireless debugging > on > Pair device with pairing code.
2. On the PC: `adb pair <ip>:<pairing port>`, then enter the code.
3. Then `adb connect <ip>:<port>`, using the port on the main Wireless debugging screen.

The phone and PC must be on the same Wi-Fi. Wireless debugging can turn off when the phone changes network, and its port
changes when it turns back on. The usual phone rules still apply (`MEMORY.md`): the release build only, only with the
owner's OK, and driving it suspends its accessibility services, which turns off their app lock. Tell them when that happens.
