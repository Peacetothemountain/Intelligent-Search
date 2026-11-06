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

Build and install the same release build the phone runs:

```
./gradlew assembleRelease
adb -s emulator-5560 install -r app/build/outputs/apk/release/app-release.apk
```

Start it from T3 Code's device tools (`device_list`, then `device_open` with `IS_Pixel_XL`) so the owner can watch it in
the Device panel, and drive it with the `agent-device` command `device_open` returns. To boot it by hand:

```
emulator -avd IS_Pixel_XL -port 5560 -no-boot-anim -no-audio -gpu host
```

`avdmanager` crashes on the `android-37.0` image ("null"), so this AVD's `.ini` and `config.ini` were written by hand from
another Android 17 AVD. Recreate it the same way if it is ever lost.

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
