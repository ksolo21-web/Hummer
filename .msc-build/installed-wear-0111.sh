#!/usr/bin/env bash
set -euo pipefail

mkdir -p wear-installed-evidence
APK="dist/MyStudyCompanion-wear-0.11.1-debug.apk"
test -f "$APK"
unzip -tq "$APK"

adb_recover() {
  local attempt
  for attempt in 1 2 3 4 5; do
    adb start-server >/dev/null 2>&1 || true
    adb reconnect >/dev/null 2>&1 || true
    if adb wait-for-device >/dev/null 2>&1 \
      && adb shell 'echo ready' 2>/dev/null | grep -q ready; then
      return 0
    fi
    sleep $((attempt * 4))
  done
  echo 'ADB did not recover after five attempts.' >&2
  return 1
}

adb_install_retry() {
  local attempt
  for attempt in 1 2 3 4; do
    echo "Wear install attempt ${attempt}" | tee -a wear-installed-evidence/install.txt
    adb_recover
    if adb install -r "$APK" 2>&1 | tee -a wear-installed-evidence/install.txt; then
      return 0
    fi
    adb kill-server >/dev/null 2>&1 || true
    sleep $((attempt * 6))
  done
  echo 'Wear APK did not install after four attempts.' >&2
  return 1
}

adb_install_retry
adb_recover
PACKAGE_PATH="$(adb shell pm path com.mystudycompanion.app.debug | tr -d '\r')"
test -n "$PACKAGE_PATH"
printf '%s\n' "$PACKAGE_PATH" > wear-installed-evidence/package-path.txt

adb shell am force-stop com.mystudycompanion.app.debug
adb logcat -c
adb shell monkey -p com.mystudycompanion.app.debug -c android.intent.category.LAUNCHER 1 \
  | tee wear-installed-evidence/launch.txt

sleep 10
adb_recover
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
