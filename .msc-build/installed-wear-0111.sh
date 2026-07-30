#!/usr/bin/env bash
set -euo pipefail

mkdir -p wear-installed-evidence
APK="dist/MyStudyCompanion-wear-0.11.1-debug.apk"
test -f "$APK"
unzip -tq "$APK"

adb wait-for-device
adb install -r "$APK" | tee wear-installed-evidence/install.txt
PACKAGE_PATH="$(adb shell pm path com.mystudycompanion.app.debug | tr -d '\r')"
test -n "$PACKAGE_PATH"
printf '%s\n' "$PACKAGE_PATH" > wear-installed-evidence/package-path.txt

adb shell am force-stop com.mystudycompanion.app.debug
adb logcat -c
adb shell monkey -p com.mystudycompanion.app.debug -c android.intent.category.LAUNCHER 1 \
  | tee wear-installed-evidence/launch.txt

sleep 8
adb shell dumpsys activity activities > wear-installed-evidence/activity.txt
adb shell dumpsys window windows > wear-installed-evidence/window.txt
adb logcat -d > wear-installed-evidence/logcat.txt

if ! grep -q 'com.mystudycompanion.app.debug' wear-installed-evidence/activity.txt \
  && ! grep -q 'com.mystudycompanion.app.debug' wear-installed-evidence/window.txt; then
  echo 'Wear app was installed but did not remain active after launch.' >&2
  exit 1
fi

if grep -E -A20 'FATAL EXCEPTION|AndroidRuntime' wear-installed-evidence/logcat.txt \
  | grep -q 'com.mystudycompanion.app.debug'; then
  echo 'Wear app produced a fatal Android runtime exception.' >&2
  exit 1
fi

adb exec-out screencap -p > wear-installed-evidence/watch-home.png
adb shell uiautomator dump /sdcard/watch.xml >/dev/null 2>&1 || true
adb pull /sdcard/watch.xml wear-installed-evidence/watch.xml >/dev/null 2>&1 || true

echo 'Wear APK installed, launched, remained active, and produced no package-scoped fatal runtime exception.' \
  | tee wear-installed-evidence/RESULT.txt
