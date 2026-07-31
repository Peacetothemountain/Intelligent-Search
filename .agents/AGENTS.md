# Deployment Rule

Whenever we make changes to the codebase and the user wants to test them, ALWAYS automatically build and install the APK to their connected Android device by running `./gradlew installDebug`. Do not ask them to do it manually. Ensure `adb devices` is connected first if needed.

- NEVER alter the three-color Material You design language (using system_accent1, system_accent2, system_accent3) on the Material search widget icons. The user considers this design perfect. Do not attempt to forcefully tint them to single colors.
