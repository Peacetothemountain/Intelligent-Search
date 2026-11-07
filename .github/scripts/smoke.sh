#!/usr/bin/env bash
# Does the minified build install and open without crashing? Goldens and unit tests can't see R8 removing something the
# app needs at runtime; this can.
set -euo pipefail
package=com.pixel.intelligentsearch

adb install -r "$1"
adb logcat -b crash -c
adb shell monkey -p "$package" -c android.intent.category.LAUNCHER 1
sleep 20

crashes=$(adb logcat -b crash -d | grep -c "$package" || true)
if [ "$crashes" -ne 0 ]; then
  adb logcat -b crash -d
  echo "The app crashed on launch."
  exit 1
fi
adb shell pidof "$package" > /dev/null || { echo "The app isn't running after launch."; exit 1; }
echo "The app opened and kept running."
